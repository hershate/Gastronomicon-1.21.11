package io.github.schntgaispock.gastronomicon.benchmark;

import io.github.schntgaispock.gastronomicon.util.NumberUtil;

/**
 * Round 3 基准：NumberUtil.asRomanNumeral / roundToPrecision 新旧**同 JVM**对比。
 * <p>
 * 诚实性：asRomanNumeral/roundToPrecision 仅在注册期（FoodEffect/FoodItemStack 描述构造）调用，
 * 非运行期热路径；本基准量化「每次调用的分配/计算成本」下降，绝对现实影响有限（见报告）。
 */
public final class NumberUtilBenchmark {

    private static final int INNER = 2_000_000;
    private static final int OUTER = 3;

    // ---- OLD 引用（逐字复制自优化前实现）----
    static String oldRoman(int x) {
        if (x >= 4000 || x <= 0) return Integer.toString(x);
        String[] thousands = { "", "M", "MM", "MMM" };
        String[] hundreds = { "", "C", "CC", "CCC", "CD", "D", "DC", "DCC", "DCCC", "CM" };
        String[] tens = { "", "X", "XX", "XXX", "XL", "L", "LX", "LXX", "LXXX", "XC" };
        String[] ones = { "", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX" };
        return thousands[x / 1000] + hundreds[(x / 100) % 10] + tens[(x / 10) % 10] + ones[x % 10];
    }

    static double oldRound(double x, int p) {
        double magn = Math.pow(10, p);
        return Math.round(x * magn) / magn;
    }

    public static void main(String[] args) {
        System.out.println("=== Round 3: NumberUtil OLD vs NEW (same JVM) ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " ops/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS);
        System.out.println();

        // 正确性核对（结果必须恒等）
        int[] inputs = {1, 4, 9, 40, 90, 400, 900, 1994, 3888, 0, 3999};
        for (int x : inputs) assertEq("roman " + x, oldRoman(x), NumberUtil.asRomanNumeral(x));
        for (int p = 0; p <= 6; p++) assertEq("round p" + p, oldRound(3.14159265, p), NumberUtil.roundToPrecision(3.14159265, p));
        System.out.println("(正确性核对通过：asRomanNumeral / roundToPrecision 结果恒等)");
        System.out.println();

        int[] rn = {1, 4, 9, 40, 90, 400, 900, 1994, 3888, 0, 7, 49, 444, 2024};

        BenchmarkHarness.Result rOld = BenchmarkHarness.measure("OLD asRomanNumeral", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += oldRoman(rn[i % rn.length]).length();
            return s;
        });
        BenchmarkHarness.Result rNew = BenchmarkHarness.measure("NEW asRomanNumeral (static 表)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += NumberUtil.asRomanNumeral(rn[i % rn.length]).length();
            return s;
        });
        rOld.print(); rNew.print();
        System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/op)%n%n",
            rOld.bestNsPerOp / rNew.bestNsPerOp, rOld.bestNsPerOp, rNew.bestNsPerOp);

        BenchmarkHarness.Result pOld = BenchmarkHarness.measure("OLD roundToPrecision (Math.pow)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += (long) (oldRound(i * 0.0001, 4) * 1e6);
            return s;
        });
        BenchmarkHarness.Result pNew = BenchmarkHarness.measure("NEW roundToPrecision (switch)", OUTER, INNER, () -> {
            long s = 0;
            for (int i = 0; i < INNER; i++) s += (long) (NumberUtil.roundToPrecision(i * 0.0001, 4) * 1e6);
            return s;
        });
        pOld.print(); pNew.print();
        System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/op)%n",
            pOld.bestNsPerOp / pNew.bestNsPerOp, pOld.bestNsPerOp, pNew.bestNsPerOp);
    }

    private static void assertEq(String label, Object a, Object b) {
        if (!a.equals(b)) throw new AssertionError(label + ": " + a + " != " + b);
    }

    private NumberUtilBenchmark() {}
}
