package com.gpmall.order.biz.handler;/**
 * Created by mic on 2019/8/1.
 */

import com.gpmall.commons.tool.exception.BizException;
import com.gpmall.order.biz.context.CreateOrderContext;
import com.gpmall.order.biz.context.TransHandlerContext;
import com.gpmall.order.constant.OrderRetCode;
import com.gpmall.order.constants.OrderConstants;
import com.gpmall.order.dal.persistence.OrderMapper;
import com.gpmall.user.IMemberService;
import com.gpmall.user.dto.QueryMemberRequest;
import com.gpmall.user.dto.QueryMemberResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.dubbo.config.annotation.Reference;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.IntegerCodec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 腾讯课堂搜索【咕泡学院】
 * 官网：www.gupaoedu.com
 * 风骚的Mic 老师
 * create-date: 2019/8/1-下午4:47
 *
 * TODO:  如何解决商品的超卖问题？ 我这里没有进行扩展，有兴趣的同学可以进行扩展
 */
@Slf4j
@Component
public class ValidateHandler extends AbstractTransHandler {

    @Autowired
    OrderMapper orderMapper;

    @Reference(mock = "com.gpmall.order.biz.mock.MockMemberService",check = false)
    IMemberService memberService;

    @Autowired
    RedissonClient redissonClient;

    @Override
    public boolean isAsync() {
        return false;
    }

    @Override
    public boolean handle(TransHandlerContext context) {
        log.info("begin ValidateHandler :context:"+context);
        CreateOrderContext createOrderContext=(CreateOrderContext)context;

        //根据md5从缓存中获取，大于1表示当前1s中重复请求，数据库层面也对key做了唯一约束
        String uniqueKey = gernatorOrderItemUnique(createOrderContext);
        RMapCache<String, Integer> mapCache = redissonClient
                .getMapCache(OrderConstants.CAR_ITEM_UNQIQUE_KEY, IntegerCodec.INSTANCE);
        mapCache.putIfAbsent(uniqueKey, 0, 1, TimeUnit.SECONDS);
        Integer incr = mapCache.addAndGet(uniqueKey, 1);
        if(incr > 1){
            throw new BizException(OrderRetCode.DB_SAVE_EXCEPTION.getCode(),OrderRetCode.DB_SAVE_EXCEPTION.getMessage());
        }

        createOrderContext.setUniqueKey(uniqueKey);
        QueryMemberRequest queryMemberRequest =new QueryMemberRequest();
        queryMemberRequest.setUserId(createOrderContext.getUserId());
        QueryMemberResponse response=memberService.queryMemberById(queryMemberRequest);
        if(OrderRetCode.SUCCESS.getCode().equals(response.getCode())){
            createOrderContext.setBuyerNickName(response.getUsername());
        }else{
            throw new BizException(response.getCode(),response.getMsg());
        }
        return true;
    }

    private String gernatorOrderItemUnique(CreateOrderContext context){
        StringBuilder localKey = new StringBuilder();
        context.getCartProductDtoList().parallelStream().forEach(cartProductDto -> {
            localKey.append(context.getUserId())
                    .append(String.valueOf(cartProductDto.getProductId()))
                    .append(String.valueOf(cartProductDto.getSalePrice()))
                    .append(String.valueOf(cartProductDto.getProductNum()))
                    .append(FastDateFormat.getInstance("yyMMddHHmmss").format(new Date()));
        });

        return DigestUtils.md5DigestAsHex(String.valueOf(localKey).getBytes());

    }


}
