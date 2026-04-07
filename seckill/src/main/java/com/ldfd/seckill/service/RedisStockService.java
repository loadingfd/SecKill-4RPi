package com.ldfd.seckill.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisStockService {

    private static final int USER_COUNT_BUCKETS = 32;

    private static final String PRE_DEDUCT_SCRIPT = """
            local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
            if stock <= 0 then
              return -1
            end
            local limit = tonumber(redis.call('GET', KEYS[3]) or '1')
            local bought = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0')
            if bought >= limit then
              return -2
            end
            redis.call('DECR', KEYS[1])
            redis.call('HINCRBY', KEYS[2], ARGV[1], 1)
            return 1
            """;

    private static final String ROLLBACK_SCRIPT = """
            redis.call('INCR', KEYS[1])
            local bought = tonumber(redis.call('HINCRBY', KEYS[2], ARGV[1], -1) or '0')
            if bought <= 0 then
              redis.call('HDEL', KEYS[2], ARGV[1])
            end
            return 1
            """;

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> preDeductLua;
    private final DefaultRedisScript<Long> rollbackLua;
    private final String stockKeyPrefix;
    private final String userCountKeyPrefix;
    private final String userLimitKeyPrefix;
    private final String orderConsumeKeyPrefix;
    private final Duration orderConsumeTtl;

    public RedisStockService(
            StringRedisTemplate redisTemplate,
            @Value("${seckill.stock-key-prefix:seckill:stock:}") String stockKeyPrefix,
            @Value("${seckill.user-count-key-prefix:seckill:user:count:}") String userCountKeyPrefix,
            @Value("${seckill.user-limit-key-prefix:seckill:user:limit:}") String userLimitKeyPrefix,
            @Value("${seckill.order-consume-key-prefix:seckill:order:consume:}") String orderConsumeKeyPrefix,
            @Value("${seckill.order-consume-ttl-hours:24}") long orderConsumeTtlHours) {
        this.redisTemplate = redisTemplate;
        this.stockKeyPrefix = stockKeyPrefix;
        this.userCountKeyPrefix = userCountKeyPrefix;
        this.userLimitKeyPrefix = userLimitKeyPrefix;
        this.orderConsumeKeyPrefix = orderConsumeKeyPrefix;
        this.orderConsumeTtl = Duration.ofHours(orderConsumeTtlHours);
        this.preDeductLua = new DefaultRedisScript<>(PRE_DEDUCT_SCRIPT, Long.class);
        this.rollbackLua = new DefaultRedisScript<>(ROLLBACK_SCRIPT, Long.class);
    }

    public long preDeduct(Long goodsId, Long userId) {
        return redisTemplate.execute(
                preDeductLua,
                List.of(stockKey(goodsId), userCountBucketKey(goodsId, userId), userLimitKey(goodsId)),
                String.valueOf(userId));
    }

    public boolean markOrderConsuming(String requestId) {
        Boolean result = redisTemplate.opsForValue().setIfAbsent(orderConsumeKey(requestId), "1", orderConsumeTtl);
        return Boolean.TRUE.equals(result);
    }

    public void clearOrderConsuming(String requestId) {
        redisTemplate.delete(orderConsumeKey(requestId));
    }

    public void rollbackReservation(Long goodsId, Long userId) {
        redisTemplate.execute(
                rollbackLua,
                List.of(stockKey(goodsId), userCountBucketKey(goodsId, userId)),
                String.valueOf(userId));
    }

    public void initStock(Long goodsId, int stock, int perUserLimit) {
        redisTemplate.opsForValue().set(stockKey(goodsId), String.valueOf(stock));
        redisTemplate.opsForValue().set(userLimitKey(goodsId), String.valueOf(perUserLimit));
        List<String> bucketKeys = new ArrayList<>(USER_COUNT_BUCKETS);
        for (int bucket = 0; bucket < USER_COUNT_BUCKETS; bucket++) {
            bucketKeys.add(userCountBucketKey(goodsId, bucket));
        }
        redisTemplate.delete(bucketKeys);
    }

    private String stockKey(Long goodsId) {
        return stockKeyPrefix + goodsId;
    }

    private String userCountBucketKey(Long goodsId, Long userId) {
        return userCountBucketKey(goodsId, Math.floorMod(userId, USER_COUNT_BUCKETS));
    }

    private String userCountBucketKey(Long goodsId, int bucket) {
        return userCountKeyPrefix + goodsId + ":" + bucket;
    }

    private String userLimitKey(Long goodsId) {
        return userLimitKeyPrefix + goodsId;
    }

    private String orderConsumeKey(String requestId) {
        return orderConsumeKeyPrefix + requestId;
    }
}
