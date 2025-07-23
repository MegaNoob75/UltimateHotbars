package org.MegaNoob.ultimatehotbars.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Handles registration of network packets for the UltimateHotbars mod.
 */
public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";

    /**
     * The main channel for sending/receiving packets.
     */
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("ultimatehotbars", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int packetId = 0;

    /**
     * Registers all packets on this channel. Call during mod setup.
     */
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
