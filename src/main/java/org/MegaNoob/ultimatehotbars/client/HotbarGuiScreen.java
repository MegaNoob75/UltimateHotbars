package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.List;

public class HotbarGuiScreen extends Screen {
    @SuppressWarnings("removal")
    private static final ResourceLocation HOTBAR_TEX =
            new ResourceLocation("textures/gui/widgets.png");

    private EditBox pageInput;

    public HotbarGuiScreen() {
        super(Component.literal("Virtual Hotbars"));
    }

    @Override
    protected void init() {
        super.init();
        int midX = this.width / 2;
        pageInput = new EditBox(this.font, midX - 30, 5, 60, 20,
                Component.translatable("key.ultimatehotbars.page_input"));
        pageInput.setValue(String.valueOf(HotbarManager.getPage() + 1));
        pageInput.setResponder(value -> {
            try {
                int p = Integer.parseInt(value) - 1;
                if (p >= 0 && p < ultimatehotbars.MAX_PAGES) {
                    HotbarManager.syncFromGame();
                    HotbarManager.setPage(p);
                }
            } catch (NumberFormatException ignored) {}
        });
        addRenderableWidget(pageInput);
    }

    public void updatePageInput() {
        pageInput.setValue(String.valueOf(HotbarManager.getPage() + 1));
    }

    public void clear() {
        HotbarManager.getCurrentHotbar().clear();
        HotbarManager.syncToGame();

    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            HotbarManager.getCurrentHotbar().clear();
            HotbarManager.syncToGame();
            return true;
        }

        if (keyCode == KeyBindings.OPEN_GUI.getKey().getValue()) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_UP -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() - 1);
                return true;
            }
            case GLFW.GLFW_KEY_DOWN -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() + 1);
                return true;
            }
            case GLFW.GLFW_KEY_LEFT -> {
                HotbarManager.syncFromGame();
                HotbarManager.setPage(HotbarManager.getPage() - 1);
                updatePageInput();
                return true;
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                HotbarManager.syncFromGame();
                HotbarManager.setPage(HotbarManager.getPage() + 1);
                updatePageInput();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @SubscribeEvent
    public static void onInventoryKey(ScreenEvent.KeyPressed event) {
        if (!(event.getScreen() instanceof Screen)) return;
        if (KeyBindings.CLEAR_HOTBAR.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            HotbarManager.getCurrentHotbar().clear();
            HotbarManager.syncToGame();
            event.setCanceled(true);
        }
        if (KeyBindings.OPEN_GUI.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof HotbarGuiScreen) {
                mc.setScreen(null);
            } else {
                mc.setScreen(new HotbarGuiScreen());
            }
            event.setCanceled(true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        pageInput.render(graphics, mouseX, mouseY, partialTicks);

        int topY = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;
        int rows = ultimatehotbars.HOTBARS_PER_PAGE;
        int rowHeight = 22;
        int totalH = rows * rowHeight;
        int startY = topY + ((bottomY - topY) - totalH) / 2;

        int midX = this.width / 2;
        int bgWidth = 182;
        int bgHeight = 22;
        int border = 1;
        int cellW = (bgWidth - border * 2) / Hotbar.SLOT_COUNT;
        int cellH = bgHeight;
        int baseX = midX - bgWidth / 2 + border;

        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int selHb = HotbarManager.getHotbar();
        for (int row = 0; row < pageHotbars.size(); row++) {
            int y = startY + row * rowHeight;
            Hotbar hb = pageHotbars.get(row);

            RenderSystem.setShaderTexture(0, HOTBAR_TEX);
            graphics.blit(HOTBAR_TEX, baseX - border, y - 3, 0, 0, bgWidth, bgHeight);

            if (row == selHb) {
                graphics.fill(
                        baseX - border, y - 3,
                        baseX - border + bgWidth, y - 3 + bgHeight,
                        0x80FFFFFF
                );
            }

            String lbl = String.valueOf(row + 1);
            graphics.drawString(
                    this.font, lbl,
                    baseX - border - 3 - this.font.width(lbl),
                    y + (rowHeight - this.font.lineHeight) / 2,
                    0xFFFFFF
            );

            int yOffset = y - 3;
            for (int slot = 0; slot < Hotbar.SLOT_COUNT; slot++) {
                ItemStack stack = hb.getSlot(slot);
                int ix = baseX + slot * cellW + (cellW - 16) / 2;
                int iy = yOffset + (cellH - 16) / 2;
                graphics.renderItem(stack, ix, iy);
                graphics.renderItemDecorations(this.font, stack, ix, iy);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            int topY = pageInput.getY() + pageInput.getHeight() + 6;
            int bottomY = this.height - 30;
            int rows = HotbarManager.HOTBARS_PER_PAGE;
            int rowH = 22;
            int totalH = rows * rowH;
            int startY = topY + ((bottomY - topY) - totalH) / 2;
            int midX = this.width / 2;
            int slotW = 18;
            int totalW = Hotbar.SLOT_COUNT * slotW;
            int baseX = midX - totalW / 2;

            if (mouseX >= baseX && mouseX < baseX + totalW &&
                    mouseY >= startY && mouseY < startY + totalH) {
                int row = (int)((mouseY - startY) / rowH);
                row = Math.max(0, Math.min(rows - 1, row));
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(row);
                HotbarManager.syncToGame();
                Minecraft.getInstance().setScreen(
                        new InventoryScreen(Minecraft.getInstance().player)
                );
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int topY = pageInput.getY() + pageInput.getHeight() + 6;
            int bottomY = this.height - 30;
            int rows = HotbarManager.HOTBARS_PER_PAGE;
            int rowHeight = 22;
            int totalH = rows * rowHeight;
            int startY = topY + ((bottomY - topY) - totalH) / 2;
            int midX = this.width / 2;
            int slotW = 18;
            int totalW = Hotbar.SLOT_COUNT * slotW;
            int baseX = midX - totalW / 2;

            if (mouseX >= baseX && mouseX < baseX + totalW &&
                    mouseY >= startY && mouseY < startY + totalH) {
                int row = (int)((mouseY - startY) / rowHeight);
                int slot = (int)((mouseX - baseX) / slotW);
                row = Math.max(0, Math.min(rows - 1, row));
                slot = Math.max(0, Math.min(Hotbar.SLOT_COUNT - 1, slot));

                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(row);
                HotbarManager.setSlot(slot);

                Minecraft mc = Minecraft.getInstance();
                mc.player.getInventory().selected = slot;
                mc.player.connection.send(
                        new ServerboundSetCarriedItemPacket(slot)
                );

                HotbarManager.syncToGame();
                mc.setScreen(null);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
