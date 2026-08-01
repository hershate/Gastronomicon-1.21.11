package io.github.schntgaispock.gastronomicon.api.loot;

import java.util.Arrays;

import org.bukkit.inventory.ItemStack;

import io.github.schntgaispock.gastronomicon.api.loot.LootTable.LootTableBuilder;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;

public class ItemLootTableBuilder extends LootTableBuilder<ItemStack> {


    @SafeVarargs
    public final ItemLootTableBuilder addItems(int weight, ItemStack... drops) {
        for (final ItemStack drop : drops) {
            final SlimefunItem sfItem = SlimefunItem.getByItem(drop);
            if (sfItem != null && sfItem.isDisabled()) {
                continue;
            }

            // 重复 drop 时 put 覆盖旧权重，totalWeight 需抵消旧值，保持总和一致。
            final Integer previous = weightedDrops.put(drop, weight);
            totalWeight += (previous == null) ? weight : (weight - previous);
        }
        return this;
    }

    @SafeVarargs
    public final ItemLootTableBuilder addItems(int weight, SlimefunItemStack... drops) {
        return addItems(weight, Arrays.stream(drops).map(SlimefunItemStack::asOne).toArray(ItemStack[]::new));
    }

    @SafeVarargs
    public final ItemLootTableBuilder addItems(ItemStack... drops) {
        return addItems(1, drops);
    }

    @SafeVarargs
    public final ItemLootTableBuilder addItems(SlimefunItemStack... drops) {
        return addItems(1, drops);
    }

}
