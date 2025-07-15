package org.MegaNoob.ultimatehotbars;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent.Loading;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.BooleanValue ENABLE_SOUNDS =
            BUILDER.comment("Play sound when switching hotbars/pages").define("enableSounds", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_DEBUG_OVERLAY =
            BUILDER.comment("Show debug overlay for UltimateHotbars").define("showDebugOverlay", false);

    public static final ForgeConfigSpec.BooleanValue SHOW_HUD_LABEL =
            BUILDER.comment("Display the hotbar/page label on HUD").define("showHudLabel", true);

    public static final ForgeConfigSpec.BooleanValue SHOW_HUD_LABEL_BACKGROUND =
            BUILDER.comment("Enable background behind HUD label text").define("showHudLabelBackground", true);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HIGHLIGHT_COLOR =
            BUILDER.comment("RGBA color for GUI highlight").defineList("highlightColor", List.of(1.0D, 1.0D, 0.0D, 0.8D), obj -> obj instanceof Double);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HUD_LABEL_BG_COLOR =
            BUILDER.comment("RGBA color for HUD label background").defineList("hudLabelBackgroundColor", List.of(0.0D, 0.0D, 0.0D, 0.5D), obj -> obj instanceof Double);

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HUD_LABEL_TEXT_COLOR =
            BUILDER.comment("RGBA color for HUD label text").defineList("hudLabelTextColor", List.of(1.0D, 1.0D, 1.0D, 1.0D), obj -> obj instanceof Double);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enableSounds = true;
    public static boolean showDebugOverlay = false;
    public static boolean showHudLabel = true;
    public static boolean showHudLabelBackground = true;
    public static float[] highlightColor = new float[]{1.0f, 1.0f, 0.0f, 0.8f};
    public static float[] hudLabelBackgroundColor = new float[]{0.0f, 0.0f, 0.0f, 0.5f};
    public static float[] hudLabelTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};

    @SubscribeEvent
    static void onLoad(final Loading event) {
        enableSounds = ENABLE_SOUNDS.get();
        showDebugOverlay = SHOW_DEBUG_OVERLAY.get();
        showHudLabel = SHOW_HUD_LABEL.get();
        showHudLabelBackground = SHOW_HUD_LABEL_BACKGROUND.get();
        highlightColor = toFloatArray(HIGHLIGHT_COLOR.get());
        hudLabelBackgroundColor = toFloatArray(HUD_LABEL_BG_COLOR.get());
        hudLabelTextColor = toFloatArray(HUD_LABEL_TEXT_COLOR.get());
    }

    public static boolean enableSounds() {
        return enableSounds;
    }

    public static boolean showDebugOverlay() {
        return showDebugOverlay;
    }

    public static boolean showHudLabel() {
        return showHudLabel;
    }

    public static boolean showHudLabelBackground() {
        return showHudLabelBackground;
    }

    public static float[] highlightColor() {
        return highlightColor;
    }

    public static float[] hudLabelBackgroundColor() {
        return hudLabelBackgroundColor;
    }

    public static float[] hudLabelTextColor() {
        return hudLabelTextColor;
    }

    private static float[] toFloatArray(List<? extends Double> list) {
        float[] rgba = new float[4];
        for (int i = 0; i < 4 && i < list.size(); i++) {
            rgba[i] = list.get(i).floatValue();
        }
        return rgba;
    }

    public static List<Double> toDoubleList(float[] rgba) {
        return List.of((double) rgba[0], (double) rgba[1], (double) rgba[2], (double) rgba[3]);
    }


    public static void resetToDefaults() {
        enableSounds = true;
        showDebugOverlay = false;
        showHudLabel = true;
        showHudLabelBackground = true;
        highlightColor = new float[]{1.0f, 1.0f, 0.0f, 0.8f};
        hudLabelBackgroundColor = new float[]{0.0f, 0.0f, 0.0f, 0.5f};
        hudLabelTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    }

    public static void save() {
        // Trigger a config reload and save if needed (placeholder for now)
        // Currently Forge auto-saves config changes, so this can be a no-op
    }


    public static void syncToForgeConfig() {
        ENABLE_SOUNDS.set(enableSounds);
        SHOW_DEBUG_OVERLAY.set(showDebugOverlay);
        SHOW_HUD_LABEL.set(showHudLabel);
        SHOW_HUD_LABEL_BACKGROUND.set(showHudLabelBackground);
        HIGHLIGHT_COLOR.set(List.of((double)highlightColor[0], (double)highlightColor[1], (double)highlightColor[2], (double)highlightColor[3]));
        HUD_LABEL_BG_COLOR.set(List.of((double)hudLabelBackgroundColor[0], (double)hudLabelBackgroundColor[1], (double)hudLabelBackgroundColor[2], (double)hudLabelBackgroundColor[3]));
        HUD_LABEL_TEXT_COLOR.set(List.of((double)hudLabelTextColor[0], (double)hudLabelTextColor[1], (double)hudLabelTextColor[2], (double)hudLabelTextColor[3]));
    }
}
