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
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Transparent screen that displays all hotbar rows when the peek key is held.
 * Supports scrolling, clicking, hover highlighting, and a scrollbar indicator.
 */
@Mod.EventBusSubscriber(
        modid = ultimatehotbars.MODID,
        value = Dist.CLIENT,
        bus   = Mod.EventBusSubscriber.Bus.FORGE
)
public class PeekHotbarScreen extends Screen {
    private static final ResourceLocation HOTBAR_TEX = new ResourceLocation("textures/gui/widgets.png");
    private static final int MAX_ROWS = 4;
    private int peekScrollRow = 0;

    public PeekHotbarScreen() {
        super(Component.empty());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        // Auto-close when peek key is released
        if (!InputConstants.isKeyDown(window, KeyBindings.PEEK_HOTBARS.getKey().getValue())) {
            mc.setScreen(null);
            return;
        }

        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        List<Hotbar> pageHotbars = HotbarManager.getCurrentPageHotbars();
        int total = pageHotbars.size();
        if (total > 0) {
            int visible = Math.min(total, MAX_ROWS);
            int cell = 20, border = 1, rowH = cell + 2;
            int bgW = Hotbar.SLOT_COUNT * cell + border * 2;
            int startX = (sw - bgW) / 2;
            int firstY = sh - 22 - 10 - visible * rowH;

            // Draw each row
            for (int i = 0; i < visible; i++) {
                int y = firstY + i * rowH;
                // Background strip
                g.blit(HOTBAR_TEX, startX - border, y - 3, 0, 0, bgW, rowH);

                // Hover highlight
                if (mouseY >= y - 3 && mouseY < y - 3 + rowH) {
                    float[] hc = Config.highlightColor();
                    int color = ((int)(hc[3] * 255) << 24)
                            | ((int)(hc[0] * 255) << 16)
                            | ((int)(hc[1] * 255) << 8)
                            |  (int)(hc[2] * 255);
                    g.fill(startX - border, y - 3, startX - border + bgW, y - 3 + rowH, color);
                }

                // Items
                Hotbar hb = pageHotbars.get(peekScrollRow + i);
                for (int s = 0; s < Hotbar.SLOT_COUNT; s++) {
                    ItemStack stack = hb.getSlot(s);
                    int x = startX + s * cell + (cell - 16) / 2;
                    g.renderItem(stack, x, y);
                    g.renderItemDecorations(mc.font, stack, x, y);
                }
            }

            // Scrollbar indicator
            if (total > visible) {
                int trackX = (sw - bgW) / 2 + bgW + 2;
                int trackY = firstY;
                int trackH = visible * rowH;
                int trackW = 4;
                // Track background
                g.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x44000000);
                // Thumb
                float ratio = (float) visible / total;
                int thumbH = Math.max(1, (int)(ratio * trackH));
                float scrollRatio = peekScrollRow / (float)(total - visible);
                int thumbY = trackY + (int)(scrollRatio * (trackH - thumbH));
                g.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, 0xFFFFFFFF);
            }
        }

        super.render(g, mouseX, mouseY, partialTicks);
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
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        List<Hotbar> bars = HotbarManager.getCurrentPageHotbars();
        int visible = Math.min(bars.size(), MAX_ROWS);
        int cell = 20, border = 1, rowH = cell + 2;
        int bgW = Hotbar.SLOT_COUNT * cell + border * 2;
        int startX = (sw - bgW) / 2;
        int firstY = sh - 22 - 10 - visible * rowH;

        for (int i = 0; i < visible; i++) {
            int y = firstY + i * rowH;
            if (my >= y - 3 && my < y - 3 + rowH) {
                int relX = (int)(mx - (startX - border));
                if (relX >= 0 && relX < bgW) {
                    int slot = relX / cell;
                    int row  = peekScrollRow + i;
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
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
}
