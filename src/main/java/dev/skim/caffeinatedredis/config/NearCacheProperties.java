package dev.skim.caffeinatedredis.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Near Cache configuration properties class.
 * Configurable via near-cache.* properties in application.yml
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "near-cache")
public class NearCacheProperties {

    /**
     * Enable/disable Near Cache
     */
    private boolean enabled = true;

    /**
     * Redis Pub/Sub channel name for cache invalidation
     */
    private String invalidationChannel = "cache:invalidation";

    /**
     * L1 (Local/Caffeine) cache default configuration
     */
    private L1CacheProperties l1 = new L1CacheProperties();

    /**
     * L2 (Global/Redis) cache default configuration
     */
    private L2CacheProperties l2 = new L2CacheProperties();

    /**
     * Per-cache configuration (cache name -> spec)
     */
    private Map<String, CacheSpec> caches = new HashMap<>();

    /**
     * Current instance ID (used to ignore self-published invalidation messages)
     */
    private String instanceId;

    @Getter
    @Setter
    public static class L1CacheProperties {
        /**
         * Enable/disable L1 cache
         */
        private boolean enabled = true;

        /**
         * Maximum number of entries
         */
        private int maxSize = 10000;

        /**
         * Expire after write duration
         */
        private Duration expireAfterWrite = Duration.ofMinutes(10);

        /**
         * Expire after access duration (null means not applied)
         */
        private Duration expireAfterAccess;
    }

    @Getter
    @Setter
    public static class L2CacheProperties {
        /**
         * Enable/disable L2 cache
         */
        private boolean enabled = true;

        /**
         * Time To Live
         */
        private Duration ttl = Duration.ofHours(1);

        /**
         * Redis key prefix
         */
        private String keyPrefix = "near-cache:";

        /**
         * Whether to cache null values
         */
        private boolean cacheNullValues = false;
    }

    /**
     * Per-cache configuration spec
     */
    @Getter
    @Setter
    public static class CacheSpec {
        /**
         * L1 maximum number of entries
         */
        private Integer l1MaxSize;

        /**
         * L1 expire after write duration
         */
        private Duration l1ExpireAfterWrite;

        /**
         * L1 expire after access duration
         */
        private Duration l1ExpireAfterAccess;

        /**
         * L2 TTL
         */
        private Duration l2Ttl;
    }
}
