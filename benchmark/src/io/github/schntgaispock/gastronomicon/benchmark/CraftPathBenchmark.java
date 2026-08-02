package io.github.schntgaispock.gastronomicon.benchmark;

import java.util.Arrays;
import java.util.List;

/**
 * Round 4 基准：GastroWorkstation 合成路径「收集槽位物品」的 stream vs plain-loop 同 JVM 对比。
 * <p>
 * 诚实性：真实路径调用 menu.getItemInSlot()/ItemStack.asOne()（需 Bukkit）。本基准用桩 Item，
 * asOne() 返回 this（其克隆成本在 OLD/NEW 两边等价，故剔除以隔离「收集方式」的开销差）。
 * 测量的是：流管线分配（Spliterator/包装/ArrayList/装箱）vs 数组+Arrays.asList 薄包装。
 * containers/tools 仍为 List 以兼容 findRecipe/matches 签名（与生产一致）。
 */
public final class CraftPathBenchmark {

    private static final int INNER = 1_000_000;
    private static final int OUTER = 3;

    // 与 GastroWorkstation 槽位布局一致
    static final int[] IN_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    static final int[] CONT_SLOTS = {15, 16};
    static final int[] TOOL_SLOTS = {46, 47, 48, 49, 50, 51};

    // 桩：模拟 menu 的槽位内容（部分空，部分有物品）
    static final StubItem[] MENU = new StubItem[54];
    static final class StubItem {
        static final StubItem X = new StubItem();
        StubItem asOne() { return this; }   // 克隆成本 OLD/NEW 等价，剔除
    }
    static {
        MENU[10] = MENU[11] = MENU[19] = MENU[20] = MENU[28] = StubItem.X;   // 5 个原料
        MENU[15] = StubItem.X;                                                // 1 个容器
        MENU[46] = MENU[47] = StubItem.X;                                     // 2 个工具
    }
    static StubItem get(int slot) { return MENU[slot]; }

    /** OLD：stream + mapToObj + toList/toArray。 */
    static int oldCollectAndHash() {
        final StubItem[] ingredients = Arrays.stream(IN_SLOTS).mapToObj(s -> {
            final StubItem i = get(s); return i == null ? null : i.asOne();
        }).toArray(StubItem[]::new);
        final List<StubItem> containers = Arrays.stream(CONT_SLOTS).mapToObj(s -> {
            final StubItem i = get(s); return i == null ? null : i.asOne();
        }).toList();
        final List<StubItem> tools = Arrays.stream(TOOL_SLOTS).mapToObj(s -> {
            final StubItem i = get(s); return i == null ? null : i.asOne();
        }).toList();
        int hash = 1;
        hash = hash * 31 + Arrays.hashCode(ingredients);
        hash = hash * 31 + containers.hashCode();
        hash = hash * 31 + tools.hashCode();
        return hash;
    }

    /** NEW：plain 循环 + Arrays.asList 薄包装（与 GastroWorkstation 改后一致）。 */
    static int newCollectAndHash() {
        final StubItem[] ingredients = new StubItem[IN_SLOTS.length];
        for (int idx = 0; idx < IN_SLOTS.length; idx++) {
            final StubItem i = get(IN_SLOTS[idx]);
            ingredients[idx] = i == null ? null : i.asOne();
        }
        final StubItem[] contArr = new StubItem[CONT_SLOTS.length];
        for (int idx = 0; idx < CONT_SLOTS.length; idx++) {
            final StubItem i = get(CONT_SLOTS[idx]);
            contArr[idx] = i == null ? null : i.asOne();
        }
        final List<StubItem> containers = Arrays.asList(contArr);
        final StubItem[] toolArr = new StubItem[TOOL_SLOTS.length];
        for (int idx = 0; idx < TOOL_SLOTS.length; idx++) {
            final StubItem i = get(TOOL_SLOTS[idx]);
            toolArr[idx] = i == null ? null : i.asOne();
        }
        final List<StubItem> tools = Arrays.asList(toolArr);
        int hash = 1;
        hash = hash * 31 + Arrays.hashCode(ingredients);
        hash = hash * 31 + containers.hashCode();
        hash = hash * 31 + tools.hashCode();
        return hash;
    }

    public static void main(String[] args) {
        System.out.println("=== Round 4: craft-path collect (stream vs loop) OLD vs NEW ===");
        System.out.println("inner=" + INNER + " x outer=" + OUTER + " = " + ((long) INNER * OUTER)
            + " crafts/round | best of " + BenchmarkHarness.DEFAULT_MEASURE_ROUNDS);
        System.out.println("(asOne 克隆成本 OLD/NEW 等价已剔除；测量收集方式开销)");
        System.out.println();

        // 正确性：两者哈希应一致（同样的物品集合）
        if (oldCollectAndHash() != newCollectAndHash()) {
            throw new AssertionError("hash mismatch: " + oldCollectAndHash() + " vs " + newCollectAndHash());
        }
        System.out.println("(哈希一致性核对通过)");
        System.out.println();

        BenchmarkHarness.Result oldR = BenchmarkHarness.measure("OLD (stream + toList)", OUTER, INNER,
            () -> { long s = 0; for (int i = 0; i < INNER; i++) s += oldCollectAndHash(); return s; });
        BenchmarkHarness.Result newR = BenchmarkHarness.measure("NEW (loop + asList)", OUTER, INNER,
            () -> { long s = 0; for (int i = 0; i < INNER; i++) s += newCollectAndHash(); return s; });
        oldR.print(); newR.print();
        System.out.printf(java.util.Locale.ROOT, "  -> 加速比: %.2fx  (%.2f -> %.2f ns/craft-collect)%n",
            oldR.bestNsPerOp / newR.bestNsPerOp, oldR.bestNsPerOp, newR.bestNsPerOp);
        System.out.println("  -> 分配：OLD 每次约 3 流管线+2 ArrayList+装箱；NEW 3 数组+2 Arrays.asList 薄包装，零装箱。");
    }

    private CraftPathBenchmark() {}
}
