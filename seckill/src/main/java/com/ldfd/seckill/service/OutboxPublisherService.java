package com.ldfd.seckill.service;

import com.ldfd.seckill.config.RabbitSeckillConfig;
import com.ldfd.seckill.dto.SeckillOrderMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class OutboxPublisherService {

    private final RabbitTemplate rabbitTemplate;

    public OutboxPublisherService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @Retryable(
            retryFor = AmqpException.class,
            maxAttemptsExpression = "${seckill.publisher.max-attempts}",
            backoff = @Backoff(
                    delayExpression = "${seckill.publisher.base-delay-ms}",
                    multiplier = 2.0
            )
    )
    public void publish(SeckillOrderMessage message) {
        rabbitTemplate.convertAndSend(
                RabbitSeckillConfig.ORDER_EXCHANGE,
                RabbitSeckillConfig.ORDER_ROUTING_KEY,
                message,
                new CorrelationData(message.requestId()));
    }

    @Recover
    public void recover(AmqpException ex, SeckillOrderMessage message) {
        log.error("publish order message failed after retries requestId={} error={}",
                message.requestId(), ex.getMessage(), ex);
        throw ex;
    }
}
