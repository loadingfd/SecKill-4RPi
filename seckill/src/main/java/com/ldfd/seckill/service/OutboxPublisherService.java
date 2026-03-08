package com.ldfd.seckill.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ldfd.seckill.config.RabbitSeckillConfig;
import com.ldfd.seckill.domain.OutboxEvent;
import com.ldfd.seckill.dto.SeckillOrderMessage;
import com.ldfd.seckill.repository.OutboxEventRepository;
import java.time.LocalDateTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class OutboxPublisherService {

    private static final List<String> RETRYABLE_STATUS = List.of("NEW", "RETRY");

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final int outboxMaxRetry;

    public OutboxPublisherService(
            OutboxEventRepository outboxEventRepository,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            @Value("${seckill.outbox-max-retry:20}") int outboxMaxRetry) {
        this.outboxEventRepository = outboxEventRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.outboxMaxRetry = outboxMaxRetry;
    }

    @Transactional
    public void saveAndPublish(SeckillOrderMessage message) {
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setRequestId(message.requestId());
        outboxEvent.setPayload(toJson(message));
        outboxEvent.setStatus("NEW");
        outboxEvent.setRetryCount(0);
        outboxEvent.setNextRetryAt(LocalDateTime.now());
        outboxEvent.setCreatedAt(LocalDateTime.now());
        outboxEvent.setUpdatedAt(LocalDateTime.now());
        outboxEventRepository.save(outboxEvent);
        publish(outboxEvent, message);
    }

    @Scheduled(fixedDelay = 3000)
    @Transactional
    public void retryPendingOutbox() {
        List<OutboxEvent> waiting = outboxEventRepository.findByStatusInAndNextRetryAtLessThanEqualOrderByIdAsc(
                RETRYABLE_STATUS, LocalDateTime.now(), PageRequest.of(0, 100));
        for (OutboxEvent event : waiting) {
            SeckillOrderMessage message = fromJson(event.getPayload());
            publish(event, message);
        }
    }

    @Transactional
    public void markConsumed(String requestId) {
        outboxEventRepository.findByRequestId(requestId).ifPresent(event -> {
            event.setStatus("CONSUMED");
            event.setUpdatedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
        });
    }

    private void publish(OutboxEvent event, SeckillOrderMessage message) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitSeckillConfig.ORDER_EXCHANGE,
                    RabbitSeckillConfig.ORDER_ROUTING_KEY,
                    message,
                    new CorrelationData(message.requestId()));
            event.setStatus("SENT");
            event.setUpdatedAt(LocalDateTime.now());
            event.setLastError(null);
        } catch (AmqpException ex) {
            int currentRetry = event.getRetryCount() + 1;
            event.setRetryCount(currentRetry);
            event.setStatus(currentRetry >= outboxMaxRetry ? "FAILED" : "RETRY");
            event.setLastError(trimError(ex.getMessage()));
            event.setNextRetryAt(LocalDateTime.now().plusSeconds(Math.min(60, currentRetry * 2L)));
            event.setUpdatedAt(LocalDateTime.now());
            log.error("publish outbox failed requestId={} retry={} error={}", message.requestId(), currentRetry, ex.getMessage());
        }
        outboxEventRepository.save(event);
    }

    private String toJson(SeckillOrderMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot serialize outbox payload", ex);
        }
    }

    private SeckillOrderMessage fromJson(String payload) {
        try {
            return objectMapper.readValue(payload, SeckillOrderMessage.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("cannot deserialize outbox payload", ex);
        }
    }

    private String trimError(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 390 ? message.substring(0, 390) : message;
    }
}
