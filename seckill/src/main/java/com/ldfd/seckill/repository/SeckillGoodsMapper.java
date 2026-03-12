package com.ldfd.seckill.repository;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface SeckillGoodsMapper {

    @Update("UPDATE seckill_goods SET stock = stock - 1 WHERE goods_id = #{goodsId} AND stock > 0")
    int deductOne(@Param("goodsId") Long goodsId);
}

