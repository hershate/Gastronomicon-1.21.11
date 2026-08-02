package io.github.schntgaispock.gastronomicon.api.recipes;

import java.util.Arrays;
import java.util.List;
import java.util.Set;


import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.api.recipes.components.RecipeComponent;
import io.github.schntgaispock.gastronomicon.api.recipes.components.RecipeInput;
import io.github.schntgaispock.gastronomicon.core.slimefun.recipes.GastroRecipeType;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import lombok.Getter;

@Getter
public class ShapedGastroRecipe extends GastroRecipe {

    public ShapedGastroRecipe(GastroRecipeType recipeType, RecipeComponent<?>[] ingredients,
        RecipeComponent<?> container, Set<ItemStack> tools, ItemStack... outputs) {
        super(recipeType, new RecipeInput(RecipeShape.SHAPED, container, Arrays.stream(ingredients).map(
            ingredient -> ingredient == null ? RecipeComponent.EMPTY : ingredient)
            .toArray(RecipeComponent<?>[]::new)), tools, outputs);
    }

    public ShapedGastroRecipe(GastroRecipeType recipeType, ItemStack[] ingredients,
        ItemStack container, Set<ItemStack> tools, ItemStack... outputs) {
        super(recipeType, RecipeShape.SHAPED, ingredients, null, tools, outputs);
    }

    public ShapedGastroRecipe(GastroRecipeType recipeType, ItemStack[] ingredients, Set<ItemStack> tools,
        ItemStack... outputs) {
        this(recipeType, ingredients, null, tools, outputs);
    }

    @Override
    public RecipeMatchResult matches(ItemStack[] givenIngredients, List<ItemStack> givenContainers,
        List<ItemStack> givenTools) {

        if (!givenContainers.stream().anyMatch(container -> getInputs().getContainer().matches(container))) {
            return RecipeMatchResult.NO_MATCH;
        }

        // 工具匹配用 isItemSimilar（SF-id 级，耐久/数量无关），避免可受损工具（如 IRON_SWORD
        // 材质的厨房刀）受损后与配方模板（无损）equals 不等而无法合成。
        if (!hasAllTools(givenTools))
            return RecipeMatchResult.NO_MATCH;

        for (int i = 0; i < givenIngredients.length; i++) {
            if (!componentMatches(getInputs().getIngredients()[i], givenIngredients[i])) {
                return RecipeMatchResult.NO_MATCH;
            }
        }

        return RecipeMatchResult.SUCCESS;
    }

}
