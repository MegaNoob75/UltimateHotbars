package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.client.KeyBindings;
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
    private static final int MAX_ROWS = 4;
    private static final int CELL = 20;
    private static final int BORDER = 1;
    private static final int ROW_H = CELL + 2;
    private static final int DELETE_BOX_W = 20;
    private static final int NUM_COL_W = 20;
    private static final int GAP = 4;
    private static final double DRAG_THRESHOLD = 5.0;

    private int peekScrollRow = 0;

    // Drag state
    private boolean potentialDrag = false, dragging = false;
    private double pressX, pressY;
    private int sourceRow, sourceSlot;
    private ItemStack draggedStack = ItemStack.EMPTY;

    public PeekHotbarScreen() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        // Close on Alt release
        if (!InputConstants.isKeyDown(window, KeyBindings.PEEK_HOTBARS.getKey().getValue())) {
            mc.setScreen(null);
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        int total = bars.size();
        if (total > 0) {
            int visible = Math.min(total, MAX_ROWS);
            int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
            int totalW = DELETE_BOX_W + GAP + NUM_COL_W + GAP + bgW;
            int left = (sw - totalW) / 2;

            int deleteX = left;
            int numX    = deleteX + DELETE_BOX_W + GAP;
            int hbX     = numX + NUM_COL_W + GAP;

            int firstY = sh - 22 - 10 - visible * ROW_H;

            // Pulsating red for delete-box border
            float pulse = (float)((Math.sin(System.currentTimeMillis() / 200.0) * 0.5 + 0.5));
            int alpha = (int)(pulse * 255);
            int redColor = (alpha << 24) | 0x00FF0000;

            // Draw delete box background & border
            int delH = Math.max(ROW_H, visible * ROW_H);
            g.fill(deleteX, firstY - 3, deleteX + DELETE_BOX_W, firstY - 3 + delH, 0x88000000);
            for (int i = 0; i < 2; i++) {
                g.fill(deleteX + i, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 2 + i, redColor);
                g.fill(deleteX + i, firstY - 3 + delH - i - 1, deleteX + DELETE_BOX_W - i, firstY - 2 + delH - i - 1, redColor);
                g.fill(deleteX + i, firstY - 3 + i, deleteX + i + 1, firstY - 2 + delH - i, redColor);
                g.fill(deleteX + DELETE_BOX_W - i - 1, firstY - 3 + i, deleteX + DELETE_BOX_W - i, firstY - 2 + delH - i, redColor);
            }
            // Draw "Delete" vertically centered
            String del = "DELETE";
            int midX = deleteX + (DELETE_BOX_W - font.width("D")) / 2;
            int textStartY = firstY - 3 + (delH - del.length() * font.lineHeight) / 2;
            for (int i = 0; i < del.length(); i++) {
                g.drawString(font, String.valueOf(del.charAt(i)), midX, textStartY + i * font.lineHeight, 0xFFFFFFFF);
            }

            // Draw rows: numbers + hotbar
            for (int i = 0; i < visible; i++) {
                int row = peekScrollRow + i;
                int y = firstY + i * ROW_H;

                // HOTBAR NUMBER
                String num = String.valueOf(row + 1);
                int numXc = numX + (NUM_COL_W - font.width(num)) / 2;
                int numYc = y + (ROW_H - font.lineHeight) / 2;
                g.drawString(font, num, numXc, numYc, 0xFFFFFF);

                // HOVER HIGHLIGHT
                if (mouseY >= y - 3 && mouseY < y - 3 + ROW_H) {
                    float[] c = Config.highlightColor();
                    int col = ((int)(c[3]*255)<<24)
                            | ((int)(c[0]*255)<<16)
                            | ((int)(c[1]*255)<<8)
                            |  (int)(c[2]*255);
                    g.fill(hbX - BORDER, y - 3, hbX - BORDER + bgW, y - 3 + ROW_H, col);
                }

                // HOTBAR SLOTS
                Hotbar hb = bars.get(row);
                // background
                g.blit(HOTBAR_TEX, hbX - BORDER, y - 3, 0, 0, bgW, ROW_H);
                for (int s = 0; s < Hotbar.SLOT_COUNT; s++) {
                    ItemStack stack = hb.getSlot(s);
                    int x = hbX + s * CELL + (CELL - 16) / 2;
                    g.renderItem(stack, x, y);
                    g.renderItemDecorations(font, stack, x, y);
                }
            }
        }

        // Dragged stack under cursor
        if (dragging && !draggedStack.isEmpty()) {
            g.renderItem(draggedStack, mouseX, mouseY);
            g.renderItemDecorations(font, draggedStack, mouseX, mouseY);
        }

        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int total = HotbarManager.getCurrentPageHotbars().size();
        int visible = Math.min(total, MAX_ROWS);
        peekScrollRow = Math.max(0, Math.min(peekScrollRow - (int)Math.signum(delta), total - visible));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
        // Identify slot under click (like getSlotCoords)
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int sh = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        int total = bars.size();
        int visible = Math.min(total, MAX_ROWS);
        int bgW = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int totalW = DELETE_BOX_W + GAP + NUM_COL_W + GAP + bgW;
        int left = (sw - totalW)/2;
        int deleteX = left;
        int numX    = deleteX + DELETE_BOX_W + GAP;
        int hbX     = numX + NUM_COL_W + GAP;
        int firstY  = sh - 22 - 10 - visible*ROW_H;

        // Check delete-box click
        int delH = Math.max(ROW_H, visible * ROW_H);
        if (mx >= deleteX && mx < deleteX+DELETE_BOX_W && my >= firstY-3 && my < firstY-3+delH) {
            // deleting nothing on click alone
            return true;
        }

        // Check slot click → start potential drag
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y = firstY + i*ROW_H;
            if (my >= y-3 && my < y-3+ROW_H) {
                int relX = (int)(mx - hbX);
                if (relX >= 0 && relX < bgW) {
                    int slot = relX / CELL;
                    Hotbar hb = bars.get(row);
                    ItemStack stack = hb.getSlot(slot);
                    potentialDrag = true;
                    dragging = false;
                    pressX = mx; pressY = my;
                    sourceRow  = row;
                    sourceSlot = slot;
                    draggedStack = stack.copy();
                    return true;
                }
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (potentialDrag && !dragging) {
            if (Math.hypot(mx - pressX, my - pressY) >= DRAG_THRESHOLD) {
                dragging = true;
                // remove from source slot
                HotbarManager.getCurrentPageHotbars().get(sourceRow).setSlot(sourceSlot, ItemStack.EMPTY);
                HotbarManager.markDirty();
                HotbarManager.saveHotbars();
                HotbarManager.syncToGame();
            }
            return dragging;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        if (!potentialDrag) {
            return super.mouseReleased(mx, my, button);
        }

        // Capture and reset drag flags
        boolean wasDragging = dragging;
        potentialDrag = false;
        dragging = false;

        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();

        // Layout calculations (must match render)
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        int total   = bars.size();
        int visible = Math.min(total, MAX_ROWS);
        int bgW     = Hotbar.SLOT_COUNT * CELL + BORDER * 2;
        int totalW  = DELETE_BOX_W + GAP + NUM_COL_W + GAP + bgW;
        int left    = (sw - totalW) / 2;
        int deleteX = left;
        int hbX     = deleteX + DELETE_BOX_W + GAP + NUM_COL_W + GAP;
        int firstY  = sh - 22 - 10 - visible * ROW_H;
        int delH    = Math.max(ROW_H, visible * ROW_H);

        // 1) If it was a simple click (no drag), treat it as selection
        if (!wasDragging) {
            // Find row under mouse
            for (int i = 0; i < visible; i++) {
                int row = peekScrollRow + i;
                int y   = firstY + i * ROW_H;
                if (my >= y - 3 && my < y - 3 + ROW_H) {
                    int relX = (int)(mx - hbX);
                    if (relX >= 0 && relX < bgW) {
                        int slot = relX / CELL;
                        // Apply selection
                        HotbarManager.setHotbar(row, "peek-click");
                        HotbarManager.setSlot(slot);
                        HotbarManager.syncToGame();
                        mc.player.getInventory().selected = slot;
                        mc.player.connection.send(
                                new ServerboundSetCarriedItemPacket(slot)
                        );
                        return true;
                    }
                }
            }
            // Click outside slots does nothing
            return true;
        }

        // 2) Otherwise, it was a real drag → drop logic as before

        boolean handled = false;

        // 2a) Same‐slot drop? restore original stack
        for (int i = 0; i < visible; i++) {
            int row = peekScrollRow + i;
            int y   = firstY + i * ROW_H;
            if (my >= y - 3 && my < y - 3 + ROW_H) {
                int relX = (int)(mx - hbX);
                int slot = relX / CELL;
                if (relX >= 0 && relX < bgW && row == sourceRow && slot == sourceSlot) {
                    bars.get(sourceRow).setSlot(sourceSlot, draggedStack);
                    handled = true;
                }
                break;
            }
        }

        // 2b) Dropped in delete box? leave it removed
        if (!handled && mx >= deleteX && mx < deleteX + DELETE_BOX_W
                && my >= firstY - 3 && my < firstY - 3 + delH) {
            handled = true;
        }

        // 2c) Dropped on a different slot? swap
        if (!handled) {
            outer: for (int i = 0; i < visible; i++) {
                int row = peekScrollRow + i;
                int y   = firstY + i * ROW_H;
                if (my >= y - 3 && my < y - 3 + ROW_H) {
                    int relX = (int)(mx - hbX);
                    if (relX >= 0 && relX < bgW) {
                        int slot = relX / CELL;
                        Hotbar target = bars.get(row);
                        ItemStack existing = target.getSlot(slot);
                        target.setSlot(slot, draggedStack);
                        bars.get(sourceRow).setSlot(sourceSlot, existing);
                        handled = true;
                    }
                    break outer;
                }
            }
        }

        // 2d) Else: drop back into original slot
        if (!handled) {
            bars.get(sourceRow).setSlot(sourceSlot, draggedStack);
        }

        // Persist & sync once per drop
        HotbarManager.markDirty();
        HotbarManager.saveHotbars();
        HotbarManager.syncToGame();

        // Clear drag stack
        draggedStack = ItemStack.EMPTY;
        return true;
    }



    @Override public boolean isPauseScreen() { return false; }
}
