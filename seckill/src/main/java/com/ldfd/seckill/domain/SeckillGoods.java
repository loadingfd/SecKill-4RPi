package com.ldfd.seckill.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "seckill_goods")
public class SeckillGoods {

    @Id
    @Column(name = "goods_id")
    private Long goodsId;

    @Column(nullable = false)
    private Integer stock;
}

