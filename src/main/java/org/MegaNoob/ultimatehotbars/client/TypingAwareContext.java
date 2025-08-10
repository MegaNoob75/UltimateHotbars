package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

/**
 * Disables your keybinds while ANY text input is focused (chat, anvil rename,
 * JEI/REI search, other mods' text widgets). No JEI imports required.
 */
public enum TypingAwareContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s == null) return true;                // no GUI → allow keys
        if (s instanceof ChatScreen) return false; // chat is typing

        // Focused widget directly an EditBox?
        GuiEventListener focused = s.getFocused();
        if (focused instanceof EditBox eb && eb.isFocused()) return false;

        // Recursively scan the current screen's children for a focused EditBox
        // Screen implements ContainerEventHandler in 1.20.1, so just cast.
        if (hasFocusedTextInput((ContainerEventHandler) s)) return false;

        return true; // otherwise, keys active (inventories/containers still work)
    }


    @Override
    public boolean conflicts(IKeyConflictContext other) {
        // behave like UNIVERSAL so we don't lose conflicts
        return other == this || other == KeyConflictContext.UNIVERSAL;
    }

    private static boolean hasFocusedTextInput(ContainerEventHandler root) {
        for (GuiEventListener child : root.children()) {
            if (child instanceof EditBox eb && eb.isFocused()) return true;
            if (child instanceof ContainerEventHandler nested && hasFocusedTextInput(nested)) return true;
        }
        return false;
    }

    @Override public String toString() { return "TYPING_AWARE"; }
}
