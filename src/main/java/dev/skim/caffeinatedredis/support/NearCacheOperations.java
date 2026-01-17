package dev.skim.caffeinatedredis.support;

import dev.skim.caffeinatedredis.cache.TwoLevelCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for Near Cache operations.
 * Provides convenient methods for cache management and monitoring.
 */
@Slf4j
@RequiredArgsConstructor
public class NearCacheOperations {

    private final TwoLevelCacheManager cacheManager;

    /**
     * Get value from cache
     *
     * @param cacheName cache name
     * @param key       cache key
     * @param type      value type
     * @return cached value or null if not found
     */
    public <T> T get(String cacheName, Object key, Class<T> type) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return null;
        }
        Cache.ValueWrapper wrapper = cache.get(key);
        if (wrapper == null) {
            return null;
        }
        return type.cast(wrapper.get());
    }

    /**
     * Put value into cache
     *
     * @param cacheName cache name
     * @param key       cache key
     * @param value     value to cache
     */
    public void put(String cacheName, Object key, Object value) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.put(key, value);
        }
    }

    /**
     * Evict a specific key from cache
     *
     * @param cacheName cache name
     * @param key       cache key to evict
     */
    public void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.evict(key);
            log.debug("Evicted key '{}' from cache '{}'", key, cacheName);
        }
    }

    /**
     * Clear all entries from a specific cache
     *
     * @param cacheName cache name to clear
     */
    public void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
            log.debug("Cleared cache '{}'", cacheName);
        }
    }

    /**
     * Clear all L1 caches across all cache names
     */
    public void clearAllL1() {
        cacheManager.clearAllL1();
        log.debug("Cleared all L1 caches");
    }

    /**
     * Get all cache names
     *
     * @return collection of cache names
     */
    public Collection<String> getCacheNames() {
        return cacheManager.getCacheNames();
    }

    /**
     * Get statistics for a specific cache
     *
     * @param cacheName cache name
     * @return cache statistics or null if cache not found
     */
    public TwoLevelCacheManager.CacheStats getStats(String cacheName) {
        return cacheManager.getCacheStats(cacheName);
    }

    /**
     * Get statistics for all caches
     *
     * @return map of cache name to statistics
     */
    public Map<String, TwoLevelCacheManager.CacheStats> getAllStats() {
        Map<String, TwoLevelCacheManager.CacheStats> allStats = new HashMap<>();
        for (String cacheName : getCacheNames()) {
            TwoLevelCacheManager.CacheStats stats = getStats(cacheName);
            if (stats != null) {
                allStats.put(cacheName, stats);
            }
        }
        return allStats;
    }

    /**
     * Check if a key exists in cache
     *
     * @param cacheName cache name
     * @param key       cache key
     * @return true if key exists, false otherwise
     */
    public boolean exists(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache == null) {
            return false;
        }
        return cache.get(key) != null;
    }

    /**
     * Get value from cache, or compute and cache if not present
     *
     * @param cacheName   cache name
     * @param key         cache key
     * @param type        value type
     * @param valueLoader function to compute value if not in cache
     * @return cached or computed value
     */
    public <T> T getOrCompute(String cacheName, Object key, Class<T> type, java.util.function.Supplier<T> valueLoader) {
        T value = get(cacheName, key, type);
        if (value != null) {
            return value;
        }

        value = valueLoader.get();
        if (value != null) {
            put(cacheName, key, value);
        }
        return value;
    }
}

