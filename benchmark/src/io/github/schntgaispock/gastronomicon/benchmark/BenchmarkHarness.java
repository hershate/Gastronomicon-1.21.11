package io.github.schntgaispock.gastronomicon.benchmark;

import java.util.function.LongSupplier;

/**
 * 极简基准运行器（沿用项目 main 方法约定，无外部依赖）。
 * <p>
 * 方法论：JIT 预热（warmup 轮，丢弃）→ 正式测量轮取最稳值；报告 ns/op 与 ops/s。
 * 单线程，禁用 JIT 优化逃逸（每次消费返回值）。
 * <p>
 * 数字是纯 JVM 下的「逻辑成本」：凡涉及 Bukkit/Slimefun 的叶子调用，已在对应
 * 基准点用桩/解析器注入替换并注明，绝对值仅供同口径前后对比。
 */
public final class BenchmarkHarness {

    /** 默认预热轮数（每轮 iterations 次） */
    public static final int DEFAULT_WARMUP_ROUNDS = 3;
    /** 默认测量轮数 */
    public static final int DEFAULT_MEASURE_ROUNDS = 5;

    public static Result measure(String name, int iterations, LongSupplier op) {
        return measure(name, iterations, DEFAULT_WARMUP_ROUNDS, DEFAULT_MEASURE_ROUNDS, op);
    }

    /**
     * @param iterations 每轮调用 op.getAsLong() 的次数；op 返回值会被累加消费，防止死代码消除
     */
    public static Result measure(String name, int iterations, int warmupRounds, int measureRounds, LongSupplier op) {
        long blackhole = 0;
        // 预热
        for (int w = 0; w < warmupRounds; w++) {
            for (int i = 0; i < iterations; i++) blackhole ^= op.getAsLong();
        }
        // 测量
        long bestNs = Long.MAX_VALUE;
        long sumNs = 0;
        for (int m = 0; m < measureRounds; m++) {
            long t0 = System.nanoTime();
            for (int i = 0; i < iterations; i++) blackhole ^= op.getAsLong();
            long dt = System.nanoTime() - t0;
            if (dt < bestNs) bestNs = dt;
            sumNs += dt;
        }
        if (blackhole == Long.MIN_VALUE) System.out.print(""); // 防止死代码消除
        double bestNsPerOp = bestNs / (double) iterations;
        double avgNsPerOp = (sumNs / (double) measureRounds) / iterations;
        Result r = new Result(name, iterations, bestNsPerOp, avgNsPerOp, measureRounds);
        return r;
    }

    public static final class Result {
        public final String name;
        public final int iterations;
        public final double bestNsPerOp;
        public final double avgNsPerOp;
        public final int rounds;

        Result(String name, int iterations, double bestNsPerOp, double avgNsPerOp, int rounds) {
            this.name = name;
            this.iterations = iterations;
            this.bestNsPerOp = bestNsPerOp;
            this.avgNsPerOp = avgNsPerOp;
            this.rounds = rounds;
        }

        public void print() {
            System.out.printf("  %-42s %12.1f ns/op (avg %8.1f) | %12.0f ops/s%n",
                name, bestNsPerOp, avgNsPerOp, 1_000_000_000.0 / bestNsPerOp);
        }
    }

    private BenchmarkHarness() {}
}
