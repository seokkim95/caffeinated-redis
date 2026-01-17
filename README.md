# Spring Boot Starter Near Cache

A Multi-Level Cache (Near-Cache) library for distributed Spring Boot MSA environments.

## 📋 Overview

This library provides a **two-level caching solution** (L1: Local/Caffeine + L2: Global/Redis) designed for Spring Boot microservices. It reduces network latency and improves response times by serving frequently accessed data from local memory while maintaining consistency across distributed instances through Redis Pub/Sub.

### Key Features

- **Two-Level Cache Architecture**: L1 (Caffeine) for ultra-fast local access + L2 (Redis) for distributed caching
- **Zero Configuration**: Works out-of-the-box with `@Cacheable` annotations
- **Distributed Cache Invalidation**: Automatic L1 cache synchronization via Redis Pub/Sub
- **Flexible Configuration**: Global defaults with per-cache override support
- **Spring Boot Auto Configuration**: Seamless integration with Spring Boot 3.x

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                           Application Instance A                              │
│  ┌────────────────────────────────────────────────────────────────────────┐  │
│  │                         TwoLevelCacheManager                            │  │
│  │  ┌──────────────────────────────────────────────────────────────────┐  │  │
│  │  │                          TwoLevelCache                            │  │  │
│  │  │                                                                   │  │  │
│  │  │   ┌───────────────────┐        ┌───────────────────┐             │  │  │
│  │  │   │     L1 Cache      │        │     L2 Cache      │             │  │  │
│  │  │   │    (Caffeine)     │        │     (Redis)       │             │  │  │
│  │  │   │                   │        │                   │             │  │  │
│  │  │   │  • In-Memory      │        │  • Distributed    │             │  │  │
│  │  │   │  • Ultra-Fast     │        │  • Persistent     │             │  │  │
│  │  │   │  • Per-Instance   │        │  • Shared         │             │  │  │
│  │  │   └───────────────────┘        └───────────────────┘             │  │  │
│  │  └──────────────────────────────────────────────────────────────────┘  │  │
│  └────────────────────────────────────────────────────────────────────────┘  │
│                                       │                                       │
│                       ┌───────────────┴───────────────┐                       │
│                       │  CacheInvalidationPublisher   │                       │
│                       └───────────────┬───────────────┘                       │
└───────────────────────────────────────┼───────────────────────────────────────┘
                                        │
                                        ▼
                      ┌─────────────────────────────────────┐
                      │       Redis Pub/Sub Channel         │
                      │        "cache:invalidation"         │
                      └─────────────────┬───────────────────┘
                                        │
                                        ▼
┌───────────────────────────────────────┼───────────────────────────────────────┐
│                       ┌───────────────┴───────────────┐                       │
│                       │  CacheInvalidationSubscriber  │                       │
│                       └───────────────────────────────┘                       │
│                                                                               │
│                           Application Instance B                              │
│                          (L1 Cache Invalidated)                               │
└───────────────────────────────────────────────────────────────────────────────┘
```

## 🔄 Cache Flow

### Read Operation

```
@Cacheable("users")
public User findById(Long id) { ... }
```

```
┌─────────────────────────────────────────────────────────────────┐
│                        Cache Read Flow                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Check L1 (Caffeine)                                         │
│     │                                                           │
│     ├─── HIT ──────────────────────────────► Return Value      │
│     │                                                           │
│     └─── MISS                                                   │
│           │                                                     │
│           ▼                                                     │
│  2. Check L2 (Redis)                                            │
│     │                                                           │
│     ├─── HIT ───► Store in L1 ───► Return Value                 │
│     │                                                           │
│     └─── MISS                                                   │
│           │                                                     │
│           ▼                                                     │
│  3. Execute Method ───► Store in L1 & L2 ───► Return Value      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Write/Evict Operation

```
@CacheEvict("users")
public void deleteUser(Long id) { ... }
```

```
┌─────────────────────────────────────────────────────────────────┐
│                       Cache Evict Flow                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  1. Evict from L1 (Local Caffeine)                              │
│     │                                                           │
│     ▼                                                           │
│  2. Evict from L2 (Redis)                                       │
│     │                                                           │
│     ▼                                                           │
│  3. Publish Invalidation Message to Redis Pub/Sub               │
│     │                                                           │
│     ▼                                                           │
│  4. Other Instances Receive Message                             │
│     │                                                           │
│     ▼                                                           │
│  5. Other Instances Evict from L1 Only                          │
│     (L2 already invalidated in step 2)                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

## 🚀 Getting Started

### 1. Add Dependency

```xml
<dependency>
    <groupId>dev.skim</groupId>
    <artifactId>spring-boot-starter-near-cache</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Redis Connection

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      # password: your-password  # if required
```

### 3. Use Cache Annotations

```java
@Service
public class UserService {

    @Cacheable(value = "users", key = "#id")
    public User findById(Long id) {
        // This method will be cached
        return userRepository.findById(id).orElse(null);
    }

    @CachePut(value = "users", key = "#user.id")
    public User save(User user) {
        // Updates the cache with the returned value
        return userRepository.save(user);
    }

    @CacheEvict(value = "users", key = "#id")
    public void deleteById(Long id) {
        // Evicts from both L1 and L2, broadcasts to other instances
        userRepository.deleteById(id);
    }

    @CacheEvict(value = "users", allEntries = true)
    public void clearAllUsers() {
        // Clears entire cache and broadcasts to other instances
    }
}
```

## ⚙️ Configuration

### Full Configuration Options

```yaml
near-cache:
  # Enable/disable Near Cache (default: true)
  enabled: true
  
  # Redis Pub/Sub channel for invalidation messages (default: cache:invalidation)
  invalidation-channel: cache:invalidation
  
  # Instance ID - auto-generated UUID if not specified
  # Useful for debugging and identifying which instance published a message
  instance-id: ${HOSTNAME:instance-1}
  
  # L1 Cache (Caffeine) Configuration
  l1:
    enabled: true              # Enable L1 cache (default: true)
    max-size: 10000            # Maximum entries (default: 10000)
    expire-after-write: 10m    # Expiration after write (default: 10m)
    expire-after-access: 5m    # Expiration after access (optional)
  
  # L2 Cache (Redis) Configuration
  l2:
    enabled: true              # Enable L2 cache (default: true)
    ttl: 1h                    # Time-to-live (default: 1h)
    key-prefix: "near-cache:"  # Redis key prefix (default: near-cache:)
    cache-null-values: false   # Cache null values (default: false)
  
  # Per-Cache Configuration (overrides global settings)
  caches:
    users:
      l1-max-size: 5000
      l1-expire-after-write: 5m
      l2-ttl: 30m
    products:
      l1-max-size: 20000
      l1-expire-after-write: 15m
      l2-ttl: 2h
    sessions:
      l1-max-size: 1000
      l1-expire-after-write: 30m
      l1-expire-after-access: 10m
      l2-ttl: 1h
```

### Configuration Properties Reference

| Property                            | Type     | Default              | Description                          |
|-------------------------------------|----------|----------------------|--------------------------------------|
| `near-cache.enabled`                | boolean  | `true`               | Enable/disable the entire Near Cache |
| `near-cache.invalidation-channel`   | String   | `cache:invalidation` | Redis Pub/Sub channel name           |
| `near-cache.instance-id`            | String   | Auto-generated UUID  | Unique instance identifier           |
| `near-cache.l1.enabled`             | boolean  | `true`               | Enable L1 (Caffeine) cache           |
| `near-cache.l1.max-size`            | int      | `10000`              | Maximum L1 cache entries             |
| `near-cache.l1.expire-after-write`  | Duration | `10m`                | L1 expiration after write            |
| `near-cache.l1.expire-after-access` | Duration | -                    | L1 expiration after access           |
| `near-cache.l2.enabled`             | boolean  | `true`               | Enable L2 (Redis) cache              |
| `near-cache.l2.ttl`                 | Duration | `1h`                 | L2 time-to-live                      |
| `near-cache.l2.key-prefix`          | String   | `near-cache:`        | Redis key prefix                     |
| `near-cache.l2.cache-null-values`   | boolean  | `false`              | Whether to cache null values         |

## 🛠️ Programmatic Cache Operations

Use `NearCacheOperations` for direct cache manipulation:

```java
@Service
@RequiredArgsConstructor
public class CacheManagementService {

    private final NearCacheOperations cacheOps;

    public void examples() {
        // Get value from cache
        User user = cacheOps.get("users", 1L, User.class);
        
        // Put value into cache
        cacheOps.put("users", 1L, new User(1L, "John"));
        
        // Check if key exists
        boolean exists = cacheOps.exists("users", 1L);
        
        // Evict specific key
        cacheOps.evict("users", 1L);
        
        // Clear entire cache
        cacheOps.clear("users");
        
        // Clear all L1 caches
        cacheOps.clearAllL1();
        
        // Get or compute
        User user = cacheOps.getOrCompute("users", 1L, User.class, 
            () -> userRepository.findById(1L).orElse(null));
        
        // Get cache statistics
        TwoLevelCacheManager.CacheStats stats = cacheOps.getStats("users");
        System.out.println("L1 Size: " + stats.l1Size());
        System.out.println("L1 Hit Count: " + stats.l1HitCount());
        System.out.println("L1 Miss Count: " + stats.l1MissCount());
        
        // Get all cache names
        Collection<String> names = cacheOps.getCacheNames();
    }
}
```

## 📊 Monitoring & Statistics

### Cache Statistics

```java
@RestController
@RequiredArgsConstructor
public class CacheStatsController {

    private final NearCacheOperations cacheOps;

    @GetMapping("/cache/stats")
    public Map<String, TwoLevelCacheManager.CacheStats> getAllStats() {
        return cacheOps.getAllStats();
    }

    @GetMapping("/cache/stats/{cacheName}")
    public TwoLevelCacheManager.CacheStats getStats(@PathVariable String cacheName) {
        return cacheOps.getStats(cacheName);
    }
}
```

### Statistics Response Example

```json
{
  "users": {
    "cacheName": "users",
    "l1Size": 1523,
    "l1HitCount": 45678,
    "l1MissCount": 2341,
    "l1EvictionCount": 123
  },
  "products": {
    "cacheName": "products",
    "l1Size": 8765,
    "l1HitCount": 123456,
    "l1MissCount": 5432,
    "l1EvictionCount": 876
  }
}
```

## 📦 Project Structure

```
src/main/java/dev/skim/caffeinatedredis/
├── benchmark/
│   ├── BenchmarkReport.java         # Report generator
│   ├── BenchmarkResult.java         # Result record
│   ├── BenchmarkRunner.java         # Test runner
│   ├── CacheBenchmarkMain.java      # Benchmark entry point
│   ├── CaffeineOnlyBenchmark.java   # L1-only adapter
│   ├── RedisOnlyBenchmark.java      # L2-only adapter
│   └── TwoLevelCacheBenchmark.java  # L1+L2 adapter
├── cache/
│   ├── TwoLevelCache.java           # Core two-level cache implementation
│   └── TwoLevelCacheManager.java    # Spring CacheManager implementation
├── config/
│   ├── NearCacheAutoConfiguration.java  # Spring Boot auto-configuration
│   └── NearCacheProperties.java         # Configuration properties
├── message/
│   └── CacheInvalidationMessage.java    # Pub/Sub message DTO
├── pubsub/
│   ├── CacheInvalidationPublisher.java  # Publishes invalidation messages
│   └── CacheInvalidationSubscriber.java # Subscribes to invalidation messages
└── support/
    └── NearCacheOperations.java         # Utility class for cache operations
```

## 🔧 Technical Details

### Cache Invalidation Message Format

```json
{
  "messageId": "550e8400-e29b-41d4-a716-446655440000",
  "sourceInstanceId": "instance-1",
  "cacheName": "users",
  "cacheKey": "123",
  "type": "EVICT",
  "timestamp": "2024-01-15T10:30:00Z"
}
```

### Message Types

| Type    | Description                    |
|---------|--------------------------------|
| `EVICT` | Single key eviction            |
| `CLEAR` | Clear all entries in a cache   |

### Redis Key Format

```
{prefix}{cacheName}:{key}

Example: near-cache:users:123
```

## 🎯 Best Practices

### 1. Choose Appropriate TTLs

```yaml
near-cache:
  caches:
    # Frequently changing data - shorter TTL
    sessions:
      l1-expire-after-write: 5m
      l2-ttl: 15m
    
    # Relatively static data - longer TTL
    configurations:
      l1-expire-after-write: 1h
      l2-ttl: 24h
```

### 2. Size L1 Cache Appropriately

Consider your application's memory constraints:

```yaml
near-cache:
  l1:
    # For memory-constrained environments
    max-size: 1000
    
    # For high-memory servers
    max-size: 100000
```

### 3. Use Meaningful Cache Names

```java
// Good
@Cacheable(value = "user-profiles", key = "#userId")
@Cacheable(value = "product-catalog", key = "#productId")

// Avoid
@Cacheable(value = "cache1", key = "#id")
```

### 4. Handle Cache Failures Gracefully

The library logs warnings on Redis failures but continues serving from L1 or falls back to the original method. No additional error handling is typically required.

## 📊 Benchmark

The library includes a comprehensive benchmark suite to demonstrate performance advantages of the TwoLevel Cache over single-layer caching strategies.

### Running the Benchmark

```bash
# Build the project first
./mvnw clean package -DskipTests

# Run benchmark using Maven (requires Redis on localhost:6379)
./mvnw exec:java

# With custom Redis host/port
./mvnw exec:java -Dexec.args="redis-host 6379"
```

### Benchmark Scenarios

| Scenario     | Read % | Write % | Purpose                          |
|--------------|--------|---------|----------------------------------|
| READ_HEAVY   | 90%    | 10%     | Typical web application workload |
| PURE_READ    | 100%   | 0%      | L1 cache hit performance         |
| WRITE_HEAVY  | 30%    | 70%     | High-write scenarios             |

### Expected Results

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         COMPARISON TABLE                                    │
├───────────────────────┬──────────────┬──────────────┬──────────────────────┤
│ Cache Strategy        │ Avg Latency  │ Throughput   │ P99 Latency          │
├───────────────────────┼──────────────┼──────────────┼──────────────────────┤
│ Caffeine-Only (L1)    │   0.0012 ms  │   2500000/s  │         0.0089 ms    │
│ Redis-Only (L2)       │   0.4523 ms  │     22000/s  │         1.2340 ms    │
│ TwoLevel (L1+L2)      │   0.0156 ms  │   1800000/s  │         0.0234 ms    │
└───────────────────────┴──────────────┴──────────────┴──────────────────────┘
```

### Performance Analysis

| Comparison                 | Improvement              | Explanation                                         |
|----------------------------|--------------------------|-----------------------------------------------------|
| TwoLevel vs Redis-Only     | ~95% faster latency      | L1 serves hot data without network round-trip       |
| TwoLevel vs Caffeine-Only  | +distributed consistency | Small overhead for L2 sync, gains cross-instance consistency |

## 📋 Requirements

- **Java**: 17+
- **Spring Boot**: 3.x
- **Redis**: 6.x+ (for Pub/Sub support)

## 🔨 Building

```bash
# Build the project
./mvnw clean package

# Install to local repository
./mvnw clean install

# Skip tests
./mvnw clean install -DskipTests
```

## 📄 License

MIT License

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.
