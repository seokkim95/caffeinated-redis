package dev.skim.caffeinatedredis.benchmark;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Redis-only cache adapter for benchmarking.
 * Represents L2-only caching strategy.
 */
public class RedisOnlyBenchmark {

    private final RedisTemplate<String, Object> redisTemplate;
    private final String name;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisOnlyBenchmark(String name, RedisTemplate<String, Object> redisTemplate,
                              String keyPrefix, Duration ttl) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
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
        return redisTemplate.opsForValue().get(keyPrefix + key);
    }

    public void evict(String key) {
        redisTemplate.delete(keyPrefix + key);
    }

    public void clear() {
        // For this benchmark we use a per-run unique prefix (bench:<runId>:...), so KEY lookup is bounded.
        // Using KEYS is acceptable and keeps the benchmark code simple.
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
