package dev.skim.caffeinatedredis.benchmark;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Benchmark runner for measuring cache performance.
 * Supports concurrent operations and latency percentile calculations.
 */
public class BenchmarkRunner {

    private final int warmupIterations;
    private final int measureIterations;
    private final int threadCount;

    public BenchmarkRunner(int warmupIterations, int measureIterations, int threadCount) {
        this.warmupIterations = warmupIterations;
        this.measureIterations = measureIterations;
        this.threadCount = threadCount;
    }

    /**
     * Run benchmark with the given operation
     *
     * @param name      benchmark name
     * @param operation operation to benchmark (receives iteration index)
     * @return benchmark result
     */
    public BenchmarkResult run(String name, String operationType, Consumer<Integer> operation) {
        // Warmup phase
        System.out.printf("  [%s] Warming up (%d iterations)...%n", name, warmupIterations);
        for (int i = 0; i < warmupIterations; i++) {
            operation.accept(i);
        }

        // Force GC before measurement
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Measurement phase
        System.out.printf("  [%s] Measuring (%d iterations, %d threads)...%n",
                name, measureIterations, threadCount);

        long[] latencies = new long[measureIterations];
        AtomicInteger index = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        long startTime = System.nanoTime();

        for (int t = 0; t < threadCount; t++) {
            executor.submit(() -> {
                try {
                    int i;
                    while ((i = index.getAndIncrement()) < measureIterations) {
                        long opStart = System.nanoTime();
                        operation.accept(i);
                        long opEnd = System.nanoTime();
                        latencies[i] = opEnd - opStart;
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.nanoTime();
        executor.shutdown();

        long totalTimeNs = endTime - startTime;
        long totalTimeMs = totalTimeNs / 1_000_000;

        // Calculate statistics
        Arrays.sort(latencies);

        long minLatency = latencies[0];
        long maxLatency = latencies[measureIterations - 1];

        long totalLatency = 0;
        for (long lat : latencies) {
            totalLatency += lat;
        }
        double avgLatencyNs = (double) totalLatency / measureIterations;
        double avgLatencyMs = avgLatencyNs / 1_000_000.0;

        double p50 = latencies[(int) (measureIterations * 0.50)] / 1_000_000.0;
        double p95 = latencies[(int) (measureIterations * 0.95)] / 1_000_000.0;
        double p99 = latencies[(int) (measureIterations * 0.99)] / 1_000_000.0;

        double throughput = (double) measureIterations / (totalTimeMs / 1000.0);

        return new BenchmarkResult(
                name,
                operationType,
                measureIterations,
                totalTimeMs,
                avgLatencyMs,
                throughput,
                minLatency,
                maxLatency,
                p50,
                p95,
                p99
        );
    }

    /**
     * Run read-heavy benchmark (90% reads, 10% writes)
     */
    public BenchmarkResult runReadHeavy(String name, Consumer<Integer> readOp, Consumer<Integer> writeOp) {
        return run(name, "READ_HEAVY (90/10)", i -> {
            if (i % 10 == 0) {
                writeOp.accept(i);
            } else {
                readOp.accept(i);
            }
        });
    }

    /**
     * Run write-heavy benchmark (30% reads, 70% writes)
     */
    public BenchmarkResult runWriteHeavy(String name, Consumer<Integer> readOp, Consumer<Integer> writeOp) {
        return run(name, "WRITE_HEAVY (30/70)", i -> {
            if (i % 10 < 7) {
                writeOp.accept(i);
            } else {
                readOp.accept(i);
            }
        });
    }
}

