package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

import net.minecraftforge.client.event.ScreenEvent.Closing;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.client.HotbarGuiScreen;
import org.MegaNoob.ultimatehotbars.client.KeyInputHandler;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

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
        KeyInputHandler.tick();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // sync selected hotbar slot
        HotbarManager.setSlot(mc.player.getInventory().selected);

        // once per second when no screen is open, detect manual hotbar changes
        long now = System.currentTimeMillis();
        if (mc.screen == null && now - lastSyncCheck > 1000) {
            lastSyncCheck = now;
            boolean changed = false;
            for (int i = 0; i < 9; i++) {
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
                HotbarManager.syncFromGame();
                for (int i = 0; i < 9; i++) {
                    lastKnownHotbar[i] = mc.player.getInventory().getItem(i).copy();
                }
            }
        }

        // sync on screen open/close
        Screen current = mc.screen;
        if (lastScreen != current) {
            if (lastScreen instanceof InventoryScreen
                    || lastScreen instanceof HotbarGuiScreen
                    || current   instanceof HotbarGuiScreen) {
                HotbarManager.syncFromGame();
            }
            lastScreen = current;
        }
    }

    /** Save on item toss (Q/drop) */
    @SubscribeEvent
    public static void onItemToss(final ItemTossEvent event) {
        if (event.getPlayer() == Minecraft.getInstance().player) {
            HotbarManager.syncFromGame();
            HotbarManager.markDirty();
            HotbarManager.syncToGame();  // if you need an immediate client update
        }
    }

    /** Save on world pickup */
    @SubscribeEvent
    public static void onItemPickup(final EntityItemPickupEvent event) {
        if (event.getEntity() == Minecraft.getInstance().player) {
            HotbarManager.syncFromGame();
            HotbarManager.markDirty();
            HotbarManager.syncToGame();  // if you need an immediate client update
        }
    }

    /** Save after any container screen closes (excluding your Hotbar GUI) */
    @SubscribeEvent
    public static void onScreenClose(final Closing event) {
        Screen screen = event.getScreen();
        if (screen instanceof AbstractContainerScreen<?>
                && !(screen instanceof HotbarGuiScreen)) {
            HotbarManager.syncFromGame();
            HotbarManager.markDirty();
            HotbarManager.syncToGame();  // if you need an immediate client update
        }
    }
}
