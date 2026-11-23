package dev.skycore.menu;

import dev.skycore.CoreContent;
import dev.skycore.block.WorldFuelSynthesizerBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class WorldFuelSynthesizerMenu extends AbstractContainerMenu {
    private final Container container;

    public WorldFuelSynthesizerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, new SimpleContainer(WorldFuelSynthesizerBlockEntity.SLOTS));
    }

    public WorldFuelSynthesizerMenu(int id, Inventory playerInventory, Container container) {
        super(CoreContent.WORLD_FUEL_SYNTHESIZER_MENU.get(), id);
        checkContainerSize(container, WorldFuelSynthesizerBlockEntity.SLOTS);
        this.container = container;
        addSlot(new Slot(container, 0, 44, 21) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(CoreContent.WORLD_ACTIVITY.get()); }
        });
        addSlot(new Slot(container, 1, 44, 54) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.is(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get()); }
        });
        for (int slot = 2; slot < 5; slot++) addSlot(new Slot(container, slot, 104 + (slot - 2) * 19, 37) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 102 + row * 18));
        }
        for (int column = 0; column < 9; column++) addSlot(new Slot(playerInventory, column, 8 + column * 18, 160));
    }

    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        if (index < 5) {
            if (!moveItemStackTo(source, 5, slots.size(), true)) return ItemStack.EMPTY;
        } else {
            int target = source.is(CoreContent.WORLD_ACTIVITY.get()) ? 0 : source.is(CoreContent.ACTIVE_TRANSMUTATION_AGENT.get()) ? 1 : -1;
            if (target < 0 || !moveItemStackTo(source, target, target + 1, false)) return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
