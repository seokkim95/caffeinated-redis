package dev.skim.caffeinatedredis.benchmark;

import dev.skim.caffeinatedredis.cache.TwoLevelCache;
import org.springframework.cache.Cache;

import java.util.function.Consumer;

/**
 * TwoLevelCache adapter for benchmarking.
 * Represents L1+L2 caching strategy (Near Cache).
 */
public class TwoLevelCacheBenchmark {

    private final Cache cache;
    private final String name;

    public TwoLevelCacheBenchmark(String name, Cache cache) {
        this.name = name;
        this.cache = cache;
    }

    public String getName() {
        return name;
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public Object get(String key) {
        Cache.ValueWrapper wrapper = cache.get(key);
        return wrapper != null ? wrapper.get() : null;
    }

    public void evict(String key) {
        cache.evict(key);
    }

    public void clear() {
        cache.clear();
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

    /**
     * Get the underlying TwoLevelCache for L1-only operations
     */
    public TwoLevelCache getTwoLevelCache() {
        if (cache instanceof TwoLevelCache) {
            return (TwoLevelCache) cache;
        }
        return null;
    }
}

