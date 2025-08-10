package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
 * - Auto-scales to fit left gap.
 * - Drag/drop parity with Peek window + accept drags from container/creative and JEI.
 * - Drop targets:
 *     * Slot grid: put/swap exact slot.
 *     * Number column: put/swap into that hotbar at same slot index (external uses currently-selected slot).
 *     * Page list: switch to that page and put/swap into currently-selected row+slot on that page.
 * - Leaving the overlay and releasing reverts overlay-origin drags (unless released over DELETE).
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

    private static boolean pointInRectLocal(int lx, int ly, int rx, int ry, int rw, int rh) {
        return lx >= rx && ly >= ry && lx < rx + rw && ly < ry + rh;
    }
    private static int argb(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
    }

    // ---- Drag & Drop state ----
    private static boolean dragging = false;
    private static boolean draggedBeyondThreshold = false;
    private static int pressLocalX = 0, pressLocalY = 0;
    private static int lastLocalX = 0, lastLocalY = 0;

    private enum DragSource { NONE, OVERLAY, EXTERNAL }
    private static DragSource dragSource = DragSource.NONE;

    // Track EXACT source hotbar (by reference) and slot (works across page/scroll)
    private static Hotbar dragSrcHotbar = null;
    private static int    dragSrcSlot   = -1;
    private static ItemStack dragStack  = ItemStack.EMPTY;

    private static final int DRAG_THRESHOLD = 3;

    // ---------- Helpers ----------

    /** Is the game currently carrying an item with the real container cursor? */
    private static boolean isCarryingFromContainer() {
        var mc = Minecraft.getInstance();
        return mc != null && mc.player != null && mc.player.containerMenu != null
                && !mc.player.containerMenu.getCarried().isEmpty();
    }

    /**
     * External stack from carried (inventory/creative) or optional JEI bridge.
     * If you add JeiBridge#getItemUnderMouse(x,y) (see note below), we’ll pick up JEI drags too.
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

    private static void saveAndSync() {
        try {
            HotbarManager.saveHotbars();
        } catch (Throwable ignored) {}
        try {
            HotbarManager.syncToGame();
        } catch (Throwable ignored) {}
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

        // Natural geometry (unscaled)
        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int totalBars = bars.size();
        int rows = Math.min(rowsRequested, totalBars);

        final int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        final int naturalW = DELETE_BOX_W + GAP + NUM_COL_W + GAP + bgW + GAP + LIST_W;
        final int naturalH = rows * ROW_H;

        // Fit between screen-left and container-left, and within screen height
        int containerLeft = acs.getGuiLeft();
        int availableW = Math.max(0, containerLeft - LEFT_MARGIN_X);
        int availableH = s.height - TOP_BOTTOM_MARGIN * 2;

        float scaleX = availableW > 0 ? Math.min(1f, (float) availableW / (float) naturalW) : 1f;
        float scaleY = availableH > 0 ? Math.min(1f, (float) availableH / (float) naturalH) : 1f;
        float scale = Math.min(scaleX, scaleY);
        if (scale <= 0.01f) return;

        int scaledH = Math.round(naturalH * scale);
        int originX = LEFT_MARGIN_X;
        int originY = Math.max(TOP_BOTTOM_MARGIN, (s.height - scaledH) / 2);

        // Cache for input
        lastOriginX = originX;
        lastOriginY = originY;
        lastNaturalW = naturalW;
        lastNaturalH = naturalH;
        lastScale = scale;

        // Local (unscaled) positions
        final int deleteX = 0;
        final int numX    = deleteX + DELETE_BOX_W + GAP;
        final int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        final int listX   = baseHbX + (bgW - BORDER) + GAP;
        final int firstY  = 0;

        int visible = rows;
        int peekMax = Math.max(0, totalBars - visible);
        peekScrollRow = Mth.clamp(peekScrollRow, 0, peekMax);

        List<String> pageNames = HotbarManager.getPageNames();
        int pageMax = Math.max(0, pageNames.size() - rows);
        pageScrollRow = Mth.clamp(pageScrollRow, 0, pageMax);

        // Transform to overlay space
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1f);

        // Page list backdrop
        int listH = rows * ROW_H;
        g.fill(listX, firstY - 3, listX + LIST_W, firstY - 3 + listH, 0x88000000);

        // Delete box (pulsing border)
        g.fill(deleteX, firstY - 3, deleteX + DELETE_BOX_W, firstY - 3 + listH, 0x88000000);
        int redColor = ((int)((Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5) * 255) << 24) | 0x00FF0000;
        for (int i = 0; i < 2; i++) {
            g.fill(deleteX + i, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 3 + i + 1, redColor);
            g.fill(deleteX + i, firstY - 3 + listH - i - 1, deleteX + DELETE_BOX_W - i, firstY - 3 + listH - i, redColor);
            g.fill(deleteX + i, firstY - 3 + i, deleteX + i + 1, firstY - 3 + listH - i, redColor);
            g.fill(deleteX + DELETE_BOX_W - i - 1, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 3 + listH - i, redColor);
        }
        // vertical "DELETE"
        String del = "DELETE";
        int midX = deleteX + (DELETE_BOX_W - font.width("D")) / 2;
        int startY = firstY - 3 + (listH - del.length() * font.lineHeight) / 2;
        for (int i = 0; i < del.length(); i++) {
            g.drawString(font, String.valueOf(del.charAt(i)), midX, startY + i * font.lineHeight, 0xFFFFFFFF);
        }

        // Scroll arrows
        if (peekScrollRow > 0) g.drawString(font, "▲", numX + (NUM_COL_W - font.width("▲")) / 2, firstY - font.lineHeight - 2, 0xFFFFFF);
        if (peekScrollRow + visible < totalBars) g.drawString(font, "▼", numX + (NUM_COL_W - font.width("▼")) / 2, firstY + visible * ROW_H + 2, 0xFFFFFF);

        // Selected slot
        int selectedRow  = Mth.clamp(HotbarManager.getHotbar(), 0, totalBars - 1);
        int selectedSlot = HotbarManager.getSlot();

        // Mouse in local space (for page hover)
        int mxLocal = (int) ((event.getMouseX() - originX) / scale);
        int myLocal = (int) ((event.getMouseY() - originY) / scale);

        // Hotbar rows
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y   = firstY + i * ROW_H;

            // row number
            String label = String.valueOf(row + 1);
            int lx = numX + (NUM_COL_W - font.width(label)) / 2;
            int ly = y + (ROW_H - font.lineHeight) / 2;
            g.drawString(font, label, lx, ly, 0xFFFFFFFF, true);

            // background strip
            g.blit(HOTBAR_TEX, baseHbX - BORDER, y - 3, 0, 0, bgW, ROW_H);

            for (int sIdx = 0; sIdx < Hotbar.SLOT_COUNT; sIdx++) {
                int x = baseHbX + sIdx * CELL + (CELL - 16) / 2;

                // Hide source cell while dragging from overlay (compare by hotbar reference)
                boolean hide = dragging && dragSource == DragSource.OVERLAY
                        && (row >= 0 && row < bars.size())
                        && bars.get(row) == dragSrcHotbar && sIdx == dragSrcSlot;

                // Selected highlight
                if (!hide && row == selectedRow && sIdx == selectedSlot) {
                    float[] hc = Config.highlightColor();
                    int col = argb(hc[0], hc[1], hc[2], hc[3]);
                    g.fill(x - 1, y - 1, x + 16 + 1, y + 16 + 1, col);
                }

                if (!hide) {
                    ItemStack stack = bars.get(row).getSlot(sIdx);
                    if (!stack.isEmpty()) {
                        g.renderItem(stack, x, y);
                        g.renderItemDecorations(font, stack, x, y);
                    }
                }
            }
        }

        // Page list with hover/selected highlight
        int trackX = listX + LIST_W - 6;
        g.fill(trackX, firstY - 3, trackX + 4, firstY - 3 + listH, 0x44000000);

        int selectedPage = HotbarManager.getPage();
        for (int i = 0; i < rows; i++) {
            int idx = pageScrollRow + i;
            if (idx >= pageNames.size()) break;
            int y = firstY + i * ROW_H;

            if (idx == selectedPage) g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, 0x44FFFFFF);
            if (pointInRectLocal(mxLocal, myLocal, listX, y - 3, LIST_W - 6, ROW_H)) {
                float[] hc = Config.highlightColor();
                int col = argb(hc[0], hc[1], hc[2], hc[3]);
                g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, col);
            }
            g.drawString(font, pageNames.get(idx), listX + 2, y, 0xFFFFFF);
        }

        if (pageNames.size() > rows) {
            int thumbH = Math.max((listH * rows) / pageNames.size(), 10);
            int pageMax2 = Math.max(1, pageNames.size() - rows);
            int thumbY = (firstY - 3) + (pageScrollRow * (listH - thumbH)) / pageMax2;
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xAAFFFFFF);
        }

        g.pose().popPose();

        // Draw overlay-ghost only for OVERLAY source; external drags are rendered by the container/JEI
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

        // Only if cursor is left of the container GUI (in our overlay area)
        if (event.getMouseX() >= acs.getGuiLeft()) return;

        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        if (event.getButton() != 0) { // left only
            event.setCanceled(true);
            return;
        }

        // Layout
        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) { event.setCanceled(true); return; }
        int rows = Math.min(rowsRequested, bars.size());

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int deleteX = 0;
        int numX    = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX   = baseHbX + (bgW - BORDER) + GAP;
        int firstY  = 0;
        int listH   = rows * ROW_H;

        boolean inBars = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, firstY - 3, bgW, rows * ROW_H);
        boolean inList = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);
        boolean inNums = pointInRectLocal(mxLocal, myLocal, numX, firstY - 3, NUM_COL_W, listH);
        boolean inDel  = pointInRectLocal(mxLocal, myLocal, deleteX, firstY - 3, DELETE_BOX_W, listH);

        // Start-of-press bookkeeping
        pressLocalX = mxLocal;
        pressLocalY = myLocal;
        lastLocalX  = mxLocal;
        lastLocalY  = myLocal;
        draggedBeyondThreshold = false;

        // Page/Nums/Delete: consume; actions on release
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
            int idxInWindow = Mth.clamp((myLocal - firstY) / ROW_H, 0, rows - 1);
            int absRow = Mth.clamp(peekScrollRow + idxInWindow, 0, bars.size() - 1);
            int relX = mxLocal - baseHbX;
            int slot = Mth.clamp(relX / CELL, 0, Hotbar.SLOT_COUNT - 1);

            ItemStack inCell = bars.get(absRow).getSlot(slot);
            if (!inCell.isEmpty()) {
                dragging = true;
                dragSource = DragSource.OVERLAY;
                dragSrcHotbar = bars.get(absRow); // keep reference (page-safe)
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
    public static void onMouseReleasedPre(net.minecraftforge.client.event.ScreenEvent.MouseButtonReleased.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;
        if (event.getButton() != 0) return; // LMB only

        // Release inside container zone? Cancel overlay ghost (revert; we didn't apply edits yet)
        if (event.getMouseX() >= acs.getGuiLeft()) {
            if (dragging && dragSource == DragSource.OVERLAY) {
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            }
            return;
        }

        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        var mc = Minecraft.getInstance();
        List<Hotbar> barsNow = HotbarManager.getCurrentPageHotbars();
        if (barsNow.isEmpty()) {
            dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            return;
        }

        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int rows = Math.min(rowsRequested, barsNow.size());

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int deleteX = 0;
        int numX    = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX   = baseHbX + (bgW - BORDER) + GAP;
        int firstY  = 0;
        int listH   = rows * ROW_H;

        boolean inBars = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, firstY - 3, bgW, rows * ROW_H);
        boolean inList = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);
        boolean inNums = pointInRectLocal(mxLocal, myLocal, numX, firstY - 3, NUM_COL_W, listH);
        boolean inDel  = pointInRectLocal(mxLocal, myLocal, deleteX, firstY - 3, DELETE_BOX_W, listH);

        // Detect an external (carried/JEI) stack even if we didn't drag
        ItemStack external = getExternalDragStack(event.getMouseX(), event.getMouseY());
        boolean hasExternal = external != null && !external.isEmpty();

        // --- Page list: switch or drop onto the selected row+slot of that page ---
        if (inList) {
            int idxInWindow = Mth.clamp((myLocal - (firstY - 3)) / ROW_H, 0, rows - 1);
            int targetPage = Mth.clamp(pageScrollRow + idxInWindow, 0, HotbarManager.getPageNames().size() - 1);

            ItemStack drop = !dragStack.isEmpty() ? dragStack : (hasExternal ? external : ItemStack.EMPTY);
            if (!drop.isEmpty()) {
                HotbarManager.setPage(targetPage, 0);
                List<Hotbar> targetBars = HotbarManager.getCurrentPageHotbars();
                int tRow = Mth.clamp(HotbarManager.getHotbar(), 0, Math.max(0, targetBars.size() - 1));
                int tSlot = HotbarManager.getSlot();

                Hotbar tHb = targetBars.get(tRow);
                ItemStack targetOld = tHb.getSlot(tSlot).copy();
                tHb.setSlot(tSlot, drop.copy());

                if (dragSource == DragSource.OVERLAY && dragSrcHotbar != null) {
                    dragSrcHotbar.setSlot(dragSrcSlot, targetOld);
                }

                saveAndSync();
                if (hasExternal) clearCarriedIfAny();

                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
                event.setCanceled(true);
                return;
            }

            // No item to drop → just switch page
            HotbarManager.setPage(targetPage, 0);
            peekScrollRow = 0;
            if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            event.setCanceled(true);
            return;
        }

        // --- Number column: drop into that hotbar at same slot index (external uses selected slot) ---
        if (inNums) {
            ItemStack drop = !dragStack.isEmpty() ? dragStack : (hasExternal ? external : ItemStack.EMPTY);
            if (!drop.isEmpty()) {
                int idxInWindow = Mth.clamp((myLocal - firstY) / ROW_H, 0, rows - 1);
                int tRowIndex = Mth.clamp(peekScrollRow + idxInWindow, 0, barsNow.size() - 1);
                Hotbar tHb = barsNow.get(tRowIndex);
                int tSlot = (dragSource == DragSource.OVERLAY && dragSrcSlot >= 0) ? dragSrcSlot : HotbarManager.getSlot();

                ItemStack targetOld = tHb.getSlot(tSlot).copy();
                tHb.setSlot(tSlot, drop.copy());
                if (dragSource == DragSource.OVERLAY && dragSrcHotbar != null) {
                    dragSrcHotbar.setSlot(dragSrcSlot, targetOld);
                }

                saveAndSync();
                if (hasExternal) clearCarriedIfAny();

                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
                event.setCanceled(true);
                return;
            }

            // nothing to drop → consume to avoid container click-through
            event.setCanceled(true);
            return;
        }

        // --- Hotbar grid: click OR drop (prefer drop when we have one) ---
        if (inBars) {
            int idxInWindow = Mth.clamp((myLocal - firstY) / ROW_H, 0, rows - 1);
            int targetRow = Mth.clamp(peekScrollRow + idxInWindow, 0, barsNow.size() - 1);
            int relX = mxLocal - baseHbX;
            int targetSlot = Mth.clamp(relX / CELL, 0, Hotbar.SLOT_COUNT - 1);

            ItemStack drop = !dragStack.isEmpty() ? dragStack : (hasExternal ? external : ItemStack.EMPTY);
            if (!drop.isEmpty()) {
                Hotbar targetHotbar = barsNow.get(targetRow);

                // Same cell? no-op
                if (dragSource == DragSource.OVERLAY && dragSrcHotbar == targetHotbar && dragSrcSlot == targetSlot) {
                    dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
                    event.setCanceled(true);
                    return;
                }

                ItemStack targetOld = targetHotbar.getSlot(targetSlot).copy();
                targetHotbar.setSlot(targetSlot, drop.copy());
                if (dragSource == DragSource.OVERLAY && dragSrcHotbar != null) {
                    dragSrcHotbar.setSlot(dragSrcSlot, targetOld); // swap
                }

                saveAndSync();
                if (hasExternal) clearCarriedIfAny();

                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
                event.setCanceled(true);
                return;
            }

            // No drop candidate → treat as a click (select row+slot and sync)
            HotbarManager.setHotbar(targetRow, "container-peek-click");
            HotbarManager.setSlot(targetSlot);
            HotbarManager.syncToGame();
            if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            event.setCanceled(true);
            return;
        }

        // --- Delete box ---
        if (inDel) {
            if (dragSource == DragSource.OVERLAY && dragSrcHotbar != null) {
                // Delete the overlay source slot
                dragSrcHotbar.setSlot(dragSrcSlot, ItemStack.EMPTY);
                saveAndSync();
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            } else if (hasExternal) {
                // If you're carrying something from Creative/JEI and drop on DELETE, discard it
                clearCarriedIfAny();
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 0.9f);
            }
            dragging = false; dragSource = DragSource.NONE; dragSrcHotbar = null; dragSrcSlot = -1; dragStack = ItemStack.EMPTY;
            event.setCanceled(true);
            return;
        }

        // Any other overlay area while dragging or carrying → consume & reset
        if ((inNums || inList || inDel) && (dragging || hasExternal)) {
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
        int rows = Math.min(rowsRequested, bars.size());

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;

        int deleteX = 0;
        int numX = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX  = baseHbX + (bgW - BORDER) + GAP;
        int firstY = 0;

        int listH = rows * ROW_H;

        boolean inBars = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, firstY - 3, bgW, rows * ROW_H);
        boolean inList = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);

        double dy = event.getScrollDelta(); // Forge 47.x
        if (dy == 0.0) return;

        Minecraft mc = Minecraft.getInstance();

        if (inList) {
            int count = HotbarManager.getPageNames().size();
            int pageMax = Math.max(0, count - rows);
            pageScrollRow = Mth.clamp(pageScrollRow - (int)Math.signum(dy), 0, pageMax);
        } else if (inBars) {
            int totalBars = bars.size();
            int visible = Math.min(totalBars, rows);
            int peekMax = Math.max(0, totalBars - visible);
            peekScrollRow = Mth.clamp(peekScrollRow - (int)Math.signum(dy), 0, peekMax);
        } else {
            return;
        }

        if (Config.enableSounds() && mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
        }
        event.setCanceled(true);
    }

    // Clear the game's carried stack (Creative/inventory cursor)
    private static void clearCarriedIfAny() {
        var mc = Minecraft.getInstance();
        if (mc != null && mc.player != null && mc.player.containerMenu != null) {
            mc.player.containerMenu.setCarried(ItemStack.EMPTY);
        }
    }

}
