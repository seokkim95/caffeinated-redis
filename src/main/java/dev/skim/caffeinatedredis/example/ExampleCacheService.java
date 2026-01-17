package dev.skim.caffeinatedredis.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Example service demonstrating Near Cache usage.
 * This class shows how to use the library.
 */
@Slf4j
@Service
public class ExampleCacheService {

    /**
     * Cache lookup example.
     * - First looks up in L1 (Caffeine)
     * - On L1 miss, looks up in L2 (Redis)
     * - On complete miss, executes actual logic and stores in cache
     *
     * @param id ID to lookup
     * @return lookup result
     */
    @Cacheable(value = "examples", key = "#id")
    public String findById(Long id) {
        log.info("Cache miss! Fetching data for id: {}", id);
        // Actual DB query or other logic
        simulateSlowOperation();
        return "Data for ID: " + id;
    }

    /**
     * Cache update example.
     * - Stores value in cache and returns it
     *
     * @param id    ID to update
     * @param value new value
     * @return stored value
     */
    @CachePut(value = "examples", key = "#id")
    public String update(Long id, String value) {
        log.info("Updating cache for id: {}", id);
        return value;
    }

    /**
     * Single cache eviction example.
     * - Removes from both L1 and L2
     * - Broadcasts L1 invalidation to other instances via Redis Pub/Sub
     *
     * @param id ID to evict
     */
    @CacheEvict(value = "examples", key = "#id")
    public void evict(Long id) {
        log.info("Evicting cache for id: {}", id);
    }

    /**
     * Clear all cache entries example.
     * - Removes all entries from the cache
     * - Broadcasts to other instances
     */
    @CacheEvict(value = "examples", allEntries = true)
    public void clearAll() {
        log.info("Clearing all cache entries");
    }

    /**
     * Example using multiple caches
     */
    @Cacheable(value = "multiCache", key = "#key")
    public String getFromMultiCache(String key) {
        log.info("Multi cache miss for key: {}", key);
        return "Value for: " + key;
    }

    private void simulateSlowOperation() {
        try {
            Thread.sleep(100); // 100ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
