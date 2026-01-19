# Near Cache Benchmark Suite

This package contains a lightweight benchmark suite (not JMH) to compare the performance of:

1. **Caffeine-Only (L1)** - Local in-memory cache
2. **Redis-Only (L2)** - Distributed network cache
3. **TwoLevel/Near Cache (L1+L2)** - Combined local + distributed cache

## Notes About Benchmark Accuracy

This benchmark is designed to be easy to run and good for relative comparisons.
For statistically rigorous microbenchmarking, prefer JMH.

To reduce noise:
- Run on an idle machine
- Disable battery-saver / performance throttling
- Run multiple times and compare medians

## Redis Key Isolation

Each benchmark run uses a unique Redis key namespace (prefix) based on the current timestamp.
This prevents collisions with leftover keys from previous runs.

## Prerequisites

- Java 17+
- Maven
- Redis running on `localhost:6379` (or specify host/port)

### Starting Redis (Docker)

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

## Running the Benchmark

### Using Maven Exec Plugin

```bash
./mvnw -DskipTests package
./mvnw exec:java

# Custom Redis host/port
./mvnw exec:java -Dexec.args="127.0.0.1 6379"
```

## Scenarios

- **Read-heavy (90/10)**: typical API workload
- **Pure read**: L1 warmed scenario
- **Write-heavy (30/70)**: frequent updates

## Interpreting Results

- TwoLevel should be significantly faster than Redis-Only when there is key locality (hot keys).
- TwoLevel will always be slower than pure Caffeine due to L2 write-through and serialization.
