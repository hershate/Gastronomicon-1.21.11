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

    @Override
    public boolean matches(ItemStack item) {
        if (item == null) {
            return false;
        }
        for (final ItemStack groupItem : component) {
            final SlimefunItem sfGroupItem = SlimefunItem.getByItem(groupItem);
            if (sfGroupItem != null) {
                if (sfGroupItem.isItem(item)) {
                    return true;
                }
            } else if (item.isSimilar(groupItem)) {
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
