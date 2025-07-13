package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    private static final long INITIAL_DELAY_MS = 300;
    private static final long REPEAT_INTERVAL_MS = 100;

    private static boolean guiJustClosed = false;

    private static final boolean[] keyHeld = new boolean[8];
    private static final long[] keyPressStart = new long[8];
    private static final long[] lastRepeat = new long[8];

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        int keyCode = event.getKeyCode();
        int scanCode = event.getScanCode();
        InputConstants.Key inputKey = InputConstants.getKey(keyCode, scanCode);
        Screen current = mc.screen;

        if (current instanceof HotbarGuiScreen &&
                mc.options.keyInventory.isActiveAndMatches(inputKey)) {
            mc.setScreen(new InventoryScreen(mc.player));
            guiJustClosed = true;
            event.setCanceled(true);
            return;
        }

        if (current instanceof HotbarGuiScreen &&
                KeyBindings.OPEN_GUI.isActiveAndMatches(inputKey)) {
            mc.setScreen(null);
            guiJustClosed = true;
            event.setCanceled(true);
            return;
        }

        if (current instanceof AbstractContainerScreen<?> &&
                KeyBindings.OPEN_GUI.isActiveAndMatches(inputKey)) {
            mc.setScreen(new HotbarGuiScreen());
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;

        int keyCode = event.getKey();
        int scanCode = event.getScanCode();
        InputConstants.Key key = InputConstants.getKey(keyCode, scanCode);

        if (KeyBindings.OPEN_GUI.isActiveAndMatches(key)) {
            if (!guiJustClosed) {
                mc.setScreen(new HotbarGuiScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (guiJustClosed) {
            if (!KeyBindings.OPEN_GUI.isDown()) {
                guiJustClosed = false;
            }
            return;
        }

        boolean inGui = mc.screen instanceof HotbarGuiScreen || mc.screen instanceof AbstractContainerScreen;
        long window = mc.getWindow().getWindow();
        long now = System.currentTimeMillis();

        // Use GLFW for accurate key state polling, bypassing modifier quirks
        boolean[] held = new boolean[8];

        held[0] = isKeyActive(KeyBindings.DECREASE_HOTBAR, window);
        held[1] = isKeyActive(KeyBindings.INCREASE_HOTBAR, window);
        held[2] = isKeyActive(KeyBindings.DECREASE_PAGE, window);
        held[3] = isKeyActive(KeyBindings.INCREASE_PAGE, window);
        held[4] = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS && inGui;
        held[5] = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS && inGui;
        held[6] = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS && inGui;
        held[7] = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS && inGui;


        for (int i = 0; i < held.length; i++) {
            if (held[i]) {
                if (!keyHeld[i]) {
                    keyHeld[i] = true;
                    triggerKey(i);
                    keyPressStart[i] = now;
                    lastRepeat[i] = 0;
                } else {
                    long heldTime = now - keyPressStart[i];
                    if (heldTime >= INITIAL_DELAY_MS && now - lastRepeat[i] >= REPEAT_INTERVAL_MS) {
                        lastRepeat[i] = now;
                        triggerKey(i);
                    }
                }
            } else {
                keyHeld[i] = false;
            }
        }
    }

    private static boolean isKeyActive(KeyMapping key, long window) {
        int keyCode = key.getKey().getValue();
        if (GLFW.glfwGetKey(window, keyCode) != GLFW.GLFW_PRESS)
            return false;

        int requiredMods = getRequiredModifiers(key);

        // Detect currently held modifiers
        boolean ctrlHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean shiftHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        boolean altHeld = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS ||
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_ALT) == GLFW.GLFW_PRESS;

        // Build actual modifier mask
        int actualMods = 0;
        if (ctrlHeld) actualMods |= GLFW.GLFW_MOD_CONTROL;
        if (shiftHeld) actualMods |= GLFW.GLFW_MOD_SHIFT;
        if (altHeld) actualMods |= GLFW.GLFW_MOD_ALT;

        // Require an exact match of modifiers
        return actualMods == requiredMods;
    }





    private static int getRequiredModifiers(KeyMapping key) {
        if (key == KeyBindings.DECREASE_PAGE || key == KeyBindings.INCREASE_PAGE) {
            return GLFW.GLFW_MOD_CONTROL;
        }
        if (key == KeyBindings.DECREASE_HOTBAR || key == KeyBindings.INCREASE_HOTBAR) {
            return 0; // no modifiers
        }
        return 0;
    }



    private static void triggerKey(int index) {
        HotbarManager.syncFromGame();
        Minecraft mc = Minecraft.getInstance();

        switch (index) {
            case 0 -> HotbarManager.cycleHotbar(-1); // '-' key → decrease hotbar
            case 1 -> HotbarManager.cycleHotbar(+1); // '=' key → increase hotbar


            case 2, 4 -> {
                HotbarManager.setPage(HotbarManager.getPage() - 1);
                updatePageInputIfOpen(mc);
            }

            case 3, 5 -> {
                HotbarManager.setPage(HotbarManager.getPage() + 1);
                updatePageInputIfOpen(mc);
            }

            case 6 -> {
                if (mc.screen instanceof HotbarGuiScreen) {
                    HotbarManager.cycleHotbar(-1); // UP = previous
                } else {
                    HotbarManager.cycleHotbar(+1); // UP = next
                }
            }

            case 7 -> {
                if (mc.screen instanceof HotbarGuiScreen) {
                    HotbarManager.cycleHotbar(+1); // DOWN = next
                } else {
                    HotbarManager.cycleHotbar(-1); // DOWN = previous
                }
            }
        }
    }


    private static void updatePageInputIfOpen(Minecraft mc) {
        if (mc.screen instanceof HotbarGuiScreen gui) {
            gui.updatePageInput();
        }
    }
}
