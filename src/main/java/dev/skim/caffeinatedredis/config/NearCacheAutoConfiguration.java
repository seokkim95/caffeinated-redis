package dev.skim.caffeinatedredis.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.skim.caffeinatedredis.cache.TwoLevelCacheManager;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationSubscriber;
import dev.skim.caffeinatedredis.support.NearCacheOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.UUID;

/**
 * Near Cache auto configuration class.
 * Automatically registers beans through Spring Boot's Auto Configuration mechanism.
 */
@Slf4j
@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass({RedisConnectionFactory.class, CacheManager.class})
@ConditionalOnProperty(prefix = "near-cache", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(NearCacheProperties.class)
@EnableCaching
public class NearCacheAutoConfiguration {

    /**
     * Generate unique instance ID.
     * Used to identify each application instance and ignore self-published Pub/Sub messages.
     */
    @Bean
    @ConditionalOnMissingBean(name = "nearCacheInstanceId")
    public String nearCacheInstanceId(NearCacheProperties properties) {
        String instanceId = properties.getInstanceId();
        if (instanceId == null || instanceId.isBlank()) {
            instanceId = UUID.randomUUID().toString();
        }
        log.info("Near Cache instance ID: {}", instanceId);
        return instanceId;
    }

    /**
     * ObjectMapper for Near Cache.
     * Includes type information for proper deserialization to original types.
     */
    @Bean
    @ConditionalOnMissingBean(name = "nearCacheObjectMapper")
    public ObjectMapper nearCacheObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        // Java 8 Date/Time support
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Polymorphic type handling configuration
        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        return objectMapper;
    }

    /**
     * RedisTemplate for Near Cache.
     * Uses JSON serialization for readability and easier debugging.
     */
    @Bean
    @ConditionalOnMissingBean(name = "nearCacheRedisTemplate")
    public RedisTemplate<String, Object> nearCacheRedisTemplate(
            RedisConnectionFactory connectionFactory,
            ObjectMapper nearCacheObjectMapper) {

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key uses String, Value uses JSON serialization
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(nearCacheObjectMapper);

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        return template;
    }

    /**
     * Cache invalidation message publisher
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheInvalidationPublisher cacheInvalidationPublisher(
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper nearCacheObjectMapper,
            NearCacheProperties properties,
            String nearCacheInstanceId) {

        return new CacheInvalidationPublisher(
                stringRedisTemplate,
                nearCacheObjectMapper,
                properties,
                nearCacheInstanceId
        );
    }

    /**
     * Cache invalidation message subscriber
     */
    @Bean
    @ConditionalOnMissingBean
    public CacheInvalidationSubscriber cacheInvalidationSubscriber(
            ObjectMapper nearCacheObjectMapper,
            String nearCacheInstanceId) {

        return new CacheInvalidationSubscriber(nearCacheObjectMapper, nearCacheInstanceId);
    }

    /**
     * Redis Pub/Sub listener container.
     *
     * This must only start when Redis (L2) is enabled. In L1-only mode we intentionally skip
     * starting Pub/Sub infrastructure to avoid requiring a real Redis connection.
     */
    @Bean
    @ConditionalOnMissingBean(name = "nearCacheMessageListenerContainer")
    @ConditionalOnExpression("${near-cache.l2.enabled:true}")
    public RedisMessageListenerContainer nearCacheMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationSubscriber subscriber,
            NearCacheProperties properties) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Subscribe to invalidation channel
        ChannelTopic topic = new ChannelTopic(properties.getInvalidationChannel());
        container.addMessageListener(subscriber, topic);

        log.info("Near Cache subscribed to invalidation channel: {}", properties.getInvalidationChannel());

        return container;
    }

    /**
     * TwoLevelCacheManager bean registration.
     * Set as default CacheManager via @Primary.
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(CacheManager.class)
    public TwoLevelCacheManager cacheManager(
            NearCacheProperties properties,
            RedisTemplate<String, Object> nearCacheRedisTemplate,
            CacheInvalidationPublisher publisher,
            CacheInvalidationSubscriber subscriber) {

        TwoLevelCacheManager cacheManager = new TwoLevelCacheManager(
                properties,
                nearCacheRedisTemplate,
                publisher
        );

        // Setter injection to avoid circular dependency
        subscriber.setCacheManager(cacheManager);

        log.info("Near Cache TwoLevelCacheManager initialized");

        return cacheManager;
    }

    /**
     * NearCacheOperations utility bean.
     * Provides convenient methods for programmatic cache operations.
     */
    @Bean
    @ConditionalOnMissingBean
    public NearCacheOperations nearCacheOperations(TwoLevelCacheManager cacheManager) {
        return new NearCacheOperations(cacheManager);
    }
}
