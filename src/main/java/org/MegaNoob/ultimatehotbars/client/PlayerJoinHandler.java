package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.ClientDataConfig;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerJoinHandler {

    @SubscribeEvent
    public static void onClientPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Load all hotbars from disk
        HotbarManager.loadHotbars();

        // Load last used hotbar and slot from saved config
        ClientDataConfig.load();
        int savedHotbar = ClientDataConfig.getInt("lastHotbar", 0);
        int savedSlot = ClientDataConfig.getInt("lastSlot", 0);

        HotbarManager.setHotbar(savedHotbar);
        HotbarManager.setSlot(savedSlot);
        HotbarManager.syncToGame();

        // Also update the actual selected slot (main hand)
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getInventory().selected = savedSlot;
        }
    }

    @SubscribeEvent
    public static void onClientPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        // Save current hotbar and slot to runtime config
        ClientDataConfig.setInt("lastHotbar", HotbarManager.getHotbar());
        ClientDataConfig.setInt("lastSlot", HotbarManager.getSlot());
        ClientDataConfig.save();
    }
}
