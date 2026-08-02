package io.github.schntgaispock.gastronomicon.api.recipes.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;


import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import lombok.Getter;
import net.kyori.adventure.text.Component;

/**
 * For recipe slots that have multiple acceptable items.
 * <hr>
 * Examples:
 * <ul>
 * <li>The wood in a bed
 * <li>The cobblestone in a stone sword
 * </ul>
 * 
 * @author SchnTgaiSpock
 */
@Getter
public class GroupRecipeComponent extends RecipeComponent<Set<ItemStack>> {

    private final @Getter NamespacedKey id;

    public GroupRecipeComponent(NamespacedKey id, Set<ItemStack> component) {
        super(component);

        this.id = id;
    }

    public GroupRecipeComponent(NamespacedKey id, ItemStack... component) {
        this(id, Set.of(component));
    }

    public GroupRecipeComponent(NamespacedKey id, Material... component) {
        this(id, Set.of(Arrays.stream(component).map(material -> new ItemStack(material)).toArray(ItemStack[]::new)));
    }

    // [perf] 缓存组内每个不可变模板的 SlimefunItem 解析 + 展开为数组。
    // 原实现每次 matches() 对组内每个元素调 getByItem；组在注册后不可变，解析恒定。
    // 数组索引遍历比 Set 迭代器更快，且消除重复 getByItem。toArray(component) 保留 Set 迭代顺序，
    // 故「命中首个即返回」的结果与原实现逐元素顺序一致。可见性/竞争同 SingleRecipeComponent。
    private volatile ItemStack[] groupItemsCache;
    private volatile SlimefunItem[] groupSfCache;
    private volatile boolean groupResolved;

    private void ensureGroupResolved() {
        if (groupResolved) {
            return;
        }
        final ItemStack[] items = component.toArray(ItemStack[]::new);
        final SlimefunItem[] sfs = new SlimefunItem[items.length];
        for (int i = 0; i < items.length; i++) {
            sfs[i] = SlimefunItem.getByItem(items[i]);
        }
        groupItemsCache = items;
        groupSfCache = sfs;
        groupResolved = true;
    }

    @Override
    public boolean matches(ItemStack item) {
        if (item == null) {
            return false;
        }
        ensureGroupResolved();
        final ItemStack[] items = groupItemsCache;
        final SlimefunItem[] sfs = groupSfCache;
        for (int i = 0; i < items.length; i++) {
            final SlimefunItem sfGroupItem = sfs[i];
            if (sfGroupItem != null) {
                if (sfGroupItem.isItem(item)) {
                    return true;
                }
            } else if (item.isSimilar(items[i])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ItemStack getDisplayItem() {
        if (component.isEmpty()) {
            return new ItemStack(Material.AIR);
        }
        final ItemStack displayitem = component.stream().findFirst().get().clone();
        final List<Component> lore = displayitem.lore() == null ? new ArrayList<>() : new ArrayList<>(displayitem.lore());
        lore.add(Component.text(""));
        for (final ItemStack itemStack : component) {
            if (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName()) {
                lore.add(Component.text("§8‑ §f").append(itemStack.getItemMeta().displayName()));
            }
        }
        displayitem.lore(lore);

        return displayitem;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

}
