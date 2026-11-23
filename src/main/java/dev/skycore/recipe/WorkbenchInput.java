package dev.skycore.recipe;

import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record WorkbenchInput(List<ItemStack> stacks) implements RecipeInput {
    @Override public ItemStack getItem(int slot) { return stacks.get(slot); }
    @Override public int size() { return stacks.size(); }
}
