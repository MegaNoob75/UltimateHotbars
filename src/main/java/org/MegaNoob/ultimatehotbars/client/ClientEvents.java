package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {
    private static final long INITIAL_DELAY_MS = 300;
    private static final long REPEAT_INTERVAL_MS = 100;

    private static boolean guiJustClosed = false;
    private static boolean lastModifierDown = false;

    private static final boolean[] keyHeld = new boolean[8];
    private static final long[] keyPressStart = new long[8];
    private static final long[] lastRepeat = new long[8];
    private static final boolean[] skipUntilReleased = new boolean[8];

    private static Screen lastScreen = null;

    private static long lastSyncCheck = 0;
    private static final ItemStack[] lastKnownHotbar = new ItemStack[9];

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
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (!(event.getScreen() instanceof HotbarGuiScreen)) return;

        double delta = event.getScrollDelta();
        if (delta == 0) return;

        Minecraft mc = Minecraft.getInstance();
        int hotbar = HotbarManager.getHotbar();
        int page = HotbarManager.getPage();

        boolean pageChanged = false;

        if (delta < 0) {
            hotbar++;
            if (hotbar >= HotbarManager.HOTBARS_PER_PAGE) {
                hotbar = 0;
                page = (page + 1) % HotbarManager.PAGES;
                HotbarManager.setPage(page);
                pageChanged = true;
            }
        } else {
            hotbar--;
            if (hotbar < 0) {
                page = (page - 1 + HotbarManager.PAGES) % HotbarManager.PAGES;
                hotbar = HotbarManager.HOTBARS_PER_PAGE - 1;
                HotbarManager.setPage(page);
                pageChanged = true;
            }
        }

        HotbarManager.setHotbar(hotbar, "scroll wheel");
        HotbarManager.syncFromGame();

        if (mc.screen instanceof HotbarGuiScreen gui) {
            gui.updatePageInput();
        }

        if (mc.player != null) {
            mc.player.playSound(
                    pageChanged ? SoundEvents.NOTE_BLOCK_BASEDRUM.get() : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f,
                    pageChanged ? 0.9f : 1.4f
            );
        }

        event.setCanceled(true);
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

        long window = mc.getWindow().getWindow();

        boolean ctrlNow = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shiftNow = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean altNow = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) ||
                InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
        boolean modifierNow = ctrlNow || shiftNow || altNow;

        if (modifierNow != lastModifierDown) {
            lastModifierDown = modifierNow;
            for (int i = 0; i < keyHeld.length; i++) {
                keyHeld[i] = false;
                skipUntilReleased[i] = true;
                keyPressStart[i] = 0;
                lastRepeat[i] = 0;
            }
        }

        boolean inGui = mc.screen instanceof HotbarGuiScreen || mc.screen instanceof AbstractContainerScreen;
        long now = System.currentTimeMillis();

       // boolean ctrlActive = ctrlNow;

        boolean[] held = {
                !ctrlNow && GLFW.glfwGetKey(window, KeyBindings.DECREASE_HOTBAR.getKey().getValue()) == GLFW.GLFW_PRESS,
                !ctrlNow && GLFW.glfwGetKey(window, KeyBindings.INCREASE_HOTBAR.getKey().getValue()) == GLFW.GLFW_PRESS,
                KeyBindings.DECREASE_PAGE.isDown(),
                KeyBindings.INCREASE_PAGE.isDown(),
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT) == GLFW.GLFW_PRESS && inGui,
                GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT) == GLFW.GLFW_PRESS && inGui,
                (mc.screen instanceof HotbarGuiScreen && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS) ||
                        (mc.screen instanceof AbstractContainerScreen && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS),

                (mc.screen instanceof HotbarGuiScreen && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_DOWN) == GLFW.GLFW_PRESS) ||
                        (mc.screen instanceof AbstractContainerScreen && GLFW.glfwGetKey(window, GLFW.GLFW_KEY_UP) == GLFW.GLFW_PRESS),


        };

        for (int i = 0; i < held.length; i++) {
            if (held[i]) {
                if (skipUntilReleased[i]) continue;
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
                skipUntilReleased[i] = false;
            }
        }

        HotbarManager.setSlot(mc.player.getInventory().selected);

        Screen current = mc.screen;
        if (lastScreen != current) {
            if (
                    lastScreen instanceof InventoryScreen || lastScreen instanceof HotbarGuiScreen ||
                            current instanceof HotbarGuiScreen
            ) {
                HotbarManager.syncFromGame();
            }
            lastScreen = current;
        }

        // ✅ Safely detect inventory changes when HUD is open
        if (mc.screen == null && mc.player != null) {
            if (now - lastSyncCheck > 1000) {
                lastSyncCheck = now;
                boolean changed = false;
                for (int i = 0; i < 9; i++) {
                    ItemStack currentStack = mc.player.getInventory().getItem(i);
                    ItemStack cachedStack = (lastKnownHotbar[i] != null) ? lastKnownHotbar[i] : ItemStack.EMPTY;

                    if (!ItemStack.matches(currentStack, cachedStack)) {
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
        }
    }


    private static void triggerKey(int index) {
        Minecraft mc = Minecraft.getInstance();
        boolean playedSound = false;
        boolean pageChanged = false;

        switch (index) {
            case 0, 6 -> {
                HotbarManager.setHotbar(HotbarManager.getHotbar() - 1, "triggerKey(-)");
                HotbarManager.syncFromGame();
                playedSound = true;
            }
            case 1, 7 -> {
                HotbarManager.setHotbar(HotbarManager.getHotbar() + 1, "triggerKey(+)");
                HotbarManager.syncFromGame();
                playedSound = true;
            }
            case 2, 5 -> { // LEFT → decrease page
                HotbarManager.setPage(HotbarManager.getPage() - 1);
                pageChanged = true;
            }
            case 3, 4 -> { // RIGHT → increase page
                HotbarManager.setPage(HotbarManager.getPage() + 1);
                pageChanged = true;
            }
        }

        if (pageChanged && mc.screen instanceof HotbarGuiScreen gui) {
            gui.updatePageInput();
        }

        if (mc.player != null && (playedSound || pageChanged)) {
            mc.player.playSound(
                    pageChanged ? SoundEvents.NOTE_BLOCK_BASEDRUM.get() : SoundEvents.UI_BUTTON_CLICK.get(),
                    0.7f,
                    pageChanged ? 0.9f : 1.4f
            );
        }
    }



}
