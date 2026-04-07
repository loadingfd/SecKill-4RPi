package com.ldfd.seckill.domain;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SeckillOrder {

    private Long id;

    private String requestId;

    private Long userId;

    private Long goodsId;

    private String status;

    private LocalDateTime createdAt;
}

