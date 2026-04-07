package com.ldfd.seckill.service;

import com.ldfd.seckill.domain.SeckillOrder;
import com.ldfd.seckill.dto.SeckillOrderMessage;
import com.ldfd.seckill.mapper.SeckillGoodsMapper;
import com.ldfd.seckill.mapper.SeckillOrderMapper;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Slf4j
@Service
public class OrderConsumerService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillGoodsMapper seckillGoodsMapper;
    private final RedisStockService redisStockService;

    public OrderConsumerService(
            SeckillOrderMapper seckillOrderMapper,
            SeckillGoodsMapper seckillGoodsMapper,
            RedisStockService redisStockService) {
        this.seckillOrderMapper = seckillOrderMapper;
        this.seckillGoodsMapper = seckillGoodsMapper;
        this.redisStockService = redisStockService;
    }

    @RabbitListener(
            queues = "seckill.order.queue",
            concurrency = "${seckill.consumer.order-concurrency:8-16}"
    )
    @Transactional
    public void consume(SeckillOrderMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws IOException {
        if (!redisStockService.markOrderConsuming(message.requestId())) {
            log.debug("skip duplicate order consume requestId={}", message.requestId());
            channel.basicAck(tag, false);
            return;
        }

        // Keep broker ack/reject aligned with DB transaction result.
        registerTransactionCallbacks(message.requestId(), channel, tag);

        try {
            if (seckillOrderMapper.countByRequestId(message.requestId()) > 0) {
                return;
            }

            int updated = seckillGoodsMapper.deductOne(message.goodsId());
            if (updated == 0) {
                redisStockService.rollbackReservation(message.goodsId(), message.userId());
                return;
            }

            SeckillOrder order = new SeckillOrder();
            order.setRequestId(message.requestId());
            order.setGoodsId(message.goodsId());
            order.setUserId(message.userId());
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());
            seckillOrderMapper.insert(order);
        } catch (Exception ex) {
            log.error("consume order message failed requestId={} error={}", message.requestId(), ex.getMessage(), ex);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        }
    }

    private void registerTransactionCallbacks(String requestId, Channel channel, long tag) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    channel.basicAck(tag, false);
                } catch (IOException ex) {
                    log.error("ack order message failed requestId={} error={}", requestId, ex.getMessage(), ex);
                }
            }

            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    redisStockService.clearOrderConsuming(requestId);
                    try {
                        channel.basicReject(tag, false);
                    } catch (IOException ex) {
                        log.error("reject order message failed requestId={} error={}", requestId, ex.getMessage(), ex);
                    }
                }
            }
        });
    }
}
