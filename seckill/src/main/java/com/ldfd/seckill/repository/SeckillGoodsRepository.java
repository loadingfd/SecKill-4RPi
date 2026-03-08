package com.ldfd.seckill.repository;

import com.ldfd.seckill.domain.SeckillGoods;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SeckillGoodsRepository extends JpaRepository<SeckillGoods, Long> {

    @Modifying
    @Query("update SeckillGoods g set g.stock = g.stock - 1 where g.goodsId = :goodsId and g.stock > 0")
    int deductOne(@Param("goodsId") Long goodsId);
}

