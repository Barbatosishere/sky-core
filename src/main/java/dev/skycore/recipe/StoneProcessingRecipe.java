package dev.skycore.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycore.CoreContent;
import dev.skycore.logic.IngredientAllocator;
import dev.skycore.progress.Milestone;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record StoneProcessingRecipe(List<CountedIngredient> inputs, Optional<CountedIngredient> fuel,
                                    List<ItemStack> results, int durationTicks, Optional<String> requiredMilestone)
        implements Recipe<WorkbenchInput> {
    public static final MapCodec<StoneProcessingRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RecipeCodecs.list(CountedIngredient.CODEC, 1, 4).fieldOf("inputs").forGetter(StoneProcessingRecipe::inputs),
            CountedIngredient.CODEC.optionalFieldOf("fuel").forGetter(StoneProcessingRecipe::fuel),
            RecipeCodecs.list(RecipeCodecs.RESULT, 1, 2).fieldOf("results").forGetter(StoneProcessingRecipe::results),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration_ticks").forGetter(StoneProcessingRecipe::durationTicks),
            Milestone.ID_CODEC.optionalFieldOf("required_milestone").forGetter(StoneProcessingRecipe::requiredMilestone)
    ).apply(instance, StoneProcessingRecipe::new));

    public Optional<int[]> consumption(WorkbenchInput input) {
        if (input.size() < 5 || fuel.filter(value -> !value.matches(input.getItem(4))).isPresent()) return Optional.empty();
        for (int slot = 0; slot < 4; slot++) {
            ItemStack stack = input.getItem(slot);
            if (!stack.isEmpty() && inputs.stream().noneMatch(ingredient -> ingredient.ingredient().test(stack))) return Optional.empty();
        }
        int[] available = new int[4];
        int[] required = new int[inputs.size()];
        boolean[][] matches = new boolean[inputs.size()][4];
        for (int slot = 0; slot < 4; slot++) available[slot] = input.getItem(slot).getCount();
        for (int i = 0; i < inputs.size(); i++) {
            required[i] = inputs.get(i).count();
            for (int slot = 0; slot < 4; slot++) matches[i][slot] = inputs.get(i).ingredient().test(input.getItem(slot));
        }
        return IngredientAllocator.allocate(available, required, matches);
    }

    @Override public boolean matches(WorkbenchInput input, Level level) { return consumption(input).isPresent(); }
    @Override public ItemStack assemble(WorkbenchInput input, HolderLookup.Provider registries) { return results.getFirst().copy(); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return results.getFirst().copy(); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CoreContent.STONE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return CoreContent.STONE_PROCESSING.get(); }

    public static final class Serializer implements RecipeSerializer<StoneProcessingRecipe> {
        private static final StreamCodec<RegistryFriendlyByteBuf, StoneProcessingRecipe> STREAM = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        @Override public MapCodec<StoneProcessingRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, StoneProcessingRecipe> streamCodec() { return STREAM; }
    }
}
