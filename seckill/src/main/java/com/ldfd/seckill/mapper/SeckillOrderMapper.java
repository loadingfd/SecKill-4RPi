package com.ldfd.seckill.mapper;

import com.ldfd.seckill.domain.SeckillOrder;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface SeckillOrderMapper {

    @Select("SELECT COUNT(1) FROM seckill_order WHERE request_id = #{requestId}")
    int countByRequestId(@Param("requestId") String requestId);

    @Select("SELECT COUNT(1) FROM seckill_order WHERE user_id = #{userId} AND goods_id = #{goodsId}")
    long countByUserIdAndGoodsId(@Param("userId") Long userId, @Param("goodsId") Long goodsId);

    @Select("SELECT id, request_id AS requestId, user_id AS userId, goods_id AS goodsId, status, created_at AS createdAt " +
            "FROM seckill_order WHERE request_id = #{requestId}")
    SeckillOrder findByRequestId(@Param("requestId") String requestId);


    @Insert("INSERT INTO seckill_order(request_id, user_id, goods_id, status, created_at) " +
            "VALUES(#{requestId}, #{userId}, #{goodsId}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(SeckillOrder order);
}


