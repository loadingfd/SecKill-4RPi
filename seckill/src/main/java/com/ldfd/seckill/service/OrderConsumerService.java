package com.ldfd.seckill.service;

import com.ldfd.seckill.dto.SeckillOrderMessage;
import com.ldfd.seckill.domain.SeckillOrder;
import com.ldfd.seckill.repository.SeckillGoodsRepository;
import com.ldfd.seckill.repository.SeckillOrderRepository;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OrderConsumerService {

    private final SeckillOrderRepository seckillOrderRepository;
    private final SeckillGoodsRepository seckillGoodsRepository;
    private final RedisStockService redisStockService;
    private final OutboxPublisherService outboxPublisherService;

    public OrderConsumerService(
            SeckillOrderRepository seckillOrderRepository,
            SeckillGoodsRepository seckillGoodsRepository,
            RedisStockService redisStockService,
            OutboxPublisherService outboxPublisherService) {
        this.seckillOrderRepository = seckillOrderRepository;
        this.seckillGoodsRepository = seckillGoodsRepository;
        this.redisStockService = redisStockService;
        this.outboxPublisherService = outboxPublisherService;
    }

    @RabbitListener(queues = "seckill.order.queue")
    @Transactional
    public void consume(SeckillOrderMessage message, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag)
            throws IOException {
        try {
            if (seckillOrderRepository.existsByRequestId(message.requestId())
                    || seckillOrderRepository.existsByUserIdAndGoodsId(message.userId(), message.goodsId())) {
                outboxPublisherService.markConsumed(message.requestId());
                channel.basicAck(tag, false);
                return;
            }

            int updated = seckillGoodsRepository.deductOne(message.goodsId());
            if (updated == 0) {
                redisStockService.rollbackReservation(message.goodsId(), message.userId());
                outboxPublisherService.markConsumed(message.requestId());
                channel.basicAck(tag, false);
                return;
            }

            SeckillOrder order = new SeckillOrder();
            order.setRequestId(message.requestId());
            order.setGoodsId(message.goodsId());
            order.setUserId(message.userId());
            order.setStatus("CREATED");
            order.setCreatedAt(LocalDateTime.now());
            seckillOrderRepository.save(order);
            outboxPublisherService.markConsumed(message.requestId());
            channel.basicAck(tag, false);
        } catch (Exception ex) {
            log.error("consume order message failed requestId={} error={}", message.requestId(), ex.getMessage(), ex);
            channel.basicReject(tag, false);
        }
    }
}

