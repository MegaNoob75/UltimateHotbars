package org.MegaNoob.ultimatehotbars;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    /**
     * Capture the hotbar right before the player actually dies,
     * so we don’t lose the items on respawn.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!event.getEntity().level().isClientSide) return;

        System.out.println("[UltimateHotbars] Capturing hotbar before death");
        HotbarManager.syncFromGame();
    }

    /**
     * After respawning, Minecraft hands you a fresh inventory.
     * Reload our saved hotbars from disk (and state) and re-apply.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerRespawnEvent event) {
        // only run on the client side
        if (!event.getEntity().level().isClientSide) return;

        System.out.println("[UltimateHotbars] Restoring hotbars on respawn");
        HotbarManager.loadHotbars();
        HotbarManager.syncToGame();
    }

    /**
     * When changing dimension, capture your old hotbar so it isn’t lost,
     * then immediately re-apply your persistent hotbars into the new dimension.
     */
    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide) return;

        System.out.println("[UltimateHotbars] Syncing from game before dimension change");
        HotbarManager.syncFromGame();                                         // ← same as before

        System.out.println("[UltimateHotbars] Restoring hotbars after dimension change");  // ← ADDED
        HotbarManager.loadHotbars();                                          // ← ADDED
        HotbarManager.syncToGame();                                           // ← ADDED
    }
}
