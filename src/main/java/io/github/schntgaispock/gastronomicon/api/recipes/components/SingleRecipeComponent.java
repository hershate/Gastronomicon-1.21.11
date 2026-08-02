package io.github.schntgaispock.gastronomicon.api.recipes.components;


import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.util.item.ItemUtil;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import lombok.Getter;

/**
 * For recipe slots that require a certain item
 *
 * Examples:
 * - The wool in a bed
 * - The stick in a stone sword
 *
 * @author SchnTgaiSpock
 */
@Getter
public class SingleRecipeComponent extends RecipeComponent<ItemStack> {

    public SingleRecipeComponent(ItemStack component) {
        super(component);
    }

    // [perf] 缓存对不可变模板 component 的 SlimefunItem 解析。
    // 原实现每次 matches() 都调 SlimefunItem.getByItem(component)，该方法对 SF 物品会读
    // PersistentDataContainer（克隆 ItemMeta）。component 在注册后不可变，SF 注册表在启动后稳定，
    // 故解析结果恒定——每次合成、每个 ElectricKitchen tick（每方块）、每个候选配方×每格原料重复解析
    // 是纯浪费。改为首次匹配时惰性解析并缓存。
    // 可见性：matches() 可能由 ElectricKitchen 的异步 ticker 线程调用，故用 volatile。
    // 竞争：多线程首次并发解析是良性的（幂等，结果一致）。SF 运行期不注销物品，故缓存无失效需求。
    private volatile SlimefunItem cachedSfComponent;
    private volatile boolean sfResolved;

    // [perf] hashCode() 经 RecipeInput.hashCode() -> GastroRecipe 注册期去重调用，缓存避免重复
    // getItemMeta().hashCode()。非每次合成路径，但与缓存主题一致、收益近乎免费。
    private volatile int cachedHash;
    private volatile boolean hashResolved;

    private SlimefunItem sfComponent() {
        if (sfResolved) {
            return cachedSfComponent;
        }
        final SlimefunItem s = SlimefunItem.getByItem(component);
        cachedSfComponent = s;
        sfResolved = true;
        return s;
    }

    // SingleRecipeComponents do not have to deal with group components in recipes
    @Override
    public boolean matches(ItemStack item) {
        if (item == null) {
            return component.getType() == Material.AIR;
        } else {
            final SlimefunItem sfComponent = sfComponent();
            if (sfComponent != null) {
                return sfComponent.isItem(item);
            }
            return item.isSimilar(component);
        }
    }

    @Override
    public ItemStack getDisplayItem() {
        return component.clone();
    }

    @Override
    public int hashCode() {
        if (hashResolved) {
            return cachedHash;
        }
        final int h = ItemUtil.hashIgnoreAmount(component);
        cachedHash = h;
        hashResolved = true;
        return h;
    }

}
