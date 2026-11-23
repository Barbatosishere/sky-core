package dev.skycore.recipe;

import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;

public final class RecipeSignature {
    private RecipeSignature() {}
    public static <T> String of(MapCodec<T> codec, T recipe, HolderLookup.Provider registries) {
        return codec.codec().encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries), recipe).getOrThrow().toString();
    }
}
