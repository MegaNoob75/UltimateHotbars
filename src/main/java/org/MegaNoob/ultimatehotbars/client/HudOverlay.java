package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HudOverlay {

    private static long ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == ClientTickEvent.Phase.END) {
            ticks++;
            // Sync any real‐hotbar changes (including stack counts) into your virtual hotbars…
            if (HotbarManager.syncFromGameIfChanged()) {
                // …and immediately persist them per‐world/per‐server
                HotbarManager.saveHotbars();
            }
        }
    }


    @SubscribeEvent
    public static void renderHotbarLabel(RenderGuiOverlayEvent.Post event) {
        // only run on the vanilla hotbar overlay
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;
        if (!Config.showHudLabel) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        // screen dimensions
        int sw = event.getWindow().getGuiScaledWidth();
        int sh = event.getWindow().getGuiScaledHeight();

        // **NEW**: lookup the custom page name
        String pageName = HotbarManager.getPageNames().get(HotbarManager.getPage());
        // build the label: "<CustomPageName>  Hotbar: <1-9>"
        String label = pageName + " / Hotbar - " + (HotbarManager.getHotbar() + 1);

        int textWidth  = font.width(label);
        int centerX    = (sw - textWidth) / 2;
        int centerY    = sh - 35;

        if (Config.showHudLabelBackground) {
            float[] bg = Config.hudLabelBackgroundColor;
            int bgColor = ((int)(bg[3] * 255) << 24)
                    | ((int)(bg[0] * 255) << 16)
                    | ((int)(bg[1] * 255) <<  8)
                    |  (int)(bg[2] * 255);
            graphics.fill(centerX - 4, centerY - 2, centerX + textWidth + 4, centerY + 10, bgColor);
        }

        float[] tc = Config.hudLabelTextColor;
        int color = ((int)(tc[3] * 255) << 24)
                | ((int)(tc[0] * 255) << 16)
                | ((int)(tc[1] * 255) <<  8)
                |  (int)(tc[2] * 255);

        graphics.drawString(font, label, centerX, centerY, color, true);

        // preserve your debug overlay if enabled
        if (Config.showDebugOverlay) {
            drawDebugOverlay(graphics, font, mc, "[DEBUG HUD - HOTBAR CONTEXT]");
        }
    }


    @SubscribeEvent
    public static void renderGuiLabel(RenderGuiEvent.Post event) {
        if (!Config.showDebugOverlay) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        drawDebugOverlay(graphics, font, mc, "[DEBUG HUD - GUI CONTEXT]");
    }

    @SubscribeEvent
    public static void renderGuiSideLabel(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?>)) {
            return;
        }
        if (!Config.showHudLabel) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        GuiGraphics graphics = event.getGuiGraphics();

        // build your label
        String pageName = HotbarManager.getPageNames().get(HotbarManager.getPage());
        String label    = pageName + " / Hotbar - " + (HotbarManager.getHotbar() + 1);
        int textWidth   = font.width(label);

        // center-bottom coords (35px up)
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        int x  = (sw - textWidth) / 2;
        int y  = sh - 35;

        // optional background
        if (Config.showHudLabelBackground) {
            float[] bg = Config.hudLabelBackgroundColor;
            int bgColor = ((int)(bg[3]*255)<<24)
                    | ((int)(bg[0]*255)<<16)
                    | ((int)(bg[1]*255)<< 8)
                    |  (int)(bg[2]*255);
            graphics.fill(x - 4, y - 2, x + textWidth + 4, y + 10, bgColor);
        }

        // text color
        float[] tc = Config.hudLabelTextColor;
        int color = ((int)(tc[3]*255)<<24)
                | ((int)(tc[0]*255)<<16)
                | ((int)(tc[1]*255)<< 8)
                |  (int)(tc[2]*255);
        graphics.drawString(font, label, x, y, color, true);
    }



    private static void drawDebugOverlay(GuiGraphics graphics, Font font, Minecraft mc, String contextLabel) {
        int x = 4;
        int y = 4;
        int line = 0;

        graphics.drawString(font, contextLabel, x, y + 12 * line++, 0xFF66FF);
        graphics.drawString(font, "Ticks: " + ticks, x, y + 12 * line++, 0xAAAAAA);

        graphics.drawString(font, "→ Live State", x, y + 12 * line++, 0xFFFFFF);
        graphics.drawString(font, "Page: " + HotbarManager.getPage(), x + 6, y + 12 * line++, 0xFFFFFF);
        graphics.drawString(font, "Hotbar: " + HotbarManager.getHotbar(), x + 6, y + 12 * line++, 0xFFFFFF);
        graphics.drawString(font, "Slot: " + HotbarManager.getSlot(), x + 6, y + 12 * line++, 0xFFFFFF);

        if (mc.player != null) {
            graphics.drawString(font, "MC Inv Slot: " + mc.player.getInventory().selected, x + 6, y + 12 * line++, 0xCCCCCC);
        }

        graphics.drawString(font, "→ Last Action", x, y + 12 * line++, 0x88FF88);
        graphics.drawString(font, "Last Hotbar Set: " + (HotbarManager.lastHotbarSet + 1), x + 6, y + 12 * line++, 0x88FF88);
        graphics.drawString(font, "Set From: " + HotbarManager.lastHotbarSource, x + 6, y + 12 * line++, 0x88FF88);

        graphics.drawString(font, "→ Last Saved", x, y + 12 * line++, 0xFF8888);
        graphics.drawString(font, "Saved Page: " + HotbarManager.lastSavedPage, x + 6, y + 12 * line++, 0xFF8888);
        graphics.drawString(font, "Saved Hotbar: " + HotbarManager.lastSavedHotbar, x + 6, y + 12 * line++, 0xFF8888);
        graphics.drawString(font, "Saved Slot: " + HotbarManager.lastSavedSlot, x + 6, y + 12 * line++, 0xFF8888);
    }
}
