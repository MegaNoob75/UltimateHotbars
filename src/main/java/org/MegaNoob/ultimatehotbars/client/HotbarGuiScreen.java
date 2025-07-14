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
import net.minecraft.client.KeyMapping;

import java.util.List;

public class HotbarGuiScreen extends Screen {
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");

    private EditBox pageInput;

    private static final long INITIAL_DELAY_MS = 300;
    private static final long REPEAT_INTERVAL_MS = 100;
    private final boolean[] keyHeld = new boolean[4];
    private final long[] keyPressStart = new long[4];
    private final long[] lastRepeat = new long[4];

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

    @Override
    public void tick() {
        super.tick();
        long now = System.currentTimeMillis();

        KeyMapping[] keys = {
                KeyBindings.ARROW_LEFT,
                KeyBindings.ARROW_RIGHT,
                KeyBindings.ARROW_UP,
                KeyBindings.ARROW_DOWN
        };

        for (int i = 0; i < keys.length; i++) {
            if (keys[i].isDown()) {
                if (!keyHeld[i]) {
                    keyHeld[i] = true;
                    keyPressStart[i] = now;
                    lastRepeat[i] = 0;
                    handleArrowKey(i);
                } else {
                    long heldTime = now - keyPressStart[i];
                    if (heldTime >= INITIAL_DELAY_MS && now - lastRepeat[i] >= REPEAT_INTERVAL_MS) {
                        lastRepeat[i] = now;
                        handleArrowKey(i);
                    }
                }
            } else {
                keyHeld[i] = false;
            }
        }
    }



    private void handleArrowKey(int index) {
        switch (index) {
            case 0 -> {
                int prev = HotbarManager.getPage();
                HotbarManager.syncFromGame();
                HotbarManager.setPage(prev - 1);
                if (HotbarManager.getPage() != prev) updatePageInput();
            }
            case 1 -> {
                int prev = HotbarManager.getPage();
                HotbarManager.syncFromGame();
                HotbarManager.setPage(prev + 1);
                if (HotbarManager.getPage() != prev) updatePageInput();
            }
            case 2 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "handleArrowKey(↑)");
            }
            case 3 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "handleArrowKey(↓)");
            }
        }
    }


    public void updatePageInput() {
        String expected = String.valueOf(HotbarManager.getPage() + 1);
        if (!pageInput.getValue().equals(expected)) {
            // Temporarily disable responder
            pageInput.setResponder(s -> {});
            pageInput.setValue(expected);
            // Restore original responder
            pageInput.setResponder(value -> {
                try {
                    int p = Integer.parseInt(value) - 1;
                    if (p >= 0 && p < ultimatehotbars.MAX_PAGES) {
                        HotbarManager.syncFromGame();
                        HotbarManager.setPage(p);
                    }
                } catch (NumberFormatException ignored) {}
            });
        }
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pageInput.isFocused() &&
                (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT ||
                        keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            HotbarManager.getCurrentHotbar().clear();
            HotbarManager.syncToGame();
            return true;
        }

        // ✅ DO NOT handle OPEN_GUI here — it's now centralized in ClientEvents
        return super.keyPressed(keyCode, scanCode, modifiers);
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
                graphics.fill(baseX - border, y - 3,
                        baseX - border + bgWidth, y - 3 + bgHeight,
                        0x80FFFFFF);
            }

            String lbl = String.valueOf(row + 1);
            graphics.drawString(this.font, lbl,
                    baseX - border - 3 - this.font.width(lbl),
                    y + (rowHeight - this.font.lineHeight) / 2,
                    0xFFFFFF);

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

        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (mouseX >= baseX && mouseX < baseX + totalW &&
                    mouseY >= startY && mouseY < startY + totalH) {
                int row = (int)((mouseY - startY) / rowHeight);
                row = Math.max(0, Math.min(rows - 1, row));
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(row, "mouseClick(RIGHT)");
                HotbarManager.syncToGame();
                Minecraft.getInstance().setScreen(
                        new InventoryScreen(Minecraft.getInstance().player)
                );
                return true;
            }
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (mouseX >= baseX && mouseX < baseX + totalW &&
                    mouseY >= startY && mouseY < startY + totalH) {
                int row = (int)((mouseY - startY) / rowHeight);
                int slot = (int)((mouseX - baseX) / slotW);
                row = Math.max(0, Math.min(rows - 1, row));
                slot = Math.max(0, Math.min(Hotbar.SLOT_COUNT - 1, slot));

                // ✅ FIXED ORDER: set first, then sync
                HotbarManager.setHotbar(row, "mouseClick(LEFT)");
                HotbarManager.setSlot(slot);
                HotbarManager.syncFromGame(); // now saves correct state

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

    @Override
    public boolean isPauseScreen() {
        return false;
    }



}
