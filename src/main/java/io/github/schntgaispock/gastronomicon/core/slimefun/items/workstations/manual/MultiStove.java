package io.github.schntgaispock.gastronomicon.core.slimefun.items.workstations.manual;

import java.util.List;
import java.util.Set;


import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.Gastronomicon;
import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe;
import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe.RecipeMatchResult;
import io.github.schntgaispock.gastronomicon.api.recipes.MultiStoveRecipe;
import io.github.schntgaispock.gastronomicon.api.recipes.RecipeRegistry;
import io.github.schntgaispock.gastronomicon.core.slimefun.recipes.GastroRecipeType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.common.CommonPatterns;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;

@Getter
@SuppressWarnings("deprecation")
public class MultiStove extends GastroWorkstation implements EnergyNetComponent {

    @RequiredArgsConstructor
    public enum Temperature {
        LOW(TEMPERATURE_BUTTON_LOW, "低"),
        MEDIUM(TEMPERATURE_BUTTON_MEDIUM, "中"),
        HIGH(TEMPERATURE_BUTTON_HIGH, "高");

        private final @Getter ItemStack item;

        private final @Getter String text;

        public static Temperature fromText(String text) {
            for (Temperature temp : values()) {
                if (temp.getText().equals(text)) {
                    return temp;
                }
            }
            throw new IllegalArgumentException(text + " is now a valid value");
        }

        public Temperature next() {
            if (ordinal() == values().length - 1) {
                return null;
            }

            return values()[ordinal() + 1];
        }

        public Temperature prev() {
            if (ordinal() == 0) {
                return null;
            }

            return values()[ordinal() - 1];
        }
    }

    public static final ItemStack TEMPERATURE_BUTTON_LOW = CustomItemStack.create(
        Material.YELLOW_STAINED_GLASS_PANE,
        "&7温度: &e低",
        "",
        "&b左键点击 &7提高温度");
    public static final ItemStack TEMPERATURE_BUTTON_MEDIUM = CustomItemStack.create(
        Material.ORANGE_STAINED_GLASS_PANE,
        "&7温度: &6中",
        "",
        "&b左键点击 &7提高温度",
        "&b右键点击 &7降低温度");
    public static final ItemStack TEMPERATURE_BUTTON_HIGH = CustomItemStack.create(
        Material.RED_STAINED_GLASS_PANE,
        "&7温度: &c高",
        "",
        "&b右键点击 &7降低温度");
    public static final int TEMPERATURE_BUTTON_SLOT = 52;
    public static final String TEMPERATURE_KEY = "gastronomicon:multi_stove/temperature";

    private final int capacity;
    private final int energyPerUse;

    public MultiStove(SlimefunItemStack item, ItemStack[] recipe, int capacity, int energyPerUse) {
        super(item, recipe);

        // capacity 必须能储电；energyPerUse 必须 >0，否则 canCraft 的 charge<energyPerUse
        // 永假 → 永远可合成且 onSuccessfulCraft 扣 0 → 免费运行。
        if (capacity <= 0 || energyPerUse <= 0) {
            throw new IllegalArgumentException("MultiStove capacity/energyPerUse 必须大于 0: " + item.getItemId());
        }
        this.capacity = capacity;
        this.energyPerUse = energyPerUse;
    }

    @Override
    protected void setup(BlockMenuPreset preset) {
        super.setup(preset);

        preset.drawBackground(TEMPERATURE_BUTTON_LOW, new int[] { TEMPERATURE_BUTTON_SLOT });
    }

    @Override
    protected void onNewInstance(BlockMenu menu, Block b) {
        super.onNewInstance(menu, b);

        menu.addMenuOpeningHandler(player -> {
            final String temp = BlockStorage.getLocationInfo(menu.getLocation(), TEMPERATURE_KEY);
            // temp 来自 BlockStorage 持久数据，正常为合法枚举名，但数据损坏/枚举变更时
            // valueOf 会抛 IAE 导致该炉菜单无法打开（每次开炉触发）。默认 LOW。
            Temperature parsed = Temperature.LOW;
            if (temp != null) {
                try {
                    parsed = Temperature.valueOf(temp);
                } catch (IllegalArgumentException e) {
                    parsed = Temperature.LOW;
                }
            }
            menu.replaceExistingItem(TEMPERATURE_BUTTON_SLOT, parsed.getItem(), false);
        });

        menu.addMenuClickHandler(TEMPERATURE_BUTTON_SLOT, (player, slot, item, action) -> {
            if (item == null || !item.hasItemMeta()) {
                return false;
            }
            final String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            final String[] parts = CommonPatterns.COLON.split(displayName, 2);
            if (parts.length < 2) {
                return false;
            }
            try {
                final Temperature t = Temperature.fromText(parts[1].trim());
                changeTemperature(menu, action.isRightClicked() ? t.prev() : t.next());
            } catch (IllegalArgumentException ignored) {
                // 显示名解析失败（非法温度文本），忽略本次点击
            }
            return false;
        });
    }

    public static void changeTemperature(BlockMenu menu, Temperature t) {
        if (t == null) {
            return;
        }
        menu.replaceExistingItem(TEMPERATURE_BUTTON_SLOT, t.getItem());
        BlockStorage.addBlockInfo(menu.getLocation(), TEMPERATURE_KEY, t.name());
    }

    @Override
    public GastroRecipeType getGastroRecipeType() {
        return GastroRecipeType.MULTI_STOVE;
    }

    @Override
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    
    protected GastroRecipe findRecipe(ItemStack[] ingredients, List<ItemStack> containers, List<ItemStack> tools,
        Player player, BlockMenu menu) {
        final ItemStack tempButton = menu.getItemInSlot(TEMPERATURE_BUTTON_SLOT);
        final Set<GastroRecipe> recipes = RecipeRegistry.getRecipes(getGastroRecipeType());
        // 同一原料组合在不同温度下可能对应不同产物，因此不能只取首个原料匹配项；
        // 需遍历全部配方，返回首个「原料匹配 且 温度吻合」的配方。
        for (final GastroRecipe recipe : recipes) {
            if (!(recipe instanceof final MultiStoveRecipe msRecipe)) {
                continue;
            }
            final RecipeMatchResult result = msRecipe.matches(ingredients, containers, tools);
            if (!result.isMatch()) {
                continue;
            } else if (!result.isCraftable()) {
                break;
            }
            if (msRecipe.getTemperature().getItem().isSimilar(tempButton)) {
                return msRecipe;
            }
        }
        return null;
    }

    @Override
    protected int getOtherHash(Player player, BlockMenu menu) {
        return menu.getItemInSlot(TEMPERATURE_BUTTON_SLOT).getType().ordinal(); // Doesn't have to be a hash, just
                                                                                // unique
    }

    @Override
    protected boolean canCraft(BlockMenu menu, Block b, Player p, boolean sendMessage) {
        final int charge = getCharge(b.getLocation());
        if (charge < getEnergyPerUse()) {
            Gastronomicon.sendMessage(p, "&e电力不足！");
            return false;
        }

        return true;
    }

    @Override
    protected void onSuccessfulCraft(Block b) {
        final int charge = getCharge(b.getLocation());
        setCharge(b.getLocation(), charge - getEnergyPerUse());
    }

}
