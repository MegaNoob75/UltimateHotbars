package org.MegaNoob.ultimatehotbars.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerJoinHandler {

    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Player just joined a world — time to overwrite Minecraft's hotbar
        HotbarManager.loadHotbars();    // Load our data (may be empty)
        HotbarManager.syncToGame();     // Force overwrite the player's hotbar
    }
}
