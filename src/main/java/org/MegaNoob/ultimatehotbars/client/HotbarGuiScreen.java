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
import org.MegaNoob.ultimatehotbars.Config;
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

    // Drag & drop state
    private static final double DRAG_THRESHOLD = 5.0;
    private boolean potentialDrag = false;
    private double pressX, pressY;
    private boolean dragging = false;
    private ItemStack draggedStack = ItemStack.EMPTY;
    private int sourcePage, sourceRow, sourceSlotIdx;
    private Hotbar sourceHotbar;

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
        this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                net.minecraft.network.chat.Component.literal("Config"),
                btn -> this.minecraft.setScreen(new HotbarConfigScreen(this))
        ).pos(10, this.height - 30).size(80, 20).build());
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
        if (dragging) {
            int currentPage = HotbarManager.getPage();
            if (index == 0) HotbarManager.setPage(currentPage - 1);
            if (index == 1) HotbarManager.setPage(currentPage + 1);
            updatePageInput();
            return;
        }
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
                HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "handleArrowKey(UP)");
            }
            case 3 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "handleArrowKey(DOWN)");
            }
        }
    }

    public void updatePageInput() {
        String expected = String.valueOf(HotbarManager.getPage() + 1);
        if (!pageInput.getValue().equals(expected)) {
            pageInput.setResponder(s -> {});
            pageInput.setValue(expected);
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
        boolean ctrl = hasControlDown();
        boolean isHotbarKey =
                keyCode == GLFW.GLFW_KEY_LEFT ||
                        keyCode == GLFW.GLFW_KEY_RIGHT ||
                        keyCode == GLFW.GLFW_KEY_UP ||
                        keyCode == GLFW.GLFW_KEY_DOWN ||
                        keyCode == GLFW.GLFW_KEY_MINUS ||
                        keyCode == GLFW.GLFW_KEY_EQUAL ||
                        (ctrl && (keyCode == GLFW.GLFW_KEY_MINUS || keyCode == GLFW.GLFW_KEY_EQUAL));
        if (isHotbarKey) {
            if (pageInput.isFocused()) pageInput.setFocused(false);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            HotbarManager.getCurrentHotbar().clear();
            HotbarManager.syncToGame();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        pageInput.render(graphics, mouseX, mouseY, partialTicks);

        int topY    = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;
        int rows     = ultimatehotbars.HOTBARS_PER_PAGE;
        int rowHeight= 22;
        int totalH   = rows * rowHeight;
        int startY   = topY + ((bottomY - topY) - totalH) / 2;
        int midX     = this.width / 2;
        int bgWidth  = 182;
        int bgHeight = 22;
        int border   = 1;
        int cellW    = (bgWidth - border * 2) / Hotbar.SLOT_COUNT;
        int cellH    = bgHeight;
        int baseX    = midX - bgWidth / 2 + border;

        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int selHb = HotbarManager.getHotbar();

        // draw each row + its items
        for (int row = 0; row < pageHotbars.size(); row++) {
            int y = startY + row * rowHeight;
            Hotbar hb = pageHotbars.get(row);

            RenderSystem.setShaderTexture(0, HOTBAR_TEX);
            graphics.blit(HOTBAR_TEX, baseX - border, y - 3, 0, 0, bgWidth, bgHeight);

            // highlight selected hotbar
            if (row == selHb) {
                float[] c = Config.highlightColor();
                int color = ((int)(c[3]*255)<<24) |
                        ((int)(c[0]*255)<<16) |
                        ((int)(c[1]*255)<< 8) |
                        (int)(c[2]*255);
                graphics.fill(
                        baseX - border, y - 3,
                        baseX - border + bgWidth, y - 3 + bgHeight,
                        color
                );
            }

            // row label
            String lbl = String.valueOf(row + 1);
            graphics.drawString(
                    this.font, lbl,
                    baseX - border - 3 - this.font.width(lbl),
                    y + (rowHeight - this.font.lineHeight)/2,
                    0xFFFFFF
            );

            int yOffset = y - 3;
            for (int slot = 0; slot < Hotbar.SLOT_COUNT; slot++) {
                ItemStack stack = hb.getSlot(slot);
                int ix = baseX + slot * cellW + (cellW - 16)/2;
                int iy = yOffset + (cellH - 16)/2;
                graphics.renderItem(stack, ix, iy);
                graphics.renderItemDecorations(this.font, stack, ix, iy);
            }
        }

        // ─── Pulsating hover-slot border ────────────────────────────
        int[] hover = getSlotCoords(mouseX, mouseY);
        if (hover != null) {
            int hoverRow  = hover[0];
            int hoverSlot = hover[1];
            int hy = startY + hoverRow * rowHeight - 3;
            int hx = baseX + hoverSlot * cellW;

            // config RGBA + pulse
            float[] arr = Config.hoverBorderColor();
            long now = System.currentTimeMillis();
            float t = (now % 1000L) / 1000f;                // 1s cycle
            float pulse = (float)(Math.sin(2 * Math.PI * t)*0.5 + 0.5);
            int alpha = (int)(arr[3] * pulse * 255);
            int r     = (int)(arr[0] * 255);
            int g     = (int)(arr[1] * 255);
            int b     = (int)(arr[2] * 255);
            int color = (alpha<<24)|(r<<16)|(g<<8)|b;

            int thickness = 1;
            // top
            graphics.fill(hx, hy, hx + cellW,           hy + thickness,       color);
            // bottom
            graphics.fill(hx, hy + cellH - thickness,   hx + cellW,           hy + cellH,           color);
            // left
            graphics.fill(hx, hy,                       hx + thickness,       hy + cellH,           color);
            // right
            graphics.fill(hx + cellW - thickness, hy,  hx + cellW,           hy + cellH,           color);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        if (dragging && !draggedStack.isEmpty()) {
            graphics.renderItem(draggedStack, mouseX, mouseY);
            graphics.renderItemDecorations(this.font, draggedStack, mouseX, mouseY);
        }
    }


    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int[] coords = getSlotCoords(mouseX, mouseY);
        if (coords == null) return super.mouseClicked(mouseX, mouseY, button);
        sourcePage    = HotbarManager.getPage();
        sourceRow     = coords[0];
        sourceSlotIdx = coords[1];
        sourceHotbar  = HotbarManager.getCurrentPageHotbars().get(sourceRow);
        potentialDrag = true;
        pressX = mouseX; pressY = mouseY;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (potentialDrag && !dragging) {
            double dx = mouseX - pressX;
            double dy = mouseY - pressY;
            if (Math.hypot(dx, dy) >= DRAG_THRESHOLD) {
                dragging = true;
                draggedStack = sourceHotbar.getSlot(sourceSlotIdx).copy();
                sourceHotbar.setSlot(sourceSlotIdx, ItemStack.EMPTY);
            }
        }
        return dragging || super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            int[] coords = getSlotCoords(mouseX, mouseY);
            int dropPage = HotbarManager.getPage();
            // Dropped onto original slot+page
            if (coords != null && dropPage == sourcePage
                    && coords[0] == sourceRow && coords[1] == sourceSlotIdx) {
                sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            }
            // Swap into another slot/page
            else if (coords != null) {
                Hotbar target = HotbarManager.getCurrentPageHotbars().get(coords[0]);
                ItemStack existing = target.getSlot(coords[1]);
                target.setSlot(coords[1], draggedStack);
                sourceHotbar.setSlot(sourceSlotIdx, existing);
            }
            // Dropped outside
            else {
                sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            }

            HotbarManager.saveHotbars();
            HotbarManager.syncToGame();
            dragging = false;
            potentialDrag = false;
            draggedStack = ItemStack.EMPTY;
            return true;
        }
        potentialDrag = false;
        return handleClick(mouseX, mouseY, button);
    }

    private boolean handleClick(double mouseX, double mouseY, int button) {
        int topY = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;
        int rows = ultimatehotbars.HOTBARS_PER_PAGE;
        int rowHeight = 22;
        int totalH = rows * rowHeight;
        int startY = topY + ((bottomY - topY) - totalH) / 2;
        int midX = this.width / 2;
        int slotW = (182 - 2) / Hotbar.SLOT_COUNT;
        int totalW = slotW * Hotbar.SLOT_COUNT;
        int baseX = midX - totalW / 2;
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (mouseX >= baseX && mouseX < baseX + totalW &&
                    mouseY >= startY && mouseY < startY + totalH) {
                int row = (int)((mouseY - startY) / rowHeight);
                row = Math.min(Math.max(row, 0), rows - 1);
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(row, "mouseClick(RIGHT)");
                HotbarManager.syncToGame();
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
                return true;
            }
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (mouseX >= baseX && mouseX < baseX + totalW &&
                    mouseY >= startY && mouseY < startY + totalH) {
                int row = (int)((mouseY - startY) / rowHeight);
                int slot = (int)((mouseX - baseX) / slotW);
                row = Math.min(Math.max(row, 0), rows - 1);
                slot = Math.min(Math.max(slot, 0), Hotbar.SLOT_COUNT - 1);
                HotbarManager.setHotbar(row, "mouseClick(LEFT)");
                HotbarManager.setSlot(slot);
                HotbarManager.syncFromGame();
                Minecraft mc = Minecraft.getInstance();
                mc.player.getInventory().selected = slot;
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                HotbarManager.syncToGame();
                mc.setScreen(null);
                return true;
            }
        }
        return false;
    }

    private int[] getSlotCoords(double mouseX, double mouseY) {
        int bgWidth = 182;
        int cellH = 22;
        int border = 1;
        int cellW = (bgWidth - border * 2) / Hotbar.SLOT_COUNT;
        int topY = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;
        int totalH = ultimatehotbars.HOTBARS_PER_PAGE * cellH;
        int startY = topY + ((bottomY - topY) - totalH) / 2;
        int baseX = (this.width - bgWidth) / 2 + border;
        if (mouseX < baseX || mouseX >= baseX + cellW * Hotbar.SLOT_COUNT ||
                mouseY < startY || mouseY >= startY + totalH) {
            return null;
        }
        int row = (int)((mouseY - startY) / cellH);
        int slot = (int)((mouseX - baseX) / cellW);
        row = Math.min(Math.max(row, 0), ultimatehotbars.HOTBARS_PER_PAGE - 1);
        slot = Math.min(Math.max(slot, 0), Hotbar.SLOT_COUNT - 1);
        return new int[]{row, slot};
    }

    @Override
    public void removed() {
        // if we were mid-drag, put it back
        if (dragging) {
            sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            dragging = false;
            potentialDrag = false;
            draggedStack = ItemStack.EMPTY;
        }
        HotbarManager.saveHotbars();
        HotbarManager.syncToGame();
        super.removed();
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
