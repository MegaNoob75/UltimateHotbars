package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
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

    public PeekHotbarScreen() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        long window = mc.getWindow().getWindow();
        if (!InputConstants.isKeyDown(window, KeyBindings.PEEK_HOTBARS.getKey().getValue())) {
            mc.setScreen(null);
            return;
        }

        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        if (bars.isEmpty()) return;

        int rows = Config.peekVisibleRows();
        int totalBars = bars.size();
        int visible = Math.min(totalBars, rows);

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int firstY = sh - 60 - (rows - 1) * ROW_H;
        int barsStartY = firstY + (rows - visible) * ROW_H;
        int areaH = rows * ROW_H;

        int baseHbX = (sw - bgW) / 2 + BORDER;
        int numX = baseHbX - BORDER - GAP - NUM_COL_W;
        int deleteX = numX - GAP - DELETE_BOX_W;
        int listX = baseHbX + bgW + GAP;
        int topY = firstY - 3;
        int listH = areaH;

        // Delete box (visual only)
        g.fill(deleteX, topY, deleteX + DELETE_BOX_W, topY + areaH, 0x88000000);
        int redColor = ((int)((Math.sin(System.currentTimeMillis()/200.0)*0.5+0.5)*255) << 24) | 0x00FF0000;
        for (int i = 0; i < 2; i++) {
            g.fill(deleteX + i, topY + i, deleteX + DELETE_BOX_W - i, topY + i + 1, redColor);
            g.fill(deleteX + i, topY + areaH - i - 1, deleteX + DELETE_BOX_W - i, topY + areaH - i, redColor);
            g.fill(deleteX + i, topY + i, deleteX + i + 1, topY + areaH - i, redColor);
            g.fill(deleteX + DELETE_BOX_W - i - 1, topY + i, deleteX + DELETE_BOX_W - i, topY + areaH - i, redColor);
        }
        String del = "DELETE";
        int midX = deleteX + (DELETE_BOX_W - font.width("D")) / 2;
        int startY = topY + (areaH - del.length() * font.lineHeight) / 2;
        for (int i = 0; i < del.length(); i++) {
            g.drawString(font, String.valueOf(del.charAt(i)), midX, startY + i * font.lineHeight, 0xFFFFFFFF);
        }

        // Scroll arrows
        if (peekScrollRow > 0) {
            g.drawString(font, "▲", numX + (NUM_COL_W - font.width("▲")) / 2, firstY - font.lineHeight - 4, 0xFFFFFF);
        }
        if (peekScrollRow + visible < totalBars) {
            g.drawString(font, "▼", numX + (NUM_COL_W - font.width("▼")) / 2, firstY + visible * ROW_H + 2, 0xFFFFFF);
        }

        // Hotbar rows – aligned bottom
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y = barsStartY + i * ROW_H;
            g.blit(HOTBAR_TEX, baseHbX - BORDER, y - 3, 0, 0, bgW, ROW_H);
            for (int s = 0; s < Hotbar.SLOT_COUNT; s++) {
                ItemStack stack = bars.get(row).getSlot(s);
                int x = baseHbX + s * CELL + (CELL - 16) / 2;
                g.renderItem(stack, x, y);
                g.renderItemDecorations(font, stack, x, y);
            }
        }

        // Page list
        g.fill(listX, topY, listX + LIST_W, topY + listH, 0x88000000);
        int trackX = listX + LIST_W - 6;
        g.fill(trackX, topY, trackX + 4, topY + listH, 0x44000000);
        List<String> pageNames = HotbarManager.getPageNames();
        int count = pageNames.size();
        int pageMax = Math.max(0, count - rows);
        pageScrollRow = Mth.clamp(pageScrollRow, 0, pageMax);
        int selected = HotbarManager.getPage();
        for (int i = 0; i < rows; i++) {
            int idx = pageScrollRow + i;
            if (idx >= count) break;
            int y = firstY + i * ROW_H;
            if (idx == selected) {
                g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, 0x44FFFFFF);
            }
            if (mouseX >= listX && mouseX < listX + LIST_W - 6 && mouseY >= y - 3 && mouseY < y - 3 + ROW_H) {
                float[] hc = Config.highlightColor();
                int col = ((int)(hc[3] * 255) << 24) | ((int)(hc[0] * 255) << 16)
                        | ((int)(hc[1] * 255) << 8) | (int)(hc[2] * 255);
                g.fill(listX, y - 3, listX + LIST_W - 6, y - 3 + ROW_H, col);
            }
            g.drawString(font, pageNames.get(idx), listX + 2, y, 0xFFFFFF);
        }
        if (count > rows) {
            int thumbH = Math.max((listH * rows) / count, 10);
            int thumbY = topY + (pageScrollRow * (listH - thumbH)) / pageMax;
            g.fill(trackX, thumbY, trackX + 4, thumbY + thumbH, 0xAAFFFFFF);
        }

        if (dragging && !draggedStack.isEmpty()) {
            g.renderItem(draggedStack, mouseX, mouseY);
            g.renderItemDecorations(font, draggedStack, mouseX, mouseY);
        }
        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return false;
        int rows = Config.peekVisibleRows();
        int totalBars = HotbarManager.getCurrentPageHotbars().size();
        int visible = Math.min(totalBars, rows);
        int pageMax = Math.max(0, HotbarManager.getPageNames().size() - rows);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int baseHbX = (sw - bgW) / 2 + BORDER;
        int listX = baseHbX + bgW + GAP;
        int firstY = sh - 60 - (rows - 1) * ROW_H;
        int topY = firstY - 3;
        int listH = rows * ROW_H;
        if (mx >= listX && mx < listX + LIST_W && my >= topY && my < topY + listH) {
            pageScrollRow = Mth.clamp(pageScrollRow - (int)Math.signum(delta), 0, pageMax);
        } else {
            peekScrollRow = Mth.clamp(peekScrollRow - (int)Math.signum(delta), 0, totalBars - visible);
        }
        if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Minecraft mc = Minecraft.getInstance(); if (mc.player == null) return false;
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars(); if (bars.isEmpty()) return true;
        int rows = Config.peekVisibleRows();
        int totalBars = bars.size();
        int visible = Math.min(totalBars, rows);
        int pageMax = Math.max(0, HotbarManager.getPageNames().size() - rows);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int firstY = sh - 60 - (rows - 1) * ROW_H;
        int barsStartY = firstY + (rows - visible) * ROW_H;
        int baseHbX = (sw - bgW) / 2 + BORDER;
        int numX = baseHbX - BORDER - GAP - NUM_COL_W;
        int deleteX = numX - GAP - DELETE_BOX_W;
        int listX = baseHbX + bgW + GAP;
        int topY = firstY - 3;
        int listH = rows * ROW_H;
        // Page list click
        if (mx >= listX && mx < listX + LIST_W && my >= topY && my < topY + listH) {
            int clicked = pageScrollRow + (int)((my - topY) / ROW_H);
            List<String> names = HotbarManager.getPageNames();
            if (clicked >= 0 && clicked < names.size()) {
                HotbarManager.setPage(clicked, 0);
                peekScrollRow = 0;
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            }
            return true;
        }
        // Delete box click
        if (mx >= deleteX && mx < deleteX + DELETE_BOX_W && my >= topY && my < topY + listH) {
            if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
            return true;
        }
        // Hotbar click for drag/init
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y = barsStartY + i * ROW_H;
            int relX = (int)(mx - baseHbX);
            if (my >= y - 3 && my < y - 3 + ROW_H && relX >= 0 && relX < bgW) {
                sourceRow = row;
                sourceSlot = relX / CELL;
                draggedStack = bars.get(row).getSlot(sourceSlot).copy();
                potentialDrag = true;
                dragging = false;
                pressX = mx; pressY = my;
                if (Config.enableSounds() && mc.player != null) mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                return true;
            }
        }
        return true;
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
        int rows = Config.peekVisibleRows();
        int totalBars = bars.size();
        int visible = Math.min(totalBars, rows);
        int pageMax = Math.max(0, HotbarManager.getPageNames().size() - rows);
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int firstY = sh - 60 - (rows - 1) * ROW_H;
        int barsStartY = firstY + (rows - visible) * ROW_H;
        int baseHbX = (sw - bgW) / 2 + BORDER;
        int deleteX = baseHbX - BORDER - GAP - NUM_COL_W - GAP - DELETE_BOX_W;
        int listY = firstY - 3;
        int listH2 = rows * ROW_H;

        // Click-to-select (no drag)
        if (!wasDragging) {
            for (int i = 0; i < visible; i++) {
                int row = peekScrollRow + i;
                int y = barsStartY + i * ROW_H;
                int relX = (int) (mx - baseHbX);
                if (my >= y - 3 && my < y - 3 + ROW_H && relX >= 0 && relX < bgW) {
                    int slot = relX / CELL;
                    HotbarManager.setHotbar(row, "peek-click");
                    HotbarManager.setSlot(slot);
                    HotbarManager.syncToGame();
                    mc.player.getInventory().selected = slot;
                    mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
                    if (Config.enableSounds() && mc.player != null) {
                        mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                    }
                    return true;
                }
            }
            return true;
        }

        boolean handled = false;
        // Dropped back on original slot
        int relX0 = (int) (mx - baseHbX);
        int slot0 = relX0 / CELL;
        int y0 = barsStartY + (sourceRow - peekScrollRow) * ROW_H;
        if (slot0 == sourceSlot && relX0 >= 0 && relX0 < CELL && my >= y0 - 3 && my < y0 - 3 + ROW_H) {
            bars.get(sourceRow).setSlot(sourceSlot, draggedStack);
            handled = true;
        }
        // Dropped in delete box
        if (!handled && mx >= deleteX && mx < deleteX + DELETE_BOX_W && my >= listY && my < listY + listH2) {
            handled = true;
        }
        // Dropped onto another slot
        if (!handled) {
            for (int i = 0; i < visible; i++) {
                int row = peekScrollRow + i;
                int y = barsStartY + i * ROW_H;
                int relX = (int) (mx - baseHbX);
                if (my >= y - 3 && my < y - 3 + ROW_H && relX >= 0 && relX < bgW) {
                    int slot = relX / CELL;
                    ItemStack existing = bars.get(row).getSlot(slot);
                    bars.get(row).setSlot(slot, draggedStack);
                    bars.get(sourceRow).setSlot(sourceSlot, existing);
                    handled = true;
                    break;
                }
            }
        }
        // Fallback: restore to original
        if (!handled) {
            bars.get(sourceRow).setSlot(sourceSlot, draggedStack);
        }

        HotbarManager.markDirty();
        HotbarManager.saveHotbars();
        HotbarManager.syncToGame();
        if (Config.enableSounds() && mc.player != null) {
            mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
        }
        draggedStack = ItemStack.EMPTY;
        return true;
    }


    @Override
    public boolean isPauseScreen() { return false; }
}
