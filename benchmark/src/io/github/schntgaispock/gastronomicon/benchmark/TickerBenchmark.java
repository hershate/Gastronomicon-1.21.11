package io.github.schntgaispock.gastronomicon.benchmark;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Round 6 基准：HuntingTrap.tick 的 getLocation() 反复分配 + containsKey+get 模式，同 JVM OLD vs NEW。
 * <p>
 * 诚实性：org.bukkit.Location 需 World，纯 JVM 无法构造。用轻量 stub Loc（含 hashCode/equals，
 * 模拟 Location 作 map 键的开销）建模「每次 new 一个对象」的分配+查找成本。真实 Location 字段更多
 * （World/yaw/pitch 等），故真实节省 >= 此处所示。
 */
public final class TickerBenchmark {

    private static final int INNER = 1_000_000;
    private static final int OUTER = 3;

    /** 桩：模拟 Location（每次 new 即一次分配；作 map 键需 hashCode/equals）。 */
    static final class Loc {
        final double x, y, z;
        Loc(double x, double y, double z) { this.x = x; this.y = y; this.z = z; }
        double getX() { return x; }
        double getY() { return y; }
        double getZ() { return z; }
        @Override public int hashCode() { long a = Double.doubleToLongBits(x), b = Double.doubleToLongBits(y), c = Double.doubleToLongBits(z); return 31 * (31 * (int)(a^(a>>>32)) + (int)(b^(b>>>32))) + (int)(c^(c>>>32)); }
        @Override public boolean equals(Object o) { if (!(o instanceof Loc l)) return false; return x==l.x && y==l.y && z==l.z; }
    }

    static final Map<Loc, Boolean> triggered = new ConcurrentHashMap<>();
    static final Loc KEY = new Loc(1, 2, 3);   // 模拟固定的方块坐标（map 中已存在）
    static { triggered.put(KEY, Boolean.TRUE); }

    /** OLD：containsKey(new Loc) + get(new Loc) + 3×(new Loc).getCoord() = 5 次分配 + 2 次 map 查找。 */
    static long oldTick() {
        long s = 0;
        if (triggered.containsKey(new Loc(1, 2, 3))) {
            if (triggered.get(new Loc(1, 2, 3))) {
                s += (long)((new Loc(1, 2, 3).getX() + 0.5) + (new Loc(1, 2, 3).getY() + 0.25) + (new Loc(1, 2, 3).getZ() + 0.5));
            }
        }
        return s;
    }

    /** NEW：单次 new Loc 缓存，复用于 get 与坐标；1 次分配 + 1 次 map 查找。 */
    static long newTick() {
        long s = 0;
        final Loc l = new Loc(1, 2, 3);
        final Boolean t = triggered.get(l);
        if (t != null) {
            if (t) {
                s += (long)((l.getX() + 0.5) + (l.getY() + 0.25) + (l.getZ() + 0.5));
            }
        }
        return s;
    }

    public static void main(String[] args) {
        System.out.println("=== Round 6: HuntingTrap.tick getLocation() 反复分配 (same JVM) ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " ticks/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS);
        System.out.println("(stub Loc < 真实 Location 字段数，故真实节省 >= 此处)");
        System.out.println();

        if (oldTick() != newTick()) throw new AssertionError("结果不一致");
        System.out.println("(结果一致性核对通过)");
        System.out.println();

        BenchmarkHarness.Result oldR = BenchmarkHarness.measure("OLD (5×new Loc + containsKey+get)", OUTER, INNER,
            () -> { long s = 0; for (int i = 0; i < INNER; i++) s += oldTick(); return s; });
        BenchmarkHarness.Result newR = BenchmarkHarness.measure("NEW (1×new Loc + 单次 get)", OUTER, INNER,
            () -> { long s = 0; for (int i = 0; i < INNER; i++) s += newTick(); return s; });
        oldR.print(); newR.print();
        System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/tick)%n",
            oldR.bestNsPerOp / newR.bestNsPerOp, oldR.bestNsPerOp, newR.bestNsPerOp);
    }

    private TickerBenchmark() {}
}
