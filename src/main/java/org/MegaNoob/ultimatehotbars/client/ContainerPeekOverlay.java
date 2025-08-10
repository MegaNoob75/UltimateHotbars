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

import java.util.List;

/**
 * Always-on "peek" overlay drawn to the LEFT of any container/inventory screen.
 * - Auto-scales uniformly so it fits between the screen's left edge and the container's left edge.
 * - Visuals match PeekHotbarScreen (hotbar rows, page list, pulsating delete box).
 * - Consumes mouse input only when the cursor is inside the scaled overlay.
 */
@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ContainerPeekOverlay {

    private ContainerPeekOverlay() {}

    // === Visual constants (same look as Peek) ===
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");
    private static final int CELL          = 20;   // logical cell (unscaled)
    private static final int BORDER        = 1;
    private static final int ROW_H         = CELL + 2; // matches vanilla hotbar strip we blit
    private static final int DELETE_BOX_W  = 20;
    private static final int NUM_COL_W     = 20;
    private static final int GAP           = 4;
    private static final int LIST_W        = 100;

    // Placement (screen-left margin)
    private static final int LEFT_MARGIN_X = 6;
    private static final int TOP_BOTTOM_MARGIN = 4;

    // Scroll state (kept identical to Peek behavior)
    private static int peekScrollRow = 0;
    private static int pageScrollRow = 0;

    // Cached layout from last render for input (scaled origin & scale)
    private static int lastOriginX = 0, lastOriginY = 0;
    private static int lastNaturalW = 0, lastNaturalH = 0;
    private static float lastScale = 1f;

    private static boolean pointInRectLocal(int lx, int ly, int rx, int ry, int rw, int rh) {
        return lx >= rx && ly >= ry && lx < rx + rw && ly < ry + rh;
    }

    private static int argb(float r, float g, float b, float a) {
        return ((int)(a * 255) << 24) | ((int)(r * 255) << 16) | ((int)(g * 255) << 8) | (int)(b * 255);
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

        // Natural (unscaled) geometry
        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int totalBars = bars.size();
        int rows = Math.min(rowsRequested, totalBars);

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int naturalW = DELETE_BOX_W + GAP + NUM_COL_W + GAP + bgW + GAP + LIST_W;
        int naturalH = rows * ROW_H;

        // Available width = distance from screen-left to container-left
        int containerLeft = acs.getGuiLeft(); // Forge 47.x / 1.20.1 getter
        int availableW = Math.max(0, containerLeft - LEFT_MARGIN_X);
        int availableH = event.getScreen().height - TOP_BOTTOM_MARGIN * 2;

        // Uniform scale so overlay fits both width & height
        float scaleX = availableW > 0 ? Math.min(1f, (float) availableW / (float) naturalW) : 1f;
        float scaleY = availableH > 0 ? Math.min(1f, (float) availableH / (float) naturalH) : 1f;
        float scale = Math.min(scaleX, scaleY);
        if (scale <= 0.01f) return; // nothing will be visible; skip

        int scaledH = Math.round(naturalH * scale);
        int originX = LEFT_MARGIN_X;
        int originY = Math.max(TOP_BOTTOM_MARGIN, (s.height - scaledH) / 2);

        // Cache layout for input handlers
        lastOriginX = originX;
        lastOriginY = originY;
        lastNaturalW = naturalW;
        lastNaturalH = naturalH;
        lastScale = scale;

        // Layout positions in NATURAL space (unscaled)
        int deleteX = 0;
        int numX = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER; // hotbar content starts after a left border
        int listX  = baseHbX + (bgW - BORDER) + GAP;
        int firstY = 0; // top of panel is local Y=0

        // clamp scroll windows
        int visible = rows;
        int peekMax = Math.max(0, totalBars - visible);
        peekScrollRow = Mth.clamp(peekScrollRow, 0, peekMax);

        List<String> pageNames = HotbarManager.getPageNames();
        int pageMax = Math.max(0, pageNames.size() - rows);
        pageScrollRow = Mth.clamp(pageScrollRow, 0, pageMax);

        // Apply transform: translate to origin then scale
        g.pose().pushPose();
        g.pose().translate(originX, originY, 0);
        g.pose().scale(scale, scale, 1f);

        // translucent backdrop for page list column (match Peek vibe)
        g.fill(listX, firstY - 3, listX + LIST_W, firstY - 3 + rows * ROW_H, 0x88000000);

        // --- Delete box (pulsating red border), drawn as a tall column ---
        int listH = rows * ROW_H;
        g.fill(deleteX, firstY - 3, deleteX + DELETE_BOX_W, firstY - 3 + listH, 0x88000000);
        int redColor = ((int)((Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5) * 255) << 24) | 0x00FF0000;
        for (int i = 0; i < 2; i++) {
            g.fill(deleteX + i, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 3 + i + 1, redColor);
            g.fill(deleteX + i, firstY - 3 + listH - i - 1, deleteX + DELETE_BOX_W - i, firstY - 3 + listH - i, redColor);
            g.fill(deleteX + i, firstY - 3 + i, deleteX + i + 1, firstY - 3 + listH - i, redColor);
            g.fill(deleteX + DELETE_BOX_W - i - 1, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 3 + listH - i, redColor);
        }
        // Vertical "DELETE" text
        String del = "DELETE";
        int midX = deleteX + (DELETE_BOX_W - font.width("D")) / 2;
        int startY = firstY - 3 + (listH - del.length() * font.lineHeight) / 2;
        for (int i = 0; i < del.length(); i++) {
            g.drawString(font, String.valueOf(del.charAt(i)), midX, startY + i * font.lineHeight, 0xFFFFFFFF);
        }

        // --- Scroll arrows above/below number column ---
        if (peekScrollRow > 0) {
            g.drawString(font, "▲", numX + (NUM_COL_W - font.width("▲")) / 2, firstY - font.lineHeight - 2, 0xFFFFFF);
        }
        if (peekScrollRow + visible < totalBars) {
            g.drawString(font, "▼", numX + (NUM_COL_W - font.width("▼")) / 2, firstY + visible * ROW_H + 2, 0xFFFFFF);
        }

        // --- Hotbar rows (vanilla widget strip & items) ---
        int selectedRow  = Mth.clamp(HotbarManager.getHotbar(), 0, totalBars - 1);
        int selectedSlot = HotbarManager.getSlot();

        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y   = firstY + i * ROW_H;

            // row number in number column
            String label = String.valueOf(row + 1);
            int lx = numX + (NUM_COL_W - font.width(label)) / 2;
            int ly = y + (ROW_H - font.lineHeight) / 2;
            g.drawString(font, label, lx, ly, 0xFFFFFFFF, true);

            // vanilla hotbar background strip (stretch just like Peek)
            g.blit(HOTBAR_TEX, baseHbX - BORDER, y - 3, 0, 0, bgW, ROW_H);

            for (int sIdx = 0; sIdx < Hotbar.SLOT_COUNT; sIdx++) {
                ItemStack stack = bars.get(row).getSlot(sIdx);
                int x = baseHbX + sIdx * CELL + (CELL - 16) / 2;
                // highlight selected
                if (row == selectedRow && sIdx == selectedSlot) {
                    float[] hc = Config.highlightColor();
                    int col = argb(hc[0], hc[1], hc[2], hc[3]);
                    g.fill(x - 1, y - 1, x + 16 + 1, y + 16 + 1, col);
                }
                if (!stack.isEmpty()) {
                    g.renderItem(stack, x, y);
                    g.renderItemDecorations(font, stack, x, y);
                }
            }
        }

        // --- Page list (right) with track & thumb ---
        int trackX = listX + LIST_W - 6;
        g.fill(trackX, firstY - 3, trackX + 4, firstY - 3 + listH, 0x44000000);

        int selectedPage = HotbarManager.getPage();
        for (int i = 0; i < rows; i++) {
            int idx = pageScrollRow + i;
            if (idx >= pageNames.size()) break;
            int y = firstY + i * ROW_H;

            if (idx == selectedPage) {
                g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, 0x44FFFFFF);
            }
            // hover highlight (need mouse in local coords, compute once)
            int mxLocal = (int) ((event.getMouseX() - originX) / scale);
            int myLocal = (int) ((event.getMouseY() - originY) / scale);
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
    }

    // ---------- CLICK ----------

    @SubscribeEvent
    public static void onMousePressedPre(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen s = event.getScreen();
        if (!(s instanceof AbstractContainerScreen<?> acs)) return;

        // Quick reject if cursor is left of the container (we’re always left of it)
        if (event.getMouseX() >= acs.getGuiLeft()) return;

        // Convert mouse to local, unscaled overlay space
        int mxLocal = (int) ((event.getMouseX() - lastOriginX) / lastScale);
        int myLocal = (int) ((event.getMouseY() - lastOriginY) / lastScale);
        if (mxLocal < 0 || myLocal < 0 || mxLocal >= lastNaturalW || myLocal >= lastNaturalH) return;

        if (event.getButton() != 0) { // left only
            event.setCanceled(true);
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { event.setCanceled(true); return; }

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) { event.setCanceled(true); return; }

        int rowsRequested = Math.max(1, Config.peekVisibleRows());
        int rows = Math.min(rowsRequested, bars.size());

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;

        int deleteX = 0;
        int numX = deleteX + DELETE_BOX_W + GAP;
        int baseHbX = numX + NUM_COL_W + GAP + BORDER;
        int listX  = baseHbX + (bgW - BORDER) + GAP;
        int firstY = 0;

        int listH = rows * ROW_H;

        boolean inDelete = pointInRectLocal(mxLocal, myLocal, deleteX, firstY - 3, DELETE_BOX_W, listH);
        boolean inNums   = pointInRectLocal(mxLocal, myLocal, numX, firstY - 3, NUM_COL_W, listH);
        boolean inBars   = pointInRectLocal(mxLocal, myLocal, baseHbX - BORDER, firstY - 3, bgW, rows * ROW_H);
        boolean inList   = pointInRectLocal(mxLocal, myLocal, listX, firstY - 3, LIST_W, listH);

        // Page list click
        if (inList) {
            int idxInWindow = Mth.clamp((myLocal - (firstY - 3)) / ROW_H, 0, rows - 1);
            int clicked = pageScrollRow + idxInWindow;
            List<String> names = HotbarManager.getPageNames();
            if (clicked >= 0 && clicked < names.size()) {
                HotbarManager.setPage(clicked, 0);
                peekScrollRow = 0; // reset hotbar window to top of page
                if (Config.enableSounds()) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            event.setCanceled(true);
            return;
        }

        // Hotbar click
        if (inBars) {
            int relY = myLocal - firstY;
            int idxInWindow = Mth.clamp(relY / ROW_H, 0, rows - 1);
            int absRow = Mth.clamp(peekScrollRow + idxInWindow, 0, bars.size() - 1);

            int relX = mxLocal - baseHbX;
            int slot = Mth.clamp(relX / CELL, 0, Hotbar.SLOT_COUNT - 1);

            HotbarManager.setHotbar(absRow, "container-peek-click");
            HotbarManager.setSlot(slot);
            HotbarManager.syncToGame();

            if (Config.enableSounds() && mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            event.setCanceled(true);
            return;
        }

        // Delete & numbers just consume the click for now
        if (inDelete || inNums) {
            if (Config.enableSounds() && mc.player != null && inDelete) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
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
}
