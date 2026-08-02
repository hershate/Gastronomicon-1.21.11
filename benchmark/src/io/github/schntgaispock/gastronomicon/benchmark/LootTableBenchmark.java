package io.github.schntgaispock.gastronomicon.benchmark;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import io.github.schntgaispock.gastronomicon.api.loot.LootTable;

/**
 * Round 2 基准：LootTable.generate 新旧算法**同 JVM 对比**，消除跨运行噪声。
 * <p>
 * 用反射从真实 build() 产出的 LootTable 读出 drops/totalWeight/prob/alias（一次性，不在热路径），
 * 分别用「旧算法（List + nextDouble()*tw）」与「新算法（数组 + nextInt(tw) + 满桶跳过）」
 * 在同一进程、同一 harness 下交替测量，得到同口径 ns/generate。
 */
public final class LootTableBenchmark {

    private static final int INNER = 5_000_000;
    private static final int OUTER = 3;

    // 反射字段（一次性读取）
    private static Field fDrops, fTw, fProb, fAlias;
    static {
        try {
            fDrops = LootTable.class.getDeclaredField("drops");   fDrops.setAccessible(true);
            fTw = LootTable.class.getDeclaredField("totalWeight"); fTw.setAccessible(true);
            fProb = LootTable.class.getDeclaredField("prob");     fProb.setAccessible(true);
            fAlias = LootTable.class.getDeclaredField("alias");   fAlias.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    /** 旧算法：List.get + 两次 ThreadLocalRandom.current() + nextDouble()*tw 浮点乘。 */
    static Object oldGenerate(List<Object> drops, int tw, int[] prob, int[] alias) {
        if (drops.isEmpty()) return null;
        int roll = ThreadLocalRandom.current().nextInt(drops.size());
        if (ThreadLocalRandom.current().nextDouble() * tw < prob[roll]) return drops.get(roll);
        return drops.get(alias[roll]);
    }

    /** 新算法：数组索引 + 单次 current() + 满桶跳过第二次随机。 */
    static Object newGenerate(Object[] drops, int tw, int[] prob, int[] alias) {
        int n = drops.length;
        if (n == 0) return null;
        if (tw <= 0) return drops[0];
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(n);
        int p = prob[roll];
        if (p >= tw) return drops[roll];
        return r.nextInt(tw) < p ? drops[roll] : drops[alias[roll]];
    }

    public static void main(String[] args) throws ReflectiveOperationException {
        System.out.println("=== Round 2: LootTable.generate OLD vs NEW (same JVM) ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " generates/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS);
        System.out.println();

        run("uniform n=10", 1, 2, 2, 5, 5, 5, 8, 8, 3, 3);
        run("skewed n=3 (70/10/2)", 70, 10, 2);
        run("binary n=2 (50/50)", 5, 5);
        run("single n=1", 100);
    }

    private static void run(String label, int... weights) {
        // 构造真实 LootTable，反射取其内部表
        LootTable<String> table = buildTable(weights);
        try {
            Object[] arr = (Object[]) fDrops.get(table);
            int tw = fTw.getInt(table);
            int[] prob = (int[]) fProb.get(table);
            int[] alias = (int[]) fAlias.get(table);
            List<Object> list = Arrays.asList(arr);

            BenchmarkHarness.Result oldR = BenchmarkHarness.measure("OLD " + label, OUTER, INNER, () -> {
                long s = 0;
                for (int i = 0; i < INNER; i++) s += oldGenerate(list, tw, prob, alias) == null ? 0 : 1;
                return s;
            });
            BenchmarkHarness.Result newR = BenchmarkHarness.measure("NEW " + label, OUTER, INNER, () -> {
                long s = 0;
                for (int i = 0; i < INNER; i++) s += newGenerate(arr, tw, prob, alias) == null ? 0 : 1;
                return s;
            });
            oldR.print();
            newR.print();
            System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/gen)%n%n",
                oldR.bestNsPerOp / newR.bestNsPerOp, oldR.bestNsPerOp, newR.bestNsPerOp);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private static LootTable<String> buildTable(int... weights) {
        // 每个权重一个独立 drop，保留插入顺序
        LootTable.LootTableBuilder<String> b = LootTable.builder(String.class);
        for (int i = 0; i < weights.length; i++) b.add(weights[i], "d" + i);
        return b.build();
    }

    private LootTableBenchmark() {}
}
