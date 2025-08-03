package org.MegaNoob.ultimatehotbars;

import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.common.MinecraftForge;
import org.MegaNoob.ultimatehotbars.client.ClientEvents;
import org.MegaNoob.ultimatehotbars.network.PacketHandler;

@Mod(ultimatehotbars.MODID)
public class ultimatehotbars {
    public static final String MODID = "ultimatehotbars";
    public static boolean DEBUG_MODE = false;

    public ultimatehotbars(final FMLJavaModLoadingContext context) {
        // Register our sync packet
        PacketHandler.register();

        // Register mod event listeners
        IEventBus modBus = context.getModEventBus();
        modBus.addListener(this::onClientSetup);

        // Register key bindings during RegisterKeyMappingsEvent
       // modBus.addListener(KeyBindings::register);

        // Register client event handlers (ticks, GUI, etc.)
        MinecraftForge.EVENT_BUS.register(new ClientEvents());

        // Register client-side config spec
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.SPEC);
    }

    private void onClientSetup(final FMLClientSetupEvent event) {
        // Load saved hotbar pages & state
        HotbarManager.loadHotbars();
    }
}
