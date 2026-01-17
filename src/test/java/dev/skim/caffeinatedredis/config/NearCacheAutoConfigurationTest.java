package dev.skim.caffeinatedredis.config;

import dev.skim.caffeinatedredis.cache.TwoLevelCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AutoConfiguration 테스트
 * Redis 연결이 없는 환경에서도 설정이 로드되는지 확인
 */
@SpringBootTest(properties = {
        "near-cache.enabled=true",
        "near-cache.l1.max-size=5000",
        "near-cache.l1.expire-after-write=5m",
        "near-cache.l2.ttl=30m"
})
class NearCacheAutoConfigurationTest {

    @MockBean
    private RedisConnectionFactory redisConnectionFactory;

    @Autowired(required = false)
    private CacheManager cacheManager;

    @Autowired(required = false)
    private NearCacheProperties properties;

    @Test
    @DisplayName("NearCacheProperties가 올바르게 바인딩된다")
    void shouldBindProperties() {
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getL1().getMaxSize()).isEqualTo(5000);
    }

    @Test
    @DisplayName("CacheManager가 TwoLevelCacheManager 타입이다")
    void shouldCreateTwoLevelCacheManager() {
        assertThat(cacheManager).isInstanceOf(TwoLevelCacheManager.class);
    }
}

