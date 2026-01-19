package dev.skim.caffeinatedredis.benchmark;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Benchmark runner for measuring cache performance.
 *
 * Notes:
 * - This is a lightweight benchmark harness (not JMH).
 * - Warmup and measurement are executed with the same thread count to reduce bias.
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

    public BenchmarkResult run(String name, String operationType, Consumer<Integer> operation) {
        System.out.printf("  [%s] Warming up (%d iterations, %d threads)...%n", name, warmupIterations, threadCount);
        runConcurrently(warmupIterations, operation, null);

        System.gc();
        sleepQuietly(150);

        System.out.printf("  [%s] Measuring (%d iterations, %d threads)...%n", name, measureIterations, threadCount);

        long[] latencies = new long[measureIterations];
        long startTime = System.nanoTime();
        runConcurrently(measureIterations, operation, latencies);
        long endTime = System.nanoTime();

        long totalTimeNs = endTime - startTime;
        long totalTimeMs = totalTimeNs / 1_000_000;

        Arrays.sort(latencies);

        long minLatency = latencies[0];
        long maxLatency = latencies[measureIterations - 1];

        long totalLatency = 0;
        for (long lat : latencies) {
            totalLatency += lat;
        }

        double avgLatencyMs = ((double) totalLatency / measureIterations) / 1_000_000.0;
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

    private void runConcurrently(int iterations, Consumer<Integer> operation, long[] latenciesOrNull) {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        int base = iterations / threadCount;
        int remainder = iterations % threadCount;

        int start = 0;
        for (int t = 0; t < threadCount; t++) {
            int size = base + (t < remainder ? 1 : 0);
            int rangeStart = start;
            int rangeEnd = start + size;
            start = rangeEnd;

            executor.submit(() -> {
                try {
                    for (int i = rangeStart; i < rangeEnd; i++) {
                        long opStart = System.nanoTime();
                        operation.accept(i);
                        long opEnd = System.nanoTime();
                        if (latenciesOrNull != null) {
                            latenciesOrNull[i] = opEnd - opStart;
                        }
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
            throw new IllegalStateException("Benchmark interrupted", e);
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public BenchmarkResult runReadHeavy(String name, Consumer<Integer> readOp, Consumer<Integer> writeOp) {
        return run(name, "READ_HEAVY (90/10)", i -> {
            if (i % 10 == 0) {
                writeOp.accept(i);
            } else {
                readOp.accept(i);
            }
        });
    }

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
