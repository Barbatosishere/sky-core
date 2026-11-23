package dev.skycore.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skycore.CoreContent;
import dev.skycore.progress.Milestone;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public record GerminationRecipe(ResourceLocation soil, CountedIngredient seed, CountedIngredient nourishment,
                                 String tree, int durationTicks, Optional<String> requiredMilestone) implements Recipe<GerminationInput> {
    private static final Codec<ResourceLocation> SOIL = ResourceLocation.CODEC.validate(id -> id.toString().equals("skycore:mineral_soil")
            ? DataResult.success(id) : DataResult.error(() -> "Unsupported germination soil: " + id));
    private static final Codec<String> TREE = Codec.STRING.validate(id -> id.equals("minecraft:oak")
            ? DataResult.success(id) : DataResult.error(() -> "Unsupported Sky Core tree: " + id));
    public static final MapCodec<GerminationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SOIL.fieldOf("soil").forGetter(GerminationRecipe::soil),
            RecipeCodecs.SINGLE.fieldOf("seed").forGetter(GerminationRecipe::seed),
            RecipeCodecs.SINGLE.fieldOf("nourishment").forGetter(GerminationRecipe::nourishment),
            TREE.fieldOf("tree").forGetter(GerminationRecipe::tree),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("duration_ticks").forGetter(GerminationRecipe::durationTicks),
            Milestone.ID_CODEC.optionalFieldOf("required_milestone").forGetter(GerminationRecipe::requiredMilestone)
    ).apply(instance, GerminationRecipe::new));

    @Override public boolean matches(GerminationInput input, Level level) {
        return soil.equals(input.soil()) && seed.matches(input.seed()) && nourishment.matches(input.nourishment());
    }
    @Override public ItemStack assemble(GerminationInput input, HolderLookup.Provider registries) { return new ItemStack(Items.OAK_SAPLING); }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return new ItemStack(Items.OAK_SAPLING); }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return CoreContent.GERMINATION_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return CoreContent.GERMINATION.get(); }

    public static final class Serializer implements RecipeSerializer<GerminationRecipe> {
        private static final StreamCodec<RegistryFriendlyByteBuf, GerminationRecipe> STREAM = ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        @Override public MapCodec<GerminationRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, GerminationRecipe> streamCodec() { return STREAM; }
    }
}
