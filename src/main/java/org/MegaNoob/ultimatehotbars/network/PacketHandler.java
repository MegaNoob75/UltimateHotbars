package org.MegaNoob.ultimatehotbars.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

@SuppressWarnings({"deprecation", "removal"})
public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            // We still use the two-arg constructor (or of) here, but suppress the warning.
            ResourceLocation.of("ultimatehotbars:main", ':'),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(
                packetId++,
                SyncHotbarPacket.class,
                SyncHotbarPacket::encode,
                SyncHotbarPacket::decode,
                SyncHotbarPacket::handle
        );
    }
}
