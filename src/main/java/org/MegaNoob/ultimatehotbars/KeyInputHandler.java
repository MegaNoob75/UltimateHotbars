package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import net.minecraftforge.client.event.ScreenEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


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
    private static boolean clearHeld = false;
    private static Screen lastScreen = null;
    private static final ItemStack[] lastKnownHotbar = new ItemStack[9];
    private static long lastSyncCheck = 0;

    private static final Logger LOGGER = LogManager.getLogger();

    // ─────── Throttle fields ───────
    private static final long NAV_THROTTLE_MS = 50;
    private static long lastNavTime = 0;
    private static volatile boolean saveInProgress = false;
    public static boolean canNavigate() {
        long now = System.currentTimeMillis();
        if (now - lastNavTime < NAV_THROTTLE_MS) return false;
        lastNavTime = now;
        return true;
    }

    // ---- UNIVERSAL TEXT FIELD FOCUS CHECK ----
    public static boolean isAnyTextFieldFocused() {
        Screen curr = Minecraft.getInstance().screen;
        // your custom GUI’s text fields
        if (curr instanceof HotbarGuiScreen gui && gui.isTextFieldFocused()) return true;
        // Minecraft chat
        if (curr instanceof ChatScreen) return true;
        // any other EditBox-based screens?
        // (add more clauses here if you open other modded text screens)
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
                    // Only snapshot & save if the real hotbar actually changed
                    if (HotbarManager.syncFromGameIfChanged()) {
                        HotbarManager.saveHotbars();
                    }

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
        // 1) never fire while typing anywhere
        if (isAnyTextFieldFocused()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 2) block immediately after closing your GUI, until the key is released
        if (guiJustClosed) {
            if (!KeyBindings.OPEN_GUI.isDown()) {
                guiJustClosed = false;
            }
            return;
        }

        long window = mc.getWindow().getWindow();
        long now    = System.currentTimeMillis();

        // 3) reset repeat state whenever Ctrl/Shift/Alt changes
        boolean ctrlNow  = InputConstants.isKeyDown(window, GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_CONTROL);
        boolean shiftNow = InputConstants.isKeyDown(window, GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_SHIFT);
        boolean altNow   = InputConstants.isKeyDown(window, GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW_KEY_RIGHT_ALT);
        boolean modifierNow = ctrlNow || shiftNow || altNow;
        if (modifierNow != lastModifierDown) {
            lastModifierDown = modifierNow;
            for (int i = 0; i < keyHeld.length; i++) {
                keyHeld[i]            = false;
                skipUntilReleased[i]  = true;
                keyPressStart[i]      = 0;
                lastRepeat[i]         = 0;
            }
        }

        // 4) read your four universal key-mappings
        boolean decHotbar = KeyBindings.DECREASE_HOTBAR.isDown();  // “-”
        boolean incHotbar = KeyBindings.INCREASE_HOTBAR.isDown();  // “=”
        boolean decPage   = KeyBindings.DECREASE_PAGE.isDown();    // “Ctrl + -”
        boolean incPage   = KeyBindings.INCREASE_PAGE.isDown();    // “Ctrl + =”

        boolean[] held = { decHotbar, incHotbar, decPage, incPage };

        // 5) trigger and repeat
        for (int i = 0; i < held.length; i++) {
            if (held[i]) {
                if (skipUntilReleased[i]) continue;
                if (!keyHeld[i]) {
                    keyHeld[i]       = true;
                    triggerKey(i);
                    keyPressStart[i] = now;
                    lastRepeat[i]    = 0;
                } else if (now - keyPressStart[i] >= INITIAL_DELAY_MS
                        && now - lastRepeat[i] >= REPEAT_INTERVAL_MS) {
                    lastRepeat[i] = now;
                    triggerKey(i);
                }
            } else {
                keyHeld[i]          = false;
                skipUntilReleased[i]= false;
            }
        }

        // 6) still allow your DELETE-key clear
        boolean clearKey = KeyBindings.CLEAR_HOTBAR.isDown();
        if (clearKey) {
            if (!clearHeld) {
                clearHeld = true;
                // Delegate to your manager’s clearCurrentHotbar, which now does:
                //   markDirty(); saveHotbars(); syncToGame();
                HotbarManager.clearCurrentHotbar();
                if (Config.enableSounds()) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.4f);
                }
            }
        } else {
            clearHeld = false;
        }

        // 7) sync on screen transitions (as before)
        Screen screen = mc.screen;
        if (lastScreen != screen) {
            if (lastScreen instanceof InventoryScreen
                    || lastScreen instanceof HotbarGuiScreen
                    || screen instanceof HotbarGuiScreen) {
                // Only snapshot & save if the real hotbar actually changed
                if (HotbarManager.syncFromGameIfChanged()) {
                    HotbarManager.saveHotbars();
                }
            }
            lastScreen = screen;
        }
    }



    @SubscribeEvent
    public static void onScreenKeyPressed(net.minecraftforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Screen screen = event.getScreen();

        // 1) Don’t do anything if player isn’t in‐game or is typing
        if (mc.player == null) return;
        if (isAnyTextFieldFocused()) return;

        // 2) Grab the raw key
        InputConstants.Key key = InputConstants.getKey(event.getKeyCode(), event.getScanCode());

        // 3) Handle page‐up/down first, then hotbar, then clear – in any GUI or container
        if (screen instanceof HotbarGuiScreen || screen instanceof AbstractContainerScreen<?>) {
            // page backwards (Ctrl + “-”)
            if (KeyBindings.DECREASE_PAGE.isActiveAndMatches(key)) {
                triggerKey(2);
                event.setCanceled(true);
                return;
            }
            // page forwards (Ctrl + “=”)
            if (KeyBindings.INCREASE_PAGE.isActiveAndMatches(key)) {
                triggerKey(3);
                event.setCanceled(true);
                return;
            }
            // hotbar backwards (“-”)
            if (KeyBindings.DECREASE_HOTBAR.isActiveAndMatches(key)) {
                triggerKey(0);
                event.setCanceled(true);
                return;
            }
            // hotbar forwards (“=”)
            if (KeyBindings.INCREASE_HOTBAR.isActiveAndMatches(key)) {
                triggerKey(1);
                event.setCanceled(true);
                return;
            }
            // clear hotbar (Delete)
            if (KeyBindings.CLEAR_HOTBAR.isActiveAndMatches(key)) {
                // clearCurrentHotbar() now does: clear slots → markDirty() → saveHotbars() → syncToGame()
                HotbarManager.clearCurrentHotbar();
                if (Config.enableSounds() && mc.player != null) {
                    mc.player.playSound(SoundEvents.UI_BUTTON_CLICK.get(), 0.7f, 1.4f);
                }
                event.setCanceled(true);
                return;
            }
        }

        // 4) Allow “open GUI” in any container (but not if it’s already your GUI)
        if (!(screen instanceof HotbarGuiScreen)
                && screen instanceof AbstractContainerScreen<?>
                && KeyBindings.OPEN_GUI.isActiveAndMatches(key)) {
            mc.setScreen(new HotbarGuiScreen());
            event.setCanceled(true);
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
        if (!canNavigate()) return;    // ← throttle!
        Minecraft mc = Minecraft.getInstance();
        boolean playedSound = false;
        boolean pageChanged = false;

        int hotbarCount = HotbarManager.getCurrentPageHotbars().size();
        int pageCount   = HotbarManager.getPageCount();

        switch (index) {
            case 0 -> { // hotbar backwards
                if (hotbarCount > 1) {
                    if (HotbarManager.syncFromGameIfChanged()) {
                        HotbarManager.saveHotbars();
                    }

                    HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "triggerKey(-)");
                    playedSound = true;
                }
            }
            case 1 -> { // hotbar forwards
                if (hotbarCount > 1) {
                    if (HotbarManager.syncFromGameIfChanged()) {
                        HotbarManager.saveHotbars();
                    }

                    HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "triggerKey(+)");
                    playedSound = true;
                }
            }
            case 2 -> { // page backwards
                if (pageCount > 1) {
                    if (HotbarManager.syncFromGameIfChanged()) {
                        HotbarManager.saveHotbars();
                    }

                    int curr = HotbarManager.getPage();
                    int prev = ((curr - 1) % pageCount + pageCount) % pageCount;
                    HotbarManager.setPage(prev, 0);
                    pageChanged = true;
                }
            }
            case 3 -> { // page forwards
                if (pageCount > 1) {
                    if (HotbarManager.syncFromGameIfChanged()) {
                        HotbarManager.saveHotbars();
                    }

                    int curr = HotbarManager.getPage();
                    int next = ((curr + 1) % pageCount + pageCount) % pageCount;
                    HotbarManager.setPage(next, 0);
                    pageChanged = true;
                }
            }
        }

        if (pageChanged && mc.screen instanceof HotbarGuiScreen gui) {
            gui.updatePageInput();
        }
        if (Config.enableSounds() && mc.player != null && (playedSound || pageChanged)) {
            mc.player.playSound(
                    pageChanged
                            ? SoundEvents.NOTE_BLOCK_BASEDRUM.get()
                            : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f, pageChanged ? 0.9f : 1.4f
            );
        }
    }



}


