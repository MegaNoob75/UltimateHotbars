package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import org.MegaNoob.ultimatehotbars.Config;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HotbarConfigScreen extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> generalWidgets = new ArrayList<>();
    private final List<AbstractWidget> colorWidgets = new ArrayList<>();

    private enum Tab { GENERAL, COLORS }
    private Tab currentTab = Tab.GENERAL;

    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int lastColorY = 0;

    public HotbarConfigScreen(Screen parent) {
        super(Component.literal("UltimateHotbars Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        generalWidgets.clear();
        colorWidgets.clear();

        int y = 60;
        // Enable sounds toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.enableSounds())
                .create(width / 2 - 100, y, 200, 20, Component.literal("Enable Sounds"),
                        (btn, val) -> {
                            Config.enableSounds = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // Max Hotbars Per Page slider
        generalWidgets.add(new AbstractSliderButton(width / 2 - 100, y, 200, 20,
                Component.literal("Max Hotbars Per Page: " + Config.getMaxHotbarsPerPage()),
                (Config.getMaxHotbarsPerPage() - 1) / 99.0
        ) {
            @Override
            protected void updateMessage() {
                int value = (int) (1 + this.value * 99);
                setMessage(Component.literal("Max Hotbars Per Page: " + value));
            }
            @Override
            protected void applyValue() {
                int value = (int) (1 + this.value * 99);
                Config.setMaxHotbarsPerPage(value);
                updateMessage();
                Config.syncToForgeConfig();
            }
        });
        y += 24;

        // Debug overlay toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.showDebugOverlay())
                .create(width / 2 - 100, y, 200, 20, Component.literal("Show Debug Overlay"),
                        (btn, val) -> {
                            Config.showDebugOverlay = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // HUD Label toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.showHudLabel())
                .create(width / 2 - 100, y, 200, 20, Component.literal("Show HUD Label"),
                        (btn, val) -> {
                            Config.showHudLabel = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // HUD Label Background toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.showHudLabelBackground())
                .create(width / 2 - 100, y, 200, 20, Component.literal("HUD Label Background"),
                        (btn, val) -> {
                            Config.showHudLabelBackground = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // --- New Scroll Throttle Option ---
        // Explanation labels (split into two lines for readability)
        generalWidgets.add(new AbstractWidget(width / 2 - 120, y, 240, 20,
                Component.literal("Scroll Throttle (ms): minimum 10 ms")) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(Minecraft.getInstance().font,
                        getMessage().getString(), getX(), getY(), 0xFFFFFF, false);
            }
            @Override
            protected void updateWidgetNarration(NarrationElementOutput n) {}
        });
        y += 20;
        generalWidgets.add(new AbstractWidget(width / 2 - 120, y, 240, 20,
                Component.literal("default 50 ms, maximum 150 ms")) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(Minecraft.getInstance().font,
                        getMessage().getString(), getX(), getY(), 0xFFFFFF, false);
            }
            @Override
            protected void updateWidgetNarration(NarrationElementOutput n) {}
        });
        y += 24;
        // Throttle slider (10-150 ms)
        generalWidgets.add(new AbstractSliderButton(width / 2 - 100, y, 200, 20,
                Component.literal("Scroll Throttle: " + Config.getScrollThrottleMs() + " ms"),
                (Config.getScrollThrottleMs() - 10) / 140.0) {
            @Override
            protected void updateMessage() {
                int v = 10 + (int) (this.value * 140);
                setMessage(Component.literal("Scroll Throttle: " + v + " ms"));
            }
            @Override
            protected void applyValue() {
                int v = 10 + (int) (this.value * 140);
                Config.setScrollThrottleMs(v);
                updateMessage();
                Config.syncToForgeConfig();
            }
        });
        y += 24;
        // --- End Scroll Throttle Option ---

        // Color sliders
        addColorSliders(colorWidgets, "Highlighted Hotbar", Config.highlightColor(), arr -> {
            Config.highlightColor = arr;
            Config.syncToForgeConfig();
        });
        addColorSliders(colorWidgets, "HUD Label Background", Config.hudLabelBackgroundColor(), arr -> {
            Config.hudLabelBackgroundColor = arr;
            Config.syncToForgeConfig();
        });
        addColorSliders(colorWidgets, "HUD Label Text", Config.hudLabelTextColor(), arr -> {
            Config.hudLabelTextColor = arr;
            Config.syncToForgeConfig();
        });
        addColorSliders(colorWidgets, "Hover Slot Border", Config.hoverBorderColor(), arr -> {
            Config.hoverBorderColor = arr;
            Config.syncToForgeConfig();
        });

        int visibleH = height - 60;
        maxScroll = Math.max(0, lastColorY - visibleH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
        renderTabContent();
    }

    private void renderTabContent() {
        clearWidgets();
        int yOffset = currentTab == Tab.COLORS ? -scrollOffset : 0;

        addRenderableWidget(Button.builder(Component.literal("General"), btn -> {
                    currentTab = Tab.GENERAL;
                    scrollOffset = 0;
                    renderTabContent();
                })
                .bounds(width / 2 - 100, 20 + yOffset, 98, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Colors"), btn -> {
                    currentTab = Tab.COLORS;
                    scrollOffset = 0;
                    renderTabContent();
                })
                .bounds(width / 2 + 2, 20 + yOffset, 98, 20).build());

        if (currentTab == Tab.GENERAL) {
            generalWidgets.forEach(this::addRenderableWidget);
        } else {
            colorWidgets.forEach(w -> {
                w.setY(w.getY() - scrollOffset);
                addRenderableWidget(w);
            });
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), btn -> onClose())
                .bounds(10, height - 30, 100, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), btn -> {
                    Config.resetToDefaults();
                    init();
                })
                .bounds(width - 110, height - 30, 100, 20).build());
    }

    private void addColorSliders(List<AbstractWidget> list, String groupTitle, float[] rgba, Consumer<float[]> updateTarget) {
        int x = width / 2 - 100;
        int startY = list.isEmpty() ? 60 : lastColorY;
        int y = startY;

        // Group title
        AbstractWidget title = new AbstractWidget(0, y, width, 14, Component.literal(groupTitle)) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                float[] arr = rgba;
                int color = ((int) (arr[3] * 255) << 24)
                        | ((int) (arr[0] * 255) << 16)
                        | ((int) (arr[1] * 255) << 8)
                        | (int) (arr[2] * 255);
                int sliderX = x + 24, sliderW = 176;
                g.drawCenteredString(Minecraft.getInstance().font, groupTitle, sliderX + sliderW / 2, getY(), color);
            }

            @Override
            protected void updateWidgetNarration(NarrationElementOutput n) {
            }
        };
        list.add(title);
        y += 14;

        // RGBA sliders
        String[] comps = {"R", "G", "B", "A"};
        for (int i = 0; i < 4; i++) {
            final int ch = i;
            AbstractWidget lbl = new AbstractWidget(x, y, 20, 20, Component.literal(comps[i] + ":")) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(Minecraft.getInstance().font, getMessage(), getX(), getY() + 6, 0xAAAAAA, false);
                }

                @Override
                protected void updateWidgetNarration(NarrationElementOutput n) {
                }
            };
            list.add(lbl);

            AbstractSliderButton slider = new AbstractSliderButton(x + 24, y, 176, 20, Component.literal(""), rgba[ch]) {
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    setMessage(Component.literal(comps[ch] + ": " + (int) (value * 255)));
                }

                @Override
                protected void applyValue() {
                    rgba[ch] = (float) value;
                    updateTarget.accept(rgba);
                }
            };
            list.add(slider);
            y += slider.getHeight();
        }

        // Preview areas follow … [unchanged]
        // …
        lastColorY = y;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentTab == Tab.COLORS) {
            scrollOffset = Mth.clamp(scrollOffset - (int) (delta * 20), 0, maxScroll);
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        int titleY = 10 + (currentTab == Tab.COLORS ? -scrollOffset : 0);
        gui.drawCenteredString(font, title, width / 2, titleY, 0xFFFFFF);
        super.render(gui, mx, my, pt);
    }

    @Override
    public void onClose() {
        Config.syncToForgeConfig();
        this.minecraft.setScreen(parent);
    }
}
