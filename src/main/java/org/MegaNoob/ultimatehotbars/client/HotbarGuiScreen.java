package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.client.KeyBindings;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class HotbarGuiScreen extends Screen {
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");

    private EditBox pageInput;
    private PageListWidget pageListWidget;

    private static final long INITIAL_DELAY_MS  = 300;
    private static final long REPEAT_INTERVAL_MS = 100;
    private final boolean[] keyHeld       = new boolean[4];
    private final long[]    keyPressStart = new long[4];
    private final long[]    lastRepeat    = new long[4];

    private static final double DRAG_THRESHOLD = 5.0;
    private boolean potentialDrag = false, dragging = false;
    private double  pressX, pressY;
    private int     sourcePage, sourceRow, sourceSlotIdx;
    private Hotbar  sourceHotbar;
    private ItemStack draggedStack = ItemStack.EMPTY;

    public HotbarGuiScreen() {
        super(Component.literal("Virtual Hotbars"));
    }

    /** Refreshes the single top-center textbox and page list to match the current page name. */
    public void updatePageInput() {
        pageInput.setResponder(null);
        pageInput.setValue(HotbarManager.getPageNames().get(HotbarManager.getPage()));
        pageInput.setResponder(this::onPageInputChanged);
        pageListWidget.updatePages();
    }

    /** @return true if the page-name text box is currently focused. */
    public boolean isTextFieldFocused() {
        return pageInput != null && pageInput.isFocused();
    }

    /**
     * @return true if the mouse is currently over the page-list widget.
     */
    public boolean isMouseOverPageList(double mouseX, double mouseY) {
        return this.pageListWidget.isMouseOver(mouseX, mouseY);
    }

    /**
     * Give access to the raw list widget dimensions, so we can
     * skip hotbar nav when hovering it.
     */
    public ObjectSelectionList<?> getPageListWidget() {
        return this.pageListWidget;
    }



    @Override
    protected void init() {
        super.init();
        int midX = this.width / 2;

        // ─── Single top-center EditBox for renaming pages ─────────
        int editW = 100;
        pageInput = new EditBox(this.font, midX - editW/2, 5, editW, 20,
                Component.literal("Page Name"));
        pageInput.setValue(HotbarManager.getPageNames().get(HotbarManager.getPage()));
        pageInput.setResponder(this::onPageInputChanged);
        addRenderableWidget(pageInput);

        // ─── Config button ───────────────────────────────────────
        addRenderableWidget(Button.builder(Component.literal("Config"),
                        btn -> this.minecraft.setScreen(new HotbarConfigScreen(this)))
                .pos(10, this.height - 30).size(80, 20).build());

        // ─── Compute hotbar & list layout ───────────────────────
        int topY    = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;
        int rows    = ultimatehotbars.HOTBARS_PER_PAGE;
        int rowH    = 22;
        int totalH  = rows * rowH;
        int startY  = topY + ((bottomY - topY) - totalH) / 2;
        int bgW     = 182;
        int baseX   = midX - bgW / 2;

        // Right-side page list
        int listX   = baseX + bgW + 10;
        int listW   = 100;
        int listTop = startY;
        int listBot = startY + totalH;

        // ─── PageListWidget ──────────────────────────────────────
        pageListWidget = new PageListWidget(
                this.minecraft, listW, this.height, listTop, listBot, 20
        );
        pageListWidget.setLeftPos(listX);
        pageListWidget.updatePages();
        addWidget(pageListWidget);

        // ─── Add / Remove Page buttons ───────────────────────────
        int btnY      = startY + totalH + 6;
        int btnW      = 80, btnH = 20, gap = 10;
        int totalBtnW = btnW * 2 + gap;
        int btnX      = midX - totalBtnW/2;
        addRenderableWidget(Button.builder(Component.literal("+ Page"),
                        b -> { HotbarManager.addPage(); updatePageInput(); })
                .pos(btnX, btnY).size(btnW, btnH).build());
        addRenderableWidget(Button.builder(Component.literal("- Page"),
                        b -> { HotbarManager.removePage(HotbarManager.getPage()); updatePageInput(); })
                .pos(btnX+btnW+gap, btnY).size(btnW, btnH).build());
    }

    /** Handler for when the top EditBox changes: renames the current page. */
    private void onPageInputChanged(String newName) {
        HotbarManager.renamePage(HotbarManager.getPage(), newName);
        pageListWidget.updatePages();
    }

    @Override
    public void tick() {
        super.tick();
        // 1) If the page-name box is focused, skip all nav
        if (pageInput.isFocused()) {
            return;
        }

        // 2) If the mouse is over the page-list widget, skip the built-in arrow hotbar nav
        double rawX = this.minecraft.mouseHandler.xpos();
        double rawY = this.minecraft.mouseHandler.ypos();
        int mx = (int)(rawX * this.width  / this.minecraft.getWindow().getScreenWidth());
        int my = (int)(rawY * this.height / this.minecraft.getWindow().getScreenHeight());
        if (isMouseOverPageList(mx, my)) {
            return;
        }

        // 3) Otherwise, do your existing arrow-key handling exactly as before
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
                    long held = now - keyPressStart[i];
                    if (held >= INITIAL_DELAY_MS &&
                            now - lastRepeat[i] >= REPEAT_INTERVAL_MS) {
                        lastRepeat[i] = now;
                        handleArrowKey(i);
                    }
                }
            } else {
                keyHeld[i] = false;
            }
        }
    }


    private void handleArrowKey(int idx) {
        if (dragging) {
            int cp = HotbarManager.getPage();
            HotbarManager.setPage(cp + (idx == 0 || idx == 2 ? -1 : +1));
            updatePageInput();
            return;
        }
        switch (idx) {
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
            case 2 -> HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "arrowUp");
            case 3 -> HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "arrowDown");
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (pageInput.isFocused()) {
            // Let EditBox handle backspace, typing, etc.
            if (pageInput.keyPressed(keyCode, scanCode, modifiers)) return true;
            // Allow Escape to close while typing
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) return super.keyPressed(keyCode, scanCode, modifiers);
            // Swallow everything else
            return true;
        }
        // Swallow raw arrows so focus doesn’t jump
        if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT ||
                keyCode == GLFW.GLFW_KEY_UP   || keyCode == GLFW.GLFW_KEY_DOWN) {
            return true;
        }
        // Delete clears current hotbar
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            HotbarManager.getCurrentHotbar().clear();
            HotbarManager.syncToGame();
            Minecraft.getInstance().player.playSound(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (pageListWidget.mouseScrolled(mx, my, delta)) return true;
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // 0) Intercept left-clicks in the page list rectangle
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            int listX  = pageListWidget.getLeft();
            int listY  = pageListWidget.getTop();
            int listW  = pageListWidget.getWidth();
            int listH  = pageListWidget.getHeight();
            int entryH = 20; // same as the itemHeight used above
            if (mx >= listX && mx < listX + listW &&
                    my >= listY && my < listY + listH) {
                int relY = (int)((my - listY) + pageListWidget.getScrollAmount());
                int clickedIndex = relY / entryH;
                if (clickedIndex >= 0 && clickedIndex < HotbarManager.getPageCount()) {
                    HotbarManager.syncFromGame();
                    HotbarManager.setPage(clickedIndex);
                    updatePageInput();
                    return true;
                }
            }
        }

        // 1) Then let the list widget handle its own scrolling if needed
        if (pageListWidget.mouseClicked(mx, my, btn)) {
            return true;
        }

        // 2) Otherwise, fall back to drag/drop & hotbar logic
        int[] coords = getSlotCoords(mx, my);
        if (coords == null) return super.mouseClicked(mx, my, btn);

        sourcePage    = HotbarManager.getPage();
        sourceRow     = coords[0];
        sourceSlotIdx = coords[1];
        sourceHotbar  = HotbarManager.getCurrentPageHotbars().get(sourceRow);
        potentialDrag = true;
        pressX = mx; pressY = my;
        Minecraft.getInstance().player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 1.0F);
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (potentialDrag && !dragging) {
            if (Math.hypot(mx - pressX, my - pressY) >= DRAG_THRESHOLD) {
                dragging = true;
                draggedStack = sourceHotbar.getSlot(sourceSlotIdx).copy();
                sourceHotbar.setSlot(sourceSlotIdx, ItemStack.EMPTY);
            }
        }
        return dragging || super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (dragging) {
            int rows = ultimatehotbars.HOTBARS_PER_PAGE, rowH = 22, totalH = rows * rowH;
            int delX = 10, delY = (this.height - 30) - totalH - 5;
            // Delete-zone
            if (mx >= delX && mx < delX + 50 && my >= delY && my < delY + totalH) {
                Minecraft.getInstance().player.playSound(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
                dragging = false; potentialDrag = false; draggedStack = ItemStack.EMPTY;
                HotbarManager.saveHotbars();
                return true;
            }
            // Swap/drop
            int[] coords = getSlotCoords(mx, my);
            int dropPg = HotbarManager.getPage();
            if (coords != null && dropPg == sourcePage
                    && coords[0] == sourceRow && coords[1] == sourceSlotIdx) {
                sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            } else if (coords != null) {
                Hotbar target = HotbarManager.getCurrentPageHotbars().get(coords[0]);
                ItemStack existing = target.getSlot(coords[1]);
                target.setSlot(coords[1], draggedStack);
                sourceHotbar.setSlot(sourceSlotIdx, existing);
            } else {
                sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            }
            HotbarManager.saveHotbars();
            HotbarManager.syncToGame();
            Minecraft.getInstance().player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 0.8F);
            dragging = false; potentialDrag = false; draggedStack = ItemStack.EMPTY;
            return true;
        }
        potentialDrag = false;
        return handleClick(mx, my, btn);
    }

    private boolean handleClick(double mx, double my, int btn) {
        int topY    = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;
        int rows    = ultimatehotbars.HOTBARS_PER_PAGE;
        int h       = 22;
        int totalH  = rows * h;
        int startY  = topY + ((bottomY - topY) - totalH) / 2;
        int slotW   = (182 - 2) / Hotbar.SLOT_COUNT;
        int totalW  = slotW * Hotbar.SLOT_COUNT;
        int baseX   = (this.width/2) - totalW/2;

        // Right-click to open inventory
        if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && mx >= baseX && mx < baseX + totalW
                && my >= startY && my < startY + totalH) {
            int row = Mth.clamp((int)((my - startY)/h), 0, rows-1);
            HotbarManager.syncFromGame();
            HotbarManager.setHotbar(row, "mouseClick(RIGHT)");
            HotbarManager.syncToGame();
            Minecraft.getInstance().setScreen(
                    new InventoryScreen(Minecraft.getInstance().player)
            );
            return true;
        }
        // Left-click to swap/place
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mx >= baseX && mx < baseX + totalW
                && my >= startY && my < startY + totalH) {
            int row = Mth.clamp((int)((my - startY)/h), 0, rows-1);
            int slot = Mth.clamp((int)((mx - baseX)/slotW), 0, Hotbar.SLOT_COUNT-1);
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

        return false;
    }

    private int[] getSlotCoords(double mx, double my) {
        int bgW    = 182;
        int cellH  = 22;
        int border = 1;
        int cellW  = (bgW - border*2)/Hotbar.SLOT_COUNT;
        int topY   = pageInput.getY() + pageInput.getHeight() + 6;
        int botY   = this.height - 30;
        int totalH = ultimatehotbars.HOTBARS_PER_PAGE * cellH;
        int startY = topY + ((botY - topY) - totalH)/2;
        int baseX  = (this.width - bgW)/2 + border;

        if (mx < baseX || mx >= baseX + cellW*Hotbar.SLOT_COUNT
                || my < startY || my >= startY + totalH) {
            return null;
        }
        int row  = Mth.clamp((int)((my - startY)/cellH), 0, ultimatehotbars.HOTBARS_PER_PAGE - 1);
        int slot = Mth.clamp((int)((mx - baseX)/cellW),    0, Hotbar.SLOT_COUNT - 1);
        return new int[]{ row, slot };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        // 1) background + rename textbox + list
        this.renderBackground(graphics);
        pageInput.render(graphics, mouseX, mouseY, partialTicks);
        pageListWidget.render(graphics, mouseX, mouseY, partialTicks);

        // 2) draw hotbars + delete zone + hover + drag preview
        int topY     = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY  = this.height - 30;
        int rows     = ultimatehotbars.HOTBARS_PER_PAGE;
        int rowH     = 22;
        int totalH   = rows * rowH;
        int startY   = topY + ((bottomY - topY) - totalH) / 2;
        int midX     = this.width / 2;
        int bgW      = 182, bgH = 22, border = 1;
        int cellW    = (bgW - border*2) / Hotbar.SLOT_COUNT;
        int cellH    = bgH;
        int baseX    = midX - bgW/2 + border;
        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int selHb    = HotbarManager.getHotbar();
        long now2    = System.currentTimeMillis();
        float t2     = (now2 % 1000L) / 1000f;

        // Each row
        for (int row = 0; row < pageHotbars.size(); row++) {
            int y = startY + row*rowH;
            Hotbar hb = pageHotbars.get(row);

            RenderSystem.setShaderTexture(0, HOTBAR_TEX);
            graphics.blit(HOTBAR_TEX, baseX-border, y-3, 0, 0, bgW, bgH);

            // highlight selected hotbar
            if (row == selHb) {
                float[] c = Config.highlightColor();
                int color = ((int)(c[3]*255)<<24)
                        | ((int)(c[0]*255)<<16)
                        | ((int)(c[1]*255)<< 8)
                        |  (int)(c[2]*255);
                graphics.fill(
                        baseX-border, y-3,
                        baseX-border+bgW, y-3+bgH,
                        color
                );
            }

            // row label
            String lbl = String.valueOf(row+1);
            graphics.drawString(
                    this.font, lbl,
                    baseX-border-3-this.font.width(lbl),
                    y + (rowH - this.font.lineHeight)/2,
                    0xFFFFFF
            );

            // items
            int yOff = y-3;
            for (int s = 0; s < Hotbar.SLOT_COUNT; s++) {
                ItemStack stack = hb.getSlot(s);
                int ix = baseX + s*cellW + (cellW-16)/2;
                int iy = yOff + (cellH-16)/2;
                graphics.renderItem(stack, ix, iy);
                graphics.renderItemDecorations(this.font, stack, ix, iy);
            }
        }

        // Delete-zone
        int delW=50, delH=totalH, delX=10, delY=(this.height-30)-delH-5;
        float pulse2 = (float)(Math.sin(2*Math.PI*t2)*0.5 + 0.5f);
        int alpha2 = (int)(pulse2 * 255), red = (alpha2<<24)|(0xFF<<16);
        graphics.fill(delX, delY, delX+delW, delY+delH, 0x88000000);
        for (int i = 0; i < 2; i++) {
            graphics.fill(delX+i, delY+i, delX+delW-i,   delY+i+1,      red);
            graphics.fill(delX+i, delY+delH-i-1, delX+delW-i, delY+delH-i, red);
            graphics.fill(delX+i, delY+i, delX+i+1, delY+delH-i, red);
            graphics.fill(delX+delW-i-1, delY+i, delX+delW-i, delY+delH-i, red);
        }
        graphics.drawCenteredString(
                this.font, "Delete",
                delX+delW/2,
                delY + (delH - this.font.lineHeight)/2,
                0xFFFFFFFF
        );

        // Hover-slot border
        int[] hov = getSlotCoords(mouseX, mouseY);
        if (hov != null) {
            int hr = hov[0], hs = hov[1];
            int hy = startY + hr*rowH - 3;
            int hx = baseX + hs*cellW;
            float[] arr = Config.hoverBorderColor();
            float pulse3 = (float)(Math.sin(2*Math.PI*t2)*0.5 + 0.5f);
            int alpha3 = (int)(arr[3]*pulse3*255),
                    cr = (int)(arr[0]*255),
                    cg = (int)(arr[1]*255),
                    cb = (int)(arr[2]*255);
            int col3 = (alpha3<<24)|(cr<<16)|(cg<<8)|cb, t=1;
            graphics.fill(hx,             hy,            hx+cellW,      hy+t,      col3);
            graphics.fill(hx,             hy+cellH-t,    hx+cellW,      hy+cellH,  col3);
            graphics.fill(hx,             hy,            hx+t,          hy+cellH,  col3);
            graphics.fill(hx+cellW-t,     hy,            hx+cellW,      hy+cellH,  col3);
        }

        // Finally draw children (buttons/list) and dragged item
        super.render(graphics, mouseX, mouseY, partialTicks);
        if (dragging && !draggedStack.isEmpty()) {
            graphics.renderItem(draggedStack, mouseX, mouseY);
            graphics.renderItemDecorations(this.font, draggedStack, mouseX, mouseY);
        }
    }

    @Override
    public void removed() {
        if (dragging) {
            sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            dragging = false; potentialDrag = false; draggedStack = ItemStack.EMPTY;
        }
        HotbarManager.saveHotbars();
        HotbarManager.syncToGame();
        super.removed();
    }

    @Override public boolean shouldCloseOnEsc() { return true; }
    @Override public boolean isPauseScreen()    { return false; }

    /**
     * Scrollable list of pages on the right side of the GUI.
     */
    private class PageListWidget extends ObjectSelectionList<PageListWidget.Entry> {
        public PageListWidget(Minecraft mc, int w, int h, int top, int bottom, int itemH) {
            super(mc, w, h, top, bottom, itemH);
        }

        protected int getScrollbarPositionX() {
            return this.getRowLeft() + getRowWidth() - 6;
        }

        public int getRowWidth() {
            return this.width;
        }

        public void updatePages() {
            this.clearEntries();
            var names = HotbarManager.getPageNames();
            for (int i = 0; i < names.size(); i++) {
                this.addEntry(new Entry(names.get(i), i));
            }
            int cur = HotbarManager.getPage();
            this.setSelected(this.children().get(cur));
            this.ensureVisible(this.children().get(cur));
        }

        private class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String name;
            private final int index;

            Entry(String name, int index) {
                this.name  = name;
                this.index = index;
            }

            @Override
            public void render(GuiGraphics g, int entryIdx, int y, int x, int listW, int entryH,
                               int mouseX, int mouseY, boolean isSelected, float pt) {
                int color = isSelected ? 0xFFFFFFFF : 0xFFAAAAAA;
                if (!isSelected
                        && mouseX >= x && mouseX < x + listW
                        && mouseY >= y && mouseY < y + entryH) {
                    float[] c = Config.highlightColor();
                    color = ((int)(c[3]*255)<<24)
                            | ((int)(c[0]*255)<<16)
                            | ((int)(c[1]*255)<<8)
                            |  (int)(c[2]*255);
                }
                g.drawString(font, name, x + 2, y + (entryH - font.lineHeight)/2, color, false);
            }

            @Override
            public boolean mouseClicked(double mx, double my, int btn) {
                // no longer needed
                return super.mouseClicked(mx, my, btn);
            }

            public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput ne) {}
            public net.minecraft.network.chat.Component getNarration() {
                return Component.literal(name);
            }
        }
    }
}
