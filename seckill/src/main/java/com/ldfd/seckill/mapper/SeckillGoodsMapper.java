package com.ldfd.seckill.mapper;

import com.ldfd.seckill.domain.SeckillGoods;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface SeckillGoodsMapper {

    @Select("SELECT COUNT(1) > 0 FROM seckill_goods WHERE goods_id = #{goodsId}")
    boolean existsById(@Param("goodsId") Long goodsId);

    @Insert("INSERT INTO seckill_goods(goods_id, stock, per_user_limit) VALUES(#{goodsId}, #{stock}, #{perUserLimit})")
    @Options(useGeneratedKeys = false)
    int insert(SeckillGoods goods);

    @Update("UPDATE seckill_goods SET stock = stock - 1 WHERE goods_id = #{goodsId} AND stock > 0")
    int deductOne(@Param("goodsId") Long goodsId);
}


