package com.ldfd.seckill.service;

import com.ldfd.seckill.domain.SeckillOrder;
import com.ldfd.seckill.dto.SeckillOrderMessage;
import com.ldfd.seckill.repository.SeckillGoodsMapper;
import com.ldfd.seckill.repository.SeckillOrderMapper;
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

        try {
            if (seckillOrderMapper.countByRequestId(message.requestId()) > 0) {
                channel.basicAck(tag, false);
                return;
            }

            int updated = seckillGoodsMapper.deductOne(message.goodsId());
            if (updated == 0) {
                redisStockService.rollbackReservation(message.goodsId(), message.userId());
                channel.basicAck(tag, false);
                return;
            }

            SeckillOrder order = new SeckillOrder();
            order.setRequestId(message.requestId());
            order.setGoodsId(message.goodsId());
            order.setUserId(message.userId());
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());
            seckillOrderMapper.insert(order);
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            redisStockService.clearOrderConsuming(message.requestId());
            log.error("consume order message failed requestId={} error={}", message.requestId(), ex.getMessage(), ex);
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            channel.basicReject(tag, false);
        }
    }
}
