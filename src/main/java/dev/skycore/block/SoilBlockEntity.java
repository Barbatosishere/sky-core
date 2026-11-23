package dev.skycore.block;

import dev.skycore.CoreContent;
import dev.skycore.logic.TreeGrowth;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import dev.skycore.recipe.GerminationInput;
import dev.skycore.recipe.GerminationRecipe;
import dev.skycore.recipe.RecipeSignature;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class SoilBlockEntity extends OwnedInventoryBlockEntity {
    private int progress;
    private int duration;
    private String workingId = "";
    private String signature = "";
    @Nullable private GerminationRecipe lastRecipe;

    public SoilBlockEntity(BlockPos pos, BlockState state) { super(CoreContent.SOIL_ENTITY.get(), pos, state, 2); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    public int progress() { return progress; }
    public int duration() { return duration; }
    private GerminationInput input(ItemStack seed, ItemStack nourishment) {
        return new GerminationInput(BuiltInRegistries.BLOCK.getKey(getBlockState().getBlock()), seed, nourishment);
    }

    public boolean begin(ServerPlayer player) {
        if (!(level instanceof ServerLevel server) || !mayUse(player)) return false;
        if (!isEmpty()) { showProgress(player); return true; }
        var teams = TeamProgressData.get(server.getServer());
        var candidate = server.getRecipeManager().getAllRecipesFor(CoreContent.GERMINATION.get()).stream()
                .filter(r -> r.value().matches(input(player.getMainHandItem(), player.getOffhandItem()), server))
                .filter(r -> r.value().requiredMilestone().map(id -> teams.has(owner, id)).orElse(true))
                .min(Comparator.comparing(r -> r.id().toString()));
        if (candidate.isEmpty()) { player.displayClientMessage(Component.translatable("message.skycore.germination_inputs"), true); return false; }
        var recipe = candidate.get();
        inventory.set(0, player.getMainHandItem().split(recipe.value().seed().count()));
        inventory.set(1, player.getOffhandItem().split(recipe.value().nourishment().count()));
        player.getInventory().setChanged();
        workingId = recipe.id().toString();
        signature = RecipeSignature.of(GerminationRecipe.CODEC, recipe.value(), server.registryAccess());
        lastRecipe = recipe.value();
        duration = recipe.value().durationTicks();
        progress = 0;
        setChanged();
        showProgress(player);
        return true;
    }

    public void showProgress(ServerPlayer player) {
        player.displayClientMessage(Component.translatable("message.skycore.germination_progress", progress, duration), true);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || owner == null || isEmpty() || !TeamAccess.online(server.getServer(), owner)) return;
        var teams = TeamProgressData.get(server.getServer());
        var candidate = server.getRecipeManager().getAllRecipesFor(CoreContent.GERMINATION.get()).stream()
                .filter(r -> r.value().matches(input(inventory.get(0), inventory.get(1)), server))
                .filter(r -> r.value().requiredMilestone().map(id -> teams.has(owner, id)).orElse(true))
                .min(Comparator.comparing(r -> r.id().toString()));
        if (candidate.isEmpty()) {
            if (progress != 0) { progress = 0; setChanged(); }
            lastRecipe = null;
            return;
        }
        var holder = candidate.get();
        GerminationRecipe recipe = holder.value();
        if (lastRecipe != recipe) {
            String currentSignature = RecipeSignature.of(GerminationRecipe.CODEC, recipe, server.registryAccess());
            if (lastRecipe != null || !workingId.equals(holder.id().toString()) || !signature.equals(currentSignature)) progress = 0;
            workingId = holder.id().toString(); signature = currentSignature; lastRecipe = recipe;
        }
        duration = recipe.durationTicks();
        if (progress < duration) { progress++; setChanged(); }
        if (progress < duration || !TreeGrowth.tryPlace(server, worldPosition)) return;
        inventory.clear();
        progress = 0; duration = 0; workingId = ""; signature = ""; lastRecipe = null;
        setChanged();
        teams.germinationCommitted(owner, holder.id().toString());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        duration = Math.max(0, tag.getInt("Duration")); progress = Math.clamp(tag.getInt("Progress"), 0, duration);
        workingId = tag.getString("Recipe"); signature = tag.getString("RecipeSignature"); lastRecipe = null;
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress); tag.putInt("Duration", duration);
        tag.putString("Recipe", workingId); tag.putString("RecipeSignature", signature);
    }
}
