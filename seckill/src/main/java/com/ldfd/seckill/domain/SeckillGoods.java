package com.ldfd.seckill.domain;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeckillGoods {

    private Long goodsId;

    private Integer stock;

    private Integer perUserLimit;
}

