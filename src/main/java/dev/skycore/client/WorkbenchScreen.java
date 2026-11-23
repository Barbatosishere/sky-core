package dev.skycore.client;

import dev.skycore.menu.WorkbenchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorkbenchScreen extends AbstractContainerScreen<WorkbenchMenu> {
    public WorkbenchScreen(WorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 174; inventoryLabelY = 81;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFFCBC5B8);
        graphics.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFF333B40);
        for (var slot : menu.slots) {
            graphics.fill(x + slot.x - 1, y + slot.y - 1, x + slot.x + 17, y + slot.y + 17, 0xFF10191F);
            graphics.fill(x + slot.x, y + slot.y, x + slot.x + 16, y + slot.y + 16, 0xFF657178);
        }
        graphics.fill(x + 74, y + 34, x + 102, y + 39, 0xFF10191F);
        int width = menu.duration() <= 0 ? 0 : (int) Math.min(28, (long) menu.progress() * 28 / menu.duration());
        graphics.fill(x + 74, y + 34, x + 74 + width, y + 39, 0xFF8ED8C2);
        graphics.drawString(font, Component.translatable("gui.skycore.fuel"), x + 76, y + 73, 0xFFD4DDD7, false);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE5ECE6, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE5ECE6, false);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
