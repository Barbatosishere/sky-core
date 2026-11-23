package dev.skycore.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class OwnedInventoryBlockEntity extends OwnedBlockEntity implements Container {
    protected final NonNullList<ItemStack> inventory;
    protected OwnedInventoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        inventory = NonNullList.withSize(slots, ItemStack.EMPTY);
    }
    @Override public int getContainerSize() { return inventory.size(); }
    @Override public boolean isEmpty() { return inventory.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return inventory.get(slot); }
    @Override public ItemStack removeItem(int slot, int count) {
        ItemStack result = ContainerHelper.removeItem(inventory, slot, count);
        if (!result.isEmpty()) setChanged();
        return result;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) { return ContainerHelper.takeItem(inventory, slot); }
    @Override public void setItem(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        stack.limitSize(Math.min(getMaxStackSize(), stack.getMaxStackSize()));
        setChanged();
    }
    @Override public boolean stillValid(Player player) {
        if (level == null || isRemoved() || level.getBlockEntity(worldPosition) != this || player.distanceToSqr(worldPosition.getCenter()) > 64) return false;
        return level.isClientSide || player instanceof ServerPlayer serverPlayer && mayUse(serverPlayer);
    }
    @Override public void clearContent() { inventory.clear(); setChanged(); }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.clear();
        ContainerHelper.loadAllItems(tag, inventory, registries);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, inventory, registries);
    }
}
