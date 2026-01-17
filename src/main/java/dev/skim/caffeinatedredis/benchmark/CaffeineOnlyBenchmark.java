package dev.skim.caffeinatedredis.benchmark;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Caffeine-only cache adapter for benchmarking.
 * Represents L1-only caching strategy.
 */
public class CaffeineOnlyBenchmark {

    private final Cache<String, Object> cache;
    private final String name;

    public CaffeineOnlyBenchmark(String name, int maxSize, Duration expireAfterWrite) {
        this.name = name;
        this.cache = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfterWrite(expireAfterWrite)
                .recordStats()
                .build();
    }

    public String getName() {
        return name;
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public Object get(String key) {
        return cache.getIfPresent(key);
    }

    public void evict(String key) {
        cache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();
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

    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats() {
        return cache.stats();
    }
}

