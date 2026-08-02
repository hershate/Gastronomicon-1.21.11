package io.github.schntgaispock.gastronomicon.core.slimefun.items.workstations.automatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;


import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe;
import io.github.schntgaispock.gastronomicon.api.recipes.components.RecipeComponent;
import io.github.schntgaispock.gastronomicon.core.slimefun.GastroGroups;
import io.github.schntgaispock.gastronomicon.core.slimefun.GastroStacks;
import io.github.schntgaispock.gastronomicon.core.slimefun.items.food.SimpleGastroFood;
import io.github.schntgaispock.gastronomicon.util.collections.Counter;
import io.github.schntgaispock.gastronomicon.util.collections.Pair;
import io.github.schntgaispock.gastronomicon.util.item.GastroKeys;
import io.github.schntgaispock.gastronomicon.util.item.ItemUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import lombok.Getter;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.AdvancedMenuClickHandler;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;

@Getter
@SuppressWarnings("deprecation")
public class ElectricKitchen extends AContainer {

    protected static final int[] BACKGROUND_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        13, 16, 17,
        22, 23, 24, 25, 26,
        31, 32, 33, 34, 35,
        40, 44,
        45, 46, 47, 48, 49, 50, 51, 52
    };

    protected static final int[] INPUT_BORDER = { 9, 18, 27, 36 };
    protected static final int ANDROID_BORDER = 14;
    protected static final int OUTPUT_BORDER = 41;
    protected static final int ANDROID_SLOT = 15;
    protected static final int STATUS_SLOT = 53;
    // AContainer 的 BlockTicker.isSynchronized() 为 false，ElectricKitchen 的 tick 在
    // Slimefun TickerTask 的异步线程上执行；而 onBlockBreak 在主线程。两者并发访问该缓存，
    // 必须使用并发安全实现，否则 HashMap 在并发 put/remove 下可能丢条目甚至损坏。
    private static Map<Location, CacheEntry> lastInputHashAndRecipe = new ConcurrentHashMap<>();

    /**
     * [perf] 每方块缓存：上次的输入哈希、命中的槽位 Counter，以及每槽的物品引用 + 该引用的哈希。
     * <p>
     * {@code ItemUtil.hashIgnoreAmount(slot)} 的开销几乎全在 {@code getItemMeta()}（克隆 meta）。
     * 经核验 {@code menu.getItemInSlot(slot)} 返回库存内<strong>同一引用</strong>（REF
     * {@code ChestMenu.getItemInSlot → inv.getItem}），且输入槽食材在两次 tick 间不会被原地改 meta
     * （consumeItem 只改 amount 或替换整个物品；食材无耐久/附魔等 meta 变化；hashIgnoreAmount 忽略 amount）。
     * 故「引用未变 ⇒ (type+meta) 哈希未变」：tick 间复用缓存哈希、跳过 getItemMeta。引用变了（玩家取放/
     * 物流替换）则 {@code ==} 失败自动重算。每方块每 tick 由 TickerTask 单线程访问，数组无需额外同步；
     * Map 用 ConcurrentHashMap 保证并发 put/remove/get 安全。
     */
    private static final class CacheEntry {
        int hash;
        Counter<Integer> found;
        final ItemStack[] slotRefs;
        final int[] slotHashes;
        CacheEntry(int slotCount) {
            slotRefs = new ItemStack[slotCount];
            slotHashes = new int[slotCount];
        }
    }

    private final EnergyNetComponentType energyComponentType = EnergyNetComponentType.CONSUMER;
    private final String machineIdentifier = "GN_ELECTRIC_KITCHEN";
    private final MachineProcessor<CraftingOperation> machineProcessor = new MachineProcessor<>(this);
    private final ItemStack progressBar = new ItemStack(Material.FLINT_AND_STEEL);

    public ElectricKitchen(SlimefunItemStack item, int capacity, int energyConsumption, int speed, ItemStack[] recipe) {
        super(GastroGroups.ELECTRIC_MACHINES, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);

        setCapacity(capacity);
        setEnergyConsumption(energyConsumption);
        setProcessingSpeed(speed);
        machineProcessor.setProgressBar(progressBar);
    }

    @Override
    public void createPreset(SlimefunItem item, String title, Consumer<BlockMenuPreset> setup) {
        new BlockMenuPreset(item.getId(), title) { // Modified from InventoryBlock

            @Override
            public void init() {
                setup.accept(this);
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return getInputSlots();
                } else {
                    return getOutputSlots();
                }
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack item) {
                if (flow == ItemTransportFlow.INSERT) {
                    final int[] slots = Arrays.stream(getInputSlots())
                        .mapToObj(slot -> new Pair<>(slot, menu.getItemInSlot(slot))) // get pairs of (slot, item in slot)
                        .filter(pair -> pair.second() != null && pair.second().getType() == item.getType()) // get all the slots/items which are the same type as the requested item
                        .mapToInt(pair -> pair.first()) // return only those slots
                        .toArray();

                    return slots.length == 0 ? getInputSlots() : slots; // length of 0 means the item isn't in the current input slots
                } else {
                    return getOutputSlots();
                }
            }

            @Override
            public boolean canOpen(Block b, Player p) {
                if (p.hasPermission("slimefun.inventory.bypass")) {
                    return true;
                } else {
                    return item.canUse(p, false) && Slimefun.getProtectionManager().hasPermission(p, b.getLocation(), Interaction.INTERACT_BLOCK);
                }
            }
        };
    }

    @Override
    public int[] getInputSlots() {
        return new int[] { 10, 11, 12, 19, 20, 21, 28, 29, 30, 37, 38, 39 };
    }

    @Override
    public int[] getOutputSlots() {
        return new int[] { 42, 43 };
    }

    
    protected BlockBreakHandler onBlockBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(Block b) {
                BlockMenu inv = BlockStorage.getInventory(b.getLocation());

                if (inv != null) {
                    inv.dropItems(b.getLocation(), getInputSlots());
                    inv.dropItems(b.getLocation(), getOutputSlots());
                    inv.dropItems(b.getLocation(), ANDROID_SLOT);
                }

                getMachineProcessor().endOperation(b);
                lastInputHashAndRecipe.remove(b.getLocation());
            }

        };
    }

    @Override
    protected void constructMenu(BlockMenuPreset preset) {
        draw(preset, GastroStacks.MENU_BACKGROUND_ITEM, BACKGROUND_SLOTS);
        draw(preset, GastroStacks.MENU_INPUT_BORDER, INPUT_BORDER);
        draw(preset, GastroStacks.MENU_ANDROID_BORDER, ANDROID_BORDER);
        draw(preset, GastroStacks.MENU_OUTPUT_BORDER, OUTPUT_BORDER);
        draw(preset, GastroStacks.MENU_NO_ANDROID, STATUS_SLOT);

        for (final int i : getOutputSlots()) { // From AContainer
            preset.addMenuClickHandler(i, new AdvancedMenuClickHandler() {
                @Override
                public boolean onClick(Player p, int slot, ItemStack cursor, ClickAction action) {
                    return false;
                }

                @Override
                public boolean onClick(InventoryClickEvent e, Player p, int slot, ItemStack cursor,
                    ClickAction action) {
                    return cursor == null || cursor.getType() == null || cursor.getType() == Material.AIR;
                }
            });
        }
    }

    private void draw(BlockMenuPreset preset, ItemStack item, int... slots) {
        for (int slot : slots) {
            preset.addItem(slot, item, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        final ItemStack android = menu.getItemInSlot(ANDROID_SLOT);
        if (android == null) {
            menu.replaceExistingItem(STATUS_SLOT, GastroStacks.MENU_NO_ANDROID);
            return null;
        }

        final String foodId = android.getItemMeta().getPersistentDataContainer().get(GastroKeys.CHEF_ANDROID_FOOD,
            PersistentDataType.STRING);
        if (foodId == null) {
            menu.replaceExistingItem(STATUS_SLOT, GastroStacks.MENU_NO_ANDROID);
            return null;
        }
        final SlimefunItem food = SlimefunItem.getById(foodId);
        if (food == null || !(food instanceof final SimpleGastroFood gastroFood)) {
            menu.replaceExistingItem(STATUS_SLOT, GastroStacks.MENU_NO_ANDROID);
            return null;
        }

        final GastroRecipe recipe = gastroFood.getGastroRecipe();

        // 哈希必须包含 foodId：若玩家不改变输入、仅更换厨师机器人（不同食物），哈希相同会
        // 命中旧 Counter（旧食物的槽位映射），却产出新机器人食物 -> 错配方/可被利用
        // （用便宜食物原料 + 换昂贵食物机器人 -> 产出昂贵食物）。
        // [perf] getInputSlots() 每次返回 new int[]；findNextRecipe 每 tick 调用，取一次复用于哈希与匹配两处循环。
        final int[] inputSlots = getInputSlots();
        CacheEntry entry = lastInputHashAndRecipe.get(menu.getLocation());
        if (entry == null) {
            entry = new CacheEntry(inputSlots.length);
            lastInputHashAndRecipe.put(menu.getLocation(), entry);
        }

        // [perf] 引用缓存：槽位物品引用未变（==）则复用上次的 hashIgnoreAmount，跳过 getItemMeta()。
        // 安全性论证见 CacheEntry 注释（getItemInSlot 返回同一引用 + 输入槽食材无原地 meta 变更）。
        int hash = foodId.hashCode();
        for (int i = 0; i < inputSlots.length; i++) {
            final ItemStack cur = menu.getItemInSlot(inputSlots[i]);
            if (cur == entry.slotRefs[i]) {
                hash = hash * 31 + entry.slotHashes[i];
            } else {
                final int sh = ItemUtil.hashIgnoreAmount(cur);
                entry.slotRefs[i] = cur;
                entry.slotHashes[i] = sh;
                hash = hash * 31 + sh;
            }
        }

        final Counter<Integer> found;
        if (entry.hash == hash) {
            found = entry.found;
        } else {
            found = new Counter<>();
            for (RecipeComponent<?> component : recipe.getInputs().getAll()) {
                if (component == RecipeComponent.EMPTY) continue;

                boolean matched = false;
                for (int slot : inputSlots) {
                    final ItemStack input = menu.getItemInSlot(slot);
                    if (component.matches(input) && input.getAmount() > found.get(slot)) {
                        matched = true;
                        found.add(slot);
                        break;
                    }
                }
                if (!matched) {
                    menu.replaceExistingItem(STATUS_SLOT, GastroStacks.MENU_INCORRECT_RECIPE);
                    return null;
                }
            }

            entry.hash = hash;
            entry.found = found;
        }

        // 输出空间检查：输出满时不消耗原料，避免操作完成后产物被 pushItem
        // 静默 void（丢物品）。对应 REF AContainer#scanForRecipe 的 fitAll 守卫。
        if (!menu.fits(recipe.getOutputs()[0], getOutputSlots())) {
            return null;
        }

        // 异步 tick 期间玩家可在主线程改动菜单：消耗前必须重新校验每个槽位仍持有
        // 足够原料，全部通过后才消耗。否则会出现：玩家中途取走原料导致 input 为 null
        // 而 input.asQuantity 抛 NPE，或原料数量不足导致空耗产出（可被恶意客户端定时
        // 点击利用）。对应 REF AContainer#scanForRecipe 的 re-validate-before-consume。
        for (final var pair : found.entries()) {
            if (pair.first() == null) {
                continue;
            }
            final ItemStack input = menu.getItemInSlot(pair.first());
            if (input == null || input.getAmount() < pair.second()) {
                // 原料已被取走或不足：作废缓存命中，下 tick 重新匹配（不消耗任何原料）
                entry.hash = 0;
                entry.found = null;
                return null;
            }
        }

        final ArrayList<ItemStack> inputs = new ArrayList<>();
        final ArrayList<ItemStack> outputs = new ArrayList<>();
        outputs.add(recipe.getOutputs()[0]);

        for (final var pair : found.entries()) {
            if (pair.first() == null) {
                continue;
            }

            final int slot = pair.first();
            final ItemStack input = menu.getItemInSlot(slot);
            inputs.add(input.asQuantity(pair.second()));
            ItemUtil.consumeItem(input, pair.second(), true).ifPresent(mat -> {
                outputs.add(new ItemStack(mat));
            });
            // 耗尽后清空槽位：否则残留 amount=0 的「幽灵」堆，而缓存哈希
            // (hashIgnoreAmount) 与命中复用的 Counter 均忽略数量，会导致
            // 下一 tick 缓存命中后从幽灵堆空耗产出（刷物品）。
            if (input.getAmount() <= 0) {
                menu.replaceExistingItem(slot, null, false);
            }
        }

        final MachineRecipe newRecipe = new MachineRecipe(60 / getSpeed(), inputs.toArray(ItemStack[]::new), outputs.toArray(ItemStack[]::new));

        return newRecipe;
    }

    /**
     * Modified from AContainer#tick
     */
    @Override
    protected void tick(Block b) {
        final BlockMenu inv = BlockStorage.getInventory(b.getLocation());
        CraftingOperation currentOperation = getMachineProcessor().getOperation(b);

        if (currentOperation != null) {
            if (takeCharge(b.getLocation())) {
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
            } else {
                inv.replaceExistingItem(STATUS_SLOT, GastroStacks.MENU_NOT_ENOUGH_ENERGY);
            }
        } else {
            final MachineRecipe next = findNextRecipe(inv);

            if (next != null && next.getInput() != null) {
                currentOperation = new CraftingOperation(next);
                getMachineProcessor().startOperation(b, currentOperation);
                getMachineProcessor().updateProgressBar(inv, STATUS_SLOT, currentOperation);
            }
        }
    }
}
