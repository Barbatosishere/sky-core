package dev.skycore.block;

import dev.skycore.CoreContent;
import dev.skycore.entropy.EntropyContent;
import dev.skycore.menu.WorldFuelSynthesizerMenu;
import dev.skycore.worldfuel.WorldFuelDaylight;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public final class WorldFuelSynthesizerBlockEntity extends OwnedInventoryBlockEntity implements MenuProvider, WorldlyContainer {
    public static final int ACTIVITY = 0;
    public static final int AGENT = 1;
    public static final int CRUDE = 2;
    public static final int STONE = 3;
    public static final int RESIDUE = 4;
    public static final int SLOTS = 5;

    public WorldFuelSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(CoreContent.WORLD_FUEL_SYNTHESIZER_ENTITY.get(), pos, state, SLOTS);
    }

    @Override public Component getDisplayName() { return Component.translatable("block.skycore.world_fuel_synthesizer"); }
    @Nullable @Override public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return stillValid(player) ? new WorldFuelSynthesizerMenu(id, playerInventory, this) : null;
    }

    @Override public int[] getSlotsForFace(@Nullable Direction side) {
        if (owner == null) return new int[0];
        if (side == Direction.UP) return new int[]{ACTIVITY};
        if (side == Direction.DOWN || side == null) return new int[]{CRUDE, STONE, RESIDUE};
        return new int[]{AGENT};
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == ACTIVITY && stack.is(CoreContent.WORLD_ACTIVITY.get())
                || slot == AGENT && stack.is(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get());
    }

    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        return owner != null && canPlaceItem(slot, stack)
                && (side == Direction.UP && slot == ACTIVITY || side != null && side.getAxis().isHorizontal() && slot == AGENT);
    }

    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return owner != null && !isRemoved() && side == Direction.DOWN && slot >= CRUDE && slot < SLOTS;
    }

    void serverTick() {
        if (level == null || level.isClientSide || owner == null || !WorldFuelDaylight.isOpenSky(level, worldPosition.above())
                || !getItem(ACTIVITY).is(CoreContent.WORLD_ACTIVITY.get()) || !getItem(AGENT).is(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get())) return;
        ItemStack crude = new ItemStack(CoreContent.CRUDE_WORLD_FUEL.get());
        ItemStack stone = new ItemStack(EntropyContent.ANTI_ENTROPY_STONE.get());
        ItemStack residue = new ItemStack(CoreContent.TRANSMUTATION_RESIDUE.get());
        crude.set(CoreContent.WORLD_FUEL_OWNER.get(), owner);
        residue.set(CoreContent.WORLD_FUEL_OWNER.get(), owner);
        if (!fits(CRUDE, crude) || !fits(STONE, stone) || !fits(RESIDUE, residue)) return;
        removeItem(ACTIVITY, 1);
        removeItem(AGENT, 1);
        append(CRUDE, crude);
        append(STONE, stone);
        append(RESIDUE, residue);
        setChanged();
    }

    private boolean fits(int slot, ItemStack result) {
        ItemStack present = getItem(slot);
        return present.isEmpty() || ItemStack.isSameItemSameComponents(present, result) && present.getCount() < present.getMaxStackSize();
    }

    private void append(int slot, ItemStack result) {
        setItem(slot, getItem(slot).isEmpty() ? result : getItem(slot).copyWithCount(getItem(slot).getCount() + 1));
    }
}
