package dev.skycore.block;

import dev.skycore.CoreContent;
import dev.skycore.logic.WorldFuelCycle;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public final class WorldActivityCollectorBlockEntity extends OwnedInventoryBlockEntity implements WorldlyContainer {
    public static final int POWDER_IN = 0;
    private static final int AGENT_IN = 1;
    private static final int ACTIVITY_IN = 2;
    private static final int CRUDE_IN = 3;
    public static final int ACTIVITY_OUT = 4;
    private static final int AGENT_OUT = 5;
    private static final int CRUDE_OUT = 6;
    private static final int STONE_OUT = 7;
    private static final int RESIDUE_OUT = 8;
    private static final int FUEL_OUT = 9;
    private static final int MACHINE_SLOTS = 10;
    private static final int[] EXPORT_SLOTS = {ACTIVITY_OUT, POWDER_IN, AGENT_IN, ACTIVITY_IN, CRUDE_IN, AGENT_OUT, CRUDE_OUT, STONE_OUT, RESIDUE_OUT, FUEL_OUT};
    private int collectionTicks;

    public WorldActivityCollectorBlockEntity(BlockPos pos, BlockState state) {
        super(CoreContent.WORLD_ACTIVITY_COLLECTOR_ENTITY.get(), pos, state, MACHINE_SLOTS);
    }

    public int collectionTicks() { return collectionTicks; }

    @Override public int[] getSlotsForFace(Direction side) { return owner == null ? new int[0] : EXPORT_SLOTS.clone(); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return false; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) { return owner != null && !isRemoved(); }

    void serverTick() {
        if (level == null || level.isClientSide || owner == null) return;
        int previous = collectionTicks;
        collectionTicks = WorldFuelCycle.advance(collectionTicks, WorldFuelCycle.COLLECTION_TICKS, true);
        ItemStack output = getItem(ACTIVITY_OUT);
        if (collectionTicks == WorldFuelCycle.COLLECTION_TICKS
                && (output.isEmpty() || ItemStack.isSameItemSameComponents(output, CoreContent.WORLD_ACTIVITY.get().getDefaultInstance())
                && output.getCount() < output.getMaxStackSize())) {
            setItem(ACTIVITY_OUT, new ItemStack(CoreContent.WORLD_ACTIVITY.get(), output.getCount() + 1));
            collectionTicks = 0;
        }
        if (collectionTicks != previous) setChanged();

        output = getItem(ACTIVITY_OUT);
        if (!output.isEmpty()) {
            IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
            if (target != null) {
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, output.copy(), false);
                if (remainder.getCount() != output.getCount()) setItem(ACTIVITY_OUT, remainder);
            }
        }
    }

    void dropInventory() {
        Containers.dropContents(level, worldPosition, this);
        clearContent();
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        collectionTicks = Math.clamp(tag.getInt("CollectionTicks"), 0, WorldFuelCycle.COLLECTION_TICKS);
        migrateProgress(tag, "ActivationTicks", POWDER_IN, WorldFuelCycle.ACTIVATION_TICKS);
        migrateProgress(tag, "RefinementTicks", CRUDE_IN, WorldFuelCycle.REFINEMENT_TICKS);
    }

    private void migrateProgress(CompoundTag tag, String key, int slot, int maximum) {
        ItemStack stack = getItem(slot);
        if (!stack.isEmpty() && tag.getInt(key) > 0 && !stack.has(CoreContent.WORLD_FUEL_PROGRESS.get())) {
            stack.set(CoreContent.WORLD_FUEL_PROGRESS.get(), Math.clamp(tag.getInt(key), 0, maximum));
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("CollectionTicks", collectionTicks);
    }
}
