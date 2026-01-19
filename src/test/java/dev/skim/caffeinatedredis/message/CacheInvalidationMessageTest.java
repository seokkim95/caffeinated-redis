package dev.skim.caffeinatedredis.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CacheInvalidationMessageTest {

    @Test
    void evictFactory_shouldCreateValidMessage() {
        String instanceId = "instance-1";
        String cacheName = "userCache";
        String key = "user:1";

        CacheInvalidationMessage message = CacheInvalidationMessage.evict(instanceId, cacheName, key);

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getSourceInstanceId()).isEqualTo(instanceId);
        assertThat(message.getCacheName()).isEqualTo(cacheName);
        assertThat(message.getCacheKey()).isEqualTo(key);
        assertThat(message.getType()).isEqualTo(CacheInvalidationMessage.InvalidationType.EVICT);
        assertThat(message.getTimestamp()).isNotNull();
    }

    @Test
    void clearFactory_shouldCreateValidMessage() {
        String instanceId = "instance-1";
        String cacheName = "userCache";

        CacheInvalidationMessage message = CacheInvalidationMessage.clear(instanceId, cacheName);

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getSourceInstanceId()).isEqualTo(instanceId);
        assertThat(message.getCacheName()).isEqualTo(cacheName);
        assertThat(message.getCacheKey()).isNull();
        assertThat(message.getType()).isEqualTo(CacheInvalidationMessage.InvalidationType.CLEAR);
        assertThat(message.getTimestamp()).isNotNull();
    }

    @Test
    void isFromInstance_shouldReturnTrueForSameInstanceId() {
        CacheInvalidationMessage message = CacheInvalidationMessage.evict("instance-1", "cache", "key");

        assertThat(message.isFromInstance("instance-1")).isTrue();
        assertThat(message.isFromInstance("instance-2")).isFalse();
    }
}
