package io.github.schntgaispock.gastronomicon.benchmark;

import io.github.schntgaispock.gastronomicon.api.loot.LootTable;
import io.github.schntgaispock.gastronomicon.util.NumberUtil;
import io.github.schntgaispock.gastronomicon.util.collections.Counter;

/**
 * Round 0 基线 / Round 2 复测：测量纯 JVM 热路径性能（真实代码、真实墙钟）。
 * 所有被测代码均可在无服务端 JVM 下运行（经 Probe 确认）。
 *
 * 覆盖：
 *  - NumberUtil.asRomanNumeral / randomRound / clamp(int) / clamp(double)
 *  - LootTable.generate (Vose 别名法；FishingNet/HuntingTrap/野生采集每 tick 调用)
 *  - Counter.add/get (ElectricKitchen 槽位映射每合成调用)
 */
public final class BaselineBenchmark {

    // op() 每次做 INNER 次工作；harness 每轮调 OUTER 次 op → INNER*OUTER 次工作/轮
    private static final int INNER = 2_000_000;
    private static final int OUTER = 3;

    public static void main(String[] args) {
        System.out.println("=== Gastronomicon pure-JVM hot-path benchmark ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " ops/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS
            + " (after " + BenchmarkHarness.DEFAULT_WARMUP_ROUNDS + " warmup)");
        System.out.println();

        // ---- NumberUtil.asRomanNumeral ----
        int[] rn = {1, 4, 9, 40, 90, 400, 900, 1994, 3888, 0};
        BenchmarkHarness.measure("NumberUtil.asRomanNumeral(mixed)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += NumberUtil.asRomanNumeral(rn[i % rn.length]).length();
            return s;
        }).print();

        // ---- NumberUtil.randomRound ----
        BenchmarkHarness.measure("NumberUtil.randomRound(0..2)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += NumberUtil.randomRound((i & 7) * 0.25);
            return s;
        }).print();

        // ---- NumberUtil.clamp ----
        BenchmarkHarness.measure("NumberUtil.clamp(int)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += NumberUtil.clamp(i, 10, 90);
            return s;
        }).print();
        BenchmarkHarness.measure("NumberUtil.clamp(double)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += (long) NumberUtil.clamp(i * 0.5, 5.0, 45.0);
            return s;
        }).print();

        // ---- LootTable.generate ----
        LootTable<String> table = LootTable.builder(String.class)
            .add(1, "a").add(2, "b", "c").add(5, "d", "e", "f")
            .add(8, "g", "h").add(3, "i", "j").build();
        BenchmarkHarness.measure("LootTable.generate (n=10)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += table.generate() == null ? 0 : 1;
            return s;
        }).print();

        LootTable<String> small = LootTable.builder(String.class).add(3, "x").add(7, "y").build();
        BenchmarkHarness.measure("LootTable.generate (n=2)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += small.generate() == null ? 0 : 1;
            return s;
        }).print();

        // 偏斜表（主导掉落 70%）——主导桶为满概率桶，跳过第二次随机
        LootTable<String> skewed = LootTable.builder(String.class)
            .add(70, "common").add(10, "uncommon").add(2, "rare").build();
        BenchmarkHarness.measure("LootTable.generate (n=3 skewed)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += skewed.generate() == null ? 0 : 1;
            return s;
        }).print();

        // ---- Counter.add / get ----
        BenchmarkHarness.measure("Counter.add (10 distinct)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) {
                Counter<Integer> c = new Counter<>();
                for (int k = 0; k < 10; k++) c.add(k, 1);
                s += c.getTotal();
            }
            return s;
        }).print();
        BenchmarkHarness.measure("Counter.get (10 distinct)", OUTER, INNER, () -> {
            Counter<Integer> c = new Counter<>();
            for (int k = 0; k < 10; k++) c.add(k, 1);
            long s = 0;
            for (int i = 0; i < INNER; i++) s += c.get(i % 10);
            return s;
        }).print();

        System.out.println();
        System.out.println("DONE.");
    }

    private BaselineBenchmark() {}
}
