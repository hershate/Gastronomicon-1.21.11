package io.github.schntgaispock.gastronomicon.core.slimefun.items.seeds;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

import javax.annotation.ParametersAreNonnullByDefault;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.block.BlockState;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.Gastronomicon;
import io.github.schntgaispock.gastronomicon.util.RecipeUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;

/**
 * A DuplicatingSeed grows upward.
 * <br>
 * <br>
 * The only <code>ItemStack</code>s allowed are cacti and sugar cane
 */
public class DuplicatingSeed extends AbstractSeed {

    @ParametersAreNonnullByDefault
    public DuplicatingSeed(SlimefunItemStack item, ItemStack[] gatherSources) {
        super(item, gatherSources);

        switch (item.getType()) {
            case CACTUS, SUGAR_CANE -> {}
            default -> Gastronomicon.log(Level.WARNING, "Registering a DuplicatingSeed that isn't a cactus or sugar cane!");
        }
    }

    @ParametersAreNonnullByDefault
    public DuplicatingSeed(SlimefunItemStack item, SlimefunItemStack harvestSource) {
        this(item, RecipeUtil.singleCenter(harvestSource));
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockPlaceHandler(true) {
            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                if (e.getBlock().getState().getLightLevel() <= 7) {
                    e.setCancelled(true);
                    BlockStorage.clearBlockInfo(e.getBlock().getLocation());
                }
            }
        });
    }
    

    @Override
    public boolean isMature(BlockState b) {
        return false;
    }

    @Override
    public List<ItemStack> getHarvestDrops(BlockState e, ItemStack item, boolean brokenByPlayer) {
        // 原先玩家破坏返回空、非玩家破坏返回 [种子]：但玩家破坏走 AbstractSeed.onPlayerBreak
        // （dropsOnPlayerBreak 默认 true），会 drops.clear() 清掉原版甘蔗/仙人掌掉落再 addAll(空)，
        // 导致玩家手动破坏 DuplicatingSeed 时什么也得不到、作物被清空丢失。玩家与非玩家
        // 破坏都应返回作物（与非玩家保护路径一致）。
        return Arrays.asList(getItem().clone());
    }

}
