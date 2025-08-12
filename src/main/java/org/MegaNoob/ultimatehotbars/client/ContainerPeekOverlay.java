package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Always-on Peek overlay on the LEFT side of any container/creative screen.
 * - Auto-scales to fit the gap between screen-left and the container GUI left edge.
 * - Drag/drop parity with the Peek window, plus accepts drops from containers/creative and JEI.
 * - Drop targets:
 *     * Slot grid: put/swap exact slot.
 *     * Number column: put/swap into that hotbar at same slot index (external uses currently-selected slot).
 *     * Page list: switch to that page and put/swap into currently-selected row+slot on that page.
 * - Leaving the overlay and releasing reverts overlay-origin drags (unless released over DELETE).
 * - Clicking a slot (even with an item in it) activates that slot; Shift+Click forces activation even if carrying.
 */
@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContainerPeekOverlay {

    private ContainerPeekOverlay() {}

    // === Visual constants (match Peek look) ===
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");
    private static final int CELL          = 20;         // logical cell (unscaled)
    private static final int BORDER        = 1;
    private static final int ROW_H         = CELL + 2;   // vanilla hotbar strip height
    private static final int DELETE_BOX_W  = 20;
    private static final int NUM_COL_W     = 20;
    private static final int GAP           = 4;
    private static final int LIST_W        = 100;

    // Placement
    private static final int LEFT_MARGIN_X     = 6;
    private static final int TOP_BOTTOM_MARGIN = 4;

    // Scroll state
    private static int peekScrollRow = 0;
    private static int pageScrollRow = 0;

    // Cached layout from last render (scaled origin & size)
    private static int lastOriginX = 0, lastOriginY = 0;
    private static int lastNaturalW = 0, lastNaturalH = 0;
    private static float lastScale = 1f;

    // ---- Drag & Drop state ----
    private static boolean dragging = false;
    private static boolean draggedBeyondThreshold = false;
    private static int pressLocalX = 0, pressLocalY = 0;
    private static int lastLocalX = 0, lastLocalY = 0;

    private enum DragSource { NONE, OVERLAY, EXTERNAL }
    private static DragSource dragSource = DragSource.NONE;

    // Track EXACT source hotbar (by reference) and slot (works across page/scroll)
    private static Hotbar    dragSrcHotbar = null;
    private static int       dragSrcSlot   = -1;
    private static ItemStack dragStack     = ItemStack.EMPTY;

    private static final int DRAG_THRESHOLD = 3;

    // ---------- Small helpers ----------
    private static boolean pointInRectLocal(int lx, int ly, int rx, int ry, int rw, int rh) {
        return lx >= rx && ly >= ry && lx < rx + rw && ly < ry + rh;
    }
    private static int argb(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }
    private static void saveAndSync() {
        try { HotbarManager.saveHotbars(); } catch (Throwable ignored) {}
        try { HotbarManager.syncToGame();  } catch (Throwable ignored) {}
    }
    /** Clear the game's carried stack (Creative/inventory cursor). */
    private static void clearCarriedIfAny() {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.player.containerMenu != null) {
            mc.player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }
    /** Force-select a vanilla hotbar slot (0..8) on client + server so selection cannot revert. */
    private static void forceSelectSlotClientAndServer(int slot0to8) {
        var mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        mc.player.getInventory().selected = slot0to8; // client
        try {
            if (mc.player.connection != null) {
                mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot0to8)); // server
            }
        } catch (Throwable ignored) {}
    }
    /**
     * External stack from carried (inventory/creative) or optional JEI bridge.
     * If JeiBridge#getItemUnderMouse(x,y) exists, we’ll pick up JEI drags too.
     */
    private static ItemStack getExternalDragStack(double screenMouseX, double screenMouseY) {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.player.containerMenu != null) {
            var carried = mc.player.containerMenu.getCarried();
            if (carried != null && !carried.isEmpty()) return carried.copy();
        }
        try {
            // Optional: public static ItemStack JeiBridge.getItemUnderMouse(double x, double y)
            Class<?> bridge = Class.forName("org.MegaNoob.ultimatehotbars.client.JeiBridge");
            var m = bridge.getMethod("getItemUnderMouse", double.class, double.class);
            Object o = m.invoke(null, screenMouseX, screenMouseY);
            if (o instanceof ItemStack st && !st.isEmpty()) return st.copy();
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    // ---------- RENDER ----------
    @SubscribeEvent
    public static void onRenderPost(ScreenEvent.Render.Post event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int totalBars = bars.size();
        int rows = Math.min(rowsRequested, totalBars);

        // UI always reserves 5 rows for DELETE + Page List
        int rowsUI = 5;

        final int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        final int naturalW = DELETE_BOX_W + GAP + NUM_COL_W + GAP + bgW + GAP + LIST_W;
        final int naturalH = Math.max(rows, rowsUI) * ROW_H;

        int containerLeft = acs.getGuiLeft();
        int availableW = Math.max(0, containerLeft - LEFT_MARGIN_X);
        int availableH = s.height - TOP_BOTTOM_MARGIN * 2;

        float scaleX = availableW > 0 ? Math.min(1f, (float) availableW / (float) naturalW) : 1f;
        float scaleY = availableH > 0 ? Math.min(1f, (float) availableH / (float) naturalH) : 1f;
        float scale  = Math.min(scaleX, scaleY);
        if (scale <= 0.01f) return;

        int scaledH = Math.round(naturalH * scale);
        int originX = LEFT_MARGIN_X;
        int originY = Math.max(TOP_BOTTOM_MARGIN, (s.height - scaledH) / 2);

        // cache for hit-tests
        lastOriginX = originX;
        lastOriginY = originY;
        lastNaturalW = naturalW;
        lastNaturalH = naturalH;
        lastScale = scale;

        // local layout
        final int deleteX = 0;
        final int numX    = deleteX + DELETE_BOX_W + GAP;
        final int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        final int listX   = baseHbX + (bgW - BORDER) + GAP;
        final int firstY  = 0;

        int visible = rows;
        int peekMax = Math.max(0, totalBars - visible);
        peekScrollRow = Mth.clamp(peekScrollRow, 0, peekMax);

        List<String> pageNames = HotbarManager.getPageNames();
        int pageMax = Math.max(0, pageNames.size() - rowsUI);
        pageScrollRow = Mth.clamp(pageScrollRow, 0, pageMax);

        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1f);

        // Page list & DELETE (always 5 rows tall)
        int listH = rowsUI * ROW_H;
        g.fill(listX, firstY - 3, listX + LIST_W, firstY - 3 + listH, 0x88000000);

        // DELETE with pulsating border
        g.fill(deleteX, firstY - 3, deleteX + DELETE_BOX_W, firstY - 3 + listH, 0x88000000);
        int redColor = ((int)((Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5) * 255) << 24) | 0x00FF0000;
        for (int i = 0; i < 2; i++) {
            g.fill(deleteX + i, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 3 + i + 1, redColor);
            g.fill(deleteX + i, firstY - 3 + listH - i - 1, deleteX + DELETE_BOX_W - i, firstY - 3 + listH - i, redColor);
            g.fill(deleteX + i, firstY - 3 + i, deleteX + i + 1, firstY - 3 + listH - i, redColor);
            g.fill(deleteX + DELETE_BOX_W - i - 1, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 3 + listH - i, redColor);
        }
        String del = "DELETE";
        int midX = deleteX + (DELETE_BOX_W - font.width("D")) / 2;
        int startY = firstY - 3 + (listH - del.length() * font.lineHeight) / 2;
        for (int i = 0; i < del.length(); i++) {
            g.drawString(font, String.valueOf(del.charAt(i)), midX, startY + i * font.lineHeight, 0xFFFFFFFF);
        }

        // === Bottom-up placement for the hotbar grid inside the 5-row window ===
        // Bars start at the bottom of the 5-row area and stack upward.
        int barsStartY = firstY + (rowsUI - rows) * ROW_H;

        // Scroll arrows use barsStartY now
        if (peekScrollRow > 0) {
            g.drawString(font, "▲", numX + (NUM_COL_W - font.width("▲")) / 2, barsStartY - font.lineHeight - 2, 0xFFFFFF);
        }
        if (peekScrollRow + visible < totalBars) {
            g.drawString(font, "▼", numX + (NUM_COL_W - font.width("▼")) / 2, barsStartY + visible * ROW_H + 2, 0xFFFFFF);
        }

        int selectedRow  = Mth.clamp(HotbarManager.getHotbar(), 0, totalBars - 1);
        int selectedSlot = HotbarManager.getSlot();

        // Mouse in local overlay space
        int mxLocal = (int) ((event.getMouseX() - originX) / scale);
        int myLocal = (int) ((event.getMouseY() - originY) / scale);

        // Draw rows bottom-up (visually) but still iterate i=0..visible-1
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y   = barsStartY + i * ROW_H;

            // number label
            String label = String.valueOf(row + 1);
            int lx = numX + (NUM_COL_W - font.width(label)) / 2;
            int ly = y + (ROW_H - font.lineHeight) / 2;
            g.drawString(font, label, lx, ly, 0xFFFFFFFF, true);

            // row strip
            g.blit(HOTBAR_TEX, baseHbX - BORDER, y - 3, 0, 0, bgW, ROW_H);

            // full-row highlight like GUI
            if (row == selectedRow) {
                float[] c = Config.highlightColor();
                int color = ((int)(c[3]*255)<<24) | ((int)(c[0]*255)<<16) | ((int)(c[1]*255)<<8) | (int)(c[2]*255);
                g.fill(baseHbX - BORDER, y - 3, baseHbX - BORDER + bgW, y - 3 + ROW_H, color);
            }

            // slot items (hide source while dragging from overlay)
            for (int sIdx = 0; sIdx < Hotbar.SLOT_COUNT; sIdx++) {
                int x = baseHbX + sIdx * CELL + (CELL - 16) / 2;
                boolean hide = dragging && dragSource == DragSource.OVERLAY
                        && (row >= 0 && row < bars.size())
                        && bars.get(row) == dragSrcHotbar && sIdx == dragSrcSlot;
                if (!hide) {
                    ItemStack stack = bars.get(row).getSlot(sIdx);
                    if (!stack.isEmpty()) {
                        g.renderItem(stack, x, y);
                        g.renderItemDecorations(font, stack, x, y);
                    }
                }
            }

            // blinking hover border for the hovered slot
            int rowLeft = baseHbX - BORDER;
            int rowTop  = y - 3;
            if (mxLocal >= rowLeft && mxLocal < rowLeft + bgW && myLocal >= rowTop && myLocal < rowTop + ROW_H) {
                int sHover = (mxLocal - baseHbX) / CELL;
                if (sHover >= 0 && sHover < Hotbar.SLOT_COUNT) {
                    int hx = baseHbX + sHover * CELL;
                    int hy = y - 3;
                    float[] arr = Config.hoverBorderColor();
                    long now = System.currentTimeMillis();
                    float t = (now % 1000L) / 1000f;
                    float pulse = (float)(Math.sin(2 * Math.PI * t) * 0.5 + 0.5f);
                    int a  = (int)(arr[3] * pulse * 255f);
                    int rc = (int)(arr[0] * 255f), gc = (int)(arr[1] * 255f), bc = (int)(arr[2] * 255f);
                    int col = (a<<24) | (rc<<16) | (gc<<8) | bc;
                    int tpx = 1;
                    g.fill(hx,              hy,               hx + CELL,      hy + tpx,       col);
                    g.fill(hx,              hy + ROW_H - tpx, hx + CELL,      hy + ROW_H,     col);
                    g.fill(hx,              hy,               hx + tpx,       hy + ROW_H,     col);
                    g.fill(hx + CELL - tpx, hy,               hx + CELL,      hy + ROW_H,     col);
                }
            }
        }

        // Page list (5 rows tall)
        int trackX = listX + LIST_W - 6;
        g.fill(trackX, firstY - 3, trackX + 4, firstY - 3 + listH, 0x44000000);

        int selectedPage = HotbarManager.getPage();
        for (int i = 0; i < rowsUI; i++) {
            int idx = pageScrollRow + i;
            if (idx >= pageNames.size()) break;
            int y = firstY + i * ROW_H;
            if (idx == selectedPage)
                g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, 0x44FFFFFF);
            if (pointInRectLocal(mxLocal, myLocal, listX, y - 3, LIST_W - 6, ROW_H)) {
                float[] hc = Config.highlightColor();
                int col = argb(hc[0], hc[1], hc[2], hc[3]);
                g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, col);
            }
            g.drawString(font, pageNames.get(idx), listX + 2, y, 0xFFFFFF);
        }

        if (pageNames.size() > rowsUI) {
            int thumbH  = Math.max((listH * rowsUI) / pageNames.size(), 10);
            int pageMax2 = Math.max(1, pageNames.size() - rowsUI);
            int thumbY  = (firstY - 3) + (pageScrollRow * (listH - thumbH)) / pageMax2;
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xAAFFFFFF);
        }

        g.pose().popPose();

        // drag ghost
        if (dragging && dragSource == DragSource.OVERLAY && !dragStack.isEmpty()) {
            int sx = (int) event.getMouseX() - 8;
            int sy = (int) event.getMouseY() - 8;
            g.renderItem(dragStack, sx, sy);
            g.renderItemDecorations(font, dragStack, sx, sy);
        }
    }



    // ---------- PRESS ----------
    @SubscribeEvent
    public static void onMousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;

        // Only if cursor is left of the container GUI (our overlay)
        if (event.getMouseX() >= acs.getGuiLeft()) return;

        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        if (event.getButton() != 0) { // LMB only
            event.setCanceled(true);
            return;
        }

        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) { event.setCanceled(true); return; }
        int rows = Math.min(rowsRequested, bars.size());
        int rowsUI = 5;

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int deleteX = 0;
        int numX    = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX   = baseHbX + (bgW - BORDER) + GAP;
        int firstY  = 0;

        // bottom-up bars start inside the 5-row area
        int barsStartY = firstY + (rowsUI - rows) * ROW_H;
        int listH   = rowsUI * ROW_H;

        boolean inBars = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, barsStartY - 3, bgW, rows * ROW_H);
        boolean inList = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);
        boolean inNums = pointInRectLocal(mxLocal, myLocal, numX, firstY - 3, NUM_COL_W, listH);
        boolean inDel  = pointInRectLocal(mxLocal, myLocal, deleteX, firstY - 3, DELETE_BOX_W, listH);

        // press bookkeeping
        pressLocalX = mxLocal;
        pressLocalY = myLocal;
        lastLocalX  = mxLocal;
        lastLocalY  = myLocal;
        draggedBeyondThreshold = false;

        if (inList || inNums || inDel) {
            dragging = false;
            dragSource = DragSource.NONE;
            dragSrcHotbar = null;
            dragSrcSlot = -1;
            dragStack = ItemStack.EMPTY;
            event.setCanceled(true);
            return;
        }

        if (inBars) {
            int idxInWindow = Mth.clamp((myLocal - barsStartY) / ROW_H, 0, rows - 1);
            int absRow = Mth.clamp(peekScrollRow + idxInWindow, 0, bars.size() - 1);
            int relX = mxLocal - baseHbX;
            int slot = Mth.clamp(relX / CELL, 0, Hotbar.SLOT_COUNT - 1);

            ItemStack inCell = bars.get(absRow).getSlot(slot);
            if (!inCell.isEmpty()) {
                // start potential drag; actual swap only if draggedBeyondThreshold becomes true
                dragging = true;
                dragSource = DragSource.OVERLAY;
                dragSrcHotbar = bars.get(absRow);
                dragSrcSlot = slot;
                dragStack = inCell.copy();
            } else {
                dragging = false;
                dragSource = DragSource.NONE;
                dragSrcHotbar = null;
                dragSrcSlot = -1;
                dragStack = ItemStack.EMPTY;
            }
            event.setCanceled(true);
        }
    }



    // ---------- DRAG ----------
    @SubscribeEvent
    public static void onMouseDraggedPre(ScreenEvent.MouseDragged.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;

        // Require LMB held (MouseDragged.Pre doesn’t give a button)
        long win = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) return;

        // If cursor moves into the container region, cancel overlay-origin drag to avoid "double holding"
        if (event.getMouseX() >= acs.getGuiLeft()) {
            if (dragging && dragSource == DragSource.OVERLAY) {
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            }
            return;
        }

        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        if (!draggedBeyondThreshold &&
                (Math.abs(mxLocal - pressLocalX) > DRAG_THRESHOLD || Math.abs(myLocal - pressLocalY) > DRAG_THRESHOLD)) {
            draggedBeyondThreshold = true;
        }

        lastLocalX = mxLocal;
        lastLocalY = myLocal;

        // If not overlay-dragging, try to adopt an external drag (carried/JEI)
        if (!dragging) {
            ItemStack ext = getExternalDragStack(event.getMouseX(), event.getMouseY());
            if (!ext.isEmpty()) {
                dragging = true;
                dragSource = DragSource.EXTERNAL;
                dragSrcHotbar = null;
                dragSrcSlot = -1;
                dragStack = ext.copy();
            }
        }

        // Interacting with overlay → stop propagation
        event.setCanceled(true);
    }

    // ---------- RELEASE ----------
    @SubscribeEvent
    public static void onMouseReleasedPre(ScreenEvent.MouseButtonReleased.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;
        if (event.getButton() != 0) return; // LMB only

        // If released inside the container GUI, cancel any overlay-origin ghost.
        if (event.getMouseX() >= acs.getGuiLeft()) {
            if (dragging && dragSource == DragSource.OVERLAY) {
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            }
            return;
        }

        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        Minecraft mc = Minecraft.getInstance();
        List<Hotbar> barsNow = HotbarManager.getCurrentPageHotbars();
        if (barsNow.isEmpty()) {
            dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            return;
        }

        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int rows = Math.min(rowsRequested, barsNow.size());
        int rowsUI = 5;

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int deleteX = 0;
        int numX    = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX   = baseHbX + (bgW - BORDER) + GAP;
        int firstY  = 0;

        int barsStartY = firstY + (rowsUI - rows) * ROW_H;
        int listH   = rowsUI * ROW_H;

        boolean inBars = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, barsStartY - 3, bgW, rows * ROW_H);
        boolean inList = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);
        boolean inNums = pointInRectLocal(mxLocal, myLocal, numX, firstY - 3, NUM_COL_W, listH);
        boolean inDel  = pointInRectLocal(mxLocal, myLocal, deleteX, firstY - 3, DELETE_BOX_W, listH);

        // External (carried / JEI) stack
        ItemStack external = getExternalDragStack(event.getMouseX(), event.getMouseY());
        boolean hasExternal = external != null && !external.isEmpty();

        // Page list click
        if (inList) {
            int idx = Mth.clamp(pageScrollRow + (myLocal - (firstY)) / ROW_H, 0, HotbarManager.getPageNames().size() - 1);
            HotbarManager.setPage(idx, 0);
            peekScrollRow = 0;
            if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            event.setCanceled(true);
            return;
        }

        // Number column
        if (inNums) {
            ItemStack drop = !dragStack.isEmpty() ? dragStack : (hasExternal ? external : ItemStack.EMPTY);
            if (!drop.isEmpty()) {
                int idxInWindow = Mth.clamp((myLocal - barsStartY) / ROW_H, 0, rows - 1);
                int tRowIndex   = Mth.clamp(peekScrollRow + idxInWindow, 0, barsNow.size() - 1);
                Hotbar tHb      = barsNow.get(tRowIndex);
                int tSlot       = (dragSource == DragSource.OVERLAY && dragSrcSlot >= 0) ? dragSrcSlot : HotbarManager.getSlot();

                ItemStack targetOld = tHb.getSlot(tSlot).copy();
                tHb.setSlot(tSlot, drop.copy());

                saveAndSync();
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
                event.setCanceled(true);
                return;
            }

            long win = Minecraft.getInstance().getWindow().getWindow();
            boolean shiftDown = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                    || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;

            int idxInWindow = Mth.clamp((myLocal - barsStartY) / ROW_H, 0, rows - 1);
            int targetRow   = Mth.clamp(peekScrollRow + idxInWindow, 0, barsNow.size() - 1);
            int targetSlot  = (dragSource == DragSource.OVERLAY && dragSrcSlot >= 0) ? dragSrcSlot : HotbarManager.getSlot();

            if (shiftDown) {
                HotbarManager.setHotbar(targetRow, "container-peek-activate");
                HotbarManager.setSlot(targetSlot);
                HotbarManager.syncToGame();
                forceSelectSlotClientAndServer(targetSlot);

                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
                event.setCanceled(true);
                return;
            }
        }

        // Delete box
        if (inDel) {
            if (dragSource == DragSource.OVERLAY && dragSrcHotbar != null) {
                dragSrcHotbar.setSlot(dragSrcSlot, ItemStack.EMPTY);
                saveAndSync();
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            } else if (hasExternal) {
                clearCarriedIfAny();
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 0.9f);
            }
            dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            event.setCanceled(true);
            return;
        }

        // Grid area
        if (inBars) {
            int idxInWindow = Mth.clamp((myLocal - barsStartY) / ROW_H, 0, rows - 1);
            int absRow = Mth.clamp(peekScrollRow + idxInWindow, 0, barsNow.size() - 1);
            int relX = mxLocal - baseHbX;
            int slot = Mth.clamp(relX / CELL, 0, Hotbar.SLOT_COUNT - 1);

            ItemStack externalNow = getExternalDragStack(event.getMouseX(), event.getMouseY());
            boolean hasExtNow = externalNow != null && !externalNow.isEmpty();
            boolean overlayDrag = dragging && dragSource == DragSource.OVERLAY && dragSrcHotbar != null && dragSrcSlot >= 0;

            boolean handled = false;

            if (overlayDrag && draggedBeyondThreshold) {
                // real drag-drop swap
                ItemStack a = dragSrcHotbar.getSlot(dragSrcSlot).copy();
                ItemStack b = barsNow.get(absRow).getSlot(slot).copy();
                dragSrcHotbar.setSlot(dragSrcSlot, b);
                barsNow.get(absRow).setSlot(slot, a);
                handled = true;
            } else if (hasExtNow) {
                // place external item
                barsNow.get(absRow).setSlot(slot, externalNow.copy());
                clearCarriedIfAny();
                handled = true;
            } else {
                // CLICK (no drag): select hotbar & slot + switch HUD slot
                HotbarManager.setHotbar(absRow, "container-peek-click-select");
                HotbarManager.setSlot(slot);
                forceSelectSlotClientAndServer(slot);
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                handled = true;
            }

            // If a drag started but wasn't handled above and we modified nothing, restore source (safety)
            if (!handled && overlayDrag && !dragStack.isEmpty() && dragSrcHotbar != null) {
                dragSrcHotbar.setSlot(dragSrcSlot, dragStack);
            }

            saveAndSync();
            draggedBeyondThreshold = false;
            dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            event.setCanceled(true);
        }
    }





    // ---------- SCROLL ----------
    @SubscribeEvent
    public static void onMouseScrolledPre(ScreenEvent.MouseScrolled.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;

        // Only act if cursor is left of the container and within our last drawn bounds
        if (event.getMouseX() >= acs.getGuiLeft()) return;

        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) return;

        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int totalBars = bars.size();
        int rows = Math.min(rowsRequested, totalBars);
        int rowsUI = 5;

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int deleteX = 0;
        int numX = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX  = baseHbX + (bgW - BORDER) + GAP;
        int firstY = 0;

        int barsStartY = firstY + (rowsUI - rows) * ROW_H;
        int listH  = rowsUI * ROW_H;

        boolean inBars = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, barsStartY - 3, bgW, rows * ROW_H);
        boolean inList = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);

        double dy = event.getScrollDelta();
        if (dy == 0.0) return;

        Minecraft mc = Minecraft.getInstance();

        if (inList) {
            int count = HotbarManager.getPageNames().size();
            int pageMax = Math.max(0, count - rowsUI);
            pageScrollRow = Mth.clamp(pageScrollRow - (int)Math.signum(dy), 0, pageMax);
            if (Config.enableSounds() && mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            event.setCanceled(true);
            return;
        }

        if (inBars) {
            // Change the selected hotbar (like HotbarGuiScreen) and keep it visible
            int selected = HotbarManager.getHotbar();
            int step = (int) Math.signum(dy);
            int newSel = Mth.clamp(selected - step, 0, totalBars - 1);
            if (newSel != selected) {
                HotbarManager.setHotbar(newSel, "container-peek-scroll");
                int visible = Math.min(totalBars, rows);
                if (newSel < peekScrollRow) {
                    peekScrollRow = newSel;
                } else if (newSel >= peekScrollRow + visible) {
                    peekScrollRow = Math.max(0, newSel - visible + 1);
                }
                if (Config.enableSounds() && mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                }
            }
            event.setCanceled(true);
        }
    }




}
