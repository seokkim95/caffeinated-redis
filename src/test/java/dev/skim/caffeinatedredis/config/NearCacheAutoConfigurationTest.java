package dev.skim.caffeinatedredis.config;

import dev.skim.caffeinatedredis.cache.TwoLevelCacheManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Smoke tests for the starter auto-configuration.
 */
@SpringBootTest(
        classes = NearCacheAutoConfigurationTest.TestApp.class,
        properties = {
                "spring.main.web-application-type=none",
                "near-cache.enabled=true",
                "near-cache.l1.max-size=5000",
                "near-cache.l1.expire-after-write=PT5M",

                // Disable L2 for this smoke test so Redis pub/sub infrastructure does not start.
                // RedisMessageListenerContainer requires a real Redis connection.
                "near-cache.l2.enabled=false",
                "near-cache.l2.ttl=PT30M",
                "near-cache.l2.key-prefix=auto-test:",
                "near-cache.invalidation-channel=auto-test:invalidation"
        }
)
class NearCacheAutoConfigurationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        // Minimal Spring Boot app for testing auto-configuration.
        // The starter auto-configuration is discovered via AutoConfiguration.imports.

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            // The smoke test does not need a real Redis connection; it only verifies bean wiring.
            return mock(RedisConnectionFactory.class);
        }
    }

    @Autowired(required = false)
    CacheManager cacheManager;

    @Autowired(required = false)
    NearCacheProperties properties;

    @Test
    void properties_shouldBeBound() {
        assertThat(properties).isNotNull();
        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getL1().getMaxSize()).isEqualTo(5000);
        assertThat(properties.getL2().getKeyPrefix()).isEqualTo("auto-test:");
        assertThat(properties.getInvalidationChannel()).isEqualTo("auto-test:invalidation");
    }

    @Test
    void cacheManager_shouldBeTwoLevelCacheManager() {
        assertThat(cacheManager).isInstanceOf(TwoLevelCacheManager.class);

        // Even when L2 is disabled, the starter should still provide the near-cache CacheManager.
        // (L1-only mode)
    }
}
