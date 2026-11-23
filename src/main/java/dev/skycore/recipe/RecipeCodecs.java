package dev.skycore.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.List;
import net.minecraft.world.item.ItemStack;

final class RecipeCodecs {
    private RecipeCodecs() {}
    static <T> Codec<List<T>> list(Codec<T> element, int min, int max) {
        return element.listOf().validate(values -> values.size() >= min && values.size() <= max
                ? DataResult.success(values) : DataResult.error(() -> "Expected " + min + ".." + max + " entries"));
    }
    static final Codec<ItemStack> RESULT = ItemStack.CODEC.validate(stack -> !stack.isEmpty() && stack.getCount() <= stack.getMaxStackSize()
            ? DataResult.success(stack) : DataResult.error(() -> "Invalid result stack size"));
    static final Codec<CountedIngredient> SINGLE = CountedIngredient.CODEC.validate(value -> value.count() == 1
            ? DataResult.success(value) : DataResult.error(() -> "Germination count must be 1"));
}
