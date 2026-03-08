package com.ldfd.seckill.repository;

import com.ldfd.seckill.domain.OutboxEvent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    Optional<OutboxEvent> findByRequestId(String requestId);

    List<OutboxEvent> findByStatusInAndNextRetryAtLessThanEqualOrderByIdAsc(
            List<String> status, LocalDateTime now, Pageable pageable);
}

