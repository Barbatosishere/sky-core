package dev.skycore.menu;

import dev.skycore.CoreContent;
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

public final class WorldActivityCollectorMenu extends AbstractContainerMenu {
    public static final int POWDER_IN = 0;
    public static final int AGENT_IN = 1;
    public static final int ACTIVITY_IN = 2;
    public static final int CRUDE_IN = 3;
    public static final int ACTIVITY_OUT = 4;
    public static final int AGENT_OUT = 5;
    public static final int CRUDE_OUT = 6;
    public static final int STONE_OUT = 7;
    public static final int RESIDUE_OUT = 8;
    public static final int FUEL_OUT = 9;
    public static final int MACHINE_SLOTS = 10;

    private final Container container;
    private final ContainerData data;

    public WorldActivityCollectorMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, new SimpleContainer(MACHINE_SLOTS), new SimpleContainerData(3));
    }

    public WorldActivityCollectorMenu(int id, Inventory playerInventory, Container container, ContainerData data) {
        super(CoreContent.WORLD_ACTIVITY_COLLECTOR_MENU.get(), id);
        checkContainerSize(container, MACHINE_SLOTS);
        checkContainerDataCount(data, 3);
        this.container = container;
        this.data = data;
        machineSlot(POWDER_IN, 8, 80);
        machineSlot(AGENT_IN, 26, 80);
        machineSlot(ACTIVITY_IN, 44, 80);
        machineSlot(CRUDE_IN, 62, 80);
        machineSlot(ACTIVITY_OUT, 80, 34);
        machineSlot(AGENT_OUT, 80, 80);
        machineSlot(CRUDE_OUT, 98, 80);
        machineSlot(STONE_OUT, 116, 80);
        machineSlot(RESIDUE_OUT, 134, 80);
        machineSlot(FUEL_OUT, 152, 80);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 128 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(playerInventory, col, 8 + col * 18, 186));
        addDataSlots(data);
    }

    private void machineSlot(int index, int x, int y) {
        addSlot(new Slot(container, index, x, y) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
    }

    public int progress(int index) { return data.get(index); }
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
        if (index < MACHINE_SLOTS) {
            if (!moveItemStackTo(source, MACHINE_SLOTS, slots.size(), true)) return ItemStack.EMPTY;
        } else return ItemStack.EMPTY;
        if (source.isEmpty()) slot.setByPlayer(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }
}
