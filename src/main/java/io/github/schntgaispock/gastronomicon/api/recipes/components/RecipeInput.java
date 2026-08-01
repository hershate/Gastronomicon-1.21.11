package io.github.schntgaispock.gastronomicon.api.recipes.components;

import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe;
import io.github.schntgaispock.gastronomicon.api.recipes.GastroRecipe.RecipeShape;
import io.github.schntgaispock.gastronomicon.util.RecipeUtil;
import lombok.Getter;
import lombok.ToString;

/**
 * Stores information about the input of a {@link GastroRecipe}. Does not
 * include tools.
 * 
 * @author SchnTgaiSpock
 */
@Getter
@ToString
public class RecipeInput {

    private final RecipeShape shapedness;
    private final RecipeComponent<?> container;
    private final RecipeComponent<?>[] ingredients;

    public RecipeInput(RecipeShape shapedness, RecipeComponent<?> container,
            RecipeComponent<?>... ingredients) {

        this.shapedness = shapedness;

        if (shapedness == RecipeShape.SHAPELESS) {
            Arrays.sort(ingredients, RecipeUtil::compareComponents);
        }
        this.ingredients = new RecipeComponent<?>[9];
        // 公开 API 防御：调用方可能传入含 null 或长度<9 的数组，缺失/null 槽位统一转为
        // EMPTY。否则 hashCode() 会因 ingredient.hashCode() 在 null 上 NPE（注册进
        // RecipeRegistry 的 HashSet 时触发）。现有内部调用方已预处理，行为不变。
        for (int i = 0; i < 9; i++) {
            this.ingredients[i] = (i < ingredients.length && ingredients[i] != null)
                ? ingredients[i]
                : RecipeComponent.EMPTY;
        }

        this.container = container;
    }

    /**
     * @return The items to display in the Slimefun guide
     */
    public ItemStack[] getDisplayIngredients() {
        return Arrays.stream(ingredients)
                .map(ingredient -> ingredient == null ? new ItemStack(Material.AIR) : ingredient.getComponent())
                .toArray(ItemStack[]::new);
    }

    public RecipeComponent<?>[] getAll() {
        final RecipeComponent<?>[] all = Arrays.copyOf(ingredients, ingredients.length + 1);
        all[ingredients.length] = container;
        return all;
    }

    @Override
    public int hashCode() {
        int hash = 1;

        hash = hash * 31 + shapedness.hashCode();
        if (container != null) {
            hash = hash * 31 + container.hashCode();
        }

        for (RecipeComponent<?> ingredient : ingredients) {
            hash = hash * 31 + ingredient.hashCode();
        }

        return hash;
    }

}