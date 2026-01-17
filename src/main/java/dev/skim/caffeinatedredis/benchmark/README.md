# Near Cache Benchmark Suite

This package contains a comprehensive benchmark suite to measure and compare the performance of different caching strategies:

1. **Caffeine-Only (L1)** - Local in-memory cache
2. **Redis-Only (L2)** - Distributed network cache  
3. **TwoLevel/Near Cache (L1+L2)** - Combined local + distributed cache

## Purpose

The benchmark suite demonstrates that the TwoLevel Cache achieves:
- **Significantly better latency** than Redis-Only (by leveraging L1 for hot data)
- **Distributed consistency** that Caffeine-Only cannot provide
- **Best of both worlds** for MSA (Microservices Architecture) environments

## Prerequisites

- **Java 17+**
- **Maven 3.6+**
- **Redis Server** running on `localhost:6379` (or specify custom host/port)

### Starting Redis

Using Docker:
```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

Or install Redis locally:
```bash
# macOS
brew install redis
brew services start redis

# Ubuntu/Debian
sudo apt-get install redis-server
sudo systemctl start redis
```

## Building the Project

```bash
cd /path/to/caffeinated-redis

# Build with Maven (skip tests for faster build)
./mvnw clean package -DskipTests

# Or with tests
./mvnw clean package
```

## Running the Benchmark

### Method 1: Using Maven Exec Plugin (Recommended)

```bash
# Run with default Redis (localhost:6379)
./mvnw exec:java

# With custom Redis host/port
./mvnw exec:java -Dexec.args="redis-host 6379"
```

### Method 2: Using Java Command

```bash
# Run with default Redis (localhost:6379)
java -cp target/spring-boot-starter-near-cache-1.0.0-SNAPSHOT.jar \
     dev.skim.caffeinatedredis.benchmark.CacheBenchmarkMain
```

### Custom Redis Connection

```bash
# Specify Redis host and port
java -cp target/spring-boot-starter-near-cache-1.0.0-SNAPSHOT.jar \
     dev.skim.caffeinatedredis.benchmark.CacheBenchmarkMain <redis-host> <redis-port>

# Example
java -cp target/spring-boot-starter-near-cache-1.0.0-SNAPSHOT.jar \
     dev.skim.caffeinatedredis.benchmark.CacheBenchmarkMain 192.168.1.100 6379
```

### Running with Dependencies (Fat JAR alternative)

If you encounter classpath issues, run from the project directory:

```bash
./mvnw compile exec:java
```

## Benchmark Configuration

The following parameters are configured in `CacheBenchmarkMain.java`:

| Parameter | Default Value | Description |
|-----------|---------------|-------------|
| `WARMUP_ITERATIONS` | 1,000 | Iterations to warm up JVM/cache before measuring |
| `MEASURE_ITERATIONS` | 10,000 | Iterations to measure for statistics |
| `THREAD_COUNT` | 4 | Concurrent threads for benchmark |
| `KEY_SPACE` | 1,000 | Number of unique keys to use |
| `PRE_POPULATE_COUNT` | 500 | Keys to pre-populate before read tests |
| `L1_MAX_SIZE` | 10,000 | Maximum L1 (Caffeine) cache entries |
| `L1_EXPIRE` | 10 minutes | L1 cache expiration time |
| `L2_TTL` | 1 hour | L2 (Redis) cache TTL |

## Benchmark Scenarios

### 1. READ-HEAVY Workload (90% reads, 10% writes)

Simulates typical web application traffic where reads vastly outnumber writes.

**Expected Result**: TwoLevel cache should show significantly lower latency than Redis-Only due to L1 hits.

### 2. PURE READ Workload (100% reads)

Measures raw read performance with a fully warmed L1 cache.

**Expected Result**: 
- Caffeine-Only: Fastest (pure in-memory)
- TwoLevel: Near Caffeine-Only performance (L1 serves all requests)
- Redis-Only: Slowest (network round-trip for every read)

### 3. WRITE-HEAVY Workload (30% reads, 70% writes)

Simulates high-write scenarios where cache updates are frequent.

**Expected Result**: TwoLevel cache shows overhead from dual writes (L1+L2) but still provides consistency benefits.

## Understanding the Output

### Metrics Explained

| Metric | Description |
|--------|-------------|
| `Total Operations` | Number of operations performed during measurement |
| `Total Time` | Wall-clock time for all operations |
| `Avg Latency` | Average time per operation |
| `Throughput` | Operations per second |
| `Min/Max Latency` | Lowest/highest latency observed |
| `P50 Latency` | Median latency (50th percentile) |
| `P95 Latency` | 95th percentile latency |
| `P99 Latency` | 99th percentile latency |

### Sample Output

```
╔═══════════════════════════════════════════════════════════════════════════════╗
║             NEAR CACHE (TWO-LEVEL CACHE) BENCHMARK SUITE                      ║
╚═══════════════════════════════════════════════════════════════════════════════╝

Configuration:
  - Redis Host       : localhost
  - Redis Port       : 6379
  - Warmup Iterations: 1000
  - Measure Iterations: 10000
  - Thread Count     : 4

═══════════════════════════════════════════════════════════════════════════════
  BENCHMARK 1: READ-HEAVY WORKLOAD (90% reads, 10% writes)
═══════════════════════════════════════════════════════════════════════════════

┌────────────────────────────────────────────────────────────────────────────┐
│                                                                      COMPARISON TABLE                                                        │
├───────────────────────┬──────────────┬──────────────┬──────────────────────┤
│ Cache Strategy        │ Avg Latency  │ Throughput   │ P99 Latency          │
├───────────────────────┼──────────────┼──────────────┼──────────────────────┤
│ Caffeine-Only (L1)    │   0.0015 ms  │  2500000/s   │         0.0120 ms    │
│ Redis-Only (L2)       │   0.4200 ms  │    24000/s   │         1.2500 ms    │
│ TwoLevel (L1+L2)      │   0.0450 ms  │   850000/s   │         0.1200 ms    │
└───────────────────────┴──────────────┴──────────────┴──────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                              ANALYSIS                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ TwoLevel vs Redis-Only:                                                     │
│   • Latency Improvement    : +89.29% (FASTER)                               │
│   • Throughput Improvement : +3441.67% (BETTER)                             │
│                                                                             │
│ TwoLevel vs Caffeine-Only:                                                  │
│   • Latency Overhead       : +2900.00% (L2 sync cost)                       │
│   • Benefit: Distributed consistency + Persistence                          │
├─────────────────────────────────────────────────────────────────────────────┤
│                            CONCLUSION                                       │
├─────────────────────────────────────────────────────────────────────────────┤
│ ✅ TwoLevel Cache achieves BETTER performance than Redis-Only              │
│    by leveraging local L1 cache for frequently accessed data.              │
│                                                                             │
│ ✅ TwoLevel Cache provides distributed consistency that                     │
│    Caffeine-Only cannot offer in multi-instance deployments.               │
│                                                                             │
│ 🎯 RECOMMENDATION: Use TwoLevel (Near) Cache for MSA environments          │
│    where both performance and consistency are required.                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Benchmark Classes

| Class | Description |
|-------|-------------|
| `CacheBenchmarkMain` | Main entry point; orchestrates all benchmark scenarios |
| `BenchmarkRunner` | Executes benchmarks with warmup, measurement, and statistics |
| `BenchmarkResult` | Record holding all benchmark metrics |
| `BenchmarkReport` | Generates comparison tables and analysis |
| `CaffeineOnlyBenchmark` | Adapter for Caffeine-only (L1) testing |
| `RedisOnlyBenchmark` | Adapter for Redis-only (L2) testing |
| `TwoLevelCacheBenchmark` | Adapter for TwoLevel (L1+L2) testing |

## Interpreting Results

### When TwoLevel Cache is Optimal

✅ **Use TwoLevel Cache when:**
- Running multiple application instances (K8s, ECS, etc.)
- Read-heavy workload (>70% reads)
- Need both performance AND distributed consistency
- Cache data can tolerate eventual consistency (Pub/Sub delay)

### When to Consider Alternatives

⚠️ **Consider Caffeine-Only when:**
- Single instance deployment
- No need for cross-instance consistency
- Maximum possible latency is required

⚠️ **Consider Redis-Only when:**
- Memory is extremely constrained
- 100% consistency is required (no L1 staleness tolerance)
- Very low cache hit rate (L1 wouldn't help)

## Troubleshooting

### Connection Refused

```
Benchmark failed: Unable to connect to Redis
```

**Solution**: Ensure Redis is running and accessible:
```bash
redis-cli ping  # Should return PONG
```

### OutOfMemoryError

```
java.lang.OutOfMemoryError: Java heap space
```

**Solution**: Increase heap size:
```bash
java -Xmx2g -cp target/spring-boot-starter-near-cache-1.0.0-SNAPSHOT.jar \
     dev.skim.caffeinatedredis.benchmark.CacheBenchmarkMain
```

### Class Not Found

```
Error: Could not find or load main class
```

**Solution**: Ensure you've built the project:
```bash
./mvnw clean package -DskipTests
```

## Customizing Benchmarks

To modify benchmark parameters, edit `CacheBenchmarkMain.java`:

```java
// Increase iterations for more accurate results
private static final int WARMUP_ITERATIONS = 5000;
private static final int MEASURE_ITERATIONS = 50000;

// Test with more threads
private static final int THREAD_COUNT = 8;

// Larger key space for more realistic scenarios
private static final int KEY_SPACE = 10000;
```

Rebuild after changes:
```bash
./mvnw clean package -DskipTests
```

## Performance Tips

1. **Run on dedicated hardware** - Avoid running on laptops with thermal throttling
2. **Close other applications** - Minimize background processes
3. **Use wired network** - If Redis is remote, avoid WiFi
4. **Warm up JVM** - The benchmark includes warmup, but multiple runs may show better results
5. **Check Redis latency** - Run `redis-cli --latency` to verify baseline

## License

MIT License

