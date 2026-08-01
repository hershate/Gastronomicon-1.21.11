package io.github.schntgaispock.gastronomicon.core.slimefun.items.workstations.automatic;

import me.mrCookieSlime.Slimefun.api.BlockStorage;
import io.github.schntgaispock.gastronomicon.Gastronomicon;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.core.slimefun.GastroGroups;
import io.github.schntgaispock.gastronomicon.core.slimefun.GastroStacks;
import io.github.schntgaispock.gastronomicon.util.RecipeUtil;
import io.github.schntgaispock.gastronomicon.util.collections.CollectionUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineProcessHolder;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import lombok.Getter;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

@Getter
@SuppressWarnings("deprecation")
public class FishingNet extends SlimefunItem implements InventoryBlock, MachineProcessHolder<CraftingOperation> {

    public static final int[] BACKGROUND_SLOTS = new int[] {
        0, 1, 2,
        9, 11,
        18, 19, 20
    };
    public static final int[] OUTPUT_BORDER = new int[] { 3, 12, 21 };
    public static final int STATUS_SLOT = 10;
    public static final ItemStack[] FISH = RecipeUtil.items(
        GastroStacks.RAW_BASS,
        GastroStacks.RAW_CARP,
        GastroStacks.RAW_EEL,
        GastroStacks.RAW_MACKEREL,
        GastroStacks.RAW_PIKE,
        GastroStacks.RAW_SQUID,
        GastroStacks.RAW_TROUT,
        GastroStacks.RAW_TUNA,
        GastroStacks.SHRIMP,
        new ItemStack(Material.COD),
        new ItemStack(Material.SALMON),
        new ItemStack(Material.PUFFERFISH),
        new ItemStack(Material.TROPICAL_FISH));

    private final String machineIdentifier = "GN_FISHING_NET";
    private final MachineProcessor<CraftingOperation> machineProcessor = new MachineProcessor<>(this);
    private final ItemStack progressBar = new ItemStack(Material.FISHING_ROD);
    private final int[] inputSlots = new int[0];
    private final int[] outputSlots = new int[] {
        4, 5, 6, 7, 8,
        13, 14, 15, 16, 17,
        22, 23, 24, 25, 26
    };
    private final int speed;

    public FishingNet(SlimefunItemStack item, int speed, ItemStack[] recipe) {
        super(GastroGroups.ELECTRIC_MACHINES, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);

        // speed 在 tick 中作 240/speed 除数，必须 >0；否则会在 ticker 线程抛
        // ArithmeticException。ElectricKitchen 经 setProcessingSpeed 校验，此处对齐。
        if (speed <= 0) {
            throw new IllegalArgumentException("FishingNet speed 必须大于 0: " + item.getItemId());
        }
        this.speed = speed;
        machineProcessor.setProgressBar(progressBar);
        createPreset(this, this::constructMenu);
    }

    @Override
    public void preRegister() {
        addItemHandler(new BlockTicker() {

            @Override
            public void tick(Block b, SlimefunItem sf, Config data) {
                FishingNet.this.tick(b);
            }

            @Override
            public boolean isSynchronized() {
                // tick 内读取/写入 BlockStorage、访问 BlockData 与方块库存，
                // 均非线程安全，必须在主线程执行。
                return true;
            }
        });

        addItemHandler(new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(Block b) {
                final BlockMenu inv = BlockStorage.getInventory(b.getLocation());
                if (inv != null)
                    inv.dropItems(b.getLocation(), getOutputSlots());
                machineProcessor.endOperation(b);
            }
        });
    }

    protected void constructMenu(BlockMenuPreset menu) {
        draw(menu, GastroStacks.MENU_BACKGROUND_ITEM, BACKGROUND_SLOTS);
        draw(menu, GastroStacks.MENU_OUTPUT_BORDER, OUTPUT_BORDER);
        draw(menu, GastroStacks.MENU_NOT_WATERLOGGED, STATUS_SLOT);
    }

    private void draw(BlockMenuPreset preset, ItemStack item, int... slots) {
        for (int slot : slots) {
            preset.addItem(slot, item, ChestMenuUtils.getEmptyClickHandler());
        }
    }
    
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        return new MachineRecipe(240 / getSpeed(), new ItemStack[] { new ItemStack(Material.AIR) }, new ItemStack[] { CollectionUtil.choice(FISH) });
    }

    protected void tick(Block b) {
        final BlockMenu inv = BlockStorage.getInventory(b.getLocation());
        CraftingOperation currentOperation = getMachineProcessor().getOperation(b);

        if (currentOperation != null) {
            if (isWaterLogged(b)) {

                if (!currentOperation.isFinished()) {
                    getMachineProcessor().updateProgressBar(inv, STATUS_SLOT, currentOperation);
                    currentOperation.addProgress(1);
                } else {
                    inv.replaceExistingItem(STATUS_SLOT, CustomItemStack.create(Material.BLACK_STAINED_GLASS_PANE, " "));
                    for (ItemStack output : currentOperation.getResults()) {
                        inv.pushItem(output.clone(), getOutputSlots());
                    }
                    getMachineProcessor().endOperation(b);
                }
            }
        } else if (isWaterLogged(b)) {
            final MachineRecipe next = findNextRecipe(inv);

            if (next != null) {
                currentOperation = new CraftingOperation(next);
                getMachineProcessor().startOperation(b, currentOperation);
                getMachineProcessor().updateProgressBar(inv, STATUS_SLOT, currentOperation);
            }
        }
    }

    private boolean isWaterLogged(Block b) {
        if (Gastronomicon.slimefunTickCount() % 20 == 0) {
            return getWaterLogged(b);
        } else {
            String waterLogged = BlockStorage.getLocationInfo(b.getLocation(), "water_logged");
            if (waterLogged == null) {
                return getWaterLogged(b);
            } else {
                return Boolean.parseBoolean(waterLogged);
            }
        }
    }

    private boolean getWaterLogged(Block b) {
        final boolean isWaterLogged = b.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged();
        final String value = String.valueOf(isWaterLogged);
        // 仅在水状态变化时写 BlockStorage。getWaterLogged 每 20 tick 被每个渔网调用一次，
        // 而水浸状态极少变化，原先每次都 addBlockInfo 产生冗余脏标记/落盘，高密度渔网下
        // 是无谓 I/O。改为读-比-写。
        if (!value.equals(BlockStorage.getLocationInfo(b.getLocation(), "water_logged"))) {
            BlockStorage.addBlockInfo(b.getLocation(), "water_logged", value);
        }
        return isWaterLogged;
    }
}
