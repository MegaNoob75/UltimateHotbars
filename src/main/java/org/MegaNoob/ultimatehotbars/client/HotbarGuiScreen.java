package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.glfw.GLFW;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.util.List;

public class HotbarGuiScreen extends Screen {
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");
    private static final Logger LOGGER = LogManager.getLogger();
    private EditBox pageInput;
    private PageListWidget pageListWidget;
    private long lastArrowKeyTime = 0;
    private boolean suppressNextHotbarScroll = false;
    // These store calculated positions from init(), used by render()
    private int _renderBtnX, _renderDelX, _renderLabelX, _renderBaseX, _renderListX, _renderBtnW;
    private int hotbarScrollRow = 0;

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

    private double lastMouseX, lastMouseY;

    public HotbarGuiScreen() {
        super(Component.literal("Virtual Hotbars"));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (isTextFieldFocused()) return true;

        Minecraft mc = Minecraft.getInstance();
        // Allow switching to inventory
        if (keyCode == mc.options.keyInventory.getKey().getValue()) {
            mc.setScreen(new InventoryScreen(mc.player));
            return true;
        }

        // Allow closing the GUI with your custom key
        if (KeyBindings.OPEN_GUI.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
            mc.setScreen(null);
            return true;
        }

        // ---- INSERT YOUR HOTBAR SCROLL LOGIC HERE ----
        if (keyCode == GLFW_KEY_UP) {
            moveHotbarSelection(-1);
            return true;
        }
        if (keyCode == GLFW_KEY_DOWN) {
            moveHotbarSelection(1);
            return true;
        }

        // (Keep your arrow/page key block if needed)
        // if (keyCode == GLFW_KEY_LEFT || keyCode == GLFW_KEY_RIGHT) ...

        return super.keyPressed(keyCode, scanCode, modifiers);
    }




    @Override
    public void mouseMoved(double mx, double my) {
        this.lastMouseX = mx;
        this.lastMouseY = my;
        super.mouseMoved(mx, my);
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
        this.clearWidgets();

        // --- Main dimensions and minimums ---
        int editW = 100;
        int bgW = 182;
        int delW = 50;
        int labelW = 20;      // space for row numbers
        int btnW = 80, btnH = 20, btnGap = 5;
        int pagelistW = 100;
        int minGap = 2;
        int minListW = 40;    // don't let page list shrink below this
        int minBtnW = 40;     // don't let buttons shrink below this
        int leftMargin = 8;

        List<Hotbar> currentHotbars = HotbarManager.getCurrentPageHotbars();
        int rows = Math.max(1, currentHotbars.size());
        int rowH = 22;

        // --- Compute actual available width for gaps and widgets ---
        int usedW = btnW + delW + labelW + bgW + pagelistW;
        int gapBudget = this.width - usedW - 2 * leftMargin;

        // If not enough space, start shrinking page list and button column width, then reduce all gaps
        int gap = 12; // start with generous spacing
        int btnWAdj = btnW, pagelistWAdj = pagelistW;
        if (gapBudget < 4 * gap) {
            // Not enough space for gaps, start shrinking widgets
            int extraNeeded = 4 * gap - gapBudget;
            // shrink page list and button column
            int shrinkList = Math.min(extraNeeded / 2, pagelistW - minListW);
            pagelistWAdj -= shrinkList;
            extraNeeded -= shrinkList;
            int shrinkBtn = Math.min(extraNeeded, btnW - minBtnW);
            btnWAdj -= shrinkBtn;
            extraNeeded -= shrinkBtn;
            // final gap (all remaining budget divided up)
            gap = Math.max(minGap, gapBudget / 4);
        }

        // --- Calculate all X positions from left to right ---
        int btnX = leftMargin;
        int delX = btnX + btnWAdj + gap;
        int labelX = delX + delW + gap;
        int baseX = labelX + labelW;
        int listX = baseX + bgW + gap;

        // --- Top Y coordinate for everything ---
        int topY = 5 + 20 + 10; // under page name

        // --- Page name textbox (centered if possible) ---
        int pageInputX = Math.max((this.width - editW) / 2, btnX + btnWAdj + gap + delW + gap + labelW + (bgW - editW) / 2);
        pageInput = new EditBox(this.font, pageInputX, 5, editW, 20, Component.literal("Page Name"));
        pageInput.setValue(HotbarManager.getPageNames().get(HotbarManager.getPage()));
        pageInput.setResponder(this::onPageInputChanged);
        addRenderableWidget(pageInput);

        // --- BUTTONS ---
        String[] labels = {"+ Hot Bar", "- Hot Bar", "+ Page", "- Page"};
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addRenderableWidget(Button.builder(
                    Component.literal(labels[idx]),
                    b -> {
                        if (idx == 0) HotbarManager.addHotbarToCurrentPage();
                        else if (idx == 1) HotbarManager.removeLastHotbarFromCurrentPage();
                        else if (idx == 2) HotbarManager.addPage();
                        else HotbarManager.removePage(HotbarManager.getPage());
                        this.minecraft.setScreen(new HotbarGuiScreen());
                    }
            ).pos(btnX, topY + idx * (btnH + btnGap)).size(btnWAdj, btnH).build());
        }
        // Config button last
        addRenderableWidget(Button.builder(Component.literal("Config"),
                        btn -> this.minecraft.setScreen(new HotbarConfigScreen(this)))
                .pos(btnX, topY + 4 * (btnH + btnGap) + 10).size(btnWAdj, btnH).build());

        // --- PAGE LIST ---
        int listTop = pageInput.getY() + pageInput.getHeight() + 6;
        int listBot = this.height - 40;
        pageListWidget = new PageListWidget(this, this.minecraft, this.font, pagelistWAdj, this.height, listTop, listBot, 20);
        pageListWidget.setLeftPos(listX);
        addWidget(pageListWidget);
        pageListWidget.updatePages();

        // --- Store X positions for render() ---
        this._renderBtnX = btnX;
        this._renderDelX = delX;
        this._renderLabelX = labelX;
        this._renderBaseX = baseX;
        this._renderListX = listX;
        this._renderBtnW = btnWAdj;
    }




    private void onPageInputChanged(String newName) {
        HotbarManager.renamePage(HotbarManager.getPage(), newName);
        pageListWidget.updatePages();
    }

    @Override
    public void tick() {
        super.tick();

    }


    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // First, handle scrolling the page list if mouse is over it
        if (this.pageListWidget.isMouseOver(mx, my)) {
            int curr = HotbarManager.getPage();
            int cnt = HotbarManager.getPageCount();
            int dir = delta > 0 ? -1 : 1;
            int wrapped = ((curr + dir) % cnt + cnt) % cnt;

            HotbarManager.setPage(wrapped, 0);  // handles both page and hotbar now
            updatePageInput();                  // updates text input + widget
            HotbarManager.syncToGame();

            if (Config.enableSounds() && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            return true;
        }

        // Else: scroll hotbars just like arrow keys, and always wrap and scroll to show
        moveHotbarSelection(delta > 0 ? -1 : 1);
        return true;
    }


    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (pageInput.isMouseOver(mx, my)) {
            pageInput.setFocused(true);
        } else {
            pageInput.setFocused(false);
        }
        return super.mouseClicked(mx, my, btn);
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
            // Recompute layout to match init()/render()
            int topY    = pageInput.getY() + pageInput.getHeight() + 6;
            int bottomY = this.height - 30;
            List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
            int rows    = pageHotbars.size();
            int rowH    = 22;
            int totalH  = rows * rowH;
            int startY  = topY + ((bottomY - topY) - totalH) / 2;
            int midX    = this.width / 2;
            int bgW     = 182;
            int border  = 1;
            int baseX   = midX - bgW/2 + border;

            // Delete-zone coordinates (50px wide, as drawn)
            int delW = 50;
            int delX = baseX - delW - 15;  // ← 15px gap to the left of hotbar numbers
            int delY = startY - 3;

            // 1) Delete drop
            if (mx >= delX && mx < delX + delW
                    && my >= delY && my < delY + totalH) {
                Minecraft.getInstance()
                        .player.playSound(SoundEvents.ITEM_BREAK, 1.0F, 1.0F);
                dragging      = false;
                potentialDrag = false;
                draggedStack  = ItemStack.EMPTY;
                HotbarManager.saveHotbars();
                return true;
            }

            // 2) Swap/drop into hotbar slots
            int[] coords = getSlotCoords(mx, my);
            int dropPg = HotbarManager.getPage();
            if (coords != null && dropPg == sourcePage
                    && coords[0] == sourceRow && coords[1] == sourceSlotIdx) {
                sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            } else if (coords != null) {
                Hotbar target   = HotbarManager.getCurrentPageHotbars().get(coords[0]);
                ItemStack exist = target.getSlot(coords[1]);
                target.setSlot(coords[1], draggedStack);
                sourceHotbar.setSlot(sourceSlotIdx, exist);
            } else {
                sourceHotbar.setSlot(sourceSlotIdx, draggedStack);
            }

            HotbarManager.saveHotbars();
            HotbarManager.syncToGame();
            Minecraft.getInstance()
                    .player.playSound(SoundEvents.ITEM_PICKUP, 1.0F, 0.8F);
            dragging      = false;
            potentialDrag = false;
            draggedStack  = ItemStack.EMPTY;
            return true;
        }

        // 3) No drag in progress → fall back to clicks
        potentialDrag = false;
        return handleClick(mx, my, btn);
    }


    // ─── Your existing handleClick(...) unchanged ─────────────────────────
    private boolean handleClick(double mx, double my, int btn) {
        int topY    = pageInput.getY() + pageInput.getHeight() + 6;
        int bottomY = this.height - 30;

        // dynamically get how many hotbar-rows we really have
        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int rows    = pageHotbars.size();
        int h       = 22;
        int totalH  = rows * h;
        int startY  = topY + ((bottomY - topY) - totalH) / 2;

        int slotW   = (182 - 2) / Hotbar.SLOT_COUNT;
        int totalW  = slotW * Hotbar.SLOT_COUNT;
        int baseX   = (this.width / 2) - totalW / 2;

        // Right-click to open that row’s inventory
        if (btn == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                && mx >= baseX && mx < baseX + totalW
                && my >= startY && my < startY + totalH) {
            int row = Mth.clamp((int)((my - startY) / h), 0, rows - 1);
            HotbarManager.syncFromGame();
            HotbarManager.setHotbar(row, "mouseClick(RIGHT)");
            HotbarManager.syncToGame();
            Minecraft.getInstance().setScreen(
                    new InventoryScreen(Minecraft.getInstance().player)
            );
            return true;
        }

        // Left-click to switch hotbar & slot
        if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && mx >= baseX && mx < baseX + totalW
                && my >= startY && my < startY + totalH) {
            int row  = Mth.clamp((int)((my - startY) / h), 0, rows - 1);
            int slot = Mth.clamp((int)((mx - baseX)  / slotW), 0, Hotbar.SLOT_COUNT - 1);
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

    // ─── Your existing getSlotCoords(...) unchanged ───────────────────
    private int[] getSlotCoords(double mx, double my) {
        int bgW    = 182;
        int cellH  = 22;
        int border = 1;
        int cellW  = (bgW - border*2) / Hotbar.SLOT_COUNT;
        int topY   = pageInput.getY() + pageInput.getHeight() + 6;
        int botY   = this.height - 30;

        // dynamically compute height for however many hotbars you really have
        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int rows   = pageHotbars.size();
        int totalH = rows * cellH;
        int startY = topY + ((botY - topY) - totalH) / 2;
        int baseX  = (this.width - bgW) / 2 + border;

        // if outside the full grid, bail out
        if (mx < baseX || mx >= baseX + cellW * Hotbar.SLOT_COUNT
                || my < startY || my >= startY + totalH) {
            return null;
        }

        int row  = (int)((my - startY) / cellH);
        int slot = (int)((mx - baseX) / cellW);

        // double-check bounds
        if (row  < 0 || row  >= rows
                || slot < 0 || slot >= Hotbar.SLOT_COUNT) {
            return null;
        }

        return new int[]{ row, slot };
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(graphics);
        pageInput.render(graphics, mouseX, mouseY, partialTicks);
        pageListWidget.render(graphics, mouseX, mouseY, partialTicks);

        int bgW = 182, bgH = 22, border = 1;
        int labelW = 20;
        int rowH = 22;
        int cellW = (bgW - border*2) / Hotbar.SLOT_COUNT;
        int cellH = bgH;
        int selHb = HotbarManager.getHotbar();
        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        long now2 = System.currentTimeMillis();
        float t2 = (now2 % 1000L) / 1000f;

        int baseX = this._renderBaseX;
        int topY = pageInput.getY() + pageInput.getHeight() + 10;

        // SCROLLING: how many hotbars fit on screen?
        int availableHeight = this.height - topY - 30; // 30px for bottom margin
        int visibleRows = Math.max(1, availableHeight / rowH);
        int totalRows = pageHotbars.size();

        // Clamp scroll row if needed
        if (hotbarScrollRow > totalRows - visibleRows) hotbarScrollRow = Math.max(0, totalRows - visibleRows);
        if (selHb < hotbarScrollRow) HotbarManager.setHotbar(hotbarScrollRow, "scroll");
        else if (selHb > hotbarScrollRow + visibleRows - 1) HotbarManager.setHotbar(hotbarScrollRow + visibleRows - 1, "scroll");

        // Only draw the visible rows
        for (int vis = 0; vis < visibleRows && (hotbarScrollRow + vis) < totalRows; vis++) {
            int row = hotbarScrollRow + vis;
            int y = topY + vis * rowH;
            Hotbar hb = pageHotbars.get(row);

            RenderSystem.setShaderTexture(0, HOTBAR_TEX);
            graphics.blit(HOTBAR_TEX, baseX - border, y - 3, 0, 0, bgW, bgH);

            // highlight selected hotbar
            if (row == selHb) {
                float[] c = Config.highlightColor();
                int color = ((int)(c[3]*255)<<24)
                        | ((int)(c[0]*255)<<16)
                        | ((int)(c[1]*255)<<8)
                        |  (int)(c[2]*255);
                graphics.fill(
                        baseX - border, y - 3,
                        baseX - border + bgW, y - 3 + bgH,
                        color
                );
            }

            // row label (never overlapped)
            String lbl = String.valueOf(row + 1);
            graphics.drawString(
                    this.font, lbl,
                    this._renderLabelX + (labelW - this.font.width(lbl)) / 2,
                    y + (rowH - this.font.lineHeight)/2,
                    0xFFFFFF
            );

            // items
            int yOff = y - 3;
            for (int s = 0; s < Hotbar.SLOT_COUNT; s++) {
                ItemStack stack = hb.getSlot(s);
                int ix = baseX + s * cellW + (cellW - 16)/2;
                int iy = yOff + (cellH - 16)/2;
                graphics.renderItem(stack, ix, iy);
                graphics.renderItemDecorations(this.font, stack, ix, iy);
            }
        }

        // Delete-zone (never overlaps labels or hotbars)
        int delW = 50, delH = Math.max(rowH, Math.min(pageHotbars.size(), visibleRows) * rowH);
        int delX = this._renderDelX, delY = topY - 3;
        float pulse2 = (float)(Math.sin(2 * Math.PI * t2) * 0.5 + 0.5);
        int alpha2 = (int)(pulse2 * 255), red = (alpha2<<24) | (0xFF<<16);
        graphics.fill(delX, delY, delX + delW, delY + delH, 0x88000000);
        for (int i = 0; i < 2; i++) {
            graphics.fill(delX + i, delY + i, delX + delW - i, delY + i + 1, red);
            graphics.fill(delX + i, delY + delH - i - 1, delX + delW - i, delY + delH - i, red);
            graphics.fill(delX + i, delY + i, delX + i + 1, delY + delH - i, red);
            graphics.fill(delX + delW - i - 1, delY + i, delX + delW - i, delY + delH - i, red);
        }
        graphics.drawCenteredString(
                this.font, "Delete",
                delX + delW/2,
                delY + (delH - this.font.lineHeight)/2,
                0xFFFFFFFF
        );

        // Hover-slot border
        int[] hov = getSlotCoords(mouseX, mouseY);
        if (hov != null) {
            int hr = hov[0], hs = hov[1];
            if (hr >= hotbarScrollRow && hr < hotbarScrollRow + visibleRows) {
                int hy = topY + (hr - hotbarScrollRow) * rowH - 3;
                int hx = baseX + hs * cellW;
                float[] arr = Config.hoverBorderColor();
                float pulse3 = (float)(Math.sin(2 * Math.PI * t2) * 0.5 + 0.5f);
                int alpha3 = (int)(arr[3] * pulse3 * 255),
                        cr = (int)(arr[0] * 255),
                        cg = (int)(arr[1] * 255),
                        cb = (int)(arr[2] * 255);
                int col3 = (alpha3<<24) | (cr<<16) | (cg<<8) | cb, t = 1;
                graphics.fill(hx,           hy,          hx + cellW,      hy + t,     col3);
                graphics.fill(hx,           hy + cellH - t, hx + cellW,      hy + cellH, col3);
                graphics.fill(hx,           hy,          hx + t,          hy + cellH, col3);
                graphics.fill(hx + cellW - t, hy,         hx + cellW,      hy + cellH, col3);
            }
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
        if (dragging && !draggedStack.isEmpty()) {
            graphics.renderItem(draggedStack, mouseX, mouseY);
            graphics.renderItemDecorations(this.font, draggedStack, mouseX, mouseY);
        }
    }

    /**
     * Moves the selected hotbar up or down, wrapping at the ends, and
     * scrolls the view so the selected hotbar is always visible.
     * @param direction -1 for up, +1 for down
     */
    private void moveHotbarSelection(int direction) {

        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int totalRows = pageHotbars.size();
        if (totalRows == 0) return; // Nothing to select!

        int bgW = 182, rowH = 22;
        int topY = pageInput.getY() + pageInput.getHeight() + 10;
        int availableHeight = this.height - topY - 30; // Space for rows
        int visibleRows = Math.max(1, availableHeight / rowH);

        int selHb = HotbarManager.getHotbar();

        // Wrap the selected index so -1 goes to last, +1 past last goes to 0
        int newSel = (selHb + direction + totalRows) % totalRows;
        HotbarManager.setHotbar(newSel, "arrow");

        // Scroll so selected row is always visible
        if (newSel < hotbarScrollRow) {
            hotbarScrollRow = newSel;
        } else if (newSel >= hotbarScrollRow + visibleRows) {
            hotbarScrollRow = newSel - visibleRows + 1;
        }

        // Clamp scroll offset to valid range (never negative, never empty space at bottom)
        hotbarScrollRow = Math.max(0, Math.min(hotbarScrollRow, totalRows - visibleRows));
        if (Config.enableSounds() && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.4f);
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


}

