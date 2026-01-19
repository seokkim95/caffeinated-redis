package dev.skim.caffeinatedredis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TwoLevelCacheTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    CacheInvalidationPublisher invalidationPublisher;

    TwoLevelCache cache;
    Cache<Object, Object> l1;

    @BeforeEach
    void setUp() {
        l1 = Caffeine.newBuilder().maximumSize(100).build();

        cache = new TwoLevelCache(
                "test-cache",
                l1,
                redisTemplate,
                invalidationPublisher,
                "test:",
                Duration.ofMinutes(10),
                false,
                true,
                true
        );
    }

    @Test
    void get_shouldReturnFromL1_whenPresent() {
        l1.put("k", "v");

        org.springframework.cache.Cache.ValueWrapper wrapper = cache.get("k");

        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isEqualTo("v");
        verify(redisTemplate.opsForValue(), never()).get(anyString());
    }

    @Test
    void get_shouldLoadFromL2AndPopulateL1_whenL1Miss() {
        when(redisTemplate.opsForValue().get(anyString())).thenReturn("v");

        org.springframework.cache.Cache.ValueWrapper wrapper = cache.get("k");

        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isEqualTo("v");
        assertThat(l1.getIfPresent("k")).isEqualTo("v");
        verify(redisTemplate.opsForValue()).get(anyString());
    }

    @Test
    void get_shouldReturnNull_whenBothMiss() {
        when(redisTemplate.opsForValue().get(anyString())).thenReturn(null);

        org.springframework.cache.Cache.ValueWrapper wrapper = cache.get("k");

        assertThat(wrapper).isNull();
        verify(redisTemplate.opsForValue()).get(anyString());
    }

    @Test
    void put_shouldWriteToL1AndL2() {
        cache.put("k", "v");

        assertThat(l1.getIfPresent("k")).isEqualTo("v");

        verify(redisTemplate.opsForValue(), atLeastOnce()).set(anyString(), eq("v"), any(Duration.class));
    }

    @Test
    void evict_shouldRemoveFromL1AndL2_andPublishInvalidation() {
        l1.put("k", "v");

        cache.evict("k");

        assertThat(l1.getIfPresent("k")).isNull();
        verify(redisTemplate).delete(anyString());
        verify(invalidationPublisher).publishEvict("test-cache", "k");
    }

    @Test
    void clear_shouldInvalidateL1_andDeleteL2Keys_andPublishInvalidation() {
        l1.put("k1", "v1");
        l1.put("k2", "v2");

        cache.clear();

        assertThat(l1.estimatedSize()).isEqualTo(0);
        verify(invalidationPublisher).publishClear("test-cache");
    }

    @Test
    void evictFromL1Only_shouldNotTouchL2_orPublish() {
        l1.put("k", "v");

        cache.evictFromL1Only("k");

        assertThat(l1.getIfPresent("k")).isNull();
        verify(redisTemplate, never()).delete(anyString());
        verify(invalidationPublisher, never()).publishEvict(anyString(), any());
    }

    @Test
    void clearL1Only_shouldNotTouchL2_orPublish() {
        l1.put("k", "v");

        cache.clearL1Only();

        assertThat(l1.estimatedSize()).isEqualTo(0);
        verifyNoInteractions(invalidationPublisher);
    }

    @Test
    void getName_shouldReturnCacheName() {
        assertThat(cache.getName()).isEqualTo("test-cache");
    }
}
