package dev.skim.caffeinatedredis.cache;

import dev.skim.caffeinatedredis.config.NearCacheProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the near-cache starter using a real Redis instance (Testcontainers).
 *
 * Notes:
 * - Pub/Sub invalidation requires multiple application instances. We simulate that by starting
 *   two separate Spring ApplicationContexts with different near-cache.instance-id values.
 */
@Testcontainers
@SpringBootTest(
        classes = TwoLevelCacheRedisIT.TestApp.class,
        properties = {
                "spring.main.web-application-type=none",
                "near-cache.enabled=true",
                "near-cache.invalidation-channel=it:invalidation",
                "near-cache.l2.key-prefix=it:",
                "near-cache.l2.ttl=PT2S",
                "near-cache.l1.enabled=true",
                "near-cache.l1.max-size=1000",
                "near-cache.l1.expire-after-write=PT5M"
        }
)
class TwoLevelCacheRedisIT {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApp {
        // Minimal Spring Boot app for integration testing.
        // The starter auto-configuration is discovered via AutoConfiguration.imports.
    }

    @org.springframework.beans.factory.annotation.Autowired
    CacheManager cacheManager;

    @org.springframework.beans.factory.annotation.Autowired
    NearCacheProperties properties;

    @Test
    void putAndGet_shouldPopulateL1AndL2() {
        Cache cache = cacheManager.getCache("users");
        assertThat(cache).isInstanceOf(TwoLevelCache.class);

        cache.put("1", "Alice");

        Cache.ValueWrapper wrapper = cache.get("1");
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isEqualTo("Alice");

        TwoLevelCache twoLevelCache = (TwoLevelCache) cache;
        assertThat(twoLevelCache.getL1Cache().getIfPresent("1")).isEqualTo("Alice");
    }

    @Test
    void l2Ttl_shouldExpireValueWhenL1Disabled() throws Exception {
        // Disable L1 for a single cache by creating a dedicated context.
        // This makes the assertion deterministic (no L1 hiding Redis expiration).
        try (ConfigurableApplicationContext ctx = newSpringContext("ttl-instance", true, false)) {
            CacheManager cm = ctx.getBean(CacheManager.class);
            NearCacheProperties props = ctx.getBean(NearCacheProperties.class);

            NearCacheProperties.CacheSpec spec = new NearCacheProperties.CacheSpec();
            spec.setL2Ttl(Duration.ofMillis(250));
            props.getCaches().put("ttl-cache", spec);

            Cache cache = cm.getCache("ttl-cache");
            cache.put("k", "v");
            assertThat(cache.get("k")).isNotNull();

            Thread.sleep(500);

            assertThat(cache.get("k")).isNull();
        }
    }

    @Test
    void evict_shouldRemoveFromL1AndL2() {
        Cache cache = cacheManager.getCache("products");
        assertThat(cache).isInstanceOf(TwoLevelCache.class);

        cache.put("p1", "coffee");
        assertThat(cache.get("p1")).isNotNull();

        cache.evict("p1");
        assertThat(cache.get("p1")).isNull();

        TwoLevelCache twoLevelCache = (TwoLevelCache) cache;
        assertThat(twoLevelCache.getL1Cache().getIfPresent("p1")).isNull();
    }

    @Test
    void clear_shouldClearL1AndL2() {
        Cache cache = cacheManager.getCache("orders");
        assertThat(cache).isInstanceOf(TwoLevelCache.class);

        cache.put("o1", "ORDER-1");
        cache.put("o2", "ORDER-2");
        assertThat(cache.get("o1")).isNotNull();
        assertThat(cache.get("o2")).isNotNull();

        cache.clear();

        assertThat(cache.get("o1")).isNull();
        assertThat(cache.get("o2")).isNull();

        TwoLevelCache twoLevelCache = (TwoLevelCache) cache;
        assertThat(twoLevelCache.getL1Cache().estimatedSize()).isEqualTo(0);
    }

    @Test
    void nullCaching_disabledByDefault_shouldRejectNullOnPut() {
        Cache cache = cacheManager.getCache("nulls");
        assertThat(cache).isInstanceOf(TwoLevelCache.class);

        assertThatThrownBy(() -> cache.put("k", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allow null");
    }

    @Test
    void pubsubInvalidation_onPut_shouldEvictOtherInstanceL1() throws Exception {
        try (ConfigurableApplicationContext ctxA = newSpringContext("instance-A", true, true);
             ConfigurableApplicationContext ctxB = newSpringContext("instance-B", true, true)) {

            CacheManager cmA = ctxA.getBean(CacheManager.class);
            CacheManager cmB = ctxB.getBean(CacheManager.class);

            Cache cacheA = cmA.getCache("users");
            Cache cacheB = cmB.getCache("users");

            TwoLevelCache b = (TwoLevelCache) cacheB;

            cacheB.put("1", "old");
            assertThat(b.getL1Cache().getIfPresent("1")).isEqualTo("old");

            cacheA.put("1", "new");

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && b.getL1Cache().getIfPresent("1") != null) {
                Thread.sleep(50);
            }

            assertThat(b.getL1Cache().getIfPresent("1")).isNull();

            Cache.ValueWrapper wrapper = cacheB.get("1");
            assertThat(wrapper).isNotNull();
            assertThat(wrapper.get()).isEqualTo("new");
        }
    }

    @Test
    void pubsubInvalidation_onEvict_shouldEvictOtherInstanceL1() throws Exception {
        try (ConfigurableApplicationContext ctxA = newSpringContext("evict-A", true, true);
             ConfigurableApplicationContext ctxB = newSpringContext("evict-B", true, true)) {

            CacheManager cmA = ctxA.getBean(CacheManager.class);
            CacheManager cmB = ctxB.getBean(CacheManager.class);

            Cache cacheA = cmA.getCache("users");
            Cache cacheB = cmB.getCache("users");

            TwoLevelCache b = (TwoLevelCache) cacheB;

            cacheA.put("1", "v1");
            cacheB.get("1");
            assertThat(b.getL1Cache().getIfPresent("1")).isEqualTo("v1");

            cacheA.evict("1");

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && b.getL1Cache().getIfPresent("1") != null) {
                Thread.sleep(50);
            }

            assertThat(b.getL1Cache().getIfPresent("1")).isNull();
        }
    }

    @Test
    void pubsubInvalidation_onClear_shouldClearOtherInstanceL1() throws Exception {
        try (ConfigurableApplicationContext ctxA = newSpringContext("clear-A", true, true);
             ConfigurableApplicationContext ctxB = newSpringContext("clear-B", true, true)) {

            CacheManager cmA = ctxA.getBean(CacheManager.class);
            CacheManager cmB = ctxB.getBean(CacheManager.class);

            Cache cacheA = cmA.getCache("users");
            Cache cacheB = cmB.getCache("users");

            TwoLevelCache b = (TwoLevelCache) cacheB;

            cacheA.put("1", "v1");
            cacheB.get("1");
            assertThat(b.getL1Cache().getIfPresent("1")).isEqualTo("v1");

            cacheA.clear();

            long deadline = System.currentTimeMillis() + 3000;
            while (System.currentTimeMillis() < deadline && b.getL1Cache().estimatedSize() > 0) {
                Thread.sleep(50);
            }

            assertThat(b.getL1Cache().estimatedSize()).isEqualTo(0);
        }
    }

    private ConfigurableApplicationContext newSpringContext(String instanceId, boolean l2Enabled, boolean l1Enabled) {
        SpringApplication app = new SpringApplication(TestApp.class);
        app.setRegisterShutdownHook(false);

        return app.run(
                "--spring.main.web-application-type=none",
                "--near-cache.enabled=true",
                "--near-cache.instance-id=" + instanceId,
                "--near-cache.invalidation-channel=it:invalidation",
                "--near-cache.l2.enabled=" + l2Enabled,
                "--near-cache.l2.key-prefix=it:",
                "--near-cache.l2.ttl=PT2S",
                "--near-cache.l2.cache-null-values=false",
                "--near-cache.l1.enabled=" + l1Enabled,
                "--near-cache.l1.max-size=1000",
                "--near-cache.l1.expire-after-write=PT5M",
                "--spring.data.redis.host=" + redis.getHost(),
                "--spring.data.redis.port=" + redis.getMappedPort(6379)
        );
    }
}
