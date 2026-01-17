package dev.skim.caffeinatedredis.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("TwoLevelCache 테스트")
class TwoLevelCacheTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CacheInvalidationPublisher invalidationPublisher;

    private TwoLevelCache cache;
    private Cache<Object, Object> caffeineCache;

    @BeforeEach
    void setUp() {
        caffeineCache = Caffeine.newBuilder()
                .maximumSize(100)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cache = new TwoLevelCache(
                "testCache",
                caffeineCache,
                redisTemplate,
                invalidationPublisher,
                "near-cache:",
                Duration.ofMinutes(10),
                false,
                true,
                true
        );
    }

    @Test
    @DisplayName("L1 캐시에 값이 있으면 L1에서 반환한다")
    void shouldReturnFromL1WhenValueExists() {
        // given
        String key = "testKey";
        String value = "testValue";
        caffeineCache.put(key, value);

        // when
        var result = cache.get(key);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo(value);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    @DisplayName("L1 미스 시 L2에서 조회하고 L1에 저장한다")
    void shouldFetchFromL2AndStoreInL1WhenL1Miss() {
        // given
        String key = "testKey";
        String value = "testValue";
        when(valueOperations.get(anyString())).thenReturn(value);

        // when
        var result = cache.get(key);

        // then
        assertThat(result).isNotNull();
        assertThat(result.get()).isEqualTo(value);
        assertThat(caffeineCache.getIfPresent(key)).isEqualTo(value);
    }

    @Test
    @DisplayName("L1, L2 모두 미스 시 null을 반환한다")
    void shouldReturnNullWhenBothCachesMiss() {
        // given
        String key = "testKey";
        when(valueOperations.get(anyString())).thenReturn(null);

        // when
        var result = cache.get(key);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("put 시 L1, L2 모두에 저장한다")
    void shouldPutInBothCaches() {
        // given
        String key = "testKey";
        String value = "testValue";

        // when
        cache.put(key, value);

        // then
        assertThat(caffeineCache.getIfPresent(key)).isEqualTo(value);
        verify(valueOperations).set(anyString(), eq(value), any(Duration.class));
    }

    @Test
    @DisplayName("evict 시 L1, L2 모두에서 삭제하고 무효화 메시지를 발행한다")
    void shouldEvictFromBothCachesAndPublishInvalidation() {
        // given
        String key = "testKey";
        String value = "testValue";
        caffeineCache.put(key, value);

        // when
        cache.evict(key);

        // then
        assertThat(caffeineCache.getIfPresent(key)).isNull();
        verify(redisTemplate).delete(anyString());
        verify(invalidationPublisher).publishEvict("testCache", key);
    }

    @Test
    @DisplayName("evictFromL1Only는 L1만 무효화하고 L2와 Publisher는 호출하지 않는다")
    void shouldOnlyEvictFromL1WhenEvictFromL1Only() {
        // given
        String key = "testKey";
        String value = "testValue";
        caffeineCache.put(key, value);

        // when
        cache.evictFromL1Only(key);

        // then
        assertThat(caffeineCache.getIfPresent(key)).isNull();
        verify(redisTemplate, never()).delete(anyString());
        verify(invalidationPublisher, never()).publishEvict(anyString(), any());
    }

    @Test
    @DisplayName("캐시 이름을 올바르게 반환한다")
    void shouldReturnCorrectCacheName() {
        assertThat(cache.getName()).isEqualTo("testCache");
    }
}

