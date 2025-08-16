package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@Mod.EventBusSubscriber(
        modid = ultimatehotbars.MODID,
        value = Dist.CLIENT,
        bus   = Mod.EventBusSubscriber.Bus.FORGE
)
public class PeekHotbarScreen extends Screen {
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");
    private static final int CELL = 20;
    private static final int BORDER = 1;
    private static final int ROW_H = CELL + 2;
    private static final int DELETE_BOX_W = 20;
    private static final int NUM_COL_W = 20;
    private static final int GAP = 4;
    private static final int LIST_W = 100;
    private static final double DRAG_THRESHOLD = 5.0;

    private int peekScrollRow = 0;
    private int pageScrollRow = 0;

    private boolean potentialDrag = false;
    private boolean dragging = false;
    private double pressX, pressY;
    private int sourceRow, sourceSlot;
    private ItemStack draggedStack = ItemStack.EMPTY;

    // throttle wheel-based hotbar switching (same fix as hotkeys)
    private long lastWheelSwitchMs = 0L;
    private int  pendingWheelHotbar = -1; // -1 = none

    public PeekHotbarScreen() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long window = mc.getWindow().getWindow();
        if (!com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, KeyBindings.PEEK_HOTBARS.getKey().getValue())) {
            mc.setScreen(null);
            return;
        }

        var bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) return;

        int rows = Config.peekVisibleRows();
        int totalBars = bars.size();
        int visible = Math.min(totalBars, rows);

        // Show pending selection immediately (visual), else actual
        int selectedRowForDisplay = Mth.clamp(
                (WheelSwitchCoordinator.getPendingTarget() >= 0
                        ? WheelSwitchCoordinator.getPendingTarget()
                        : HotbarManager.getHotbar()),
                0, totalBars - 1);

        // Keep selectedRowForDisplay visible right away
        if (selectedRowForDisplay < peekScrollRow) {
            peekScrollRow = selectedRowForDisplay;
        } else if (selectedRowForDisplay >= peekScrollRow + visible) {
            peekScrollRow = Math.max(0, selectedRowForDisplay - visible + 1);
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");
        final int CELL = 20, BORDER = 1, ROW_H = CELL + 2, GAP = 4, NUM_COL_W = 20, DELETE_BOX_W = 20, LIST_W = 100;

        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int firstY = sh - 60 - (rows - 1) * ROW_H;
        int barsStartY = firstY + (rows - visible) * ROW_H;
        int baseHbX = (sw - bgW) / 2 + BORDER;
        int numX = baseHbX - BORDER - GAP - NUM_COL_W;
        int deleteX = numX - GAP - DELETE_BOX_W;
        int listX = baseHbX + bgW + GAP;
        int topY = firstY - 3;

        // DELETE box (pulse frame)
        int areaH = rows * ROW_H;
        g.fill(deleteX, topY, deleteX + DELETE_BOX_W, topY + areaH, 0x88000000);
        int redColor = ((int)((Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5) * 255) << 24) | 0x00FF0000;
        for (int i = 0; i < 2; i++) {
            g.fill(deleteX + i, topY + i, deleteX + DELETE_BOX_W - i, topY + i + 1, redColor);
            g.fill(deleteX + i, topY + areaH - i - 1, deleteX + DELETE_BOX_W - i, topY + areaH - i, redColor);
            g.fill(deleteX + i, topY + i, deleteX + i + 1, topY + areaH - i, redColor);
            g.fill(deleteX + DELETE_BOX_W - i - 1, topY + i, deleteX + DELETE_BOX_W - i, topY + areaH - i, redColor);
        }
        String del = "DELETE";
        int midX = deleteX + (mc.font.width("D") < DELETE_BOX_W ? (DELETE_BOX_W - mc.font.width("D")) / 2 : 2);
        int startY = topY + (areaH - del.length() * mc.font.lineHeight) / 2;
        for (int i = 0; i < del.length(); i++) {
            g.drawString(mc.font, String.valueOf(del.charAt(i)), midX, startY + i * mc.font.lineHeight, 0xFFFFFFFF);
        }

        // scroll arrows
        if (peekScrollRow > 0) g.drawString(mc.font, "▲", numX + (NUM_COL_W - mc.font.width("▲")) / 2, firstY - mc.font.lineHeight - 4, 0xFFFFFF);
        if (peekScrollRow + visible < totalBars) g.drawString(mc.font, "▼", numX + (NUM_COL_W - mc.font.width("▼")) / 2, firstY + visible * ROW_H + 2, 0xFFFFFF);

        int selectedSlot = HotbarManager.getSlot();

        long nowMs = System.currentTimeMillis();
        // Draw rows — bottom aligned
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y   = barsStartY + i * ROW_H;

            // row number
            String label = String.valueOf(row + 1);
            int lx = numX + (NUM_COL_W - mc.font.width(label)) / 2;
            int ly = y + (ROW_H - mc.font.lineHeight) / 2;
            g.drawString(mc.font, label, lx, ly, 0xFFFFFFFF, true);

            // row strip
            g.blit(HOTBAR_TEX, baseHbX - BORDER, y - 3, 0, 0, bgW, ROW_H);

            // full-row highlight (pending or actual)
            if (row == selectedRowForDisplay) {
                float[] c = Config.highlightColor();
                int color = ((int)(c[3]*255)<<24) | ((int)(c[0]*255)<<16) | ((int)(c[1]*255)<<8) | (int)(c[2]*255);
                g.fill(baseHbX - BORDER, y - 3, baseHbX - BORDER + bgW, y - 3 + ROW_H, color);
            }

            // items
            for (int sIdx = 0; sIdx < Hotbar.SLOT_COUNT; sIdx++) {
                ItemStack stack = bars.get(row).getSlot(sIdx);
                int x = baseHbX + sIdx * CELL + (CELL - 16) / 2;
                g.renderItem(stack, x, y);
                g.renderItemDecorations(mc.font, stack, x, y);
            }

            // blinking hover border
            int cellLeft = baseHbX - BORDER;
            int cellTop  = y - 3;
            if (mouseX >= cellLeft && mouseX < cellLeft + bgW && mouseY >= cellTop && mouseY < cellTop + ROW_H) {
                int sHover = (mouseX - baseHbX) / CELL;
                if (sHover >= 0 && sHover < Hotbar.SLOT_COUNT) {
                    int hx = baseHbX + sHover * CELL;
                    int hy = y - 3;
                    float[] arr = Config.hoverBorderColor();
                    float t = (nowMs % 1000L) / 1000f;
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

        // Page list
        int listH = rows * ROW_H;
        int trackX = listX + 100 - 6;
        g.fill(listX, topY, listX + 100, topY + listH, 0x88000000);
        g.fill(trackX, topY, trackX + 4, topY + listH, 0x44000000);

        var pageNames = HotbarManager.getPageNames();
        int count = pageNames.size();
        int pageMax = Math.max(0, count - rows);
        pageScrollRow = Mth.clamp(pageScrollRow, 0, pageMax);
        int selectedPage = HotbarManager.getPage();

        for (int i = 0; i < rows; i++) {
            int idx = pageScrollRow + i;
            if (idx >= count) break;
            int y = firstY + i * ROW_H;

            if (idx == selectedPage) {
                g.fill(listX, y - 3, listX + 100 - 6, y - 3 + ROW_H, 0x44FFFFFF);
            }
            if (mouseX >= listX && mouseX < listX + 100 - 6 && mouseY >= y - 3 && mouseY < y - 3 + ROW_H) {
                float[] hc = Config.highlightColor();
                int col = ((int)(hc[3]*255) << 24) | ((int)(hc[0]*255) << 16) | ((int)(hc[1]*255) << 8) | (int)(hc[2]*255);
                g.fill(listX, y - 3, listX + 100 - 6, y - 3 + ROW_H, col);
            }
            g.drawString(mc.font, pageNames.get(idx), listX + 2, y, 0xFFFFFF);
        }

        if (count > rows) {
            int thumbH = Math.max((listH * rows) / count, 10);
            int thumbY = topY + (pageScrollRow * (listH - thumbH)) / pageMax;
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xAAFFFFFF);
        }
    }







    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return super.mouseScrolled(mouseX, mouseY, delta);

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) return super.mouseScrolled(mouseX, mouseY, delta);

        int rowsPref   = Math.max(1, Config.peekVisibleRows());
        int totalBars  = bars.size();
        int visible    = Math.min(totalBars, rowsPref);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        final int bgW     = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        final int firstY  = sh - 60 - (rowsPref - 1) * ROW_H;
        final int barsY   = firstY + (rowsPref - visible) * ROW_H;
        final int baseHbX = (sw - bgW) / 2 + BORDER;

        // Page list geometry
        final int listX   = baseHbX + bgW + GAP;
        final int topY    = firstY - 3;
        final int listH   = rowsPref * ROW_H;

        boolean overList = mouseX >= listX && mouseX < listX + LIST_W && mouseY >= topY && mouseY < topY + listH;
        if (overList) {
            int count = HotbarManager.getPageNames().size();
            int max = Math.max(0, count - rowsPref);
            int step = (int) Math.signum(delta);
            pageScrollRow = Mth.clamp(pageScrollRow - step, 0, max);
            return true;
        }

        boolean overBars = mouseX >= (baseHbX - BORDER) && mouseX < (baseHbX - BORDER + bgW)
                && mouseY >= barsY - 3 && mouseY < barsY - 3 + visible * ROW_H;

        if (overBars) {
            int selected = HotbarManager.getHotbar();
            int step = (int) Math.signum(delta);

            // ---- WRAP AROUND (matches hotkeys) ----
            int newSel = Math.floorMod(selected - step, totalBars);

            if (newSel != selected) {
                // queue for safe commit on client tick
                WheelSwitchCoordinator.request(newSel);

                // keep requested row in view immediately
                if (newSel < peekScrollRow) {
                    peekScrollRow = newSel;
                } else if (newSel >= peekScrollRow + visible) {
                    peekScrollRow = Math.max(0, newSel - visible + 1);
                }
                // play the click sound now
                if (Config.enableSounds() && mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                }
            }
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }






    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0) return super.mouseClicked(mouseX, mouseY, button);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return super.mouseClicked(mouseX, mouseY, button);

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) return super.mouseClicked(mouseX, mouseY, button);

        int rowsPref   = Math.max(1, Config.peekVisibleRows());
        int totalBars  = bars.size();
        int visible    = Math.min(totalBars, rowsPref);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        final int bgW     = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        final int firstY  = sh - 60 - (rowsPref - 1) * ROW_H;
        final int barsY   = firstY + (rowsPref - visible) * ROW_H;
        final int baseHbX = (sw - bgW) / 2 + BORDER;

        // Page list geometry (same as render)
        final int numX    = baseHbX - BORDER - GAP - NUM_COL_W;
        final int deleteX = numX - GAP - DELETE_BOX_W;
        final int listX   = baseHbX + bgW + GAP;
        final int topY    = firstY - 3;
        final int listH   = rowsPref * ROW_H;

        // Click in page list => switch page, reset hotbar to 0
        if (mouseX >= listX && mouseX < listX + LIST_W && mouseY >= topY && mouseY < topY + listH) {
            List<String> names = HotbarManager.getPageNames();
            int idxInWindow = Mth.clamp((int) ((mouseY - (firstY)) / ROW_H), 0, rowsPref - 1);
            int target = Mth.clamp(pageScrollRow + idxInWindow, 0, names.size() - 1);
            HotbarManager.setPage(target, 0);
            peekScrollRow = 0;
            if (Config.enableSounds() && mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            return true;
        }

        // Click in hotbar grid => select that hotbar and that slot + update main HUD slot
        boolean overBars = mouseX >= (baseHbX - BORDER) && mouseX < (baseHbX - BORDER + bgW)
                && mouseY >= barsY - 3 && mouseY < barsY - 3 + visible * ROW_H;

        if (overBars) {
            int rowIdx = Mth.clamp((int) ((mouseY - barsY) / ROW_H), 0, visible - 1);
            int absRow = Mth.clamp(peekScrollRow + rowIdx, 0, totalBars - 1);
            int relX   = (int) mouseX - baseHbX;
            int slot   = Mth.clamp(relX / CELL, 0, Hotbar.SLOT_COUNT - 1);

            HotbarManager.setHotbar(absRow, "peek-click-select");
            HotbarManager.setSlot(slot);

            // Also switch the vanilla selected slot (client + server)
            if (mc.player != null && mc.getConnection() != null) {
                mc.player.getInventory().selected = slot;
                mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
            }

            if (Config.enableSounds() && mc.player != null) {
                mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        Minecraft mc = Minecraft.getInstance();
        if (potentialDrag && !dragging) {
            if (Math.hypot(mx - pressX, my - pressY) >= DRAG_THRESHOLD) {
                dragging = true;
                HotbarManager.getCurrentPageHotbars().get(sourceRow).setSlot(sourceSlot, ItemStack.EMPTY);
                HotbarManager.markDirty();
                HotbarManager.saveHotbars();
                HotbarManager.syncToGame();
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            return dragging;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance();
        if (!potentialDrag || mc.player == null) {
            return super.mouseReleased(mx, my, button);
        }
        boolean wasDragging = dragging;
        potentialDrag = false;
        dragging = false;

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        int rows       = Config.peekVisibleRows();
        int totalBars  = bars.size();
        int visible    = Math.min(totalBars, rows);
        int sw         = mc.getWindow().getGuiScaledWidth();
        int sh         = mc.getWindow().getGuiScaledHeight();
        int bgW        = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int firstY     = sh - 60 - (rows - 1) * ROW_H;
        int barsStartY = firstY + (rows - visible) * ROW_H;
        int baseHbX    = (sw - bgW) / 2 + BORDER;
        int deleteX    = baseHbX - BORDER - GAP - NUM_COL_W - GAP - DELETE_BOX_W;
        int listY      = firstY - 3;
        int listH2     = rows * ROW_H;

        // Click-to-select (no drag)
        if (!wasDragging) {
            for (int i = 0; i < visible; i++) {
                int row  = peekScrollRow + i;
                int y    = barsStartY + i * ROW_H;
                int relX = (int) (mx - baseHbX);
                if (my >= y - 3 && my < y - 3 + ROW_H && relX >= 0 && relX < bgW) {
                    int slot = relX / CELL;
                    HotbarManager.setHotbar(row, "peek-click");
                    HotbarManager.setSlot(slot);
                    HotbarManager.syncToGame();
                    mc.player.getInventory().selected = slot;
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                    if (Config.enableSounds()) {
                        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                    }
                    return true;
                }
            }
            return true;
        }

        boolean handled = false;
        int relX  = (int) (mx - baseHbX);
        int slot  = relX / CELL;
        int y0     = barsStartY + (sourceRow - peekScrollRow) * ROW_H;

        // 1) Dropped back on original slot?
        if (slot == sourceSlot
                && relX >= sourceSlot * CELL && relX < (sourceSlot + 1) * CELL
                && my >= y0 - 3 && my < y0 - 3 + ROW_H) {
            bars.get(sourceRow).setSlot(sourceSlot, draggedStack);
            handled = true;
        }

        // 2) Dropped in delete box?
        if (!handled
                && mx >= deleteX && mx < deleteX + DELETE_BOX_W
                && my >= listY   && my < listY   + listH2) {
            // clear the slot
            bars.get(sourceRow).setSlot(sourceSlot, ItemStack.EMPTY);
            handled = true;
        }

        // 3) Dropped onto another slot?
        if (!handled) {
            for (int i = 0; i < visible; i++) {
                int row = peekScrollRow + i;
                int yy  = barsStartY + i * ROW_H;
                if (my >= yy - 3 && my < yy - 3 + ROW_H
                        && relX >= 0 && relX < bgW) {
                    int targetSlot = relX / CELL;
                    ItemStack existing = bars.get(row).getSlot(targetSlot);
                    bars.get(row).setSlot(targetSlot, draggedStack);
                    bars.get(sourceRow).setSlot(sourceSlot, existing);
                    handled = true;
                    break;
                }
            }
        }

        // 4) Fallback restore (shouldn’t happen now)
        if (!handled) {
            bars.get(sourceRow).setSlot(sourceSlot, draggedStack);
        }

        // persist and sync
        HotbarManager.markDirty();
        HotbarManager.saveHotbars();
        HotbarManager.syncToGame();
        if (Config.enableSounds()) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
        }
        draggedStack = ItemStack.EMPTY;
        return true;
    }



    @Override
    public boolean isPauseScreen() { return false; }
}
