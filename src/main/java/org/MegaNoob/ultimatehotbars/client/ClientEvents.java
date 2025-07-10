package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "ultimatehotbars", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {
    private boolean didInitialSync = false;
    private int lastSelectedSlot = -1;

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(KeyBindings.OPEN_GUI);
        event.register(KeyBindings.CLEAR_HOTBAR);
        // Register additional keybindings here
    }

    @SubscribeEvent
    public void onScreenKey(ScreenEvent.KeyPressed.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        InputConstants.Key inputKey = InputConstants.getKey(keyCode, scanCode);

        // Allow arrow key hotbar/page navigation in container-based screens
        if (event.getScreen() instanceof AbstractContainerScreen<?>) {
            boolean ctrl = (event.getModifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
            HotbarManager.syncFromGame();

            if (keyCode == GLFW.GLFW_KEY_LEFT || KeyBindings.ARROW_LEFT.isActiveAndMatches(inputKey)) {
                if (ctrl) HotbarManager.setPage(HotbarManager.getPage() - 1);
                else      HotbarManager.setHotbar(HotbarManager.getHotbar() - 1);
                event.setCanceled(true);
            } else if (keyCode == GLFW.GLFW_KEY_RIGHT || KeyBindings.ARROW_RIGHT.isActiveAndMatches(inputKey)) {
                if (ctrl) HotbarManager.setPage(HotbarManager.getPage() + 1);
                else      HotbarManager.setHotbar(HotbarManager.getHotbar() + 1);
                event.setCanceled(true);
            } else if (keyCode == GLFW.GLFW_KEY_UP || KeyBindings.ARROW_UP.isActiveAndMatches(inputKey)) {
                HotbarManager.setPage(HotbarManager.getPage() + 1);
                event.setCanceled(true);
            } else if (keyCode == GLFW.GLFW_KEY_DOWN || KeyBindings.ARROW_DOWN.isActiveAndMatches(inputKey)) {
                HotbarManager.setPage(HotbarManager.getPage() - 1);
                event.setCanceled(true);
            }

            // Clear hotbar from inventory
            if (KeyBindings.CLEAR_HOTBAR.isActiveAndMatches(inputKey)) {
                HotbarManager.getCurrentHotbar().clear();
                HotbarManager.syncToGame();
                event.setCanceled(true);
            }

            // Open mod GUI from any screen (except already in it)
            if (!(event.getScreen() instanceof HotbarGuiScreen) &&
                    KeyBindings.OPEN_GUI.isActiveAndMatches(inputKey)) {
                mc.setScreen(new HotbarGuiScreen());
                event.setCanceled(true);
            }
        }

        // If in Hotbar GUI and inventory key is pressed, switch to inventory
        if (event.getScreen() instanceof HotbarGuiScreen && keyCode == mc.options.keyInventory.getKey().getValue()) {
            mc.setScreen(new InventoryScreen(mc.player));
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            didInitialSync = false;
            lastSelectedSlot = -1;
            return;
        }

        if (!didInitialSync) {
            didInitialSync = true;
            HotbarManager.loadHotbars();
            HotbarManager.syncToGame();
            lastSelectedSlot = mc.player.getInventory().selected;
        }

        if (mc.screen == null) {
            if (KeyBindings.DECREASE_HOTBAR.consumeClick()) {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() - 1);
            }
            if (KeyBindings.INCREASE_HOTBAR.consumeClick()) {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() + 1);
            }
            if (KeyBindings.DECREASE_PAGE.consumeClick()) {
                HotbarManager.syncFromGame();
                HotbarManager.setPage(HotbarManager.getPage() - 1);
            }
            if (KeyBindings.INCREASE_PAGE.consumeClick()) {
                HotbarManager.syncFromGame();
                HotbarManager.setPage(HotbarManager.getPage() + 1);
            }
            if (KeyBindings.OPEN_GUI.consumeClick()) {
                mc.setScreen(new HotbarGuiScreen());
            }
            if (KeyBindings.CLEAR_HOTBAR.consumeClick()) {
                HotbarManager.getCurrentHotbar().clear();
                HotbarManager.syncToGame();
            }
        }

        int currentSelected = mc.player.getInventory().selected;
        if (currentSelected != lastSelectedSlot) {
            HotbarManager.setSlot(currentSelected);
            lastSelectedSlot = currentSelected;
        }
    }
}
