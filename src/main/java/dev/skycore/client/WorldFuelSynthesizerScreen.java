package dev.skycore.client;

import dev.skycore.menu.WorldFuelSynthesizerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WorldFuelSynthesizerScreen extends AbstractContainerScreen<WorldFuelSynthesizerMenu> {
    public WorldFuelSynthesizerScreen(WorldFuelSynthesizerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageHeight = 184;
        inventoryLabelY = 90;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333B40);
        for (var slot : menu.slots) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF10191F);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF657178);
        }
        graphics.drawString(font, ">", leftPos + 80, topPos + 42, 0xFFE5ECE6, false);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE5ECE6, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE5ECE6, false);
        graphics.drawString(font, Component.translatable("gui.skycore.synthesizer_activity"), 8, 25, 0xFFE5ECE6, false);
        graphics.drawString(font, Component.translatable("gui.skycore.synthesizer_agent"), 8, 58, 0xFFE5ECE6, false);
        graphics.drawString(font, Component.translatable("gui.skycore.synthesizer_sky"), 8, 78, 0xFFE5ECE6, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
