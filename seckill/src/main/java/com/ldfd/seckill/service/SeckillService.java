package com.ldfd.seckill.service;

import com.ldfd.seckill.domain.SeckillGoods;
import com.ldfd.seckill.dto.SeckillOrderMessage;
import com.ldfd.seckill.dto.SeckillSubmitResult;
import com.ldfd.seckill.mapper.SeckillGoodsMapper;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    private final RedisStockService redisStockService;
    private final OrderMessagePublisherService orderMessagePublisherService;
    private final SeckillGoodsMapper seckillGoodsMapper;

    public SeckillService(
            RedisStockService redisStockService,
            OrderMessagePublisherService orderMessagePublisherService,
            SeckillGoodsMapper seckillGoodsMapper) {
        this.redisStockService = redisStockService;
        this.orderMessagePublisherService = orderMessagePublisherService;
        this.seckillGoodsMapper = seckillGoodsMapper;
    }

    public SeckillSubmitResult submit(Long goodsId, Long userId) {
        long result = redisStockService.preDeduct(goodsId, userId);
        if (result == -1L) {
            return new SeckillSubmitResult(false, null, "sold out");
        }
        if (result == -2L) {
            return new SeckillSubmitResult(false, null, "exceed per-user limit");
        }
        if (result != 1L) {
            return new SeckillSubmitResult(false, null, "busy, please retry");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        SeckillOrderMessage message = new SeckillOrderMessage(requestId, userId, goodsId, LocalDateTime.now());
        try {
            orderMessagePublisherService.publish(message);
            return new SeckillSubmitResult(true, requestId, "accepted");
        } catch (RuntimeException ex) {
            redisStockService.rollbackReservation(goodsId, userId);
            return new SeckillSubmitResult(false, null, "send message failed");
        }
    }

    @Transactional
    public void initGoodsStock(Long goodsId, Integer stock, Integer perUserLimit) {
        if (seckillGoodsMapper.existsById(goodsId)) {
            throw new IllegalStateException("activity already initialized; only new goodsId can use new limit");
        }
        SeckillGoods goods = new SeckillGoods();
        goods.setGoodsId(goodsId);
        goods.setStock(stock);
        goods.setPerUserLimit(perUserLimit);
        seckillGoodsMapper.insert(goods);
        redisStockService.initStock(goodsId, stock, perUserLimit);
    }
}
