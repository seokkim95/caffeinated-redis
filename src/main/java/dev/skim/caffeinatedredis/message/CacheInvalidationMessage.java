package dev.skim.caffeinatedredis.message;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Cache invalidation message DTO.
 * Used to broadcast cache invalidation to other instances via Redis Pub/Sub.
 */
@Getter
@Builder
@ToString
public class CacheInvalidationMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique message ID
     */
    private final String messageId;

    /**
     * Source instance ID that published this message
     */
    private final String sourceInstanceId;

    /**
     * Cache name
     */
    private final String cacheName;

    /**
     * Cache key (null means clear entire cache)
     */
    private final Object cacheKey;

    /**
     * Invalidation type
     */
    private final InvalidationType type;

    /**
     * Message creation timestamp
     */
    private final Instant timestamp;

    /**
     * Invalidation type enumeration
     */
    public enum InvalidationType {
        /**
         * Single key eviction
         */
        EVICT,
        /**
         * Clear entire cache
         */
        CLEAR
    }

    @JsonCreator
    public CacheInvalidationMessage(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("sourceInstanceId") String sourceInstanceId,
            @JsonProperty("cacheName") String cacheName,
            @JsonProperty("cacheKey") Object cacheKey,
            @JsonProperty("type") InvalidationType type,
            @JsonProperty("timestamp") Instant timestamp) {
        this.messageId = messageId;
        this.sourceInstanceId = sourceInstanceId;
        this.cacheName = cacheName;
        this.cacheKey = cacheKey;
        this.type = type;
        this.timestamp = timestamp;
    }

    /**
     * Create a single key eviction message
     */
    public static CacheInvalidationMessage evict(String instanceId, String cacheName, Object key) {
        return CacheInvalidationMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sourceInstanceId(instanceId)
                .cacheName(cacheName)
                .cacheKey(key)
                .type(InvalidationType.EVICT)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Create a cache clear message
     */
    public static CacheInvalidationMessage clear(String instanceId, String cacheName) {
        return CacheInvalidationMessage.builder()
                .messageId(UUID.randomUUID().toString())
                .sourceInstanceId(instanceId)
                .cacheName(cacheName)
                .cacheKey(null)
                .type(InvalidationType.CLEAR)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Check if this message was published by the given instance
     */
    public boolean isFromInstance(String instanceId) {
        return this.sourceInstanceId != null && this.sourceInstanceId.equals(instanceId);
    }
}
