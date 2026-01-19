package dev.skim.caffeinatedredis.benchmark;

import org.springframework.cache.Cache;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Cache adapter for benchmarking the TwoLevel/Near Cache strategy.
 */
public class TwoLevelCacheBenchmark {

    private final Cache cache;
    private final String name;

    public TwoLevelCacheBenchmark(String name, Cache cache) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.cache = Objects.requireNonNull(cache, "cache must not be null");
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
        return i -> get(keyOf(i, keySpace));
    }

    /**
     * Get write operation for benchmark
     */
    public Consumer<Integer> getWriteOperation(int keySpace) {
        return i -> put(keyOf(i, keySpace), "value-" + i);
    }

    /**
     * Pre-populate cache with test data
     */
    public void prePopulate(int count) {
        for (int i = 0; i < count; i++) {
            put("key-" + i, "value-" + i);
        }
    }

    private static String keyOf(int i, int keySpace) {
        return "key-" + (i % keySpace);
    }
}
