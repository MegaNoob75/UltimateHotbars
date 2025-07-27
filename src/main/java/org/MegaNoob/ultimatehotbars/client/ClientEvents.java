package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.MegaNoob.ultimatehotbars.client.HotbarGuiScreen;
import org.MegaNoob.ultimatehotbars.client.KeyBindings;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    private static Screen lastScreen = null;
    private static long lastSyncCheck = 0;
    private static final ItemStack[] lastKnownHotbar = new ItemStack[9];

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        HotbarManager.loadHotbars();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Sync selected slot
        HotbarManager.setSlot(mc.player.getInventory().selected);

        // Check for inventory changes
        long now = System.currentTimeMillis();
        if (mc.screen == null && now - lastSyncCheck > 1000) {
            lastSyncCheck = now;
            boolean changed = false;
            for (int i = 0; i < 9; i++) {
                ItemStack current = mc.player.getInventory().getItem(i);
                ItemStack cached  = lastKnownHotbar[i] != null ? lastKnownHotbar[i] : ItemStack.EMPTY;
                if (!ItemStack.matches(current, cached)) {
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

        // Sync when switching between screens
        Screen current = mc.screen;
        if (lastScreen != current) {
            if (lastScreen instanceof InventoryScreen
                    || lastScreen instanceof HotbarGuiScreen
                    || current instanceof HotbarGuiScreen) {
                HotbarManager.syncFromGame();
            }
            lastScreen = current;
        }
    }
}
