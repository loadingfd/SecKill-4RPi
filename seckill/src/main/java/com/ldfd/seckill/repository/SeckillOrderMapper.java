package com.ldfd.seckill.repository;

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


    @Insert("INSERT INTO seckill_order(request_id, user_id, goods_id, status, created_at) " +
            "VALUES(#{requestId}, #{userId}, #{goodsId}, #{status}, #{createdAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(SeckillOrder order);
}

