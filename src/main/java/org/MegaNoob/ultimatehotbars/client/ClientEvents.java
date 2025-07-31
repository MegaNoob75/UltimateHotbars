package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.MegaNoob.ultimatehotbars.Hotbar;

@Mod.EventBusSubscriber(
        modid = ultimatehotbars.MODID,
        bus   = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT
)
public class ClientEvents {
    private static Screen lastScreen    = null;
    private static long   lastSyncCheck = 0;
    private static final ItemStack[] lastKnownHotbar = new ItemStack[9];


    /** Load persisted hotbars when the player logs in */
    @SubscribeEvent
    public static void onPlayerLogin(final ClientPlayerNetworkEvent.LoggingIn event) {
        HotbarManager.loadHotbars();
    }

    /** Each client tick (END phase), run our tick() then do the normal sync checks */
    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        // run the key-handling every tick, even in GUIs
        org.MegaNoob.ultimatehotbars.client.KeyInputHandler.tick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // sync selected hotbar slot
        HotbarManager.setSlot(mc.player.getInventory().selected);

        // once per second when no screen is open, detect manual hotbar changes
        long now = System.currentTimeMillis();
        if (mc.screen == null && now - lastSyncCheck > 1000) {
            lastSyncCheck = now;
            boolean changed = false;
            for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
                ItemStack curr   = mc.player.getInventory().getItem(i);
                ItemStack cached = lastKnownHotbar[i] != null
                        ? lastKnownHotbar[i]
                        : ItemStack.EMPTY;
                if (!ItemStack.matches(curr, cached)) {
                    changed = true;
                    break;
                }
            }
            if (changed) {
                if (HotbarManager.syncFromGameIfChanged()) {
                    HotbarManager.saveHotbars();
                }
                for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
                    lastKnownHotbar[i] = mc.player.getInventory().getItem(i).copy();
                }
            }
        }

        // *** HERE’S THE MISSING DECLARATION ***
        Screen current = mc.screen;

        // sync on screen open/close
        if (lastScreen instanceof InventoryScreen
                || lastScreen instanceof HotbarGuiScreen
                || current   instanceof HotbarGuiScreen) {
            if (HotbarManager.syncFromGameIfChanged()) {
                HotbarManager.saveHotbars();
            }
        }
        lastScreen = current;
    }


    /** Save on item toss (Q/drop) */
    @SubscribeEvent
    public static void onItemToss(final ItemTossEvent event) {
        if (event.getPlayer() == Minecraft.getInstance().player) {
            // Only pull & save if the real hotbar actually changed (throws an item out)
            if (HotbarManager.syncFromGameIfChanged()) {
                HotbarManager.saveHotbars();
            }
            // (Optional) push the new virtual state back into the game immediately:
            HotbarManager.syncToGame();
        }
    }


/** Save on world pickup */
@SubscribeEvent
public static void onItemPickup(final EntityItemPickupEvent event) {
    if (event.getEntity() == Minecraft.getInstance().player) {
        // Only pull & save if the real hotbar actually changed (picked up an item)
        if (HotbarManager.syncFromGameIfChanged()) {
            HotbarManager.saveHotbars();
        }
        // Immediately update the client inventory from our virtual hotbar
        HotbarManager.syncToGame();
    }
}


/** Save after any container screen closes (excluding your Hotbar GUI) */
@SubscribeEvent
public static void onScreenClose(final ScreenEvent.Closing event) {
    Screen screen = event.getScreen();
    if (screen instanceof AbstractContainerScreen<?>
            && !(screen instanceof HotbarGuiScreen)) {
        // Only pull & save if the real hotbar actually changed
        if (HotbarManager.syncFromGameIfChanged()) {
            HotbarManager.saveHotbars();
        }
        // Update the client inventory from our virtual hotbar
        HotbarManager.syncToGame();
    }
}

}
