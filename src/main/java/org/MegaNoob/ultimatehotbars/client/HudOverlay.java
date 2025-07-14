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
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HudOverlay {

    private static long ticks = 0;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase == ClientTickEvent.Phase.END) {
            ticks++;
        }
    }

    @SubscribeEvent
    public static void renderHotbarLabel(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        // ✅ Restore the original centered label above the hotbar
        int sw = event.getWindow().getGuiScaledWidth();
        int sh = event.getWindow().getGuiScaledHeight();
        String label = "Page: " + (HotbarManager.getPage() + 1)
                + "  Hotbar: " + (HotbarManager.getHotbar() + 1);
        int centerX = (sw - font.width(label)) / 2;
        int centerY = sh - 35;
        graphics.drawString(font, label, centerX, centerY, 0xFFFFFF, true);

        // ✅ Optional debug overlay
        if (ultimatehotbars.DEBUG_MODE) {
            drawDebugOverlay(graphics, font, mc, "[DEBUG HUD - HOTBAR CONTEXT]");
        }
    }


    @SubscribeEvent
    public static void renderGuiLabel(RenderGuiEvent.Post event) {
        if (!ultimatehotbars.DEBUG_MODE) return;

        Minecraft mc = Minecraft.getInstance();
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;

        drawDebugOverlay(graphics, font, mc, "[DEBUG HUD - GUI CONTEXT]");
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
