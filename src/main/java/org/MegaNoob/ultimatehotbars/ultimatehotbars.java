package org.MegaNoob.ultimatehotbars;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import org.MegaNoob.ultimatehotbars.client.ClientEvents;
import org.MegaNoob.ultimatehotbars.network.PacketHandler;


@Mod(ultimatehotbars.MODID)
public class ultimatehotbars {
    public static final String MODID = "ultimatehotbars";
    public static final int HOTBARS_PER_PAGE = 10;  // how many hotbars per page
    public static final int MAX_PAGES        = 100; // total pages available

    public ultimatehotbars(final FMLJavaModLoadingContext context) {
        // Register our sync packet
        PacketHandler.register();

        IEventBus modBus = context.getModEventBus();
        modBus.addListener(this::onClientSetup);

        MinecraftForge.EVENT_BUS.register(new ClientEvents());

        // Save the hotbar when Minecraft is closing
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            HotbarManager.syncFromGame();
        }));
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        HotbarManager.loadHotbars();  // OK to keep, loads the saved data early
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }

}
