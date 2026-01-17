package dev.skim.caffeinatedredis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.skim.caffeinatedredis.config.NearCacheProperties;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Two-level cache manager (L1: Caffeine + L2: Redis)
 * Implements Spring's CacheManager interface for @Cacheable annotation support.
 */
@Slf4j
public class TwoLevelCacheManager implements CacheManager {

    private final ConcurrentMap<String, TwoLevelCache> cacheMap = new ConcurrentHashMap<>(16);
    private final NearCacheProperties properties;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheInvalidationPublisher invalidationPublisher;

    public TwoLevelCacheManager(NearCacheProperties properties,
                                 RedisTemplate<String, Object> redisTemplate,
                                 CacheInvalidationPublisher invalidationPublisher) {
        this.properties = properties;
        this.redisTemplate = redisTemplate;
        this.invalidationPublisher = invalidationPublisher;
    }

    @Override
    @Nullable
    public org.springframework.cache.Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, this::createCache);
    }

    @Override
    public Collection<String> getCacheNames() {
        return Collections.unmodifiableSet(cacheMap.keySet());
    }

    /**
     * Create a new TwoLevelCache instance
     */
    private TwoLevelCache createCache(String name) {
        log.info("Creating TwoLevelCache: {}", name);

        // Use per-cache configuration or default values
        NearCacheProperties.CacheSpec cacheSpec = properties.getCaches().get(name);

        // L1 cache configuration
        int l1MaxSize = (cacheSpec != null && cacheSpec.getL1MaxSize() != null)
                ? cacheSpec.getL1MaxSize()
                : properties.getL1().getMaxSize();

        Duration l1ExpireAfterWrite = (cacheSpec != null && cacheSpec.getL1ExpireAfterWrite() != null)
                ? cacheSpec.getL1ExpireAfterWrite()
                : properties.getL1().getExpireAfterWrite();

        Duration l1ExpireAfterAccess = (cacheSpec != null && cacheSpec.getL1ExpireAfterAccess() != null)
                ? cacheSpec.getL1ExpireAfterAccess()
                : properties.getL1().getExpireAfterAccess();

        // L2 cache configuration
        Duration l2Ttl = (cacheSpec != null && cacheSpec.getL2Ttl() != null)
                ? cacheSpec.getL2Ttl()
                : properties.getL2().getTtl();

        // Build Caffeine cache
        Caffeine<Object, Object> caffeineBuilder = Caffeine.newBuilder()
                .maximumSize(l1MaxSize);

        if (l1ExpireAfterWrite != null && !l1ExpireAfterWrite.isZero()) {
            caffeineBuilder.expireAfterWrite(l1ExpireAfterWrite);
        }
        if (l1ExpireAfterAccess != null && !l1ExpireAfterAccess.isZero()) {
            caffeineBuilder.expireAfterAccess(l1ExpireAfterAccess);
        }

        Cache<Object, Object> caffeineCache = caffeineBuilder.build();

        return new TwoLevelCache(
                name,
                caffeineCache,
                redisTemplate,
                invalidationPublisher,
                properties.getL2().getKeyPrefix(),
                l2Ttl,
                properties.getL2().isCacheNullValues(),
                properties.getL1().isEnabled(),
                properties.getL2().isEnabled()
        );
    }

    /**
     * Evict from L1 cache only (when receiving Pub/Sub message from other instances)
     *
     * @param cacheName cache name
     * @param key       cache key
     */
    public void evictFromL1Only(String cacheName, Object key) {
        TwoLevelCache cache = cacheMap.get(cacheName);
        if (cache != null) {
            cache.evictFromL1Only(key);
        }
    }

    /**
     * Clear L1 cache only (when receiving Pub/Sub message from other instances)
     *
     * @param cacheName cache name
     */
    public void clearL1Only(String cacheName) {
        TwoLevelCache cache = cacheMap.get(cacheName);
        if (cache != null) {
            cache.clearL1Only();
        }
    }

    /**
     * Clear all L1 caches
     */
    public void clearAllL1() {
        cacheMap.values().forEach(TwoLevelCache::clearL1Only);
    }

    /**
     * Get cache statistics (for debugging/monitoring)
     */
    public CacheStats getCacheStats(String cacheName) {
        TwoLevelCache cache = cacheMap.get(cacheName);
        if (cache == null) {
            return null;
        }

        Cache<Object, Object> l1Cache = cache.getL1Cache();
        com.github.benmanes.caffeine.cache.stats.CacheStats caffeineStats = l1Cache.stats();

        return new CacheStats(
                cacheName,
                l1Cache.estimatedSize(),
                caffeineStats.hitCount(),
                caffeineStats.missCount(),
                caffeineStats.evictionCount()
        );
    }

    /**
     * Cache statistics record
     */
    public record CacheStats(
            String cacheName,
            long l1Size,
            long l1HitCount,
            long l1MissCount,
            long l1EvictionCount
    ) {}
}
