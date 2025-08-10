package org.MegaNoob.ultimatehotbars.client;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IIngredientListOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IRecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * JEI bridge + plugin:
 * - Registers as a JEI plugin to capture the runtime (exactly like your working version).
 * - hasTextFocus() matches the old behavior (overlay.hasKeyboardFocus()), with a small vanilla fallback.
 * - getItemUnderMouse(...) stays version-agnostic via reflection (so no generics headaches).
 */
@JeiPlugin
public final class JeiBridge implements IModPlugin {
    private static volatile IJeiRuntime RUNTIME;

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ultimatehotbars.MODID, "jei_bridge");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        RUNTIME = runtime;
    }

    /** Returns true if JEI’s overlay (e.g., the search box) has keyboard focus. */
    public static boolean hasTextFocus() {
        IJeiRuntime rt = RUNTIME;
        if (rt != null) {
            IIngredientListOverlay overlay = rt.getIngredientListOverlay();
            if (overlay != null && overlay.hasKeyboardFocus()) {
                return true; // <- this is the exact check you had before
            }
            // Some JEI versions may also route focus via the recipes GUI; harmless extra check:
            IRecipesGui recipes = rt.getRecipesGui();
            if (recipes != null && callBool(recipes, "hasKeyboardFocus")) {
                return true;
            }
        }

        // Vanilla/mod fallback: focused EditBox that can consume input
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.screen != null) {
                Object focused = mc.screen.getFocused();
                if (focused instanceof EditBox eb) {
                    try {
                        if ((boolean) EditBox.class.getMethod("canConsumeInput").invoke(eb)) return true;
                    } catch (NoSuchMethodException noMethod) {
                        if (eb.isFocused() && eb.isActive()) return true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        return false;
    }

    /**
     * Returns the ItemStack currently under the mouse in JEI (item list or recipe GUI),
     * or ItemStack.EMPTY if none. Uses reflection so it tolerates API differences.
     */
    public static ItemStack getItemUnderMouse(double x, double y) {
        IJeiRuntime rt = RUNTIME;
        if (rt == null) return ItemStack.EMPTY;

        // 1) Ingredient List Overlay (left/right JEI panes)
        try {
            Object overlay = rt.getIngredientListOverlay();
            ItemStack fromOverlay = extractFromOverlayOrRecipes(overlay);
            if (!fromOverlay.isEmpty()) return fromOverlay;
        } catch (Throwable ignored) {}

        // 2) Recipes GUI (open recipe pages)
        try {
            Object recipes = rt.getRecipesGui();
            ItemStack fromRecipes = extractFromOverlayOrRecipes(recipes);
            if (!fromRecipes.isEmpty()) return fromRecipes;
        } catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }

    // ---------------- Internal reflection helpers ----------------

    /** Try all known signatures on overlay/recipes to get an ItemStack. */
    private static ItemStack extractFromOverlayOrRecipes(Object obj) {
        if (obj == null) return ItemStack.EMPTY;

        // Newer JEI: Optional<ITypedIngredient<?>> getIngredientUnderMouse()
        try {
            Method m0 = obj.getClass().getMethod("getIngredientUnderMouse");
            Object r0 = m0.invoke(obj);
            ItemStack st = extractItemStack(r0);
            if (!st.isEmpty()) return st;
        } catch (Throwable ignored) {}

        // Typed: getIngredientUnderMouse(IIngredientType) -> Optional<T> or T
        try {
            Class<?> vanillaTypes = Class.forName("mezz.jei.api.constants.VanillaTypes");
            Object ITEM_STACK = vanillaTypes.getField("ITEM_STACK").get(null);
            Class<?> iType = Class.forName("mezz.jei.api.ingredients.IIngredientType");
            Method m1 = obj.getClass().getMethod("getIngredientUnderMouse", iType);
            Object r1 = m1.invoke(obj, ITEM_STACK);
            ItemStack st = extractItemStack(r1);
            if (!st.isEmpty()) return st;
        } catch (Throwable ignored) {}

        return ItemStack.EMPTY;
    }

    /** Extract an ItemStack from various possible JEI return types. */
    private static ItemStack extractItemStack(Object any) {
        if (any == null) return ItemStack.EMPTY;

        // Direct ItemStack
        if (any instanceof ItemStack st) return st.copy();

        // Optional<?>
        if (any instanceof Optional<?> opt) {
            return opt.map(JeiBridge::extractItemStack).orElse(ItemStack.EMPTY);
        }

        // ITypedIngredient<?> (has getIngredient())
        try {
            Method getIngredient = any.getClass().getMethod("getIngredient");
            Object ing = getIngredient.invoke(any);
            if (ing instanceof ItemStack st) return st.copy();
        } catch (Throwable ignored) {}

        // Iterable fall-back
        if (any instanceof Iterable<?> it) {
            for (Object o : it) {
                ItemStack st = extractItemStack(o);
                if (!st.isEmpty()) return st;
            }
        }

        return ItemStack.EMPTY;
    }

    /** Call a no-arg boolean method by name; return false on any failure. */
    private static boolean callBool(Object target, String methodName) {
        try {
            Method m = target.getClass().getMethod(methodName);
            Object r = m.invoke(target);
            return (r instanceof Boolean b) && b;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
