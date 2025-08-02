package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.client.KeyBindings;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

/**
 * Toggles the transparent peek screen on/off exactly once per Alt press/release,
 * using raw GLFW polling so there’s no flicker.
 */
@Mod.EventBusSubscriber(
        modid = ultimatehotbars.MODID,
        value = Dist.CLIENT,
        bus   = Mod.EventBusSubscriber.Bus.FORGE
)
public class PeekHotbarController {
    private static boolean screenOpen = false;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent event) {
        if (event.phase != ClientTickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        // Raw check: is the PEEK_HOTBARS key currently down?
        boolean down = InputConstants.isKeyDown(
                window,
                KeyBindings.PEEK_HOTBARS.getKey().getValue()
        );

        if (down && !screenOpen) {
            // Key just pressed → open our transparent peek screen
            mc.setScreen(new PeekHotbarScreen());
            screenOpen = true;
        } else if (!down && screenOpen) {
            // Key just released → close it
            if (mc.screen instanceof PeekHotbarScreen) {
                mc.setScreen(null);
            }
            screenOpen = false;
        }
    }
}
