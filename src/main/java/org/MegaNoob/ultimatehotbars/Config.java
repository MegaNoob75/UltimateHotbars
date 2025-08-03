package org.MegaNoob.ultimatehotbars;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent.Loading;
import java.util.List;

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
            BUILDER.comment("RGBA color for GUI highlight").defineList(
                    "highlightColor",
                    List.of(1.0D, 1.0D, 0.0D, 0.8D),
                    obj -> obj instanceof Double
            );

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HUD_LABEL_BG_COLOR =
            BUILDER.comment("RGBA color for HUD label background").defineList(
                    "hudLabelBackgroundColor",
                    List.of(0.0D, 0.0D, 0.0D, 0.5D),
                    obj -> obj instanceof Double
            );

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HUD_LABEL_TEXT_COLOR =
            BUILDER.comment("RGBA color for HUD label text").defineList(
                    "hudLabelTextColor",
                    List.of(1.0D, 1.0D, 1.0D, 1.0D),
                    obj -> obj instanceof Double
            );

    public static final ForgeConfigSpec.ConfigValue<List<? extends Double>> HOVER_BORDER_COLOR =
            BUILDER.comment("RGBA color for hover-slot border highlight").defineList(
                    "hoverBorderColor",
                    List.of(1.0D, 1.0D, 1.0D, 1.0D),
                    obj -> obj instanceof Double
            );

    // Peek hotbar rows
    public static final ForgeConfigSpec.IntValue PEEK_ROWS =
            BUILDER.comment("Number of hotbar rows visible in PeekHotbarScreen")
                    .defineInRange("peekRows", 5, 1, 20);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    // Backing fields
    public static boolean enableSounds = true;
    public static boolean showDebugOverlay = false;
    public static boolean showHudLabel = true;
    public static boolean showHudLabelBackground = true;
    public static float[] highlightColor = new float[]{1.0f, 1.0f, 0.0f, 0.8f};
    public static float[] hudLabelBackgroundColor = new float[]{0.0f, 0.0f, 0.0f, 0.5f};
    public static float[] hudLabelTextColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};
    public static float[] hoverBorderColor = new float[]{1.0f, 1.0f, 1.0f, 1.0f};

    // Legacy setting: maximum hotbars per page in main GUI
    public static int maxHotbarsPerPage = 20;

    @SubscribeEvent
    static void onLoad(final Loading event) {
        enableSounds = ENABLE_SOUNDS.get();
        showDebugOverlay = SHOW_DEBUG_OVERLAY.get();
        showHudLabel = SHOW_HUD_LABEL.get();
        showHudLabelBackground = SHOW_HUD_LABEL_BACKGROUND.get();
        highlightColor = toFloatArray(HIGHLIGHT_COLOR.get());
        hudLabelBackgroundColor = toFloatArray(HUD_LABEL_BG_COLOR.get());
        hudLabelTextColor = toFloatArray(HUD_LABEL_TEXT_COLOR.get());
        hoverBorderColor = toFloatArray(HOVER_BORDER_COLOR.get());
        // maxHotbarsPerPage is loaded elsewhere in GUI logic
    }

    // Accessors
    public static boolean enableSounds() { return enableSounds; }
    public static boolean showDebugOverlay() { return showDebugOverlay; }
    public static boolean showHudLabel() { return showHudLabel; }
    public static boolean showHudLabelBackground() { return showHudLabelBackground; }
    public static float[] highlightColor() { return highlightColor; }
    public static float[] hudLabelBackgroundColor() { return hudLabelBackgroundColor; }
    public static float[] hudLabelTextColor() { return hudLabelTextColor; }
    public static float[] hoverBorderColor() { return hoverBorderColor; }

    /** Rows in PeekHotbarScreen */
    public static int peekVisibleRows() { return PEEK_ROWS.get(); }

    /** Max hotbars per page in main GUI */
    public static int getMaxHotbarsPerPage() { return maxHotbarsPerPage; }
    public static void setMaxHotbarsPerPage(int value) {
        maxHotbarsPerPage = value;
        save();
    }

    public static int getScrollThrottleMs() { return scrollThrottleMs; }
    public static void setScrollThrottleMs(int ms) { scrollThrottleMs = ms; save(); }

    // scrollThrottleMs field
    private static int scrollThrottleMs = 50;

    private static float[] toFloatArray(List<? extends Double> list) {
        float[] rgba = new float[4];
        for (int i = 0; i < 4 && i < list.size(); i++) rgba[i] = list.get(i).floatValue();
        return rgba;
    }
    public static List<Double> toDoubleList(float[] rgba) {
        return List.of((double)rgba[0],(double)rgba[1],(double)rgba[2],(double)rgba[3]);
    }

    public static void resetToDefaults() {
        enableSounds = true;
        showDebugOverlay = false;
        showHudLabel = true;
        showHudLabelBackground = true;
        highlightColor = new float[]{1,1,0,0.8f};
        hudLabelBackgroundColor = new float[]{0,0,0,0.5f};
        hudLabelTextColor = new float[]{1,1,1,1};
        hoverBorderColor = new float[]{1,1,1,1};
        // peekRows remains at user-configured value
        // maxHotbarsPerPage unchanged
    }

    public static void save() {
        // No-op; Forge auto-saves
    }

    public static void syncToForgeConfig() {
        ENABLE_SOUNDS.set(enableSounds);
        SHOW_DEBUG_OVERLAY.set(showDebugOverlay);
        SHOW_HUD_LABEL.set(showHudLabel);
        SHOW_HUD_LABEL_BACKGROUND.set(showHudLabelBackground);
        HIGHLIGHT_COLOR.set(toDoubleList(highlightColor));
        HUD_LABEL_BG_COLOR.set(toDoubleList(hudLabelBackgroundColor));
        HUD_LABEL_TEXT_COLOR.set(toDoubleList(hudLabelTextColor));
        HOVER_BORDER_COLOR.set(toDoubleList(hoverBorderColor));
        // PEEK_ROWS not modified here
    }
}
