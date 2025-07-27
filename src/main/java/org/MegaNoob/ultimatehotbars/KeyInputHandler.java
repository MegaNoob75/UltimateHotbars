package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

import static org.lwjgl.glfw.GLFW.*;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class KeyInputHandler {
    private static final long INITIAL_DELAY_MS = 300;
    private static final long REPEAT_INTERVAL_MS = 100;

    private static final boolean[] keyHeld = new boolean[8];
    private static final long[] keyPressStart = new long[8];
    private static final long[] lastRepeat = new long[8];
    private static final boolean[] skipUntilReleased = new boolean[8];

    private static boolean guiJustClosed = false;
    private static boolean lastModifierDown = false;
    private static Screen lastScreen = null;
    private static final ItemStack[] lastKnownHotbar = new ItemStack[9];
    private static long lastSyncCheck = 0;

    // ---- UNIVERSAL TEXT FIELD FOCUS CHECK ----
    public static boolean isAnyTextFieldFocused() {
        Screen curr = Minecraft.getInstance().screen;
        if (curr instanceof HotbarGuiScreen gui) {
            if (gui.isTextFieldFocused()) return true;
        }
        // Check for focused EditBox on any screen (vanilla, modded, etc.)
        if (curr != null && curr.getFocused() instanceof EditBox editBox) {
            return editBox.isFocused();
        }
        // Add more checks for other mod GUIs/textboxes if needed
        return false;
    }

    public static void handleKeyPressed(int keyCode, int scanCode) {
        if (isAnyTextFieldFocused()) return;

        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;

        InputConstants.Key inputKey = InputConstants.getKey(keyCode, scanCode);

        if (mc.player == null) return;

        if (current instanceof HotbarGuiScreen gui) {
            if (gui.isTextFieldFocused()) return;

            double rawX = mc.mouseHandler.xpos();
            double rawY = mc.mouseHandler.ypos();
            int mx = (int) (rawX * gui.width / mc.getWindow().getScreenWidth());
            int my = (int) (rawY * gui.height / mc.getWindow().getScreenHeight());

            if (gui.isMouseOverPageList(mx, my) && (keyCode == GLFW_KEY_UP || keyCode == GLFW_KEY_DOWN)) {
                int page = HotbarManager.getPage();
                int pageCount = HotbarManager.getPageCount();
                int dir = (keyCode == GLFW_KEY_DOWN) ? 1 : -1;
                int newPage = ((page + dir) % pageCount + pageCount) % pageCount;

                if (newPage != page) {
                    HotbarManager.syncFromGame();
                    HotbarManager.setPage(newPage, 0);
                    gui.updatePageInput();

                    // Optional: play sound when changing pages
                    if (Config.enableSounds() && Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.0f);
                    }
                }
                return;
            }

            // Switch to vanilla inventory with inventory key
            if (mc.options.keyInventory.isActiveAndMatches(inputKey)) {
                mc.setScreen(new InventoryScreen(mc.player));
                guiJustClosed = true;
                return;
            }
            // Close GUI with its own key
            if (KeyBindings.OPEN_GUI.isActiveAndMatches(inputKey)) {
                mc.setScreen(null);
                guiJustClosed = true;
                return;
            }
        }

        // Open GUI from inventory or other screens, but not if just closed
        if (!(current instanceof HotbarGuiScreen) &&
                KeyBindings.OPEN_GUI.isActiveAndMatches(inputKey)) {
            if (!guiJustClosed && !isAnyTextFieldFocused()) {
                mc.setScreen(new HotbarGuiScreen());
            }
        }
    }

    public static void handleRawKeyInput(int keyCode, int scanCode) {
        handleKeyPressed(keyCode, scanCode);
    }

    public static void tick() {
        // --- Don't interfere with typing in a text field (mod or vanilla) ---
        if (isAnyTextFieldFocused()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // --- Block if a text field is focused in your custom GUI ---
        if (mc.screen instanceof HotbarGuiScreen gui && gui.isTextFieldFocused()) return;

        // --- Prevent immediately reopening the GUI after closing it with the keybind ---
        if (guiJustClosed) {
            if (!KeyBindings.OPEN_GUI.isDown()) guiJustClosed = false;
            return;
        }

        long window = mc.getWindow().getWindow();
        boolean ctrlNow = InputConstants.isKeyDown(window, GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_CONTROL);
        boolean shiftNow = InputConstants.isKeyDown(window, GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_SHIFT);
        boolean altNow = InputConstants.isKeyDown(window, GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_ALT);
        boolean modifierNow = ctrlNow || shiftNow || altNow;

        // --- Reset key repeat logic if modifier is pressed or released ---
        if (modifierNow != lastModifierDown) {
            lastModifierDown = modifierNow;
            for (int i = 0; i < keyHeld.length; i++) {
                keyHeld[i] = false;
                skipUntilReleased[i] = true;
                keyPressStart[i] = 0;
                lastRepeat[i] = 0;
            }
        }

        boolean inGui = mc.screen instanceof HotbarGuiScreen
                || mc.screen instanceof AbstractContainerScreen<?>;

        boolean inHotbarGui = mc.screen instanceof HotbarGuiScreen;
        boolean inInventory = mc.screen instanceof AbstractContainerScreen<?> && !(mc.screen instanceof HotbarGuiScreen);
        long now = System.currentTimeMillis();

        // --- Handle hotbar and page navigation keybinds everywhere ---
        boolean[] held = {
                !ctrlNow && glfwGetKey(window, KeyBindings.DECREASE_HOTBAR.getKey().getValue()) == GLFW_PRESS,
                !ctrlNow && glfwGetKey(window, KeyBindings.INCREASE_HOTBAR.getKey().getValue()) == GLFW_PRESS,
                KeyBindings.DECREASE_PAGE.isDown(),
                KeyBindings.INCREASE_PAGE.isDown(),
                glfwGetKey(window, GLFW_KEY_RIGHT) == GLFW_PRESS && inGui,
                glfwGetKey(window, GLFW_KEY_LEFT) == GLFW_PRESS && inGui,
                // Only handle UP/DOWN in inventory, NOT when HotbarGuiScreen is open
                inInventory && glfwGetKey(window, GLFW_KEY_DOWN) == GLFW_PRESS, // index 6 (Down = decrease)
                inInventory && glfwGetKey(window, GLFW_KEY_UP) == GLFW_PRESS    // index 7 (Up = increase)
        };

        for (int i = 0; i < held.length; i++) {
            if (held[i]) {
                if (skipUntilReleased[i]) continue;
                if (!keyHeld[i]) {
                    keyHeld[i] = true;
                    triggerKey(i);
                    keyPressStart[i] = now;
                    lastRepeat[i] = 0;
                } else if (now - keyPressStart[i] >= INITIAL_DELAY_MS
                        && now - lastRepeat[i] >= REPEAT_INTERVAL_MS) {
                    lastRepeat[i] = now;
                    triggerKey(i);
                }
            } else {
                keyHeld[i] = false;
                skipUntilReleased[i] = false;
            }
        }

        // --- Always track the player's currently selected slot ---
        HotbarManager.setSlot(mc.player.getInventory().selected);

        // --- Detect hotbar changes and save them in ANY SCREEN ---
        // (removes restriction to main HUD only)
        if (mc.player != null && now - lastSyncCheck > 1000) {
            lastSyncCheck = now;
            boolean changed = false;
            for (int i = 0; i < 9; i++) {
                ItemStack current = mc.player.getInventory().getItem(i);
                ItemStack cached = lastKnownHotbar[i] != null ? lastKnownHotbar[i] : ItemStack.EMPTY;
                if (!ItemStack.matches(current, cached)) {
                    changed = true;
                    break;
                }
            }
            if (changed) {
                HotbarManager.syncFromGame();     // Copy player hotbar to your virtual data
                HotbarManager.saveHotbars();      // Save to disk
                System.out.println("[UltimateHotbars] Hotbar changed in inventory and saved!"); // Debug
                for (int i = 0; i < 9; i++) {
                    lastKnownHotbar[i] = mc.player.getInventory().getItem(i).copy();
                }
            }
        }

        // --- Sync hotbars when switching between screens, to keep everything consistent ---
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



    @SubscribeEvent
    public static void onScreenKeyPressed(net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();

        if (mc.player == null) return;
        if (screen instanceof HotbarGuiScreen) return;
        if (KeyInputHandler.isAnyTextFieldFocused()) return;

        // Allow from any container screen (including vanilla & modded inventories)
        if (screen instanceof AbstractContainerScreen<?>
                && KeyBindings.OPEN_GUI.isActiveAndMatches(InputConstants.getKey(event.getKeyCode(), event.getScanCode()))) {
            mc.setScreen(new HotbarGuiScreen());
            event.setCanceled(true); // Prevent vanilla from handling this key
        }
    }



    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        // === OPEN HOTBAR GUI FROM WORLD (when no other GUI is open) ===
        if (mc.player != null && mc.screen == null && KeyBindings.OPEN_GUI.isDown()) {
            mc.setScreen(new HotbarGuiScreen());
            return; // Prevent double opens in one tick
        }

        tick();
    }


    private static void triggerKey(int index) {
        Minecraft mc = Minecraft.getInstance();
        boolean playedSound = false;
        boolean pageChanged = false;

        // --- Hotbar and page counts for logic below ---
        int hotbarCount = HotbarManager.getCurrentPageHotbars().size();
        int pageCount = HotbarManager.getPageCount();

        switch (index) {
            // - (decrement hotbar)
            case 0 -> {
                // Always save the current hotbar before switching!
                HotbarManager.syncFromGame();
                HotbarManager.saveHotbars();

                if (hotbarCount > 1) { // Only switch if more than one hotbar
                    HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "triggerKey(-)");
                    HotbarManager.syncToGame(); // Now load the new hotbar into player inventory
                    playedSound = true;
                }
            }
            // = (increment hotbar)
            case 1 -> {
                HotbarManager.syncFromGame();
                HotbarManager.saveHotbars();

                if (hotbarCount > 1) {
                    HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "triggerKey(+)");
                    HotbarManager.syncToGame();
                    playedSound = true;
                }
            }
            // Ctrl - and left arrow (decrement page)
            case 2, 5 -> {
                HotbarManager.syncFromGame();
                HotbarManager.saveHotbars();

                if (pageCount > 1) { // Only switch if more than one page
                    int curr = HotbarManager.getPage();
                    int newPage = ((curr - 1) % pageCount + pageCount) % pageCount;
                    HotbarManager.setPage(newPage, 0);
                    HotbarManager.syncToGame(); // Load the new page's hotbar into inventory
                    pageChanged = true;
                }
            }
            // Ctrl = and right arrow (increment page)
            case 3, 4 -> {
                HotbarManager.syncFromGame();
                HotbarManager.saveHotbars();

                if (pageCount > 1) {
                    int curr = HotbarManager.getPage();
                    int newPage = ((curr + 1) % pageCount + pageCount) % pageCount;
                    HotbarManager.setPage(newPage, 0);
                    HotbarManager.syncToGame();
                    pageChanged = true;
                }
            }
            // Up arrow (only in inventory)
            case 6 -> {
                HotbarManager.syncFromGame();
                HotbarManager.saveHotbars();

                if (hotbarCount > 1) {
                    HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "arrow");
                    HotbarManager.syncToGame();
                    playedSound = true;
                }
            }
            // Down arrow (only in inventory)
            case 7 -> {
                HotbarManager.syncFromGame();
                HotbarManager.saveHotbars();

                if (hotbarCount > 1) {
                    HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "arrow");
                    HotbarManager.syncToGame();
                    playedSound = true;
                }
            }
        }

        // Update page input if in GUI (for live UI update)
        if (pageChanged && mc.screen instanceof HotbarGuiScreen gui) {
            gui.updatePageInput();
        }

        // Play feedback sound if enabled
        if (Config.enableSounds() && mc.player != null && (playedSound || pageChanged)) {
            mc.player.playSound(
                    pageChanged ? SoundEvents.NOTE_BLOCK_BASEDRUM.get() : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f,
                    pageChanged ? 0.9f : 1.4f
            );
        }
    }

}
