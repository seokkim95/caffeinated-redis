package dev.skim.caffeinatedredis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.support.AbstractValueAdaptingCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Two-level cache implementation (L1: Caffeine + L2: Redis)
 *
 * Read flow:
 * 1. Lookup in L1 (Caffeine)
 * 2. L1 miss -> Lookup in L2 (Redis)
 * 3. L2 hit -> Store in L1 and return
 * 4. L2 miss -> Return null (cache miss)
 *
 * Write flow:
 * 1. Store in both L1 and L2
 * 2. Broadcast invalidation to other instances via Redis Pub/Sub
 */
@Slf4j
public class TwoLevelCache extends AbstractValueAdaptingCache {

    private final String name;
    private final Cache<Object, Object> l1Cache;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheInvalidationPublisher invalidationPublisher;
    private final String keyPrefix;
    private final Duration l2Ttl;
    private final boolean l1Enabled;
    private final boolean l2Enabled;

    public TwoLevelCache(String name,
                         Cache<Object, Object> l1Cache,
                         RedisTemplate<String, Object> redisTemplate,
                         CacheInvalidationPublisher invalidationPublisher,
                         String keyPrefix,
                         Duration l2Ttl,
                         boolean allowNullValues,
                         boolean l1Enabled,
                         boolean l2Enabled) {
        super(allowNullValues);
        this.name = name;
        this.l1Cache = l1Cache;
        this.redisTemplate = redisTemplate;
        this.invalidationPublisher = invalidationPublisher;
        this.keyPrefix = keyPrefix;
        this.l2Ttl = l2Ttl;
        this.l1Enabled = l1Enabled;
        this.l2Enabled = l2Enabled;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public Object getNativeCache() {
        return this;
    }

    /**
     * Get L1 cache (Caffeine)
     */
    public Cache<Object, Object> getL1Cache() {
        return this.l1Cache;
    }

    @Override
    @Nullable
    protected Object lookup(Object key) {
        // Step 1: Lookup in L1 cache
        if (l1Enabled) {
            Object value = l1Cache.getIfPresent(key);
            if (value != null) {
                log.trace("L1 cache hit: cache={}, key={}", name, key);
                return value;
            }
            log.trace("L1 cache miss: cache={}, key={}", name, key);
        }

        // Step 2: Lookup in L2 cache (Redis)
        if (l2Enabled) {
            String redisKey = createRedisKey(key);
            try {
                Object value = redisTemplate.opsForValue().get(redisKey);
                if (value != null) {
                    log.trace("L2 cache hit: cache={}, key={}", name, key);
                    // L2 hit -> Store in L1
                    if (l1Enabled) {
                        l1Cache.put(key, value);
                    }
                    return value;
                }
                log.trace("L2 cache miss: cache={}, key={}", name, key);
            } catch (Exception e) {
                log.warn("Failed to get value from L2 cache: cache={}, key={}", name, key, e);
            }
        }

        return null;
    }

    @Override
    @Nullable
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper wrapper = get(key);
        if (wrapper != null) {
            @SuppressWarnings("unchecked")
            T value = (T) wrapper.get();
            return value;
        }

        // Cache miss -> Call valueLoader and store
        try {
            T value = valueLoader.call();
            put(key, value);
            return value;
        } catch (Exception e) {
            throw new ValueRetrievalException(key, valueLoader, e);
        }
    }

    @Override
    public void put(Object key, @Nullable Object value) {
        Object storeValue = toStoreValue(value);

        // Store in L1
        if (l1Enabled) {
            l1Cache.put(key, storeValue);
        }

        // Store in L2
        if (l2Enabled) {
            String redisKey = createRedisKey(key);
            try {
                if (l2Ttl != null && !l2Ttl.isZero()) {
                    redisTemplate.opsForValue().set(redisKey, storeValue, l2Ttl);
                } else {
                    redisTemplate.opsForValue().set(redisKey, storeValue);
                }
            } catch (Exception e) {
                log.warn("Failed to put value to L2 cache: cache={}, key={}", name, key, e);
            }
        }

        log.trace("Cache put: cache={}, key={}", name, key);
    }

    @Override
    @Nullable
    public ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
        Object existingValue = lookup(key);
        if (existingValue != null) {
            return toValueWrapper(existingValue);
        }
        put(key, value);
        return null;
    }

    @Override
    public void evict(Object key) {
        // Remove from L1
        if (l1Enabled) {
            l1Cache.invalidate(key);
        }

        // Remove from L2
        if (l2Enabled) {
            String redisKey = createRedisKey(key);
            try {
                redisTemplate.delete(redisKey);
            } catch (Exception e) {
                log.warn("Failed to evict from L2 cache: cache={}, key={}", name, key, e);
            }
        }

        // Broadcast invalidation to other instances
        if (invalidationPublisher != null) {
            invalidationPublisher.publishEvict(name, key);
        }

        log.trace("Cache evict: cache={}, key={}", name, key);
    }

    @Override
    public void clear() {
        // Clear all from L1
        if (l1Enabled) {
            l1Cache.invalidateAll();
        }

        // Clear all from L2 (only keys for this cache name)
        if (l2Enabled) {
            try {
                String pattern = keyPrefix + name + ":*";
                var keys = redisTemplate.keys(pattern);
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("Failed to clear L2 cache: cache={}", name, e);
            }
        }

        // Broadcast invalidation to other instances
        if (invalidationPublisher != null) {
            invalidationPublisher.publishClear(name);
        }

        log.trace("Cache clear: cache={}", name);
    }

    /**
     * Evict from L1 cache only (used when receiving Pub/Sub message)
     */
    public void evictFromL1Only(Object key) {
        if (l1Enabled) {
            l1Cache.invalidate(key);
            log.trace("L1 cache evict only: cache={}, key={}", name, key);
        }
    }

    /**
     * Clear L1 cache only (used when receiving Pub/Sub message)
     */
    public void clearL1Only() {
        if (l1Enabled) {
            l1Cache.invalidateAll();
            log.trace("L1 cache clear only: cache={}", name);
        }
    }

    /**
     * Create Redis key
     */
    private String createRedisKey(Object key) {
        return keyPrefix + name + ":" + key.toString();
    }
}
