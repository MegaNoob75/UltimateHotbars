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

/**
 * Handles all of our key & scroll bindings, plus hotbar sync logic.
 */
@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    private static final long INITIAL_DELAY_MS    = 300;
    private static final long REPEAT_INTERVAL_MS  = 100;

    private static boolean guiJustClosed = false;
    private static boolean lastModifierDown = false;

    // Eight “held” key states for our custom repeats
    private static final boolean[] keyHeld       = new boolean[8];
    private static final long[]    keyPressStart = new long[8];
    private static final long[]    lastRepeat    = new long[8];
    private static final boolean[] skipUntilReleased = new boolean[8];

    private static Screen lastScreen = null;

    private static long lastSyncCheck = 0;
    private static final ItemStack[] lastKnownHotbar = new ItemStack[9];

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        // Safe to load here: player is fully initialized
        HotbarManager.loadHotbars();
    }

    @SubscribeEvent
    public static void onScreenKey(ScreenEvent.KeyPressed.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Screen current = mc.screen;
        if (mc.player == null) return;

        // ─── IF THE RENAME TEXT BOX IS FOCUSED, IGNORE ALL BOUND KEYS ─────
        if (current instanceof HotbarGuiScreen gui && gui.isTextFieldFocused()) {
            return;
        }

        // pull raw key info once
        int keyCode  = event.getKeyCode();
        int scanCode = event.getScanCode();
        InputConstants.Key inputKey = InputConstants.getKey(keyCode, scanCode);

        // ─── NEW: if hovering the page-list, let ↑/↓ change the selected page ─────
        if (current instanceof HotbarGuiScreen gui2) {
            double rawX = mc.mouseHandler.xpos();
            double rawY = mc.mouseHandler.ypos();
            int mx = (int)(rawX * gui2.width  / mc.getWindow().getScreenWidth());
            int my = (int)(rawY * gui2.height / mc.getWindow().getScreenHeight());
            if (gui2.isMouseOverPageList(mx, my) &&
                    (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN)) {
                int page    = HotbarManager.getPage();
                int maxPage = HotbarManager.getPageCount() - 1;
                int newPage = keyCode == GLFW.GLFW_KEY_DOWN
                        ? Math.min(page + 1, maxPage)
                        : Math.max(page - 1, 0);
                if (newPage != page) {
                    HotbarManager.syncFromGame();
                    HotbarManager.setPage(newPage);
                    gui2.updatePageInput();
                }
                event.setCanceled(true);
                return;
            }
        }

        // ─── Inventory key while in our GUI → open actual inventory ───────────────
        if (current instanceof HotbarGuiScreen &&
                mc.options.keyInventory.isActiveAndMatches(inputKey)) {
            mc.setScreen(new InventoryScreen(mc.player));
            guiJustClosed = true;
            event.setCanceled(true);
            return;
        }

        // ─── “Open GUI” key while in our GUI → close it ──────────────────────────
        if (current instanceof HotbarGuiScreen &&
                KeyBindings.OPEN_GUI.isActiveAndMatches(inputKey)) {
            mc.setScreen(null);
            guiJustClosed = true;
            event.setCanceled(true);
            return;
        }

        // ─── “Open GUI” key while in a container → switch to our GUI ─────────────
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

        // Open our GUI when the key is pressed (unless we just closed it)
        if (KeyBindings.OPEN_GUI.isActiveAndMatches(InputConstants.getKey(event.getKey(), event.getScanCode()))) {
            if (!guiJustClosed) {
                mc.setScreen(new HotbarGuiScreen());
            }
        }
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        // 0) Only deal with our quick-hotbar GUI here
        if (!(event.getScreen() instanceof HotbarGuiScreen gui)) return;

        // 1) If the page-name box is focused, skip paging
        if (gui.isTextFieldFocused()) return;

        // 2) If the mouse is over the page-list widget, let it handle the scroll
        double mx = event.getMouseX();
        double my = event.getMouseY();
        if (gui.isMouseOverPageList(mx, my)) {
            return;
        }

        // 3) Now perform hotbar-scrolling exactly as before
        double delta = event.getScrollDelta();
        if (delta == 0) return;

        Minecraft mc = Minecraft.getInstance();
        int hotbar = HotbarManager.getHotbar();
        int page   = HotbarManager.getPage();
        boolean pageChanged = false;
        int pageCount = HotbarManager.getPageCount();

        if (delta < 0) {
            // scroll down → next slot/page
            hotbar++;
            if (hotbar >= HotbarManager.HOTBARS_PER_PAGE) {
                hotbar = 0;
                page  = (page + 1) % pageCount;
                pageChanged = true;
            }
        } else {
            // scroll up → previous slot/page
            hotbar--;
            if (hotbar < 0) {
                hotbar = HotbarManager.HOTBARS_PER_PAGE - 1;
                page  = (page - 1 + pageCount) % pageCount;
                pageChanged = true;
            }
        }

        if (pageChanged) HotbarManager.setPage(page);
        HotbarManager.setHotbar(hotbar, "scroll wheel");

        // sync the GUI text box & play the click/drum sound
        if (mc.screen instanceof HotbarGuiScreen g2) g2.updatePageInput();
        if (Config.enableSounds() && mc.player != null) {
            mc.player.playSound(
                    pageChanged
                            ? SoundEvents.NOTE_BLOCK_BASEDRUM.get()
                            : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f,
                    pageChanged ? 0.9f : 1.4f
            );
        }

        // consume the event so it isn’t passed on
        event.setCanceled(true);
    }


    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // ─── IGNORE ALL HOTKEY PROCESSING IF THE TEXTBOX IS FOCUSED ───────
        Screen scr = mc.screen;
        if (scr instanceof HotbarGuiScreen gui && gui.isTextFieldFocused()) {
            return;
        }

        // Handle just‐closed logic (so holding the GUI‐open key doesn’t immediately reopen)
        if (guiJustClosed) {
            if (!KeyBindings.OPEN_GUI.isDown()) {
                guiJustClosed = false;
            }
            return;
        }

        long window = mc.getWindow().getWindow();

        // Modifier‐key reset logic (unchanged)
        boolean ctrlNow = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftNow = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean altNow  = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean modifierNow = ctrlNow || shiftNow || altNow;

        if (modifierNow != lastModifierDown) {
            lastModifierDown = modifierNow;
            for (int i = 0; i < keyHeld.length; i++) {
                keyHeld[i]       = false;
                skipUntilReleased[i] = true;
                keyPressStart[i] = 0;
                lastRepeat[i]    = 0;
            }
        }

        boolean inGui = mc.screen instanceof HotbarGuiScreen
                || mc.screen instanceof AbstractContainerScreen<?>;
        long now = System.currentTimeMillis();

        // 8‐entry held‐state array remains unchanged
        boolean[] held = {
                !ctrlNow && GLFW.glfwGetKey(window, KeyBindings.DECREASE_HOTBAR.getKey().getValue()) == GLFW.GLFW_PRESS,
                !ctrlNow && GLFW.glfwGetKey(window, KeyBindings.INCREASE_HOTBAR.getKey().getValue()) == GLFW.GLFW_PRESS,
                KeyBindings.DECREASE_PAGE.isDown(),
                KeyBindings.INCREASE_PAGE.isDown(),
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS && inGui,
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT)  == GLFW.GLFW_PRESS && inGui,
                (mc.screen instanceof HotbarGuiScreen && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP)   == GLFW.GLFW_PRESS) ||
                        (mc.screen instanceof AbstractContainerScreen<?> && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS),
                (mc.screen instanceof HotbarGuiScreen && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) ||
                        (mc.screen instanceof AbstractContainerScreen<?> && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP)   == GLFW.GLFW_PRESS)
        };

        // Process held‐key auto‐repeat & sound (unchanged)
        for (int i = 0; i < held.length; i++) {
            if (held[i]) {
                if (skipUntilReleased[i]) continue;
                if (!keyHeld[i]) {
                    keyHeld[i] = true;
                    triggerKey(i);
                    keyPressStart[i] = now;
                    lastRepeat[i]    = 0;
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

        // Keep the physical selected slot in sync
        HotbarManager.setSlot(mc.player.getInventory().selected);

        // Detect changes to the player inventory when HUD is open
        if (mc.screen == null && mc.player != null && now - lastSyncCheck > 1000) {
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

        // Sync when opening/closing GUIs or switching between them
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

    private static void triggerKey(int index) {
        Minecraft mc = Minecraft.getInstance();
        boolean playedSound = false;
        boolean pageChanged  = false;

        switch (index) {
            case 0, 6 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "triggerKey(-)");
                playedSound = true;
            }
            case 1, 7 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "triggerKey(+)");
                playedSound = true;
            }
            case 2, 5 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setPage(HotbarManager.getPage() - 1);
                pageChanged = true;
            }
            case 3, 4 -> {
                HotbarManager.syncFromGame();
                HotbarManager.setPage(HotbarManager.getPage() + 1);
                pageChanged = true;
            }
        }

        // Update the rename‐textbox if we changed page inside the GUI
        if (pageChanged && mc.screen instanceof HotbarGuiScreen gui) {
            gui.updatePageInput();
        }

        // Play sounds if enabled
        if (Config.enableSounds() && mc.player != null && (playedSound || pageChanged)) {
            mc.player.playSound(
                    pageChanged
                            ? SoundEvents.NOTE_BLOCK_BASEDRUM.get()
                            : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f,
                    pageChanged ? 0.9f : 1.4f
            );
        }
    }
}
