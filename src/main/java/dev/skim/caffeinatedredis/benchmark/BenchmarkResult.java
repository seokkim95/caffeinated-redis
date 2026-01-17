package dev.skim.caffeinatedredis.benchmark;

/**
 * Benchmark result record for storing performance metrics.
 */
public record BenchmarkResult(
        String cacheName,
        String operation,
        int totalOperations,
        long totalTimeMs,
        double avgLatencyMs,
        double throughputOpsPerSec,
        long minLatencyNs,
        long maxLatencyNs,
        double p50LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs
) {
    @Override
    public String toString() {
        return String.format(
                """
                ┌─────────────────────────────────────────────────────────────┐
                │ Benchmark Result: %-40s │
                ├─────────────────────────────────────────────────────────────┤
                │ Operation          : %-38s │
                │ Total Operations   : %-38d │
                │ Total Time         : %-34d ms │
                │ Avg Latency        : %-34.4f ms │
                │ Throughput         : %-30.2f ops/sec │
                │ Min Latency        : %-34.2f ms │
                │ Max Latency        : %-34.2f ms │
                │ P50 Latency        : %-34.4f ms │
                │ P95 Latency        : %-34.4f ms │
                │ P99 Latency        : %-34.4f ms │
                └─────────────────────────────────────────────────────────────┘
                """,
                cacheName,
                operation,
                totalOperations,
                totalTimeMs,
                avgLatencyMs,
                throughputOpsPerSec,
                minLatencyNs / 1_000_000.0,
                maxLatencyNs / 1_000_000.0,
                p50LatencyMs,
                p95LatencyMs,
                p99LatencyMs
        );
    }
}

