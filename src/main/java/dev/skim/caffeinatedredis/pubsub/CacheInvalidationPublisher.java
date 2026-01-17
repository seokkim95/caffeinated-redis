package dev.skim.caffeinatedredis.pubsub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.skim.caffeinatedredis.config.NearCacheProperties;
import dev.skim.caffeinatedredis.message.CacheInvalidationMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Cache invalidation message publisher.
 * Broadcasts cache invalidation to other instances via Redis Pub/Sub.
 */
@Slf4j
@RequiredArgsConstructor
public class CacheInvalidationPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NearCacheProperties properties;
    private final String instanceId;

    /**
     * Publish a single key eviction message
     *
     * @param cacheName cache name
     * @param key       cache key
     */
    public void publishEvict(String cacheName, Object key) {
        CacheInvalidationMessage message = CacheInvalidationMessage.evict(instanceId, cacheName, key);
        publish(message);
    }

    /**
     * Publish a cache clear message
     *
     * @param cacheName cache name
     */
    public void publishClear(String cacheName) {
        CacheInvalidationMessage message = CacheInvalidationMessage.clear(instanceId, cacheName);
        publish(message);
    }

    /**
     * Publish message to Redis Pub/Sub channel
     */
    private void publish(CacheInvalidationMessage message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            String channel = properties.getInvalidationChannel();

            redisTemplate.convertAndSend(channel, jsonMessage);

            log.debug("Published cache invalidation message: channel={}, message={}",
                    channel, message);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize cache invalidation message: {}", message, e);
        } catch (Exception e) {
            log.error("Failed to publish cache invalidation message: {}", message, e);
        }
    }

    /**
     * Get the current instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }
}
