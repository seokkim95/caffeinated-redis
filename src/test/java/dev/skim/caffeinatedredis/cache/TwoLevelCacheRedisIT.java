package dev.skim.caffeinatedredis.cache;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.skim.caffeinatedredis.config.NearCacheProperties;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationSubscriber;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TwoLevelCache using real Redis via Testcontainers.
 */
@Testcontainers
class TwoLevelCacheRedisIT {

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379);

    private RedisMessageListenerContainer listenerContainer;

    @AfterEach
    void tearDown() {
        if (listenerContainer != null) {
            listenerContainer.stop();
        }
    }

    @Test
    void putThenGet_shouldWorkAndPopulateL1() {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisTemplate<String, Object> redisTemplate = redisTemplate(connectionFactory);

        CacheInvalidationPublisher publisher = publisher(connectionFactory, "it-instance-1");

        TwoLevelCache cache = new TwoLevelCache(
                "it-cache",
                Caffeine.newBuilder().maximumSize(1000).build(),
                redisTemplate,
                publisher,
                "it:",
                Duration.ofMinutes(1),
                false,
                true,
                true
        );

        cache.put("k1", "v1");

        var wrapper = cache.get("k1");
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isEqualTo("v1");

        // L1 should have the value
        assertThat(cache.getL1Cache().getIfPresent("k1")).isEqualTo("v1");
    }

    @Test
    void l2Ttl_shouldExpireInRedis() throws Exception {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisTemplate<String, Object> redisTemplate = redisTemplate(connectionFactory);

        CacheInvalidationPublisher publisher = publisher(connectionFactory, "it-instance-1");

        Duration ttl = Duration.ofMillis(200);

        TwoLevelCache cache = new TwoLevelCache(
                "ttl-cache",
                Caffeine.newBuilder().maximumSize(1000).build(),
                redisTemplate,
                publisher,
                "ttl:",
                ttl,
                false,
                false, // L1 disabled: ensure we observe Redis expiration directly
                true
        );

        cache.put("k1", "v1");
        assertThat(cache.get("k1")).isNotNull();

        Thread.sleep(350);

        // After TTL, Redis should no longer have the key
        assertThat(cache.get("k1")).isNull();
    }

    @Test
    void pubsubInvalidation_shouldEvictOtherInstanceL1OnPut() throws Exception {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisTemplate<String, Object> redisTemplate = redisTemplate(connectionFactory);

        String channel = "it:invalidation";

        // Instance A (publisher)
        CacheInvalidationPublisher publisherA = publisher(connectionFactory, "instance-A", channel);

        TwoLevelCache cacheA = new TwoLevelCache(
                "users",
                Caffeine.newBuilder().maximumSize(1000).build(),
                redisTemplate,
                publisherA,
                "it:",
                Duration.ofMinutes(1),
                false,
                true,
                true
        );

        // Instance B (subscriber)
        TwoLevelCacheManager managerB = cacheManager(redisTemplate, publisher(connectionFactory, "instance-B", channel));
        CacheInvalidationSubscriber subscriberB = new CacheInvalidationSubscriber(objectMapper(), "instance-B");
        subscriberB.setCacheManager(managerB);

        listenerContainer = new RedisMessageListenerContainer();
        listenerContainer.setConnectionFactory(connectionFactory);
        listenerContainer.addMessageListener(subscriberB, new ChannelTopic(channel));
        listenerContainer.afterPropertiesSet();
        listenerContainer.start();

        TwoLevelCache cacheB = (TwoLevelCache) managerB.getCache("users");
        assertThat(cacheB).isNotNull();

        // Seed B's L1 with an old value
        cacheB.put("1", "old");
        assertThat(cacheB.getL1Cache().getIfPresent("1")).isEqualTo("old");

        // A writes a new value -> should publish invalidation -> B L1 should evict key "1"
        cacheA.put("1", "new");

        // Wait briefly for pub/sub
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline && cacheB.getL1Cache().getIfPresent("1") != null) {
            Thread.sleep(50);
        }

        assertThat(cacheB.getL1Cache().getIfPresent("1")).isNull();

        // Next read on B should load from Redis (new value) and repopulate L1
        var wrapper = cacheB.get("1");
        assertThat(wrapper).isNotNull();
        assertThat(wrapper.get()).isEqualTo("new");
        assertThat(cacheB.getL1Cache().getIfPresent("1")).isEqualTo("new");
    }

    private RedisConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        return factory;
    }

    private ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        return objectMapper;
    }

    private RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper());

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    private CacheInvalidationPublisher publisher(RedisConnectionFactory connectionFactory, String instanceId) {
        return publisher(connectionFactory, instanceId, "it:invalidation");
    }

    private CacheInvalidationPublisher publisher(RedisConnectionFactory connectionFactory, String instanceId, String channel) {
        NearCacheProperties properties = new NearCacheProperties();
        properties.setInvalidationChannel(channel);
        properties.setInstanceId(instanceId);
        return new CacheInvalidationPublisher(new StringRedisTemplate(connectionFactory), objectMapper(), properties, instanceId);
    }

    private TwoLevelCacheManager cacheManager(RedisTemplate<String, Object> redisTemplate, CacheInvalidationPublisher publisher) {
        NearCacheProperties properties = new NearCacheProperties();
        properties.setInvalidationChannel("it:invalidation");
        properties.setInstanceId(UUID.randomUUID().toString());

        NearCacheProperties.L1CacheProperties l1 = new NearCacheProperties.L1CacheProperties();
        l1.setEnabled(true);
        l1.setMaxSize(1000);
        l1.setExpireAfterWrite(Duration.ofMinutes(5));
        properties.setL1(l1);

        NearCacheProperties.L2CacheProperties l2 = new NearCacheProperties.L2CacheProperties();
        l2.setEnabled(true);
        l2.setKeyPrefix("it:");
        l2.setTtl(Duration.ofMinutes(1));
        properties.setL2(l2);

        return new TwoLevelCacheManager(properties, redisTemplate, publisher);
    }
}
