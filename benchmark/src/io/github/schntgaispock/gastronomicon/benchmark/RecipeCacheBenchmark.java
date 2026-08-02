package io.github.schntgaispock.gastronomicon.benchmark;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round 1 基准：量化「RecipeComponent 缓存 SlimefunItem 解析」的收益。
 * <p>
 * 诚实性说明：真正的 matches() 依赖 isSimilar()/getByItem()，经 Probe 实测无法在纯 JVM 运行
 * （需 CraftServer / Slimefun 实例）。本基准用**忠实算法模型**量化优化机制本身：
 *  - resolver 模拟 SlimefunItem.getByItem（HashMap.get，对应其快速负路径）。
 *  - HEAVY 档额外合成一次「ItemMeta 克隆 + PDC 读」的近似开销（小数组拷贝），对应 SF 物品路径。
 *  - 调用计数为精确值（无关 resolver 成本）；墙钟为同口径前后对比。
 * 绝对墙钟需游戏内 Probe（见报告）校准真实 getByItem 成本后定位。
 */
public final class RecipeCacheBenchmark {

    // === 负载参数（贴近真实规模）===
    static final int RECIPES = 200;          // 配方数
    static final int SLOTS = 4;              // 每配方平均非空原料槽数
    static final int DISTINCT = 800;         // 去重后不同原料模板数（<= RECIPES*SLOTS）
    static final int OPS = 2_000;            // 每轮 findRecipe 操作次数（外层）
    static final double SF_RATIO = 0.30;     // 30% 模板为 SF 物品（getByItem 返回非 null）

    /** 模拟一次 findRecipe：遍历全部 R 个配方，每个配方检查 SLOTS 个槽 → 共 R*SLOTS 次 matches。 */
    static final int MATCHES_PER_OP = RECIPES * SLOTS;

    // resolver 调用计数器
    static final AtomicLong resolverCalls = new AtomicLong();

    // 模拟「解析出的 SlimefunItem」哨兵
    static final Object SF_SENTINEL = new Object();

    /** resolver（模拟 getByItem）。tier: 0=轻(HashMap.get)，1=重(+模拟 meta 克隆/PDC 读)。 */
    static Object resolve(int key, int tier, HashMap<Integer, Object> map) {
        resolverCalls.incrementAndGet();
        Object v = map.get(key);
        if (tier == 1 && v != null) {
            // 模拟 SF 物品路径的 ItemMeta 克隆 + PDC 读开销
            byte[] meta = META_BUF.get();
            System.arraycopy(meta, 0, COPY_BUF.get(), 0, meta.length);
        }
        return v;
    }

    static final ThreadLocal<byte[]> META_BUF = ThreadLocal.withInitial(() -> new byte[64]);
    static final ThreadLocal<byte[]> COPY_BUF = ThreadLocal.withInitial(() -> new byte[64]);

    // === BEFORE：每次 matches 都解析 ===
    static boolean matchBefore(int key, int tier, HashMap<Integer, Object> map) {
        Object sf = resolve(key, tier, map);     // <- 优化消除的对象
        return sf != null;                        // isItem/isSimilar 的结果对计时无影响，简化
    }

    // === AFTER：惰性缓存（volatile 语义用数组单线程模型近似；计数仍准确）===
    static final class Cached {
        volatile Object sf;
        volatile boolean resolved;
        final int key;
        Cached(int key) { this.key = key; }
    }

    static boolean matchAfter(Cached c, int tier, HashMap<Integer, Object> map) {
        Object sf = c.sf;
        if (!c.resolved) {                        // 惰性解析一次
            sf = resolve(c.key, tier, map);
            c.sf = sf;
            c.resolved = true;
        }
        return sf != null;
    }

    public static void main(String[] args) {
        System.out.println("=== Round 1: RecipeComponent cache benchmark ===");
        System.out.printf("负载: %d 配方 × %d 槽 = %d matches/op | %d ops | SF比例 %.0f%%%n",
            RECIPES, SLOTS, MATCHES_PER_OP, OPS, SF_RATIO * 100);
        System.out.println("(每次 findRecipe 对所有候选配方×所有槽调用 component.matches)");
        System.out.println();

        // 构造 resolver 映射（30% SF 物品）
        HashMap<Integer, Object> map = new HashMap<>();
        for (int k = 0; k < DISTINCT; k++) {
            map.put(k, (k / (double) DISTINCT < SF_RATIO) ? SF_SENTINEL : null);
        }

        // 构造配方（每个配方 SLOTS 个 key，循环取自 DISTINCT）
        int[][] recipeKeys = new int[RECIPES][];
        for (int r = 0; r < RECIPES; r++) {
            int[] ks = new int[SLOTS];
            for (int s = 0; s < SLOTS; s++) ks[s] = (r * SLOTS + s) % DISTINCT;
            recipeKeys[r] = ks;
        }
        Cached[][] recipeCached = new Cached[RECIPES][];
        for (int r = 0; r < RECIPES; r++) {
            Cached[] cs = new Cached[SLOTS];
            for (int s = 0; s < SLOTS; s++) cs[s] = new Cached(recipeKeys[r][s]);
            recipeCached[r] = cs;
        }

        for (int tier = 0; tier <= 1; tier++) {
            final int t = tier;  // lambda 需事实上 final
            String tierName = tier == 0 ? "轻(HashMap.get ≈ vanilla getByItem 快速负路径)"
                                        : "重(+模拟 meta 克隆/PDC 读 ≈ SF 物品路径)";
            System.out.println("--- resolver 档: " + tierName + " ---");

            // BEFORE 计时
            resolverCalls.set(0);
            BenchmarkHarness.Result before = BenchmarkHarness.measure(
                "BEFORE (每次解析)", OPS, MATCHES_PER_OP, () -> {
                    long acc = 0;
                    for (int r = 0; r < RECIPES; r++) {
                        int[] ks = recipeKeys[r];
                        for (int s = 0; s < SLOTS; s++) if (matchBefore(ks[s], t, map)) acc++;
                    }
                    return acc;
                });
            long beforeCalls = resolverCalls.get();

            // AFTER：先 prime 缓存（稳态），再计时
            for (int r = 0; r < RECIPES; r++) for (int s = 0; s < SLOTS; s++) matchAfter(recipeCached[r][s], t, map);
            resolverCalls.set(0);
            BenchmarkHarness.Result after = BenchmarkHarness.measure(
                "AFTER  (惰性缓存)", OPS, MATCHES_PER_OP, () -> {
                    long acc = 0;
                    for (int r = 0; r < RECIPES; r++) {
                        Cached[] cs = recipeCached[r];
                        for (int s = 0; s < SLOTS; s++) if (matchAfter(cs[s], t, map)) acc++;
                    }
                    return acc;
                });
            long afterCalls = resolverCalls.get();

            before.print();
            after.print();
            double speedup = before.bestNsPerOp / after.bestNsPerOp;
            System.out.printf("  -> 墙钟加速比: %.2fx | ns/findRecipe: %.1f -> %.1f%n",
                speedup, before.bestNsPerOp, after.bestNsPerOp);
            System.out.printf("  -> resolver 调用: BEFORE=%,d  AFTER=%,d  (消除 %,.0fx)%n%n",
                beforeCalls, afterCalls, afterCalls == 0 ? Double.POSITIVE_INFINITY : (double) beforeCalls / afterCalls);
        }

        System.out.println("结论: 调用消除为精确值；墙钟加速比随真实 getByItem 成本放大（重档更接近 SF 物品实测）。");
    }

    private RecipeCacheBenchmark() {}
}
