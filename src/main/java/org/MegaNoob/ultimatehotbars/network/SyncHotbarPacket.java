package org.MegaNoob.ultimatehotbars.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncHotbarPacket {
    private final int page;
    private final int hotbarIndex;
    private final ItemStack[] stacks;

    public SyncHotbarPacket(int page, int hotbarIndex, ItemStack[] stacks) {
        this.page = page;
        this.hotbarIndex = hotbarIndex;
        this.stacks = stacks;
    }

    public static void encode(SyncHotbarPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.page);
        buf.writeInt(msg.hotbarIndex);
        buf.writeInt(msg.stacks.length);
        for (ItemStack stack : msg.stacks) {
            buf.writeItem(stack);
        }
    }

    public static SyncHotbarPacket decode(FriendlyByteBuf buf) {
        int page = buf.readInt();
        int hotbarIndex = buf.readInt();
        int len = buf.readInt();
        ItemStack[] stacks = new ItemStack[len];
        for (int i = 0; i < len; i++) {
            stacks[i] = buf.readItem();
        }
        return new SyncHotbarPacket(page, hotbarIndex, stacks);
    }

    public static void handle(SyncHotbarPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null) return;

            Inventory inv = player.getInventory();
            // Overwrite the server’s hotbar slots (0–8) with the virtual stacks
            for (int i = 0; i < msg.stacks.length; i++) {
                inv.setItem(i, msg.stacks[i]);
            }
        });
        ctx.setPacketHandled(true);
    }
}
