package dev.skycore.recipe;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

public record GerminationInput(ResourceLocation soil, ItemStack seed, ItemStack nourishment) implements RecipeInput {
    @Override public ItemStack getItem(int slot) { return switch (slot) { case 0 -> seed; case 1 -> nourishment; default -> throw new IndexOutOfBoundsException(slot); }; }
    @Override public int size() { return 2; }
}
