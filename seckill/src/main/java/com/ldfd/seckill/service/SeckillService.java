package com.ldfd.seckill.service;

import com.ldfd.seckill.domain.SeckillGoods;
import com.ldfd.seckill.dto.SeckillOrderMessage;
import com.ldfd.seckill.dto.SeckillSubmitResult;
import com.ldfd.seckill.repository.SeckillGoodsRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeckillService {

    private final RedisStockService redisStockService;
    private final OutboxPublisherService outboxPublisherService;
    private final SeckillGoodsRepository seckillGoodsRepository;

    public SeckillService(
            RedisStockService redisStockService,
            OutboxPublisherService outboxPublisherService,
            SeckillGoodsRepository seckillGoodsRepository) {
        this.redisStockService = redisStockService;
        this.outboxPublisherService = outboxPublisherService;
        this.seckillGoodsRepository = seckillGoodsRepository;
    }

    public SeckillSubmitResult submit(Long goodsId, Long userId) {
        long result = redisStockService.preDeduct(goodsId, userId);
        if (result == -1L) {
            return new SeckillSubmitResult(false, null, "sold out");
        }
        if (result == -2L) {
            return new SeckillSubmitResult(false, null, "duplicate request");
        }
        if (result != 1L) {
            return new SeckillSubmitResult(false, null, "busy, please retry");
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        SeckillOrderMessage message = new SeckillOrderMessage(requestId, userId, goodsId, LocalDateTime.now());
        try {
            outboxPublisherService.publish(message);
            return new SeckillSubmitResult(true, requestId, "accepted");
        } catch (RuntimeException ex) {
            redisStockService.rollbackReservation(goodsId, userId);
            return new SeckillSubmitResult(false, null, "send message failed");
        }
    }

    @Transactional
    public void initGoodsStock(Long goodsId, Integer stock) {
        SeckillGoods goods = seckillGoodsRepository.findById(goodsId).orElseGet(SeckillGoods::new);
        goods.setGoodsId(goodsId);
        goods.setStock(stock);
        seckillGoodsRepository.save(goods);
        redisStockService.initStock(goodsId, stock);
    }
}
