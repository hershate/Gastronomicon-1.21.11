package io.github.schntgaispock.gastronomicon.benchmark;

/**
 * Round 7 基准：ElectricKitchen 每 tick 输入哈希的 getItemMeta() 引用缓存，同 JVM OLD vs NEW。
 * <p>
 * 诚实性：真实 hashIgnoreAmount 调 ItemUtil.hashIgnoreAmount → getItemMeta().hashCode()（克隆 meta），
 * 需 Bukkit。用 stub Item 的「昂贵 hashIgnoreAmount」（拷贝小数组模拟 meta 克隆+哈希）建模其成本。
 * 真实 getItemMeta 更重，故真实节省 >= 此处。安全性论证（getItemInSlot 返回同一引用 + 输入槽无原地
 * meta 变更）见 ElectricKitchen.CacheEntry 注释与报告。
 */
public final class ElectricKitchenHashBenchmark {

    private static final int INNER = 200_000;
    private static final int OUTER = 3;
    private static final int SLOTS = 9;

    /** stub：模拟槽位物品；hashIgnoreAmount() 昂贵（模拟 getItemMeta 克隆 + hashCode）。 */
    static final class Item {
        final byte[] meta = new byte[64];
        int hashIgnoreAmount() {
            final byte[] copy = new byte[64];           // 模拟 meta 克隆
            int h = 1;
            for (int i = 0; i < 64; i++) { copy[i] = (byte) (meta[i] + i); h = h * 31 + copy[i]; }
            return h;
        }
    }

    /** OLD：每 tick 对 9 槽各算一次 hashIgnoreAmount（每次 getItemMeta 克隆）。 */
    static int oldHash(Item[] slots) {
        int hash = 1;
        for (int i = 0; i < SLOTS; i++) hash = hash * 31 + (slots[i] == null ? 0 : slots[i].hashIgnoreAmount());
        return hash;
    }

    /** NEW：引用缓存；引用未变复用上次哈希、跳过 hashIgnoreAmount。 */
    static final class Entry {
        final Item[] refs = new Item[SLOTS];
        final int[] hashes = new int[SLOTS];
    }
    static int newHash(Item[] slots, Entry e) {
        int hash = 1;
        for (int i = 0; i < SLOTS; i++) {
            final Item cur = slots[i];
            if (cur == e.refs[i]) {
                hash = hash * 31 + e.hashes[i];
            } else {
                final int sh = cur == null ? 0 : cur.hashIgnoreAmount();
                e.refs[i] = cur; e.hashes[i] = sh;
                hash = hash * 31 + sh;
            }
        }
        return hash;
    }

    public static void main(String[] args) {
        System.out.println("=== Round 7: ElectricKitchen per-tick hash 引用缓存 (same JVM) ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " ticks/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS + " | slots=" + SLOTS);
        System.out.println("(稳态：槽位引用 tick 间不变 —— 现实主场景，输入非每 tick 变)");
        System.out.println();

        final Item[] slots = new Item[SLOTS];
        for (int i = 0; i < SLOTS; i++) slots[i] = new Item();
        final Entry e = new Entry();
        // prime NEW cache（首 tick 各算一次）
        newHash(slots, e);

        if (oldHash(slots) != newHash(slots, e)) throw new AssertionError("哈希不一致");
        System.out.println("(哈希一致性核对通过)");
        System.out.println();

        BenchmarkHarness.Result oldR = BenchmarkHarness.measure("OLD (每 tick 9× hashIgnoreAmount)", OUTER, INNER,
            () -> { long s = 0; for (int i = 0; i < INNER; i++) s += oldHash(slots); return s; });
        BenchmarkHarness.Result newR = BenchmarkHarness.measure("NEW (引用缓存，稳态复用)", OUTER, INNER,
            () -> { long s = 0; for (int i = 0; i < INNER; i++) s += newHash(slots, e); return s; });
        oldR.print(); newR.print();
        System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/tick)%n",
            oldR.bestNsPerOp / newR.bestNsPerOp, oldR.bestNsPerOp, newR.bestNsPerOp);
        System.out.println("  -> 稳态下 OLD 每 tick 9 次 getItemMeta 克隆，NEW 0 次（首 tick 后）。");
    }

    private ElectricKitchenHashBenchmark() {}
}
