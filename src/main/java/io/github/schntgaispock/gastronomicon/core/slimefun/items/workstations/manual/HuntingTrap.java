package io.github.schntgaispock.gastronomicon.core.slimefun.items.workstations.manual;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.Gastronomicon;
import io.github.schntgaispock.gastronomicon.core.slimefun.GastroGroups;
import io.github.schntgaispock.gastronomicon.util.NumberUtil;
import io.github.thebusybiscuit.slimefun4.api.events.BlockPlacerPlaceEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import lombok.Getter;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;

@Getter
@SuppressWarnings("deprecation")
public abstract class HuntingTrap extends SimpleSlimefunItem<BlockUseHandler> {

    private final Map<Location, Boolean> triggeredTraps = new ConcurrentHashMap<>();

    protected HuntingTrap(SlimefunItemStack item, ItemStack[] recipe) {
        super(GastroGroups.TOOLS, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
    }

    @Override
    public void preRegister() {
        super.preRegister();

        addItemHandler(new BlockPlaceHandler(true) {

            @Override
            public void onBlockPlacerPlace(BlockPlacerPlaceEvent e) {
                startCatch(e.getBlock().getLocation());
            }

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                startCatch(e.getBlock().getLocation());
            }

        });

        addItemHandler(new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                BlockStorage.clearBlockInfo(b.getLocation());
                if (triggeredTraps.containsKey(b.getLocation()) && triggeredTraps.get(b.getLocation()))
                    dropCatch(b.getLocation());
                triggeredTraps.remove(b.getLocation());
            }
        });

        addItemHandler(new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem item, Config config) {
                // [perf] b.getLocation() 每次返回新 Location；原 tick 调用 ~5 次（containsKey/get/三坐标）。
                // 缓存为 local 复用，并把 containsKey+get 合并为单次 get。每个陷阱每 tick 触发。
                final Location l = b.getLocation();
                final Boolean triggered = triggeredTraps.get(l);
                if (triggered != null) {
                    if (triggered) {
                        b.getWorld().spawnParticle(
                            Particle.WAX_OFF,
                            l.getX() + 0.5,
                            l.getY() + 0.25,
                            l.getZ() + 0.5,
                            4,
                            0.2, 0.05, 0.2, 0,
                            null,
                            true);
                    }
                } else {
                    startCatch(l);
                }
            }

            @Override
            public boolean isSynchronized() {
                // tick 内访问 World(spawnParticle)、BlockStorage 并与主线程上的
                // 交互/破坏处理器共享 triggeredTraps，必须在主线程执行。
                return true;
            }

        });
    }

    @Override
    public BlockUseHandler getItemHandler() {
        return e -> {
            e.cancel();
            if (e.getClickedBlock().isEmpty())
                return;
            final Location l = e.getClickedBlock().get().getLocation();
            if (triggeredTraps.containsKey(l) && triggeredTraps.get(l)) {
                dropCatch(l);
                startCatch(l);
            }
        };
    }

    protected abstract ItemStack getCatch(Location l);

    protected abstract boolean canCatch(Location l);

    private boolean startCatch(Location l) {
        triggeredTraps.put(l, false);
        if (!canCatch(l))
            return false;

        Gastronomicon.scheduleSyncDelayedTask(() -> {
            if (!BlockStorage.hasBlockInfo(l)) {
                return;
            }
            final String id = BlockStorage.checkID(l);
            if (id.equals(getId())) {
                l.getWorld().playSound(l, Sound.ENTITY_EVOKER_FANGS_ATTACK, SoundCategory.BLOCKS, 1f, 1.5f);
                triggeredTraps.put(l, true);
            }
        }, 20 * (long) NumberUtil.clamp(ThreadLocalRandom.current().nextGaussian(120, 30), 60, 180));

        return true;
    }

    private void dropCatch(Location l) {
        // getCatch 契约允许返回 null（如 biome 不在掉落表时），dropItemNaturally 对 null
        // 会 NPE。当前由 canCatch 守卫（不触发则不调用），但契约不一致是脆弱点，补判空。
        final ItemStack catchItem = getCatch(l);
        if (catchItem == null) {
            return;
        }
        l.getWorld().dropItemNaturally(l, catchItem);
    }
}
