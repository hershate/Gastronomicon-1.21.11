package io.github.schntgaispock.gastronomicon.core.slimefun.items.workstations.manual;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import io.github.mooy1.infinitylib.core.AddonConfig;
import io.github.mooy1.infinitylib.machines.MenuBlock;
import io.github.schntgaispock.gastronomicon.Gastronomicon;
import io.github.schntgaispock.gastronomicon.api.events.PlayerGastroFoodCraftEvent;
import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe;
import io.github.schntgaispock.gastronomicon.api.recipes.RecipeRegistry;
import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe.RecipeMatchResult;
import io.github.schntgaispock.gastronomicon.core.slimefun.GastroGroups;
import io.github.schntgaispock.gastronomicon.core.slimefun.GastroStacks;
import io.github.schntgaispock.gastronomicon.core.slimefun.items.food.GastroFood;
import io.github.schntgaispock.gastronomicon.core.slimefun.recipes.GastroRecipeType;
import io.github.schntgaispock.gastronomicon.util.NumberUtil;
import io.github.schntgaispock.gastronomicon.util.RecipeUtil;
import io.github.schntgaispock.gastronomicon.util.collections.Pair;
import io.github.schntgaispock.gastronomicon.util.item.ItemUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import net.kyori.adventure.text.Component;

@SuppressWarnings("deprecation")
public abstract class GastroWorkstation extends MenuBlock {

    protected static final int[] BACKGROUND_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        13, 17,
        22, 23, 24, 25, 26,
        31, 35,
        36, 37, 38, 39, 40, 41, 42, 43, 44
    };
    protected static final int[] INGREDIENT_BORDER_SLOTS = { 9, 18, 27 };
    protected static final int[] CONTAINER_BORDER_SLOTS = { 14 };
    protected static final int[] OUTPUT_BORDER_SLOTS = { 32 };
    protected static final int[] TOOL_BORDER_SLOTS = { 45 };
    protected static final int CRAFT_BUTTON_SLOT = 53;
    private static Map<Location, Pair<Integer, GastroRecipe>> lastInputHashAndRecipe = new HashMap<>();
    /**
     * perfect-food-max-chance 的懒加载缓存：合成在每次成功产出 SF 食物时都会用到，
     * 原先每次都 getConfig().getDouble（split 键 + Map 遍历），高频合成下开销无谓。
     * 配置在启动后基本不变，且合成仅在主线程，故用主线程懒加载即可。
     */
    private static double cachedPerfectFoodMaxChance = Double.NaN;

    private static double getPerfectFoodMaxChance() {
        if (Double.isNaN(cachedPerfectFoodMaxChance)) {
            cachedPerfectFoodMaxChance = NumberUtil.clamp(
                Gastronomicon.getInstance().getConfig().getDouble("perfect-food-max-chance", 0.25),
                0.0, 1.0);
        }
        return cachedPerfectFoodMaxChance;
    }

    public GastroWorkstation(SlimefunItemStack item, ItemStack[] recipe) {
        super(GastroGroups.BASIC_MACHINES, item, RecipeType.ENHANCED_CRAFTING_TABLE, recipe);
    }

    @Override
    protected int[] getInputSlots() {
        return new int[] { 10, 11, 12, 19, 20, 21, 28, 29, 30 };
    }

    @Override
    protected int[] getOutputSlots() {
        return new int[] { 33, 34 };
    }

    protected int[] getToolSlots() {
        return new int[] { 46, 47, 48, 49, 50, 51 };
    }

    protected int[] getContainerSlots() {
        return new int[] { 15, 16 };
    }

    public abstract GastroRecipeType getGastroRecipeType();

    @Override
    protected void setup(BlockMenuPreset preset) {
        preset.drawBackground(BACKGROUND_ITEM, BACKGROUND_SLOTS);
        preset.drawBackground(GastroStacks.MENU_INGREDIENT_BORDER, INGREDIENT_BORDER_SLOTS);
        preset.drawBackground(GastroStacks.MENU_CONTAINER_BORDER, CONTAINER_BORDER_SLOTS);
        preset.drawBackground(OUTPUT_BORDER, OUTPUT_BORDER_SLOTS);
        preset.drawBackground(GastroStacks.MENU_TOOL_BORDER, TOOL_BORDER_SLOTS);
        preset.drawBackground(GastroStacks.MENU_CRAFT_BUTTON, new int[] { CRAFT_BUTTON_SLOT });
    }

    @Override
    protected void onBreak(BlockBreakEvent e, BlockMenu menu) {
        super.onBreak(e, menu);
        final Location l = menu.getLocation();
        menu.dropItems(l, getToolSlots());
        menu.dropItems(l, getContainerSlots());
        // 清除该方块的配方缓存，避免长期运行下放置/破坏工作站导致静态 Map 内存泄漏
        lastInputHashAndRecipe.remove(l);
    }

    protected void onSuccessfulCraft(Block b) {}

    @Override
    protected void onNewInstance(BlockMenu menu, Block b) {
        super.onNewInstance(menu, b);

        menu.addMenuClickHandler(CRAFT_BUTTON_SLOT, (player, slot, item, action) -> {
            if (!canCraft(menu, b, player, true))
                return false;

            // Check if there is a free slot to craft
            Integer freeSlot = null;
            for (int s : getOutputSlots()) {
                final ItemStack stack = menu.getItemInSlot(s);
                if (stack == null || stack.getType() == Material.AIR) {
                    freeSlot = s;
                    break;
                }
            }

            if (freeSlot == null)
                return false;

            // Get the items in the menu
            final ItemStack[] ingredients = Arrays.stream(getInputSlots()).mapToObj(s -> {
                final ItemStack i = menu.getItemInSlot(s);
                return i == null ? null : i.asOne();
            }).toArray(ItemStack[]::new);
            final List<ItemStack> containers = Arrays.stream(getContainerSlots()).mapToObj(s -> {
                final ItemStack i = menu.getItemInSlot(s);
                return i == null ? null : i.asOne();
            }).toList();
            final List<ItemStack> tools = Arrays.stream(getToolSlots()).mapToObj(s -> {
                final ItemStack i = menu.getItemInSlot(s);
                return i == null ? null : i.asOne();
            }).toList();

            int hash = 1;

            // Get the hashes, and check against last crafted recipe
            hash = hash * 31 + Arrays.hashCode(ingredients);
            hash = hash * 31 + containers.hashCode();
            hash = hash * 31 + tools.hashCode();
            hash = hash * 31 + getOtherHash(player, menu);

            final Pair<Integer, GastroRecipe> hashRecipePair;
            if (lastInputHashAndRecipe.containsKey(menu.getLocation())) {
                hashRecipePair = lastInputHashAndRecipe.get(menu.getLocation());
            } else {
                hashRecipePair = new Pair<>(0, null);
            }

            final GastroRecipe recipe;
            if (hashRecipePair.first() == hash && hashRecipePair.second() != null) {
                // Can skip searching if hashes are the same
                recipe = hashRecipePair.second();
            } else {
                // Otherwise start search
                recipe = findRecipe(ingredients, containers, tools, player, menu);
                if (recipe == null) {
                    Gastronomicon.sendMessage(player, "&eUnknown recipe!");
                    return false;
                } else if (lastInputHashAndRecipe.containsKey(menu.getLocation())) {
                    lastInputHashAndRecipe.get(menu.getLocation()).first(hash);
                    lastInputHashAndRecipe.get(menu.getLocation()).second(recipe);
                } else {
                    lastInputHashAndRecipe.put(menu.getLocation(), new Pair<>(hash, recipe));
                }
            }
            final ItemStack[] recipeOutputs = recipe.getOutputs();

            // Call the event
            final PlayerGastroFoodCraftEvent craftEvent = new PlayerGastroFoodCraftEvent(player, recipe);
            if (!craftEvent.callEvent()) {
                if (craftEvent.getMessage() != null)
                    Gastronomicon.sendMessage(player, Component.text(craftEvent.getMessage()));
                return false;
            }

            onSuccessfulCraft(b);

            // Calculate the crafting result
            final ItemStack output;
            final ItemStack[] toReturn;
            // 仅 GastroFood（有完美版本的食品）才走熟练度/完美概率分支；其他 SF 产物
            // （如假设的第三方非食物多产物配方）不应套用完美食物逻辑。原先 sfItem != null
            // 会把任意 length>1 的 SF 产物配方误当食品。对当前所有配方行为不变。
            final SlimefunItem sfItem = (recipeOutputs.length > 1) ? SlimefunItem.getByItem(recipeOutputs[0]) : null;
            if (sfItem instanceof final GastroFood gastroFood) {
                final AddonConfig playerData = Gastronomicon.getInstance().getPlayerData();
                final String proficiencyPath = player.getUniqueId() + ".proficiencies." + gastroFood.getId();
                final int proficiency = playerData.getInt(proficiencyPath, 0);

                // Add 1 to proficiency
                playerData.set(proficiencyPath, proficiency + 1);

                // perfect-food-max-chance 来自 config.yml（默认 0.25），经懒加载缓存。
                // 钳制到 [0, 1]，确保 randomRound 仅返回 0/1（否则越界取到产物数组
                // 后面的"返还物品"槽位）。
                final double perfectProbability = NumberUtil.clamp(proficiency / 864.0, 0.0, getPerfectFoodMaxChance());

                output = recipeOutputs[NumberUtil.randomRound(perfectProbability)];
                toReturn = Arrays.copyOfRange(recipeOutputs, 2, recipeOutputs.length);
            } else {
                output = recipeOutputs[0];
                toReturn = Arrays.copyOfRange(recipeOutputs, 1, recipeOutputs.length);
            }

            // Subtract inputs
            // 注意：consumeItem / subtract 耗尽物品后只把 amount 置 0，槽位里仍残留
            // amount=0 的「幽灵」堆。由于配方匹配(isSimilar)与缓存哈希(asOne)均忽略
            // 数量，幽灵堆仍会被当作有效原料，导致可空耗产出（刷物品）。因此耗尽后
            // 必须显式清空槽位。
            final Inventory topInv = player.getOpenInventory().getTopInventory();
            Arrays.stream(getInputSlots()).forEach(s -> {
                final ItemStack i = menu.getItemInSlot(s);
                if (i != null) {
                    ItemUtil.consumeItem(i, 1, true).ifPresentOrElse(
                        mat -> topInv.setItem(s, new ItemStack(mat)),
                        () -> { if (i.getAmount() <= 0) topInv.setItem(s, null); });
                }
            });
            for (final int containerSlot : getContainerSlots()) {
                final ItemStack i = menu.getItemInSlot(containerSlot);
                if (i != null && hashRecipePair.second() != null
                    && hashRecipePair.second().getInputs().getContainer().matches(i)) {
                    i.subtract();
                    if (i.getAmount() <= 0) {
                        topInv.setItem(containerSlot, null);
                    }
                    break;
                }
            }

            // Place the result in the inventory
            final Inventory inv = player.getOpenInventory().getTopInventory();

            for (final int outputSlot : getOutputSlots()) {
                final ItemStack currentlyInOutput = inv.getItem(outputSlot);

                if (currentlyInOutput == null || currentlyInOutput.getType() == Material.AIR) {
                    inv.setItem(outputSlot, output.clone());
                    break;
                } else if (RecipeUtil.recipeHash(output) == RecipeUtil.recipeHash(currentlyInOutput)) {
                    if (currentlyInOutput.getMaxStackSize() == currentlyInOutput.getAmount()) {
                        continue;
                    } else {
                        currentlyInOutput.add();
                        break;
                    }
                }
            }
            ItemUtil.giveItems(player, toReturn);
            return false;
        });

    }

    /**
     * Check for basic crafting eligibility, like enough energy/water
     * 
     * @param menu
     *            The current menu
     * @param b
     *            The workstation block
     * @param p
     *            The player trying to craft
     * @param sendMessage Whether or not to send a message to the player
     * @return Whether or not the player can craft something in this workstation.
     *         Do not check for recipes here
     */
    protected abstract boolean canCraft(BlockMenu menu, Block b, Player p, boolean sendMessage);

    @Nullable
    @ParametersAreNonnullByDefault
    protected GastroRecipe findRecipe(ItemStack[] ingredients, List<ItemStack> containers, List<ItemStack> tools,
        Player player, BlockMenu menu) {
        final Set<GastroRecipe> recipes = RecipeRegistry.getRecipes(getGastroRecipeType());

        for (final GastroRecipe recipe : recipes) {
            final RecipeMatchResult result = recipe.matches(ingredients.clone(), containers, tools);
            if (!result.isMatch()) {
                continue;
            } else if (!result.isCraftable()) {
                break;
            } else {
                return recipe;
            }
        }

        return null;
    }

    protected int getOtherHash(Player player, BlockMenu menu) {
        return 0;
    }

}