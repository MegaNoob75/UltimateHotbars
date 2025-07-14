package org.MegaNoob.ultimatehotbars;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonEvents {

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        if (!event.getEntity().level().isClientSide) return;

        System.out.println("[UltimateHotbars] Syncing from game on player death");
        HotbarManager.syncFromGame();
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!event.getEntity().level().isClientSide) return;

        System.out.println("[UltimateHotbars] Syncing from game on dimension change");
        HotbarManager.syncFromGame();
    }
}
