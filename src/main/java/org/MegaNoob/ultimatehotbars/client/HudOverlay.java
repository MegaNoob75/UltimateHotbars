package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class HudOverlay {
    /** HUD overlay: centered label above in-game hotbar */
    @SubscribeEvent
    public static void renderHotbarLabel(RenderGuiOverlayEvent.Post event) {
        if (!event.getOverlay().id().equals(VanillaGuiOverlay.HOTBAR.id())) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        int sw = event.getWindow().getGuiScaledWidth();
        int sh = event.getWindow().getGuiScaledHeight();
        Font font = mc.font;

        String label = "Page: " + (HotbarManager.getPage() + 1)
                + "  Hotbar: " + (HotbarManager.getHotbar() + 1);

        int centerX = (sw - font.width(label)) / 2;
        int centerY = sh - 35;
        graphics.drawString(font, label, centerX, centerY, 0xFFFFFF, true);
    }

    /** GUI screens: side label to the left of inventory-like GUIs */
    @SubscribeEvent
    public static void renderGuiSideLabel(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;

        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;

        String label = "Page: " + (HotbarManager.getPage() + 1)
                + "  Hotbar: " + (HotbarManager.getHotbar() + 1);

        int labelX = container.getGuiLeft() - 10 - font.width(label);
        int labelY = container.getGuiTop() + container.getYSize() - 24;

        GuiGraphics graphics = event.getGuiGraphics();
        graphics.drawString(font, label, labelX, labelY, 0xFFFFFF, true);
    }
}
