package com.ldfd.seckill.service;

import java.time.Duration;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisStockService {

    private static final String PRE_DEDUCT_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock <= 0 then
              return -1
            end
            if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
              return -2
            end
            redis.call('DECR', KEYS[1])
            redis.call('SADD', KEYS[2], ARGV[1])
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> preDeductLua;
    private final String stockKeyPrefix;
    private final String userKeyPrefix;

    public RedisStockService(
            StringRedisTemplate redisTemplate,
            @Value("${seckill.stock-key-prefix:seckill:stock:}") String stockKeyPrefix,
            @Value("${seckill.user-key-prefix:seckill:users:}") String userKeyPrefix) {
        this.redisTemplate = redisTemplate;
        this.stockKeyPrefix = stockKeyPrefix;
        this.userKeyPrefix = userKeyPrefix;
        this.preDeductLua = new DefaultRedisScript<>(PRE_DEDUCT_SCRIPT, Long.class);
    }

    public long preDeduct(Long goodsId, Long userId) {
        Long result = redisTemplate.execute(
                preDeductLua,
                List.of(stockKey(goodsId), userSetKey(goodsId)),
                String.valueOf(userId));
        return result == null ? -99L : result;
    }

    public void rollbackReservation(Long goodsId, Long userId) {
        redisTemplate.opsForValue().increment(stockKey(goodsId));
        redisTemplate.opsForSet().remove(userSetKey(goodsId), String.valueOf(userId));
    }

    public void initStock(Long goodsId, int stock) {
        redisTemplate.opsForValue().set(stockKey(goodsId), String.valueOf(stock));
        redisTemplate.expire(userSetKey(goodsId), Duration.ofHours(24));
    }

    private String stockKey(Long goodsId) {
        return stockKeyPrefix + goodsId;
    }

    private String userSetKey(Long goodsId) {
        return userKeyPrefix + goodsId;
    }
}
