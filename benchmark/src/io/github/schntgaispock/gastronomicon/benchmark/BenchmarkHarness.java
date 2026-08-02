package io.github.schntgaispock.gastronomicon.benchmark;

import java.util.function.LongSupplier;

/**
 * 极简基准运行器（沿用项目 main 方法约定，无外部依赖）。
 * <p>
 * 方法论：JIT 预热（warmup 轮，丢弃）→ 正式测量轮取最稳值；报告 ns/op 与 ops/s。
 * <p>
 * 关键设计：op() 内部用**扁平循环**做 innerOps 次工作并返回校验和；harness 每轮调用 op() outerCalls 次。
 * 计时粒度 = outerCalls × innerOps（足够大以压制系统抖动），且每次工作单元**不经过 lambda 调用**
 * （lambda 仅 outerCalls 次/轮，可忽略），从而测量的是被测代码本身而非调度开销。
 * <p>
 * 数字是纯 JVM 下的「逻辑成本」：凡涉及 Bukkit/Slimefun 的叶子调用，已在对应基准点用桩/解析器
 * 注入替换并注明，绝对值仅供同口径前后对比。
 */
public final class BenchmarkHarness {

    public static final int DEFAULT_WARMUP_ROUNDS = 5;
    public static final int DEFAULT_MEASURE_ROUNDS = 8;

    /**
     * @param outerCalls 每轮调用 op() 的次数
     * @param innerOps   op() 每次执行的工作单元数（harness 据此换算 ns/op）
     * @param op         执行 innerOps 次工作并返回校验和（防死代码消除）
     */
    public static Result measure(String name, int outerCalls, int innerOps, LongSupplier op) {
        return measure(name, outerCalls, innerOps, DEFAULT_WARMUP_ROUNDS, DEFAULT_MEASURE_ROUNDS, op);
    }

    public static Result measure(String name, int outerCalls, int innerOps, int warmupRounds, int measureRounds,
            LongSupplier op) {
        long blackhole = 0;
        for (int w = 0; w < warmupRounds; w++) {
            for (int o = 0; o < outerCalls; o++) blackhole ^= op.getAsLong();
        }
        long bestNs = Long.MAX_VALUE;
        long sumNs = 0;
        for (int m = 0; m < measureRounds; m++) {
            long t0 = System.nanoTime();
            for (int o = 0; o < outerCalls; o++) blackhole ^= op.getAsLong();
            long dt = System.nanoTime() - t0;
            if (dt < bestNs) bestNs = dt;
            sumNs += dt;
        }
        if (blackhole == Long.MIN_VALUE) System.out.print("");
        long totalOps = (long) outerCalls * innerOps;
        double bestNsPerOp = bestNs / (double) totalOps;
        double avgNsPerOp = (sumNs / (double) measureRounds) / totalOps;
        return new Result(name, totalOps, bestNsPerOp, avgNsPerOp, measureRounds);
    }

    public static final class Result {
        public final String name;
        public final long totalOps;
        public final double bestNsPerOp;
        public final double avgNsPerOp;
        public final int rounds;

        Result(String name, long totalOps, double bestNsPerOp, double avgNsPerOp, int rounds) {
            this.name = name;
            this.totalOps = totalOps;
            this.bestNsPerOp = bestNsPerOp;
            this.avgNsPerOp = avgNsPerOp;
            this.rounds = rounds;
        }

        public void print() {
            // Locale.ROOT 强制小数点为 '.'，避免 Windows 默认 locale 用逗号影响报告可读性
            System.out.printf(java.util.Locale.ROOT, "  %-44s %12.2f ns/op (avg %8.2f) | %12.0f ops/s%n",
                name, bestNsPerOp, avgNsPerOp, 1_000_000_000.0 / bestNsPerOp);
        }
    }

    private BenchmarkHarness() {}
}
