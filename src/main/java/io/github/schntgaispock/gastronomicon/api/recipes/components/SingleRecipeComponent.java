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

    // SingleRecipeComponents do not have to deal with group components in recipes
    @Override
    public boolean matches(ItemStack item) {
        if (item == null) {
            return component.getType() == Material.AIR;
        } else {
            final SlimefunItem sfComponent = SlimefunItem.getByItem(component);
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
        return ItemUtil.hashIgnoreAmount(component);
    }

}
