package io.github.schntgaispock.gastronomicon.benchmark;

import java.util.HashMap;

/**
 * Round 5 基准：Counter.add 的 map 查找次数 4→1，同 JVM OLD vs NEW。
 * <p>
 * 隔离 add 主路径（累加已存在键）的 map 操作开销：OLD = containsKey + get + get + 改（≈4 查找），
 * NEW = 单次 get + 原地改（1 查找）。max/min 维护逻辑两边一致，不纳入本微基准差异（见报告）。
 * 行为正确性由 CounterTests（5 组 add/sub/max/min 断言）git stash 前后逐位一致已证。
 */
public final class CounterBenchmark {

    private static final int INNER = 2_000_000;
    private static final int OUTER = 3;
    private static final int KEYS = 9;  // 与 ElectricKitchen 输入槽数一致

    /** OLD：containsKey + set(get()+amount) → containsKey + 2 get + 改 ≈ 4 次 map 查找。 */
    static int oldAdd(HashMap<Integer, int[]> map, int key, int amount) {
        if (map.containsKey(key)) {
            map.get(key)[0] = map.get(key)[0] + amount;
            return map.get(key)[0];
        }
        map.put(key, new int[] { amount });
        return amount;
    }

    /** NEW：单次 get + 原地改 → 1 次 map 查找。 */
    static int newAdd(HashMap<Integer, int[]> map, int key, int amount) {
        final int[] v = map.get(key);
        if (v != null) {
            v[0] += amount;
            return v[0];
        }
        map.put(key, new int[] { amount });
        return amount;
    }

    public static void main(String[] args) {
        System.out.println("=== Round 5: Counter.add map-lookup 4->1 (same JVM) ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " adds/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS + " | keys=" + KEYS);
        System.out.println();

        BenchmarkHarness.Result oldR = BenchmarkHarness.measure("OLD (containsKey+get+get+改)", OUTER, INNER, () -> {
            final HashMap<Integer, int[]> map = new HashMap<>();
            for (int k = 0; k < KEYS; k++) map.put(k, new int[] { 0 });
            long s = 0;
            for (int i = 0; i < INNER; i++) s += oldAdd(map, i % KEYS, 1);
            return s;
        });
        BenchmarkHarness.Result newR = BenchmarkHarness.measure("NEW (单次 get+原地改)", OUTER, INNER, () -> {
            final HashMap<Integer, int[]> map = new HashMap<>();
            for (int k = 0; k < KEYS; k++) map.put(k, new int[] { 0 });
            long s = 0;
            for (int i = 0; i < INNER; i++) s += newAdd(map, i % KEYS, 1);
            return s;
        });
        oldR.print(); newR.print();
        System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/add)%n",
            oldR.bestNsPerOp / newR.bestNsPerOp, oldR.bestNsPerOp, newR.bestNsPerOp);
    }

    private CounterBenchmark() {}
}
