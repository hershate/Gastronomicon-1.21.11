package io.github.schntgaispock.gastronomicon.util;

import java.util.Arrays;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.api.recipes.components.RecipeComponent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import lombok.experimental.UtilityClass;

@UtilityClass
public class RecipeUtil {

    /**
     * 将任意配方元素转换为 {@link ItemStack}。
     * <p>
     * 适配 REF (Slimefun 4.9.5)：官方重构后 {@link SlimefunItemStack} 不再继承
     * {@link ItemStack}，因此需要在此显式转换。SlimefunItemStack 通过
     * {@link SlimefunItemStack#asOne()} 取得其带 SF id 的 ItemStack 副本。
     */
    public static ItemStack toStack(Object element) {
        if (element == null) {
            return null;
        }
        if (element instanceof final ItemStack is) {
            return is;
        }
        if (element instanceof final SlimefunItemStack sfis) {
            return sfis.asOne();
        }
        if (element instanceof final Material m) {
            return new ItemStack(m);
        }
        throw new IllegalArgumentException("无法转换为 ItemStack: " + element);
    }

    /**
     * 以可变参数形式构造 9 格配方数组，元素可为 {@link ItemStack}、
     * {@link SlimefunItemStack}、{@link Material} 或 <code>null</code>。
     */
    public static ItemStack[] items(Object... elements) {
        final ItemStack[] recipe = new ItemStack[elements.length];
        for (int i = 0; i < elements.length; i++) {
            recipe[i] = toStack(elements[i]);
        }
        return recipe;
    }

    public static ItemStack[] empty() {
        return new ItemStack[9];
    }

    public static ItemStack[] single(Object item) {
        final ItemStack[] recipe = new ItemStack[9];
        recipe[0] = toStack(item);
        return recipe;
    }

    public static ItemStack[] singleCenter(Object item) {
        final ItemStack[] recipe = new ItemStack[9];
        recipe[4] = toStack(item);
        return recipe;
    }

    public static ItemStack[] collection(Object... items) {
        return RecipeUtil.collection(items, 9);
    }

    private static ItemStack[] collection(Object[] items, int maxLength) {
        final ItemStack[] recipe = new ItemStack[9];

        int l = Math.min(items.length, maxLength);
        for (int i = 0; i < l; i++) {
            recipe[i] = toStack(items[i]);
        }
        return recipe;
    }

    public static ItemStack[] cyclic(Object outer, Object inner) {
        final ItemStack o = toStack(outer);
        final ItemStack i = toStack(inner);
        return new ItemStack[] { o, o, o, o, i, o, o, o, o };
    }

    public static ItemStack[] cyclic(Object outer) {
        return RecipeUtil.cyclic(outer, null);
    }

    public static ItemStack[] cyclicAlternating(Object corner, Object middle, Object inner) {
        final ItemStack c = toStack(corner);
        final ItemStack m = toStack(middle);
        final ItemStack in = toStack(inner);
        return new ItemStack[] { c, m, c, m, in, m, c, m, c };
    }

    public static ItemStack[] cyclicAlternating(Object corner, Object middle) {
        return RecipeUtil.cyclicAlternating(corner, middle, null);
    }

    public static ItemStack[] row(Object item, int rowNumber) {
        final int rowStart = NumberUtil.clamp(rowNumber, 0, 2) * 3;
        final ItemStack is = toStack(item);
        final ItemStack[] recipe = new ItemStack[9];
        recipe[rowStart] = recipe[rowStart + 1] = recipe[rowStart + 2] = is;

        return recipe;
    }

    public static ItemStack[] column(Object item, int colNumber) {
        final int colStart = NumberUtil.clamp(colNumber, 0, 2);
        final ItemStack is = toStack(item);
        final ItemStack[] recipe = new ItemStack[9];
        recipe[colStart] = recipe[colStart + 3] = recipe[colStart + 6] = is;

        return recipe;
    }

    public static ItemStack[] diagonal(Object item, int slope) {
        final ItemStack is = toStack(item);
        final ItemStack[] recipe = new ItemStack[9];
        if (slope == 1)
            recipe[2] = recipe[4] = recipe[6] = is;
        else if (slope == -1)
            recipe[0] = recipe[4] = recipe[8] = is;

        return recipe;
    }

    public static ItemStack[] block(Object item) {
        final ItemStack is = toStack(item);
        final ItemStack[] recipe = new ItemStack[9];
        Arrays.fill(recipe, is);
        return recipe;
    }

    public static int compareComponents(RecipeComponent<?> component1, RecipeComponent<?> component2) {
        return Integer.compare(recipeHash(component1 == null ? null : component1.getComponent()),
            recipeHash(component2 == null ? null : component2.getComponent()));
    }

    public static int compareItemStacks(ItemStack item1, ItemStack item2) {
        return Integer.compare(recipeHash(item1 == null ? null : item1), recipeHash(item2 == null ? null : item2));
    }

    public static int recipeHash(Object object) {
        if (object == null)
            return Integer.MAX_VALUE;
        if (object instanceof final SlimefunItemStack sfStack)
            return sfStack.getItemId().hashCode();
        if (object instanceof final ItemStack stack) {
            final SlimefunItem sfItem = SlimefunItem.getByItem(stack);
            if (sfItem != null) {
                return sfItem.getId().hashCode();
            }
            return stack.getType().hashCode();
        }

        return object.hashCode();
    }

}
