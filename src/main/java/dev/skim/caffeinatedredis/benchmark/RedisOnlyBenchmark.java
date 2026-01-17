package dev.skim.caffeinatedredis.benchmark;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Redis-only cache adapter for benchmarking.
 * Represents L2-only caching strategy (network-based).
 */
public class RedisOnlyBenchmark {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String name;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisOnlyBenchmark(String name, RedisTemplate<String, Object> redisTemplate,
                              String keyPrefix, Duration ttl) {
        this.name = name;
        this.redisTemplate = redisTemplate;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    public String getName() {
        return name;
    }

    public void put(String key, Object value) {
        String redisKey = keyPrefix + key;
        if (ttl != null && !ttl.isZero()) {
            redisTemplate.opsForValue().set(redisKey, value, ttl);
        } else {
            redisTemplate.opsForValue().set(redisKey, value);
        }
    }

    public Object get(String key) {
        String redisKey = keyPrefix + key;
        return redisTemplate.opsForValue().get(redisKey);
    }

    public void evict(String key) {
        String redisKey = keyPrefix + key;
        redisTemplate.delete(redisKey);
    }

    public void clear() {
        var keys = redisTemplate.keys(keyPrefix + "*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Get read operation for benchmark
     */
    public Consumer<Integer> getReadOperation(int keySpace) {
        return i -> get("key-" + (i % keySpace));
    }

    /**
     * Get write operation for benchmark
     */
    public Consumer<Integer> getWriteOperation(int keySpace) {
        return i -> put("key-" + (i % keySpace), "value-" + i);
    }

    /**
     * Pre-populate cache with test data
     */
    public void prePopulate(int count) {
        for (int i = 0; i < count; i++) {
            put("key-" + i, "value-" + i);
        }
    }
}

