package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class KeyBindings {

    // In-world hotbar/page keybinds
    public static final KeyMapping DECREASE_HOTBAR =
            new KeyMapping("key.ultimatehotbars.dec_hotbar", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_MINUS, "key.categories.ultimatehotbars");

    public static final KeyMapping INCREASE_HOTBAR =
            new KeyMapping("key.ultimatehotbars.inc_hotbar", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_EQUAL, "key.categories.ultimatehotbars");

    public static final KeyMapping DECREASE_PAGE =
            new KeyMapping("key.ultimatehotbars.dec_page", KeyConflictContext.IN_GAME,
                    KeyModifier.CONTROL, InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_MINUS, "key.categories.ultimatehotbars");

    public static final KeyMapping INCREASE_PAGE =
            new KeyMapping("key.ultimatehotbars.inc_page", KeyConflictContext.IN_GAME,
                    KeyModifier.CONTROL, InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_EQUAL, "key.categories.ultimatehotbars");

    public static final KeyMapping OPEN_GUI =
            new KeyMapping("key.ultimatehotbars.open_gui", InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_H, "key.categories.ultimatehotbars");

    // Arrow keys active *only* in GUI screens
    public static final KeyMapping ARROW_LEFT =
            new KeyMapping("key.ultimatehotbars.arrow_left", KeyConflictContext.GUI,
                    KeyModifier.NONE, InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT, "key.categories.ultimatehotbars");

    public static final KeyMapping ARROW_RIGHT =
            new KeyMapping("key.ultimatehotbars.arrow_right", KeyConflictContext.GUI,
                    KeyModifier.NONE, InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_RIGHT, "key.categories.ultimatehotbars");

    public static final KeyMapping ARROW_UP =
            new KeyMapping("key.ultimatehotbars.arrow_up", KeyConflictContext.GUI,
                    KeyModifier.NONE, InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_UP, "key.categories.ultimatehotbars");

    public static final KeyMapping ARROW_DOWN =
            new KeyMapping("key.ultimatehotbars.arrow_down", KeyConflictContext.GUI,
                    KeyModifier.NONE, InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_DOWN, "key.categories.ultimatehotbars");

        public static final KeyMapping CLEAR_HOTBAR = new KeyMapping(
            "key.ultimatehotbars.clear_hotbar",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_DELETE,
            "key.categories.ultimatehotbars"
    );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        // in-world keybinds
        event.register(DECREASE_HOTBAR);
        event.register(INCREASE_HOTBAR);
        event.register(DECREASE_PAGE);
        event.register(INCREASE_PAGE);
        event.register(OPEN_GUI);
        // GUI-only arrow keys
        event.register(ARROW_LEFT);
        event.register(ARROW_RIGHT);
        event.register(ARROW_UP);
        event.register(ARROW_DOWN);


    }
}
