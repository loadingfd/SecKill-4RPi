package com.ldfd.seckill.repository;

import com.ldfd.seckill.domain.SeckillOrder;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeckillOrderRepository extends JpaRepository<SeckillOrder, Long> {

    boolean existsByRequestId(String requestId);

    long countByUserIdAndGoodsId(Long userId, Long goodsId);

    Optional<SeckillOrder> findByRequestId(String requestId);
}
