package io.github.mooy1.infinitylib.machines;

import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

/**
 * Vendor 自 {@code InfinityLib}。
 * <p>
 * 1.1.6 REF 适配：原 {@code new CustomItemStack(Material, String)} 等构造在 REF Slimefun 4.9.5 中
 * 已改为 {@code public final class}（不再继承 {@link ItemStack}），故全部替换为静态工厂
 * {@link CustomItemStack#create}。否则 shaded 字节码会在加载期抛 {@link VerifyError}。
 */
public abstract class MenuBlock extends SlimefunItem {

    public static final ItemStack PROCESSING_ITEM = CustomItemStack.create(Material.LIME_STAINED_GLASS_PANE, "&a处理中...");
    public static final ItemStack NO_ENERGY_ITEM = CustomItemStack.create(Material.RED_STAINED_GLASS_PANE, "&c电力不足!");
    public static final ItemStack IDLE_ITEM = CustomItemStack.create(Material.BLACK_STAINED_GLASS_PANE, "&8空闲");
    public static final ItemStack NO_ROOM_ITEM = CustomItemStack.create(Material.ORANGE_STAINED_GLASS_PANE, "&6空间不足!");
    public static final ItemStack OUTPUT_BORDER = CustomItemStack.create(ChestMenuUtils.getOutputSlotTexture(), "&6输出");
    public static final ItemStack INPUT_BORDER = CustomItemStack.create(ChestMenuUtils.getInputSlotTexture(), "&9输入");
    public static final ItemStack BACKGROUND_ITEM = ChestMenuUtils.getBackground();

    public MenuBlock(ItemGroup category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);

        addItemHandler(new BlockBreakHandler(false, false) {

            @Override
            public void onPlayerBreak(BlockBreakEvent e, ItemStack itemStack, List<ItemStack> list) {
                BlockMenu menu = BlockStorage.getInventory(e.getBlock());
                if (menu != null) {
                    onBreak(e, menu);
                }
            }

        }, new BlockPlaceHandler(false) {

            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                onPlace(e, e.getBlockPlaced());
            }

        });
    }

    @Override
    public final void postRegister() {
        new MenuBlockPreset(this);
    }

    protected abstract void setup(BlockMenuPreset preset);

    protected final int[] getTransportSlots(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
        switch (flow) {
            case INSERT:
                return getInputSlots(menu, item);
            case WITHDRAW:
                return getOutputSlots();
            default:
                return new int[0];
        }
    }

    protected int[] getInputSlots(DirtyChestMenu menu, ItemStack item) {
        return getInputSlots();
    }

    protected abstract int[] getInputSlots();

    protected abstract int[] getOutputSlots();

    protected void onNewInstance(BlockMenu menu, Block b) {
    }

    protected void onBreak(BlockBreakEvent e, BlockMenu menu) {
        Location l = menu.getLocation();
        menu.dropItems(l, getInputSlots());
        menu.dropItems(l, getOutputSlots());
    }

    protected void onPlace(BlockPlaceEvent e, Block b) {
    }

}
