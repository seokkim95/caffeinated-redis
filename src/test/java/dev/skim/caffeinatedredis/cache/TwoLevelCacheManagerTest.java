package dev.skim.caffeinatedredis.cache;

import dev.skim.caffeinatedredis.config.NearCacheProperties;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TwoLevelCacheManagerTest {

    @Mock
    RedisTemplate<String, Object> redisTemplate;

    @Mock
    CacheInvalidationPublisher invalidationPublisher;

    TwoLevelCacheManager cacheManager;
    NearCacheProperties properties;

    @BeforeEach
    void setUp() {
        properties = new NearCacheProperties();
        properties.setEnabled(true);
        properties.setInvalidationChannel("test:invalidation");

        NearCacheProperties.L1CacheProperties l1 = new NearCacheProperties.L1CacheProperties();
        l1.setEnabled(true);
        l1.setMaxSize(1000);
        l1.setExpireAfterWrite(Duration.ofMinutes(5));
        properties.setL1(l1);

        NearCacheProperties.L2CacheProperties l2 = new NearCacheProperties.L2CacheProperties();
        l2.setEnabled(true);
        l2.setTtl(Duration.ofMinutes(30));
        l2.setKeyPrefix("test:");
        properties.setL2(l2);

        cacheManager = new TwoLevelCacheManager(properties, redisTemplate, invalidationPublisher);
    }

    @Test
    void getCache_shouldCreateAndReturnCache() {
        org.springframework.cache.Cache cache = cacheManager.getCache("testCache");

        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("testCache");
        assertThat(cache).isInstanceOf(TwoLevelCache.class);
    }

    @Test
    void getCache_shouldReturnSameInstance_forSameName() {
        org.springframework.cache.Cache c1 = cacheManager.getCache("testCache");
        org.springframework.cache.Cache c2 = cacheManager.getCache("testCache");
        assertThat(c1).isSameAs(c2);
    }

    @Test
    void getCache_shouldReturnDifferentInstances_forDifferentNames() {
        org.springframework.cache.Cache c1 = cacheManager.getCache("cache1");
        org.springframework.cache.Cache c2 = cacheManager.getCache("cache2");
        assertThat(c1).isNotSameAs(c2);
    }

    @Test
    void getCacheNames_shouldReturnCreatedNames() {
        cacheManager.getCache("cache1");
        cacheManager.getCache("cache2");

        assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder("cache1", "cache2");
    }

    @Test
    void perCacheSpec_shouldBeAccepted() {
        NearCacheProperties.CacheSpec spec = new NearCacheProperties.CacheSpec();
        spec.setL1MaxSize(500);
        spec.setL1ExpireAfterWrite(Duration.ofMinutes(2));
        spec.setL2Ttl(Duration.ofMinutes(15));
        properties.getCaches().put("customCache", spec);

        org.springframework.cache.Cache cache = cacheManager.getCache("customCache");
        assertThat(cache).isNotNull();
        assertThat(cache.getName()).isEqualTo("customCache");
    }
}
