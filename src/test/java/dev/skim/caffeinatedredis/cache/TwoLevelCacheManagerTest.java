package dev.skim.caffeinatedredis.cache;

import dev.skim.caffeinatedredis.config.NearCacheProperties;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TwoLevelCacheManager 테스트")
class TwoLevelCacheManagerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private CacheInvalidationPublisher invalidationPublisher;

    private TwoLevelCacheManager cacheManager;
    private NearCacheProperties properties;

    @BeforeEach
    void setUp() {
        properties = new NearCacheProperties();
        properties.setEnabled(true);
        properties.setInvalidationChannel("test:invalidation");

        NearCacheProperties.L1CacheProperties l1Props = new NearCacheProperties.L1CacheProperties();
        l1Props.setEnabled(true);
        l1Props.setMaxSize(1000);
        l1Props.setExpireAfterWrite(Duration.ofMinutes(5));
        properties.setL1(l1Props);

        NearCacheProperties.L2CacheProperties l2Props = new NearCacheProperties.L2CacheProperties();
        l2Props.setEnabled(true);
        l2Props.setTtl(Duration.ofMinutes(30));
        l2Props.setKeyPrefix("test:");
        properties.setL2(l2Props);

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheManager = new TwoLevelCacheManager(properties, redisTemplate, invalidationPublisher);
    }

    @Test
    @DisplayName("캐시를 생성하고 반환한다")
    void shouldCreateAndReturnCache() {
        // when
        var cache = cacheManager.getCache("testCache");

        // then
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("testCache");
    }

    @Test
    @DisplayName("같은 이름의 캐시는 동일한 인스턴스를 반환한다")
    void shouldReturnSameCacheInstanceForSameName() {
        // when
        var cache1 = cacheManager.getCache("testCache");
        var cache2 = cacheManager.getCache("testCache");

        // then
        assertThat(cache1).isSameAs(cache2);
    }

    @Test
    @DisplayName("다른 이름의 캐시는 다른 인스턴스를 반환한다")
    void shouldReturnDifferentCacheInstanceForDifferentName() {
        // when
        var cache1 = cacheManager.getCache("cache1");
        var cache2 = cacheManager.getCache("cache2");

        // then
        assertThat(cache1).isNotSameAs(cache2);
    }

    @Test
    @DisplayName("캐시 이름 목록을 반환한다")
    void shouldReturnCacheNames() {
        // given
        cacheManager.getCache("cache1");
        cacheManager.getCache("cache2");

        // when
        var cacheNames = cacheManager.getCacheNames();

        // then
        assertThat(cacheNames).containsExactlyInAnyOrder("cache1", "cache2");
    }

    @Test
    @DisplayName("개별 캐시 설정이 적용된다")
    void shouldApplyPerCacheConfiguration() {
        // given
        NearCacheProperties.CacheSpec spec = new NearCacheProperties.CacheSpec();
        spec.setL1MaxSize(500);
        spec.setL1ExpireAfterWrite(Duration.ofMinutes(2));
        spec.setL2Ttl(Duration.ofMinutes(15));
        properties.getCaches().put("customCache", spec);

        // when
        var cache = cacheManager.getCache("customCache");

        // then
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("customCache");
    }
}

