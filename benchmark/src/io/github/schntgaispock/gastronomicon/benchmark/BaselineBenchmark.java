package io.github.schntgaispock.gastronomicon.benchmark;

import io.github.schntgaispock.gastronomicon.api.loot.LootTable;
import io.github.schntgaispock.gastronomicon.util.NumberUtil;
import io.github.schntgaispock.gastronomicon.util.collections.Counter;

/**
 * Round 0 基线：测量纯 JVM 热路径的当前性能（真实代码、真实墙钟）。
 * 所有被测代码均可在无服务端 JVM 下运行（经 Probe 确认）。
 *
 * 覆盖：
 *  - NumberUtil.asRomanNumeral / randomRound / clamp(int) / clamp(double)
 *  - LootTable.generate (Vose 别名法；FishingNet/HuntingTrap/野生采集每 tick 调用)
 *  - Counter.add/get (ElectricKitchen 槽位映射每合成调用)
 */
public final class BaselineBenchmark {

    // 每个 measure 轮调用 op() 的次数；op() 内部再做 100 次微操作 → 2_000_000 次/轮
    private static final int ITERS = 20_000;

    public static void main(String[] args) {
        System.out.println("=== Gastronomicon Baseline (pure-JVM hot paths) ===");
        System.out.println("iters/op-set = " + ITERS + "  | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS
            + " (after " + BenchmarkHarness.DEFAULT_WARMUP_ROUNDS + " warmup)");
        System.out.println();

        // ---- NumberUtil.asRomanNumeral ----
        BenchmarkHarness.measure("NumberUtil.asRomanNumeral(mixed)", ITERS, () -> {
            long s = 0;
            int[] inputs = {1, 4, 9, 40, 90, 400, 900, 1994, 3888, 0};
            for (int i = 0; i < 100; i++) s += NumberUtil.asRomanNumeral(inputs[i % inputs.length]).length();
            return s;
        }).print();

        // ---- NumberUtil.randomRound ----
        BenchmarkHarness.measure("NumberUtil.randomRound(0..2)", ITERS, () -> {
            long s = 0;
            for (int i = 0; i < 100; i++) s += NumberUtil.randomRound((i & 7) * 0.25);
            return s;
        }).print();

        // ---- NumberUtil.clamp ----
        BenchmarkHarness.measure("NumberUtil.clamp(int)", ITERS, () -> {
            long s = 0;
            for (int i = 0; i < 100; i++) s += NumberUtil.clamp(i, 10, 90);
            return s;
        }).print();
        BenchmarkHarness.measure("NumberUtil.clamp(double)", ITERS, () -> {
            long s = 0;
            for (int i = 0; i < 100; i++) s += (long) NumberUtil.clamp(i * 0.5, 5.0, 45.0);
            return s;
        }).print();

        // ---- LootTable.generate (与既有 LootTableTests 同口径，但测量墙钟) ----
        // 构造一个贴近真实掉落表规模的表（10 项、权重不一）
        LootTable<String> table = LootTable.builder(String.class)
            .add(1, "a")
            .add(2, "b", "c")
            .add(5, "d", "e", "f")
            .add(8, "g", "h")
            .add(3, "i", "j")
            .build();
        BenchmarkHarness.measure("LootTable.generate (n=10)", ITERS, () -> {
            long s = 0;
            for (int i = 0; i < 100; i++) s += table.generate() == null ? 0 : 1;
            return s;
        }).print();

        // 小表（n=2，渔网常见）
        LootTable<String> small = LootTable.builder(String.class).add(3, "x").add(7, "y").build();
        BenchmarkHarness.measure("LootTable.generate (n=2)", ITERS, () -> {
            long s = 0;
            for (int i = 0; i < 100; i++) s += small.generate() == null ? 0 : 1;
            return s;
        }).print();

        // ---- Counter.add / get ----
        BenchmarkHarness.measure("Counter.add (10 distinct)", ITERS, () -> {
            Counter<Integer> c = new Counter<>();
            for (int i = 0; i < 10; i++) c.add(i % 10, 1);
            return c.getTotal();
        }).print();
        BenchmarkHarness.measure("Counter.get (10 distinct)", ITERS, () -> {
            Counter<Integer> c = new Counter<>();
            for (int i = 0; i < 10; i++) c.add(i, 1);
            long s = 0;
            for (int i = 0; i < 10; i++) s += c.get(i);
            return s;
        }).print();

        System.out.println();
        System.out.println("DONE. (these numbers are the Round 0 baseline for pure-JVM paths)");
    }

    private BaselineBenchmark() {}
}
