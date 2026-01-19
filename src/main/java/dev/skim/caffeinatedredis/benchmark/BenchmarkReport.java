package dev.skim.caffeinatedredis.benchmark;

import java.util.ArrayList;
import java.util.List;

/**
 * Benchmark report generator for comparing cache strategies.
 */
public class BenchmarkReport {

    private static final int WIDTH = 78;

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

        System.out.println("┌" + "─".repeat(WIDTH) + "┐");
        System.out.println("│" + padRight("ANALYSIS") + "│");
        System.out.println("├" + "─".repeat(WIDTH) + "┤");

        if (twoLevel != null && redisOnly != null) {
            double latencyImprovement = ((redisOnly.avgLatencyMs() - twoLevel.avgLatencyMs()) / redisOnly.avgLatencyMs()) * 100;
            double throughputImprovement = ((twoLevel.throughputOpsPerSec() - redisOnly.throughputOpsPerSec()) / redisOnly.throughputOpsPerSec()) * 100;

            System.out.println("│" + padRight("TwoLevel vs Redis-Only:") + "│");
            System.out.println("│" + padRight(String.format("  - Latency improvement    : %+.2f%% %s", latencyImprovement,
                    latencyImprovement > 0 ? "(FASTER)" : "(SLOWER)")) + "│");
            System.out.println("│" + padRight(String.format("  - Throughput improvement : %+.2f%% %s", throughputImprovement,
                    throughputImprovement > 0 ? "(BETTER)" : "(WORSE)")) + "│");
        }

        if (twoLevel != null && caffeineOnly != null) {
            double latencyDiff = ((twoLevel.avgLatencyMs() - caffeineOnly.avgLatencyMs()) / caffeineOnly.avgLatencyMs()) * 100;

            System.out.println("│" + padRight("") + "│");
            System.out.println("│" + padRight("TwoLevel vs Caffeine-Only:") + "│");
            System.out.println("│" + padRight(String.format("  - Latency overhead       : %+.2f%% (dual-write + L2 sync)", latencyDiff)) + "│");
            System.out.println("│" + padRight("  - Benefit                : distributed consistency + persistence") + "│");
        }

        if (caffeineOnly != null && redisOnly != null) {
            double networkOverhead = ((redisOnly.avgLatencyMs() - caffeineOnly.avgLatencyMs()) / caffeineOnly.avgLatencyMs()) * 100;
            System.out.println("│" + padRight("") + "│");
            System.out.println("│" + padRight(String.format("Network overhead (Redis vs Caffeine): %+.2f%%", networkOverhead)) + "│");
        }

        System.out.println("├" + "─".repeat(WIDTH) + "┤");
        System.out.println("│" + padRight("CONCLUSION") + "│");
        System.out.println("├" + "─".repeat(WIDTH) + "┤");

        if (twoLevel != null && redisOnly != null) {
            if (twoLevel.avgLatencyMs() < redisOnly.avgLatencyMs()) {
                System.out.println("│" + padRight("[PASS] TwoLevel is faster than Redis-Only in this run.") + "│");
                System.out.println("│" + padRight("       Hot keys are served from L1; writes keep L2 consistent.") + "│");
                System.out.println("│" + padRight("       Recommendation: Use TwoLevel for MSA read-heavy workloads.") + "│");
            } else {
                System.out.println("│" + padRight("[WARN] TwoLevel was not faster than Redis-Only in this run.") + "│");
                System.out.println("│" + padRight("       Results vary depending on network and access distribution.") + "│");
            }
        }

        System.out.println("└" + "─".repeat(WIDTH) + "┘");
        System.out.println();
    }

    private static String padRight(String s) {
        if (s == null) {
            s = "";
        }
        if (s.length() >= WIDTH) {
            return s.substring(0, WIDTH);
        }
        return s + " ".repeat(WIDTH - s.length());
    }
}
