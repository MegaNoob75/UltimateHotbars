package org.MegaNoob.ultimatehotbars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Syncs the player's "carried" (cursor) stack from client -> server.
 * Prevents client/server desync that can cause items to be dropped.
 */
public class SetCarriedPacket {
    private final ItemStack stack;

    public SetCarriedPacket(ItemStack stack) {
        this.stack = (stack == null) ? ItemStack.EMPTY : stack;
    }

    public static void encode(SetCarriedPacket msg, FriendlyByteBuf buf) {
        buf.writeItem(msg.stack);
    }

    public static SetCarriedPacket decode(FriendlyByteBuf buf) {
        return new SetCarriedPacket(buf.readItem());
    }

    public static void handle(SetCarriedPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;
            // Update server-side carried and broadcast so both sides agree
            player.containerMenu.setCarried(msg.stack);
            player.containerMenu.broadcastChanges();
        });
        ctx.setPacketHandled(true);
    }
}
