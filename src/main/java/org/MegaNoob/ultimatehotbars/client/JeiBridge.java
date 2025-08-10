package org.MegaNoob.ultimatehotbars.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.resources.ResourceLocation;

/** Minimal JEI plugin: captures runtime and exposes a focus check. */
@JeiPlugin
public final class JeiBridge implements IModPlugin {
    private static volatile IJeiRuntime RUNTIME;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation("ultimatehotbars", "jei_bridge");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RUNTIME = runtime;
    }

    /** @return true if JEI’s overlay or recipes UI currently has keyboard focus (e.g., search box). */
    // JeiBridge.java
    public static boolean hasTextFocus() {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return false;

        IIngredientListOverlay overlay = rt.getIngredientListOverlay();
        return overlay != null && overlay.hasKeyboardFocus();
    }

}
