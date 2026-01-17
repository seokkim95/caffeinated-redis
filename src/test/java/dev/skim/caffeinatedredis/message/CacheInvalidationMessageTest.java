package dev.skim.caffeinatedredis.message;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CacheInvalidationMessage 테스트")
class CacheInvalidationMessageTest {

    @Test
    @DisplayName("evict 메시지를 올바르게 생성한다")
    void shouldCreateEvictMessage() {
        // given
        String instanceId = "instance-1";
        String cacheName = "userCache";
        String key = "user:1";

        // when
        CacheInvalidationMessage message = CacheInvalidationMessage.evict(instanceId, cacheName, key);

        // then
        assertThat(message.getMessageId()).isNotNull();
        assertThat(message.getSourceInstanceId()).isEqualTo(instanceId);
        assertThat(message.getCacheName()).isEqualTo(cacheName);
        assertThat(message.getCacheKey()).isEqualTo(key);
        assertThat(message.getType()).isEqualTo(CacheInvalidationMessage.InvalidationType.EVICT);
        assertThat(message.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("clear 메시지를 올바르게 생성한다")
    void shouldCreateClearMessage() {
        // given
        String instanceId = "instance-1";
        String cacheName = "userCache";

        // when
        CacheInvalidationMessage message = CacheInvalidationMessage.clear(instanceId, cacheName);

        // then
        assertThat(message.getMessageId()).isNotNull();
        assertThat(message.getSourceInstanceId()).isEqualTo(instanceId);
        assertThat(message.getCacheName()).isEqualTo(cacheName);
        assertThat(message.getCacheKey()).isNull();
        assertThat(message.getType()).isEqualTo(CacheInvalidationMessage.InvalidationType.CLEAR);
        assertThat(message.getTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("같은 인스턴스에서 발행된 메시지인지 확인한다")
    void shouldCheckIfFromSameInstance() {
        // given
        String instanceId = "instance-1";
        CacheInvalidationMessage message = CacheInvalidationMessage.evict(instanceId, "cache", "key");

        // when & then
        assertThat(message.isFromInstance("instance-1")).isTrue();
        assertThat(message.isFromInstance("instance-2")).isFalse();
    }
}

