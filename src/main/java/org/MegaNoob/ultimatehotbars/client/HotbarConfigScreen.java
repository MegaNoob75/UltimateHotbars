package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import org.MegaNoob.ultimatehotbars.Config;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.resources.ResourceLocation;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HotbarConfigScreen extends Screen {
    private final Screen parent;
    private final List<AbstractWidget> generalWidgets = new ArrayList<>();
    private final List<AbstractWidget> colorWidgets   = new ArrayList<>();

    private enum Tab { GENERAL, COLORS }
    private Tab currentTab = Tab.GENERAL;

    private int scrollOffset = 0;
    private int maxScroll    = 0;
    private int lastColorY   = 0;

    public HotbarConfigScreen(Screen parent) {
        super(Component.literal("UltimateHotbars Config"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        generalWidgets.clear();
        colorWidgets.clear();

        // --- GENERAL TAB CONTENT ---
        int y = 60;
        generalWidgets.add(
                CycleButton.onOffBuilder(Config.enableSounds())
                        .create(width/2 - 100, y, 200, 20, Component.literal("Enable Sounds"),
                                (btn,val) -> { Config.enableSounds = val; Config.syncToForgeConfig(); })
        );
        y += 24;
        generalWidgets.add(
                CycleButton.onOffBuilder(Config.showDebugOverlay())
                        .create(width/2 - 100, y, 200, 20, Component.literal("Show Debug Overlay"),
                                (btn,val) -> { Config.showDebugOverlay = val; Config.syncToForgeConfig(); })
        );
        y += 24;
        generalWidgets.add(
                CycleButton.onOffBuilder(Config.showHudLabel())
                        .create(width/2 - 100, y, 200, 20, Component.literal("Show HUD Label"),
                                (btn,val) -> { Config.showHudLabel = val; Config.syncToForgeConfig(); })
        );
        y += 24;
        generalWidgets.add(
                CycleButton.onOffBuilder(Config.showHudLabelBackground())
                        .create(width/2 - 100, y, 200, 20, Component.literal("HUD Label Background"),
                                (btn,val) -> { Config.showHudLabelBackground = val; Config.syncToForgeConfig(); })
        );

        // --- COLOR SLIDERS CONTENT ---
        addColorSliders(colorWidgets,
                "Highlighted Hotbar",
                Config.highlightColor(),
                arr -> { Config.highlightColor = arr; Config.syncToForgeConfig(); }
        );
        addColorSliders(colorWidgets,
                "HUD Label Background",
                Config.hudLabelBackgroundColor(),
                arr -> { Config.hudLabelBackgroundColor = arr; Config.syncToForgeConfig(); }
        );
        addColorSliders(colorWidgets,
                "HUD Label Text",
                Config.hudLabelTextColor(),
                arr -> { Config.hudLabelTextColor = arr; Config.syncToForgeConfig(); }
        );

        // Compute how far we can scroll (visible area = screen height minus header & footer)
        int visibleH = height - 60;
        maxScroll = Math.max(0, lastColorY - visibleH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        renderTabContent();
    }

    // 3) renderTabContent(...) – tab buttons now scroll with content
    private void renderTabContent() {
        clearWidgets();
        int yOffset = currentTab == Tab.COLORS ? -scrollOffset : 0;

        // Tab buttons (their Y is shifted by yOffset)
        addRenderableWidget(
                Button.builder(Component.literal("General"), b -> {
                            currentTab = Tab.GENERAL; scrollOffset = 0; renderTabContent();
                        })
                        .bounds(width/2 - 100, 20 + yOffset, 98, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("Colors"), b -> {
                            currentTab = Tab.COLORS; scrollOffset = 0; renderTabContent();
                        })
                        .bounds(width/2 +   2, 20 + yOffset, 98, 20).build()
        );

        // Body
        if (currentTab == Tab.GENERAL) {
            generalWidgets.forEach(this::addRenderableWidget);
        } else {
            colorWidgets.forEach(w -> {
                w.setY(w.getY() - scrollOffset);
                addRenderableWidget(w);
            });
        }

        // Done (bottom-left) & Reset (bottom-right), fixed
        addRenderableWidget(
                Button.builder(Component.literal("Done"), b -> onClose())
                        .bounds(10, height - 30, 100, 20).build()
        );
        addRenderableWidget(
                Button.builder(Component.literal("Reset to Defaults"), b -> {
                            Config.resetToDefaults(); init();
                        })
                        .bounds(width - 110, height - 30, 100, 20).build()
        );
    }

    private void addColorSliders(List<AbstractWidget> list,
                                 String groupTitle,
                                 float[] rgba,
                                 Consumer<float[]> updateTarget) {
        int x = width/2 - 100;
        int startY = list.isEmpty() ? 60 : lastColorY;
        int y = startY;

        // 1) Group title
        AbstractWidget title = new AbstractWidget(0, y, width, 14, Component.literal(groupTitle)) {
            @Override
            protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                float[] arr = rgba;
                int titleColor = ((int)(arr[3]*255)<<24)
                        | ((int)(arr[0]*255)<<16)
                        | ((int)(arr[1]*255)<<8)
                        |  (int)(arr[2]*255);
                int sliderX = x + 24, sliderW = 176;
                int titleX  = sliderX + sliderW/2;
                g.drawCenteredString(Minecraft.getInstance().font,
                        groupTitle, titleX, getY(), titleColor);
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
        };
        list.add(title);
        y += 14;

        // 2) RGBA sliders
        String[] comps = {"R","G","B","A"};
        for (int i = 0; i < 4; i++) {
            final int ch = i;
            // channel label
            AbstractWidget lbl = new AbstractWidget(x, y, 20, 20, Component.literal(comps[i] + ":")) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    g.drawString(Minecraft.getInstance().font,
                            getMessage(), getX(), getY() + 6, 0xAAAAAA, false);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
            };
            list.add(lbl);

            // slider
            AbstractSliderButton slider = new AbstractSliderButton(
                    x + 24, y, 176, 20,
                    Component.literal(""), rgba[ch]
            ) {
                // instance initializer to seed the label
                {
                    updateMessage();
                }

                @Override
                protected void updateMessage() {
                    setMessage(Component.literal(comps[ch] + ": " + (int)(value * 255)));
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

        // 3) Preview area (60×20)
        int previewW = 60, previewH = 20;
        int previewX = x + 24 + 176 + 10;
        int previewY = startY + ((y - startY)/2) - (previewH/2);

        if ("Highlighted Hotbar".equals(groupTitle)) {
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    int slotSize = 18, spacing = 2;
                    int baseX = getX(), baseY = getY();

                    // 1) translucent black background for the whole preview
                    g.fill(baseX, baseY, baseX + getWidth(), baseY + getHeight(), 0x88000000);

                    // 2) highlight bar behind the slots
                    int colorInt = ((int)(rgba[3] * 255) << 24)
                            | ((int)(rgba[0] * 255) << 16)
                            | ((int)(rgba[1] * 255) <<  8)
                            |  (int)(rgba[2] * 255);
                    g.fill(baseX, baseY, baseX + getWidth(), baseY + getHeight(), colorInt);

                    // 3) thin grey borders around each slot
                    int border = 0xFFAAAAAA;
                    for (int i = 0; i < 3; i++) {
                        int sx = baseX + i * (slotSize + spacing);
                        int sy = baseY;
                        // top
                        g.fill(sx, sy, sx + slotSize, sy + 1, border);
                        // bottom
                        g.fill(sx, sy + slotSize - 1, sx + slotSize, sy + slotSize, border);
                        // left
                        g.fill(sx, sy, sx + 1, sy + slotSize, border);
                        // right
                        g.fill(sx + slotSize - 1, sy, sx + slotSize, sy + slotSize, border);
                    }

                    // 4) mock items on top
                    Minecraft mc = Minecraft.getInstance();
                    ItemStack[] examples = {
                            new ItemStack(Items.DIAMOND),
                            new ItemStack(Items.IRON_INGOT),
                            new ItemStack(Items.GOLD_INGOT)
                    };
                    for (int i = 0; i < 3; i++) {
                        int sx = baseX + i * (slotSize + spacing);
                        int sy = baseY;
                        int ix = sx + (slotSize - 16) / 2;
                        int iy = sy + (slotSize - 16) / 2;
                        g.renderItem(examples[i], ix, iy);
                        g.renderItemDecorations(mc.font, examples[i], ix, iy, null);
                    }
                }

                @Override
                protected void updateWidgetNarration(NarrationElementOutput n) { }
            };
            list.add(preview);

        } else if ("HUD Label Background".equals(groupTitle)) {
            float[] textArr = Config.hudLabelTextColor();
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    int bgColor = ((int)(rgba[3]*255)<<24)
                            | ((int)(rgba[0]*255)<<16)
                            | ((int)(rgba[1]*255)<<8)
                            |  (int)(rgba[2]*255);
                    int txtColor = ((int)(textArr[3]*255)<<24)
                            | ((int)(textArr[0]*255)<<16)
                            | ((int)(textArr[1]*255)<<8)
                            |  (int)(textArr[2]*255);
                    g.fill(getX(), getY(), getX()+getWidth(), getY()+getHeight(), bgColor);
                    g.drawCenteredString(Minecraft.getInstance().font,
                            "Pg 1", getX() + previewW/2, getY() + (previewH - 8)/2, txtColor);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
            };
            list.add(preview);

        } else if ("HUD Label Text".equals(groupTitle)) {
            // preview the HUD‐label text on top of your HUD‐label background
            float[] bgArr = Config.hudLabelBackgroundColor();
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override
                protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    // fill with the background‐color
                    int bgColor = ((int)(bgArr[3] * 255) << 24)
                            | ((int)(bgArr[0] * 255) << 16)
                            | ((int)(bgArr[1] * 255) <<  8)
                            |  (int)(bgArr[2] * 255);
                    g.fill(getX(), getY(),
                            getX() + getWidth(), getY() + getHeight(),
                            bgColor);

                    // draw the letter(s) in your text‐color
                    int txtColor = ((int)(rgba[3] * 255) << 24)
                            | ((int)(rgba[0] * 255) << 16)
                            | ((int)(rgba[1] * 255) <<  8)
                            |  (int)(rgba[2] * 255);
                    g.drawCenteredString(Minecraft.getInstance().font,
                            "Pg 1",
                            getX() + previewW/2,
                            getY() + (previewH - 8)/2,
                            txtColor);
                }
                @Override
                protected void updateWidgetNarration(NarrationElementOutput n) { }
            };
            list.add(preview);
        }


        // 4) record end-of-group Y
        lastColorY = y;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (currentTab == Tab.COLORS) {
            scrollOffset = Mth.clamp(scrollOffset - (int)(delta * 20), 0, maxScroll);
            init();
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }

    // 3) render(....)
    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        // title scrolls with colours tab
        int titleY = 10 + (currentTab == Tab.COLORS ? -scrollOffset : 0);
        gui.drawCenteredString(font, title, width/2, titleY, 0xFFFFFF);
        super.render(gui, mx, my, pt);
    }

    @Override
    public void onClose() {
        Config.syncToForgeConfig();
        this.minecraft.setScreen(parent);
    }
}
