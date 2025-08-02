package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
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
 * While open:
 *  • Renders all hotbar rows above the vanilla bar
 *  • mouseScrolled → pages through rows
 *  • mouseClicked → picks a slot & hotbar
 *  • Checks raw key state in render(), and auto-closes on Alt release
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
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        // If Alt is released, close this screen immediately:
        if (!InputConstants.isKeyDown(window, KeyBindings.PEEK_HOTBARS.getKey().getValue())) {
            mc.setScreen(null);
            return;
        }

        // Draw peeked hotbars above the vanilla bar:
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

            for (int i = 0; i < visible; i++) {
                int y = firstY + i * rowH;
                // background strip
                g.blit(HOTBAR_TEX, startX - border, y - 3, 0, 0, bgW, rowH);
                // items
                Hotbar hb = pageHotbars.get(peekScrollRow + i);
                for (int s = 0; s < Hotbar.SLOT_COUNT; s++) {
                    ItemStack stack = hb.getSlot(s);
                    int x = startX + s * cell + (cell - 16) / 2;
                    g.renderItem(stack, x, y);
                    g.renderItemDecorations(mc.font, stack, x, y);
                }
            }
        }

        super.render(g, mouseX, mouseY, pt);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        // Change page of hotbar rows
        int total = HotbarManager.getCurrentPageHotbars().size();
        int visible = Math.min(total, MAX_ROWS);
        peekScrollRow = Math.max(0, Math.min(peekScrollRow - (int)Math.signum(delta), total - visible));
        return true; // consume
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
