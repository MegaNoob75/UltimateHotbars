package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

@Mod.EventBusSubscriber(
        modid = ultimatehotbars.MODID,
        bus   = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public class PeekDragGhostRenderer {

    @SubscribeEvent
    public static void onScreenRenderPost(ScreenEvent.Render.Post event) {
        Screen s = event.getScreen();
        if (!(s instanceof PeekHotbarScreen peek)) return;

        // Only draw when actually dragging something in Peek (after threshold)
        if (!peek.isPeekDragging()) return;

        ItemStack stack = peek.getPeekDraggedStack();
        if (stack == null || stack.isEmpty()) return;

        GuiGraphics g = event.getGuiGraphics();
        int mx = (int) event.getMouseX();
        int my = (int) event.getMouseY();

        // Draw above everything else
        var pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, 400); // high z to guarantee on-top

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();

        // Slight offset so the cursor sits near the top-left like vanilla drags
        int drawX = mx - 8;
        int drawY = my - 8;

        g.renderItem(stack, drawX, drawY);
        g.renderItemDecorations(net.minecraft.client.Minecraft.getInstance().font, stack, drawX, drawY);

        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        pose.popPose();
    }

}
