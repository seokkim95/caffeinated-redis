package dev.skim.caffeinatedredis.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark report generator for comparing cache strategies.
 */
public class BenchmarkReport {

    private final List<BenchmarkResult> results = new ArrayList<>();
    private final String title;

    public BenchmarkReport(String title) {
        this.title = title;
    }

    public void addResult(BenchmarkResult result) {
        results.add(result);
    }

    public void printReport() {
        System.out.println();
        System.out.println("╔═════════════════════════════════════════════════════════════════════════════╗");
        System.out.printf("║ %-75s ║%n", title);
        System.out.println("╠═════════════════════════════════════════════════════════════════════════════╣");
        System.out.println();

        for (BenchmarkResult result : results) {
            System.out.println(result);
        }

        printComparisonTable();
        printAnalysis();
    }

    private void printComparisonTable() {
        System.out.println("┌─────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                         COMPARISON TABLE                                    │");
        System.out.println("├───────────────────────┬──────────────┬──────────────┬──────────────────────┤");
        System.out.println("│ Cache Strategy        │ Avg Latency  │ Throughput   │ P99 Latency          │");
        System.out.println("├───────────────────────┼──────────────┼──────────────┼──────────────────────┤");

        for (BenchmarkResult result : results) {
            System.out.printf("│ %-21s │ %8.4f ms │ %10.0f/s │ %14.4f ms    │%n",
                    result.cacheName(),
                    result.avgLatencyMs(),
                    result.throughputOpsPerSec(),
                    result.p99LatencyMs());
        }

        System.out.println("└───────────────────────┴──────────────┴──────────────┴──────────────────────┘");
        System.out.println();
    }

    private void printAnalysis() {
        if (results.size() < 2) {
            return;
        }

        BenchmarkResult caffeineOnly = null;
        BenchmarkResult redisOnly = null;
        BenchmarkResult twoLevel = null;

        for (BenchmarkResult result : results) {
            if (result.cacheName().contains("Caffeine")) {
                caffeineOnly = result;
            } else if (result.cacheName().contains("Redis")) {
                redisOnly = result;
            } else if (result.cacheName().contains("TwoLevel") || result.cacheName().contains("Near")) {
                twoLevel = result;
            }
        }

        System.out.println("┌─────────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│                              ANALYSIS                                       │");
        System.out.println("├─────────────────────────────────────────────────────────────────────────────┤");

        if (twoLevel != null && redisOnly != null) {
            double latencyImprovement = ((redisOnly.avgLatencyMs() - twoLevel.avgLatencyMs()) / redisOnly.avgLatencyMs()) * 100;
            double throughputImprovement = ((twoLevel.throughputOpsPerSec() - redisOnly.throughputOpsPerSec()) / redisOnly.throughputOpsPerSec()) * 100;

            System.out.printf("│ TwoLevel vs Redis-Only:                                                     │%n");
            System.out.printf("│   • Latency Improvement    : %+.2f%% %s                               │%n",
                    latencyImprovement,
                    latencyImprovement > 0 ? "(FASTER)" : "(SLOWER)");
            System.out.printf("│   • Throughput Improvement : %+.2f%% %s                               │%n",
                    throughputImprovement,
                    throughputImprovement > 0 ? "(BETTER)" : "(WORSE)");
        }

        if (twoLevel != null && caffeineOnly != null) {
            double latencyDiff = ((twoLevel.avgLatencyMs() - caffeineOnly.avgLatencyMs()) / caffeineOnly.avgLatencyMs()) * 100;

            System.out.printf("│                                                                             │%n");
            System.out.printf("│ TwoLevel vs Caffeine-Only:                                                  │%n");
            System.out.printf("│   • Latency Overhead       : %+.2f%% (L2 sync cost)                     │%n",
                    latencyDiff);
            System.out.printf("│   • Benefit: Distributed consistency + Persistence                         │%n");
        }

        if (caffeineOnly != null && redisOnly != null) {
            double networkOverhead = ((redisOnly.avgLatencyMs() - caffeineOnly.avgLatencyMs()) / caffeineOnly.avgLatencyMs()) * 100;
            System.out.printf("│                                                                             │%n");
            System.out.printf("│ Network Overhead (Redis vs Caffeine): %+.2f%%                            │%n",
                    networkOverhead);
        }

        System.out.println("├─────────────────────────────────────────────────────────────────────────────┤");
        System.out.println("│                            CONCLUSION                                       │");
        System.out.println("├─────────────────────────────────────────────────────────────────────────────┤");

        if (twoLevel != null && redisOnly != null && caffeineOnly != null) {
            if (twoLevel.avgLatencyMs() < redisOnly.avgLatencyMs()) {
                System.out.println("│ ✅ TwoLevel Cache achieves BETTER performance than Redis-Only              │");
                System.out.println("│    by leveraging local L1 cache for frequently accessed data.              │");
                System.out.println("│                                                                             │");
                System.out.println("│ ✅ TwoLevel Cache provides distributed consistency that                     │");
                System.out.println("│    Caffeine-Only cannot offer in multi-instance deployments.               │");
                System.out.println("│                                                                             │");
                System.out.println("│ 🎯 RECOMMENDATION: Use TwoLevel (Near) Cache for MSA environments          │");
                System.out.println("│    where both performance and consistency are required.                    │");
            } else {
                System.out.println("│ ⚠️  Results may vary based on network conditions and data access patterns. │");
            }
        }

        System.out.println("└─────────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }
}

