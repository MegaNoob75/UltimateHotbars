package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
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
    // Throttle helper: return true when we should SKIP handling right now.
    private static boolean shouldThrottleNav() {
        long now = System.currentTimeMillis();
        boolean throttle = (now - lastNavTime) < NAV_THROTTLE_MS;
        if (!throttle) {
            // Only advance the window when we're allowed to navigate
            lastNavTime = now;
        }
        return throttle;
    }
    /** Public API (kept for other classes): return true when we MAY handle navigation. */
    public static boolean canNavigate() {
        return !shouldThrottleNav();
    }

    // ---- UNIVERSAL TEXT INPUT FOCUS CHECK (JEI + vanilla, using canConsumeInput) ----
    public static boolean isAnyTextFieldFocused() {
        // A) JEI overlay search (keep if you added the tiny plugin; otherwise this try/catch is harmless)
        try {
            if (org.MegaNoob.ultimatehotbars.client.JeiBridge.hasTextFocus()) {
                return true;
            }
        } catch (Throwable ignored) {}

        // B) Normal screens
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        net.minecraft.client.gui.screens.Screen screen = mc.screen;
        if (screen == null) return false;

        // Chat always captures typing
        if (screen instanceof net.minecraft.client.gui.screens.ChatScreen) return true;

        // Focused widget is an EditBox and can actually take input?
        net.minecraft.client.gui.components.events.GuiEventListener focused = screen.getFocused();
        if (focused instanceof net.minecraft.client.gui.components.EditBox eb && eb.canConsumeInput()) {
            return true;
        }

        // Recursively scan children (Screen implements ContainerEventHandler in 1.20.1)
        return hasFocusedTextInput((net.minecraft.client.gui.components.events.ContainerEventHandler) screen);
    }

    /** Recursively looks for an EditBox that can currently consume input. */
    private static boolean hasFocusedTextInput(
            net.minecraft.client.gui.components.events.ContainerEventHandler root) {
        for (net.minecraft.client.gui.components.events.GuiEventListener child : root.children()) {
            if (child instanceof net.minecraft.client.gui.components.EditBox eb && eb.canConsumeInput()) {
                return true;
            }
            if (child instanceof net.minecraft.client.gui.components.events.ContainerEventHandler nested
                    && hasFocusedTextInput(nested)) {
                return true;
            }
        }
        return false;
    }


    public static void tick() {
        // 1) never fire while typing anywhere — and reset repeat state so keys aren't "stuck"
        if (isAnyTextFieldFocused()) {
            for (int i = 0; i < keyHeld.length; i++) {
                keyHeld[i]           = false;
                skipUntilReleased[i] = false; // allow a fresh press right after leaving the field
                keyPressStart[i]     = 0;
                lastRepeat[i]        = 0;
            }
            return;
        }

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
                keyHeld[i]           = false;
                skipUntilReleased[i] = true;  // require release after modifier change
                keyPressStart[i]     = 0;
                lastRepeat[i]        = 0;
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
                keyHeld[i]           = false;
                skipUntilReleased[i] = false;
            }
        }

        // 6) still allow your DELETE-key clear
        boolean clearKey = KeyBindings.CLEAR_HOTBAR.isDown();
        if (clearKey) {
            if (!clearHeld) {
                clearHeld = true;
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
        if (screen instanceof AbstractContainerScreen<?>
                && KeyBindings.OPEN_GUI.isActiveAndMatches(key)
                && !isAnyTextFieldFocused()) {
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
        if (shouldThrottleNav()) return;   // ← throttle!
        Minecraft mc = Minecraft.getInstance();
        boolean playedSound = false;
        boolean pageChanged = false;

        int hotbarCount = HotbarManager.getCurrentPageHotbars().size();
        int pageCount   = HotbarManager.getPageCount();

        switch (index) {
            case 0 -> { // hotbar backwards
                if (hotbarCount > 1) {
                    // Queue through the same coordinator used by wheel scrolling.
                    // This avoids races with the wheel by snapshotting+switching once per tick.
                    int target = HotbarManager.getHotbar() - 1;
                    org.MegaNoob.ultimatehotbars.client.WheelSwitchCoordinator.request(target);
                    playedSound = true;
                }
            }
            case 1 -> { // hotbar forwards
                if (hotbarCount > 1) {
                    int target = HotbarManager.getHotbar() + 1;
                    org.MegaNoob.ultimatehotbars.client.WheelSwitchCoordinator.request(target);
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
                    pageChanged ? SoundEvents.NOTE_BLOCK_BASEDRUM.get()
                            : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f, pageChanged ? 0.9f : 1.4f
            );
        }
    }




}


