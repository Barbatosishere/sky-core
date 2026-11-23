package dev.skycore.menu;

import dev.skycore.CoreContent;
import dev.skycore.block.WorkbenchBlockEntity;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class WorkbenchMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public WorkbenchMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, new SimpleContainer(7), new SimpleContainerData(4));
    }
    public WorkbenchMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(CoreContent.WORKBENCH_MENU.get(), id);
        checkContainerSize(container, 7);
        checkContainerDataCount(data, 4);
        this.container = container; this.data = data;
        for (int slot = 0; slot < 4; slot++) addSlot(new Slot(container, slot, 24 + slot % 2 * 18, 35 + slot / 2 * 18));
        addSlot(new Slot(container, 4, 78, 53));
        for (int slot = 5; slot < 7; slot++) addSlot(new Slot(container, slot, 114 + (slot - 5) * 20, 44) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 92 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 150));
        addDataSlots(data);
    }
    public int progress() { return (data.get(0) & 0xFFFF) | (data.get(1) & 0xFFFF) << 16; }
    public int duration() { return (data.get(2) & 0xFFFF) | (data.get(3) & 0xFFFF) << 16; }
    @Override public boolean stillValid(Player player) { return container.stillValid(player); }
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack present = slot.getItem();
        ItemStack original = present.copy();
        if (index < 7) {
            if (!moveItemStackTo(present, 7, slots.size(), true)) return ItemStack.EMPTY;
        } else if (present.is(CoreContent.PRIMORDIAL_FUEL.get())) {
            if (!moveItemStackTo(present, 4, 5, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(present, 0, 4, false)) return ItemStack.EMPTY;
        if (present.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, present);
        return original;
    }
}
