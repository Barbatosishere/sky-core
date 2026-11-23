package dev.skycore.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

public record CountedIngredient(Ingredient ingredient, int count) {
    public static final Codec<CountedIngredient> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
            Codec.intRange(1, 64).fieldOf("count").forGetter(CountedIngredient::count)
    ).apply(instance, CountedIngredient::new));

    public boolean matches(ItemStack stack) { return ingredient.test(stack) && stack.getCount() >= count; }
}
