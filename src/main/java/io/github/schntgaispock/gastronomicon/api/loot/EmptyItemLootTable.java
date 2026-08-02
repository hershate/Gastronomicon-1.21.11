package io.github.schntgaispock.gastronomicon.api.loot;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

public class EmptyItemLootTable extends LootTable<ItemStack> {

    protected EmptyItemLootTable() {
        super(new ItemStack[0], 0, new int[0], new int[0]);
    }

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public ItemStack generate() {
        return new ItemStack(Material.AIR);
    }
    
}
