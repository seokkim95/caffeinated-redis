package dev.skim.caffeinatedredis.benchmark;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.skim.caffeinatedredis.cache.TwoLevelCacheManager;
import dev.skim.caffeinatedredis.config.NearCacheProperties;
import dev.skim.caffeinatedredis.pubsub.CacheInvalidationPublisher;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Main benchmark application comparing cache strategies:
 * 1. Caffeine-Only (L1 only)
 * 2. Redis-Only (L2 only)
 * 3. TwoLevel/Near Cache (L1 + L2)
 *
 * This benchmark demonstrates that TwoLevel Cache achieves better performance
 * than Redis-Only while maintaining distributed consistency.
 */
public class CacheBenchmarkMain {

    // Benchmark configuration
    private static final int WARMUP_ITERATIONS = 1000;
    private static final int MEASURE_ITERATIONS = 10000;
    private static final int THREAD_COUNT = 4;
    private static final int KEY_SPACE = 1000;
    private static final int PRE_POPULATE_COUNT = 500;

    // Cache configuration
    private static final int L1_MAX_SIZE = 10000;
    private static final Duration L1_EXPIRE = Duration.ofMinutes(10);
    private static final Duration L2_TTL = Duration.ofHours(1);

    // Redis configuration (default)
    private static final String REDIS_HOST = "localhost";
    private static final int REDIS_PORT = 6379;

    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                                                                               ║");
        System.out.println("║             NEAR CACHE (TWO-LEVEL CACHE) BENCHMARK SUITE                      ║");
        System.out.println("║                                                                               ║");
        System.out.println("║   Comparing: Caffeine-Only vs Redis-Only vs TwoLevel (L1+L2)                  ║");
        System.out.println("║                                                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Parse Redis connection from args if provided
        String redisHost = args.length > 0 ? args[0] : REDIS_HOST;
        int redisPort = args.length > 1 ? Integer.parseInt(args[1]) : REDIS_PORT;

        System.out.println("Configuration:");
        System.out.println("  - Redis Host       : " + redisHost);
        System.out.println("  - Redis Port       : " + redisPort);
        System.out.println("  - Warmup Iterations: " + WARMUP_ITERATIONS);
        System.out.println("  - Measure Iterations: " + MEASURE_ITERATIONS);
        System.out.println("  - Thread Count     : " + THREAD_COUNT);
        System.out.println("  - Key Space        : " + KEY_SPACE);
        System.out.println("  - L1 Max Size      : " + L1_MAX_SIZE);
        System.out.println("  - L1 Expire        : " + L1_EXPIRE);
        System.out.println("  - L2 TTL           : " + L2_TTL);
        System.out.println();

        try {
            // Initialize components
            RedisConnectionFactory connectionFactory = createRedisConnectionFactory(redisHost, redisPort);
            RedisTemplate<String, Object> redisTemplate = createRedisTemplate(connectionFactory);
            StringRedisTemplate stringRedisTemplate = new StringRedisTemplate(connectionFactory);

            // Run benchmarks
            runAllBenchmarks(redisTemplate, stringRedisTemplate);

        } catch (Exception e) {
            System.err.println("Benchmark failed: " + e.getMessage());
            System.err.println("Tip: Ensure Redis is running and reachable (e.g., redis-cli ping). ");
            System.exit(1);
        }
    }

    private static void runAllBenchmarks(RedisTemplate<String, Object> redisTemplate,
                                         StringRedisTemplate stringRedisTemplate) {

        BenchmarkRunner runner = new BenchmarkRunner(WARMUP_ITERATIONS, MEASURE_ITERATIONS, THREAD_COUNT);

        // Use a unique namespace per run to avoid collisions with leftover keys.
        String runId = Long.toString(Instant.now().toEpochMilli());
        String redisOnlyPrefix = "bench:" + runId + ":redis:";
        String twoLevelPrefix = "bench:" + runId + ":twolevel:";
        String invalidationChannel = "bench:" + runId + ":invalidation";

        // ===== BENCHMARK 1: READ-HEAVY WORKLOAD (90% reads, 10% writes) =====
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  BENCHMARK 1: READ-HEAVY WORKLOAD (90% reads, 10% writes)");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println();

        BenchmarkReport readHeavyReport = new BenchmarkReport("READ-HEAVY WORKLOAD BENCHMARK RESULTS");

        // 1.1 Caffeine-Only
        CaffeineOnlyBenchmark caffeineOnly = new CaffeineOnlyBenchmark(
                "Caffeine-Only (L1)", L1_MAX_SIZE, L1_EXPIRE);
        caffeineOnly.prePopulate(PRE_POPULATE_COUNT);
        BenchmarkResult caffeineReadHeavy = runner.runReadHeavy(
                caffeineOnly.getName(),
                caffeineOnly.getReadOperation(KEY_SPACE),
                caffeineOnly.getWriteOperation(KEY_SPACE));
        readHeavyReport.addResult(caffeineReadHeavy);

        // 1.2 Redis-Only
        RedisOnlyBenchmark redisOnly = new RedisOnlyBenchmark(
                "Redis-Only (L2)", redisTemplate, redisOnlyPrefix, L2_TTL);
        redisOnly.clear();
        redisOnly.prePopulate(PRE_POPULATE_COUNT);
        BenchmarkResult redisReadHeavy = runner.runReadHeavy(
                redisOnly.getName(),
                redisOnly.getReadOperation(KEY_SPACE),
                redisOnly.getWriteOperation(KEY_SPACE));
        readHeavyReport.addResult(redisReadHeavy);

        // 1.3 TwoLevel Cache
        TwoLevelCacheBenchmark twoLevel = createTwoLevelBenchmark(
                redisTemplate, stringRedisTemplate, twoLevelPrefix, invalidationChannel);
        twoLevel.clear();
        twoLevel.prePopulate(PRE_POPULATE_COUNT);
        BenchmarkResult twoLevelReadHeavy = runner.runReadHeavy(
                twoLevel.getName(),
                twoLevel.getReadOperation(KEY_SPACE),
                twoLevel.getWriteOperation(KEY_SPACE));
        readHeavyReport.addResult(twoLevelReadHeavy);

        readHeavyReport.printReport();

        // ===== BENCHMARK 2: PURE READ WORKLOAD (L1 hit scenario) =====
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  BENCHMARK 2: PURE READ WORKLOAD (100% reads, L1 cache hit scenario)");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println();

        BenchmarkReport pureReadReport = new BenchmarkReport("PURE READ WORKLOAD BENCHMARK RESULTS");

        // Re-populate caches for pure read test
        caffeineOnly.clear();
        caffeineOnly.prePopulate(KEY_SPACE);
        BenchmarkResult caffeinePureRead = runner.run(
                caffeineOnly.getName(), "PURE_READ",
                caffeineOnly.getReadOperation(KEY_SPACE));
        pureReadReport.addResult(caffeinePureRead);

        redisOnly.clear();
        redisOnly.prePopulate(KEY_SPACE);
        BenchmarkResult redisPureRead = runner.run(
                redisOnly.getName(), "PURE_READ",
                redisOnly.getReadOperation(KEY_SPACE));
        pureReadReport.addResult(redisPureRead);

        twoLevel.clear();
        twoLevel.prePopulate(KEY_SPACE);
        // Warm up L1 by reading all keys once
        for (int i = 0; i < KEY_SPACE; i++) {
            twoLevel.get("key-" + i);
        }
        BenchmarkResult twoLevelPureRead = runner.run(
                twoLevel.getName(), "PURE_READ (L1 warmed)",
                twoLevel.getReadOperation(KEY_SPACE));
        pureReadReport.addResult(twoLevelPureRead);

        pureReadReport.printReport();

        // ===== BENCHMARK 3: WRITE-HEAVY WORKLOAD (30% reads, 70% writes) =====
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println("  BENCHMARK 3: WRITE-HEAVY WORKLOAD (30% reads, 70% writes)");
        System.out.println("═══════════════════════════════════════════════════════════════════════════════");
        System.out.println();

        BenchmarkReport writeHeavyReport = new BenchmarkReport("WRITE-HEAVY WORKLOAD BENCHMARK RESULTS");

        caffeineOnly.clear();
        BenchmarkResult caffeineWriteHeavy = runner.runWriteHeavy(
                caffeineOnly.getName(),
                caffeineOnly.getReadOperation(KEY_SPACE),
                caffeineOnly.getWriteOperation(KEY_SPACE));
        writeHeavyReport.addResult(caffeineWriteHeavy);

        redisOnly.clear();
        BenchmarkResult redisWriteHeavy = runner.runWriteHeavy(
                redisOnly.getName(),
                redisOnly.getReadOperation(KEY_SPACE),
                redisOnly.getWriteOperation(KEY_SPACE));
        writeHeavyReport.addResult(redisWriteHeavy);

        twoLevel.clear();
        BenchmarkResult twoLevelWriteHeavy = runner.runWriteHeavy(
                twoLevel.getName(),
                twoLevel.getReadOperation(KEY_SPACE),
                twoLevel.getWriteOperation(KEY_SPACE));
        writeHeavyReport.addResult(twoLevelWriteHeavy);

        writeHeavyReport.printReport();

        // Cleanup
        redisOnly.clear();
        twoLevel.clear();

        printFinalSummary();
    }

    private static RedisConnectionFactory createRedisConnectionFactory(String host, int port) {
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
        factory.afterPropertiesSet();
        return factory;
    }

    private static RedisTemplate<String, Object> createRedisTemplate(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = createBenchmarkObjectMapper();

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    private static ObjectMapper createBenchmarkObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        objectMapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);

        return objectMapper;
    }

    private static TwoLevelCacheBenchmark createTwoLevelBenchmark(RedisTemplate<String, Object> redisTemplate,
                                                                  StringRedisTemplate stringRedisTemplate,
                                                                  String keyPrefix,
                                                                  String invalidationChannel) {
        // Create properties
        NearCacheProperties properties = new NearCacheProperties();
        properties.setEnabled(true);
        properties.setInvalidationChannel(invalidationChannel);
        properties.setInstanceId(UUID.randomUUID().toString());

        NearCacheProperties.L1CacheProperties l1Props = new NearCacheProperties.L1CacheProperties();
        l1Props.setEnabled(true);
        l1Props.setMaxSize(L1_MAX_SIZE);
        l1Props.setExpireAfterWrite(L1_EXPIRE);
        properties.setL1(l1Props);

        NearCacheProperties.L2CacheProperties l2Props = new NearCacheProperties.L2CacheProperties();
        l2Props.setEnabled(true);
        l2Props.setTtl(L2_TTL);
        l2Props.setKeyPrefix(keyPrefix);
        properties.setL2(l2Props);

        // Publisher is required by TwoLevelCache for invalidation signaling.
        // For this benchmark, there is no subscriber container, so it does not add real pub/sub overhead.
        ObjectMapper objectMapper = createBenchmarkObjectMapper();
        CacheInvalidationPublisher publisher = new CacheInvalidationPublisher(
                stringRedisTemplate, objectMapper, properties, properties.getInstanceId());

        TwoLevelCacheManager cacheManager = new TwoLevelCacheManager(properties, redisTemplate, publisher);
        org.springframework.cache.Cache cache = cacheManager.getCache("benchmark");

        return new TwoLevelCacheBenchmark("TwoLevel/Near Cache (L1+L2)", cache);
    }

    private static void printFinalSummary() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                           FINAL SUMMARY                                       ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                               ║");
        System.out.println("║  Key Findings:                                                                ║");
        System.out.println("║                                                                               ║");
        System.out.println("║  1. Caffeine-Only (L1):                                                       ║");
        System.out.println("║     [+] Fastest latency (in-memory)                                          ║");
        System.out.println("║     [-] No distributed consistency                                           ║");
        System.out.println("║     [-] Data lost on restart                                                 ║");
        System.out.println("║                                                                               ║");
        System.out.println("║  2. Redis-Only (L2):                                                         ║");
        System.out.println("║     [+] Distributed consistency                                              ║");
        System.out.println("║     [+] Data persistence                                                     ║");
        System.out.println("║     [-] Network latency overhead                                             ║");
        System.out.println("║                                                                               ║");
        System.out.println("║  3. TwoLevel/Near Cache (L1+L2):                                             ║");
        System.out.println("║     [+] Near in-memory latency for hot data (L1 hits)                        ║");
        System.out.println("║     [+] Distributed consistency via Redis                                    ║");
        System.out.println("║     [+] Automatic L1 invalidation across instances                           ║");
        System.out.println("║     [+] Best of both worlds for MSA environments                             ║");
        System.out.println("║                                                                               ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════════════════╣");
        System.out.println("║                                                                               ║");
        System.out.println("║  Tip: TwoLevel Cache is optimal when:                                        ║");
        System.out.println("║     - Running multiple application instances                                  ║");
        System.out.println("║     - Read-heavy workload with occasional writes                              ║");
        System.out.println("║     - Need both performance and consistency                                   ║");
        System.out.println("║                                                                               ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════════╝");
        System.out.println();
    }
}

