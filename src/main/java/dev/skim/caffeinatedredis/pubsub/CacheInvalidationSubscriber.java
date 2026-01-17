package dev.skim.caffeinatedredis.pubsub;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skim.caffeinatedredis.cache.TwoLevelCacheManager;
import dev.skim.caffeinatedredis.message.CacheInvalidationMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;

import java.nio.charset.StandardCharsets;

/**
 * Cache invalidation message subscriber.
 * Receives messages from Redis Pub/Sub and invalidates local cache (L1).
 */
@Slf4j
public class CacheInvalidationSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final String instanceId;
    private TwoLevelCacheManager cacheManager;

    public CacheInvalidationSubscriber(ObjectMapper objectMapper, String instanceId) {
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    /**
     * Set the CacheManager (setter injection to avoid circular dependency)
     */
    public void setCacheManager(TwoLevelCacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String jsonMessage = new String(message.getBody(), StandardCharsets.UTF_8);
            CacheInvalidationMessage invalidationMessage =
                    objectMapper.readValue(jsonMessage, CacheInvalidationMessage.class);

            // Ignore messages published by this instance
            if (invalidationMessage.isFromInstance(instanceId)) {
                log.trace("Ignoring self-published invalidation message: {}", invalidationMessage.getMessageId());
                return;
            }

            log.debug("Received cache invalidation message: {}", invalidationMessage);

            processInvalidation(invalidationMessage);

        } catch (Exception e) {
            log.error("Failed to process cache invalidation message: {}",
                    new String(message.getBody(), StandardCharsets.UTF_8), e);
        }
    }

    /**
     * Process the invalidation message
     */
    private void processInvalidation(CacheInvalidationMessage message) {
        if (cacheManager == null) {
            log.warn("CacheManager is not set, cannot process invalidation");
            return;
        }

        String cacheName = message.getCacheName();

        switch (message.getType()) {
            case EVICT -> {
                cacheManager.evictFromL1Only(cacheName, message.getCacheKey());
                log.debug("Evicted key '{}' from L1 cache '{}'", message.getCacheKey(), cacheName);
            }
            case CLEAR -> {
                cacheManager.clearL1Only(cacheName);
                log.debug("Cleared L1 cache '{}'", cacheName);
            }
            default -> log.warn("Unknown invalidation type: {}", message.getType());
        }
    }
}
