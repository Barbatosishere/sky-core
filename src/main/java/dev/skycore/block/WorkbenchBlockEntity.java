package dev.skycore.block;

import dev.skycore.CoreContent;
import dev.skycore.logic.StackTransactions;
import dev.skycore.entropy.EntropyRuntime;
import dev.skycore.menu.WorkbenchMenu;
import dev.skycore.progress.TeamAccess;
import dev.skycore.progress.TeamProgressData;
import dev.skycore.recipe.RecipeSignature;
import dev.skycore.recipe.StoneProcessingRecipe;
import dev.skycore.recipe.WorkbenchInput;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.state.BlockState;

public final class WorkbenchBlockEntity extends OwnedInventoryBlockEntity implements MenuProvider {
    private int progress;
    private int duration;
    private String workingId = "";
    private String signature = "";
    @Nullable private StoneProcessingRecipe lastRecipe;
    private List<ItemStack> lastInputs = List.of();
    private final ContainerData data = new ContainerData() {
        @Override public int get(int index) { return switch (index) { case 0 -> progress & 0xFFFF; case 1 -> progress >>> 16; case 2 -> duration & 0xFFFF; case 3 -> duration >>> 16; default -> 0; }; }
        @Override public void set(int index, int value) {}
        @Override public int getCount() { return 4; }
    };

    public WorkbenchBlockEntity(BlockPos pos, BlockState state) { super(CoreContent.WORKBENCH_ENTITY.get(), pos, state, 7); }
    public int progress() { return progress; }
    public int duration() { return duration; }
    public ContainerData menuData() { return data; }
    @Override public void setChanged() {
        if (inventory != null && lastInputs != null && lastInputs.size() == 5 && !sameInputs()) {
            progress = 0;
            rememberInputs();
        }
        super.setChanged();
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack result = super.removeItemNoUpdate(slot);
        if (!result.isEmpty() && slot < 5) { progress = 0; rememberInputs(); setChanged(); }
        return result;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return slot < 5; }
    @Override public Component getDisplayName() { return Component.translatable("block.skycore.stone_workbench"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new WorkbenchMenu(id, inventory, this, data); }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || owner == null || !TeamAccess.online(server.getServer(), owner)) return;
        if (!sameInputs()) progress = 0;
        rememberInputs();
        WorkbenchInput input = new WorkbenchInput(inventory.subList(0, 5));
        TeamProgressData teams = TeamProgressData.get(server.getServer());
        var holder = server.getRecipeManager().getAllRecipesFor(CoreContent.STONE_PROCESSING.get()).stream()
                .filter(r -> r.value().requiredMilestone().map(id -> teams.has(owner, id)).orElse(true))
                .filter(r -> r.value().matches(input, server))
                .min(Comparator.comparing(r -> r.id().toString()));
        if (holder.isEmpty()) { reset(); return; }
        RecipeHolder<StoneProcessingRecipe> selected = holder.get();
        StoneProcessingRecipe recipe = selected.value();
        if (!EntropyRuntime.productionReady(server.getServer(), selected.id().toString(), recipe.results())) return;
        if (recipe != lastRecipe) {
            String currentSignature = RecipeSignature.of(StoneProcessingRecipe.CODEC, recipe, server.registryAccess());
            if (lastRecipe != null || !workingId.equals(selected.id().toString()) || !signature.equals(currentSignature)) progress = 0;
            signature = currentSignature;
            workingId = selected.id().toString();
            lastRecipe = recipe;
        }
        duration = recipe.durationTicks();
        var outputs = StackTransactions.insert(inventory.subList(5, 7), recipe.results());
        if (outputs.isEmpty()) return;
        if (progress < duration) progress++;
        setChanged();
        if (progress < duration) return;
        var consumption = recipe.consumption(input);
        if (consumption.isEmpty() || recipe.requiredMilestone().filter(id -> !teams.has(owner, id)).isPresent()) { reset(); return; }
        // Every precondition has been evaluated. Commit only detached final stacks, then notify progression.
        List<ItemStack> replacement = new ArrayList<>(inventory.stream().map(ItemStack::copy).toList());
        for (int slot = 0; slot < 4; slot++) replacement.get(slot).shrink(consumption.get()[slot]);
        recipe.fuel().ifPresent(fuel -> replacement.get(4).shrink(fuel.count()));
        replacement.set(5, outputs.get().get(0));
        replacement.set(6, outputs.get().get(1));
        for (int slot = 0; slot < 7; slot++) inventory.set(slot, replacement.get(slot));
        progress = 0;
        rememberInputs();
        setChanged();
        teams.processingCommitted(owner, selected.id().toString());
        EntropyRuntime.productionCommitted(server.getServer(), owner, selected.id().toString(), recipe.results());
    }

    private boolean sameInputs() {
        if (lastInputs.size() != 5) return false;
        for (int slot = 0; slot < 5; slot++) if (!ItemStack.matches(inventory.get(slot), lastInputs.get(slot))) return false;
        return true;
    }
    private void rememberInputs() { lastInputs = inventory.subList(0, 5).stream().map(ItemStack::copy).toList(); }
    private void reset() {
        if (progress != 0 || duration != 0 || !workingId.isEmpty()) {
            progress = 0; duration = 0; workingId = ""; signature = ""; lastRecipe = null; setChanged();
        }
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        duration = Math.max(0, tag.getInt("Duration"));
        progress = Math.clamp(tag.getInt("Progress"), 0, duration);
        workingId = tag.getString("Recipe"); signature = tag.getString("RecipeSignature"); lastRecipe = null;
        rememberInputs();
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (!sameInputs()) { progress = 0; rememberInputs(); }
        super.saveAdditional(tag, registries);
        tag.putInt("Progress", progress); tag.putInt("Duration", duration);
        tag.putString("Recipe", workingId); tag.putString("RecipeSignature", signature);
    }
}
