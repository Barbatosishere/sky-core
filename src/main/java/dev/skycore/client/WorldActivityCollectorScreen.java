package dev.skycore.client;

import dev.skycore.logic.WorldFuelCycle;
import dev.skycore.menu.WorldActivityCollectorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorldActivityCollectorScreen extends AbstractContainerScreen<WorldActivityCollectorMenu> {
    public WorldActivityCollectorScreen(WorldActivityCollectorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 210;
        inventoryLabelY = 116;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = leftPos;
        int top = topPos;
        graphics.fill(left, top, left + imageWidth, top + imageHeight, 0xFFCBC5B8);
        graphics.fill(left + 3, top + 3, left + imageWidth - 3, top + imageHeight - 3, 0xFF333B40);
        for (var slot : menu.slots) {
            graphics.fill(left + slot.x - 1, top + slot.y - 1, left + slot.x + 17, top + slot.y + 17, 0xFF10191F);
            graphics.fill(left + slot.x, top + slot.y, left + slot.x + 16, top + slot.y + 16, 0xFF657178);
        }
        progress(graphics, 43, 55, 0, WorldFuelCycle.COLLECTION_TICKS, 0xFF8ED8C2);
    }

    private void progress(GuiGraphics graphics, int x, int y, int index, int duration, int color) {
        int width = 90;
        int filled = menu.progress(index) * width / duration;
        graphics.fill(leftPos + x, topPos + y, leftPos + x + width, topPos + y + 4, 0xFF10191F);
        graphics.fill(leftPos + x, topPos + y, leftPos + x + filled, topPos + y + 4, color);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int color = 0xFFE5ECE6;
        graphics.drawString(font, title, titleLabelX, titleLabelY, color, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, color, false);
        graphics.drawString(font, Component.translatable("gui.skycore.world_activity_collect"), 16, 20, color, false);
        graphics.drawString(font, Component.translatable("gui.skycore.world_activity_recovery"), 8, 68, color, false);
        graphics.drawString(font, Component.translatable("gui.skycore.world_activity_export"), 8, 103, color, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
