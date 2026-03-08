package com.ldfd.seckill.dto;

import java.time.LocalDateTime;

public record SeckillOrderMessage(String requestId, Long userId, Long goodsId, LocalDateTime createdAt) {
}

