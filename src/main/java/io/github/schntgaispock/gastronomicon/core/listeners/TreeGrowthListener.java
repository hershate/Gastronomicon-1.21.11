package io.github.schntgaispock.gastronomicon.core.listeners;

import io.github.schntgaispock.gastronomicon.Gastronomicon;
import io.github.schntgaispock.gastronomicon.api.trees.TreeStructure;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;

public class TreeGrowthListener implements Listener {

    public static void setup() {
        Bukkit.getPluginManager().registerEvents(new TreeGrowthListener(), Gastronomicon.getInstance());
    }

    @EventHandler
    public void onTreeGrow(StructureGrowEvent e) {
        Location l = e.getLocation();
        if (!BlockStorage.hasBlockInfo(l)) {
            return;
        }
        final String sapling = BlockStorage.checkID(l);
        final TreeStructure tree = TreeStructure.getLoadedTrees().get(sapling);
        if (tree == null) {
            return;
        }

        e.setCancelled(true);
        BlockStorage.clearBlockInfo(l);

        // 1.1.5: 原 getScheduler().run(...) 即 GuizhanLib Scheduler 的同步下一 tick 调度，
        // 等价于 Bukkit.getScheduler().runTask(...)。内联以去除对鬼斩前置库的运行期依赖。
        Bukkit.getScheduler().runTask(Gastronomicon.getInstance(), () -> {
            try {
                tree.build(l, sapling);
            } catch (NullPointerException | IllegalArgumentException err) {
                err.printStackTrace();
            }
        });

    }
}
