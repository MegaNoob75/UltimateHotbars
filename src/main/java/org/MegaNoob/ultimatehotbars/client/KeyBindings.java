package org.MegaNoob.ultimatehotbars.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.api.distmarker.Dist;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(
        modid = ultimatehotbars.MODID,
        bus   = Mod.EventBusSubscriber.Bus.MOD,   // key‐mappings fire on the MOD bus
        value = Dist.CLIENT
)
public class KeyBindings {

    public static final KeyMapping DECREASE_HOTBAR =
            new KeyMapping(
                    "key.ultimatehotbars.dec_hotbar",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_MINUS,
                    "key.categories.ultimatehotbars"
            );

    public static final KeyMapping PEEK_HOTBARS =
            new KeyMapping(
                    "key.ultimatehotbars.peek_hotbars",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_LEFT_ALT,
                    "key.categories.ultimatehotbars"
            );

    public static final KeyMapping INCREASE_HOTBAR =
            new KeyMapping(
                    "key.ultimatehotbars.inc_hotbar",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_EQUAL,
                    "key.categories.ultimatehotbars"
            );

    public static final KeyMapping DECREASE_PAGE =
            new KeyMapping(
                    "key.ultimatehotbars.dec_page",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.CONTROL,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_MINUS,
                    "key.categories.ultimatehotbars"
            );

    public static final KeyMapping INCREASE_PAGE =
            new KeyMapping(
                    "key.ultimatehotbars.inc_page",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.CONTROL,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_EQUAL,
                    "key.categories.ultimatehotbars"
            );

    public static final KeyMapping OPEN_GUI =
            new KeyMapping(
                    "key.ultimatehotbars.open_gui",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_H,
                    "key.categories.ultimatehotbars"
            );

    public static final KeyMapping CLEAR_HOTBAR =
            new KeyMapping(
                    "key.ultimatehotbars.clear_hotbar",
                    TypingAwareContext.INSTANCE,
                    KeyModifier.NONE,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_DELETE,
                    "key.categories.ultimatehotbars"
            );

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(DECREASE_HOTBAR);
        event.register(INCREASE_HOTBAR);
        event.register(DECREASE_PAGE);
        event.register(INCREASE_PAGE);
        event.register(OPEN_GUI);
        event.register(CLEAR_HOTBAR);
        event.register(PEEK_HOTBARS);
    }
}
