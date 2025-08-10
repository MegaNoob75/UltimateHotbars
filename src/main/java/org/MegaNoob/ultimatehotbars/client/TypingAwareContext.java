package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;

public enum TypingAwareContext implements IKeyConflictContext {
    INSTANCE;

    @Override
    public boolean isActive() {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.screen;
        if (s == null) return true;                 // in-world → allow keys
        if (s instanceof ChatScreen) return false;  // chat is always typing

        // Focused widget directly an EditBox that can take input?
        GuiEventListener focused = s.getFocused();
        if (focused instanceof EditBox eb && eb.canConsumeInput()) {
            return false; // typing → key inactive
        }

        // Screen implements ContainerEventHandler in 1.20.1 → scan children
        if (hasFocusedTextInput((ContainerEventHandler) s)) {
            return false; // typing somewhere in the tree → key inactive
        }

        return true; // otherwise, keys are active (e.g., inventories/containers without typing)
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        // behave like UNIVERSAL for conflicts
        return other == this || other == KeyConflictContext.UNIVERSAL;
    }

    /** TRUE iff any EditBox in this container can currently consume input. */
    private static boolean hasFocusedTextInput(ContainerEventHandler root) {
        for (GuiEventListener child : root.children()) {
            if (child instanceof EditBox eb && eb.canConsumeInput()) {
                return true;
            }
            if (child instanceof ContainerEventHandler nested && hasFocusedTextInput(nested)) {
                return true;
            }
        }
        return false;
    }

    @Override public String toString() { return "TYPING_AWARE"; }
}
