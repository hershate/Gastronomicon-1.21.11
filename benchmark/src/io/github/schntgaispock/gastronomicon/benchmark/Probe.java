package io.github.schntgaispock.gastronomicon.benchmark;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;

/**
 * 探针：确定哪些 Bukkit/Slimefun API 可在纯 JVM（无服务端）下运行。
 * <p>
 * 这决定了「真实合成匹配路径」能否用纯 JVM 基准量化，还是必须依赖
 * 游戏内 Probe / Mockito 桩。每项均 try/catch，打印结论。
 */
public final class Probe {

    public static void main(String[] args) {
        System.out.println("=== Gastronomicon Benchmark Probe ===");
        System.out.println("java.runtime: " + System.getProperty("java.version"));
        System.out.println();

        probe("1. new ItemStack(Material.STONE)", () -> {
            ItemStack is = new ItemStack(Material.STONE);
            if (is == null) throw new RuntimeException("null");
        });

        probe("2. stack.getType()", () -> {
            ItemStack is = new ItemStack(Material.STONE);
            if (is.getType() != Material.STONE) throw new RuntimeException("wrong type " + is.getType());
        });

        probe("3. stack.hasItemMeta()", () -> {
            ItemStack is = new ItemStack(Material.STONE);
            is.hasItemMeta();
        });

        probe("4. stack.getItemMeta()", () -> {
            ItemStack is = new ItemStack(Material.STONE);
            is.getItemMeta();
        });

        probe("5. stack.isSimilar(other)", () -> {
            ItemStack a = new ItemStack(Material.STONE);
            ItemStack b = new ItemStack(Material.STONE);
            a.isSimilar(b);
        });

        probe("6. stack.hashCode()", () -> {
            ItemStack is = new ItemStack(Material.STONE);
            is.hashCode();
        });

        probe("7. Slimefun.getRegistry() (static)", () -> {
            // 多数 Slimefun 静态访问在未启动时会 NPE/返回 null
            Object r = Slimefun.getRegistry();
            if (r == null) throw new RuntimeException("registry null");
        });

        probe("8. SlimefunItem.getByItem(vanilla stack)", () -> {
            ItemStack is = new ItemStack(Material.STONE);
            // 未启动时通常会 NPE（访问 registry）
            SlimefunItem sfi = SlimefunItem.getByItem(is);
            if (sfi != null) throw new RuntimeException("unexpected non-null " + sfi);
        });

        System.out.println();
        System.out.println("=== 结论 ===");
        System.out.println("若 1-6 通过而 7-8 失败：Bukkit ItemStack 基本可用，但 Slimefun 静态注册表不可用。");
        System.out.println("  -> 合成匹配需通过「注入解析器」桩（getByItem 用 HashMap 模拟）才能纯 JVM 基准。");
        System.out.println("若 3/4/5 失败：ItemStack 需服务端，匹配路径只能游戏内 Probe / Mockito。");
    }

    @FunctionalInterface
    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static void probe(String name, ThrowingRunnable r) {
        try {
            r.run();
            System.out.println("  [OK]   " + name);
        } catch (Throwable t) {
            System.out.println("  [FAIL] " + name + "  -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    private Probe() {}
}
