package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.MegaNoob.ultimatehotbars.Config;

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

        int y = 60;

        // Enable sounds toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.enableSounds())
                .create(width/2-100, y, 200, 20, Component.literal("Enable Sounds"),
                        (btn, val)->{
                            Config.enableSounds = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // Max Hotbars Per Page slider
        generalWidgets.add(new AbstractSliderButton(width/2-100, y, 200, 20,
                Component.literal("Max Hotbars Per Page: " + Config.getMaxHotbarsPerPage()),
                (Config.getMaxHotbarsPerPage() - 1) / 99.0) {
            @Override protected void updateMessage() {
                int v = (int)(1 + this.value * 99);
                setMessage(Component.literal("Max Hotbars Per Page: " + v));
            }
            @Override protected void applyValue() {
                int v = (int)(1 + this.value * 99);
                Config.setMaxHotbarsPerPage(v);
                updateMessage();
                Config.syncToForgeConfig();
            }
        });
        y += 24;

        // Debug overlay toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.showDebugOverlay())
                .create(width/2-100, y, 200, 20, Component.literal("Show Debug Overlay"),
                        (btn, val)->{
                            Config.showDebugOverlay = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // HUD Label toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.showHudLabel())
                .create(width/2-100, y, 200, 20, Component.literal("Show HUD Label"),
                        (btn, val)->{
                            Config.showHudLabel = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // HUD Label Background toggle
        generalWidgets.add(CycleButton.onOffBuilder(Config.showHudLabelBackground())
                .create(width/2-100, y, 200, 20, Component.literal("HUD Label Background"),
                        (btn, val)->{
                            Config.showHudLabelBackground = val;
                            Config.syncToForgeConfig();
                        }));
        y += 24;

        // --- Scroll Throttle Option ---
        // Explanation, line 1
        generalWidgets.add(new AbstractWidget(width/2-120, y, 240, 20,
                Component.literal("Scroll Throttle (ms): minimum 10 ms")) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(Minecraft.getInstance().font, getMessage().getString(), getX(), getY(), 0xFFFFFF, false);
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
        });
        y += 20;
        // Explanation, line 2
        generalWidgets.add(new AbstractWidget(width/2-120, y, 240, 20,
                Component.literal("default 50 ms, maximum 150 ms")) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                g.drawString(Minecraft.getInstance().font, getMessage().getString(), getX(), getY(), 0xFFFFFF, false);
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
        });
        y += 24;
        // Throttle slider
        generalWidgets.add(new AbstractSliderButton(width/2-100, y, 200, 20,
                Component.literal("Scroll Throttle: " + Config.getScrollThrottleMs() + " ms"),
                (Config.getScrollThrottleMs() - 10) / 140.0) {
            @Override protected void updateMessage() {
                int v = 10 + (int)(this.value * 140);
                setMessage(Component.literal("Scroll Throttle: " + v + " ms"));
            }
            @Override protected void applyValue() {
                int v = 10 + (int)(this.value * 140);
                Config.setScrollThrottleMs(v);
                updateMessage();
                Config.syncToForgeConfig();
            }
        });
        y += 24;
        // --- End Scroll Throttle Option ---

        // Color sliders & previews
        addColorSliders(colorWidgets, "Highlighted Hotbar", Config.highlightColor(), arr->{
            Config.highlightColor = arr;
            Config.syncToForgeConfig();
        });
        addColorSliders(colorWidgets, "HUD Label Background", Config.hudLabelBackgroundColor(), arr->{
            Config.hudLabelBackgroundColor = arr;
            Config.syncToForgeConfig();
        });
        addColorSliders(colorWidgets, "HUD Label Text", Config.hudLabelTextColor(), arr->{
            Config.hudLabelTextColor = arr;
            Config.syncToForgeConfig();
        });
        addColorSliders(colorWidgets, "Hover Slot Border", Config.hoverBorderColor(), arr->{
            Config.hoverBorderColor = arr;
            Config.syncToForgeConfig();
        });

        int visibleH = height - 60;
        maxScroll   = Math.max(0, lastColorY - visibleH);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        renderTabContent();
    }

    private void renderTabContent() {
        clearWidgets();
        int yOffset = (currentTab == Tab.COLORS) ? -scrollOffset : 0;

        addRenderableWidget(Button.builder(Component.literal("General"), b->{
            currentTab = Tab.GENERAL; scrollOffset = 0; renderTabContent();
        }).bounds(width/2-100, 20+yOffset, 98, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Colors"), b->{
            currentTab = Tab.COLORS; scrollOffset = 0; renderTabContent();
        }).bounds(width/2+2, 20+yOffset, 98, 20).build());

        if (currentTab == Tab.GENERAL) {
            generalWidgets.forEach(this::addRenderableWidget);
        } else {
            colorWidgets.forEach(w->{
                w.setY(w.getY() - scrollOffset);
                addRenderableWidget(w);
            });
        }

        addRenderableWidget(Button.builder(Component.literal("Done"), b->onClose())
                .bounds(10, height-30, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Reset to Defaults"), b->{
            Config.resetToDefaults(); init();
        }).bounds(width-110, height-30, 100, 20).build());
    }

    private void addColorSliders(List<AbstractWidget> list, String groupTitle, float[] rgba, Consumer<float[]> updateTarget) {
        int x = width/2 - 100;
        int startY = list.isEmpty() ? 60 : lastColorY;
        int y = startY;

        // Title
        AbstractWidget title = new AbstractWidget(0, y, width, 14, Component.literal(groupTitle)) {
            @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                float[] arr = rgba;
                int col = ((int)(arr[3]*255)<<24)
                        |((int)(arr[0]*255)<<16)
                        |((int)(arr[1]*255)<<8)
                        |(int)(arr[2]*255);
                int sx = x+24, sw = 176;
                g.drawCenteredString(Minecraft.getInstance().font, groupTitle, sx+sw/2, getY(), col);
            }
            @Override protected void updateWidgetNarration(NarrationElementOutput n) {}
        };
        list.add(title); y+=14;

        // RGBA sliders
        String[] comps = {"R","G","B","A"};
        for(int i=0;i<4;i++){
            int ch = i;
            AbstractWidget lbl = new AbstractWidget(x, y, 20,20, Component.literal(comps[i]+":")) {
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt){
                    g.drawString(Minecraft.getInstance().font, getMessage(), getX(), getY()+6, 0xAAAAAA, false);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n){}
            };
            list.add(lbl);

            AbstractSliderButton slider = new AbstractSliderButton(x+24, y,176,20, Component.literal(""), rgba[ch]){
                { updateMessage(); }
                @Override protected void updateMessage(){
                    setMessage(Component.literal(comps[ch]+": "+(int)(value*255)));
                }
                @Override protected void applyValue(){
                    rgba[ch] = (float)value;
                    updateTarget.accept(rgba);
                }
            };
            list.add(slider);
            y += slider.getHeight();
        }

        // Preview area
        int previewW = 60, previewH = 20;
        int previewX = x+24+176+10;
        int previewY = startY + ((y-startY)/2) - (previewH/2);

        if ("Highlighted Hotbar".equals(groupTitle)) {
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt) {
                    int slotSize=18, spacing=2;
                    g.fill(getX(), getY(), getX()+getWidth(), getY()+getHeight(), 0x88000000);
                    int colorInt = ((int)(rgba[3]*255)<<24)
                            |((int)(rgba[0]*255)<<16)
                            |((int)(rgba[1]*255)<<8)
                            |(int)(rgba[2]*255);
                    g.fill(getX(), getY(), getX()+getWidth(), getY()+getHeight(), colorInt);
                    int border=0xFFAAAAAA;
                    for(int i=0;i<3;i++){
                        int sx = getX() + i*(slotSize+spacing);
                        g.fill(sx, getY(), sx+slotSize, getY()+1, border);
                        g.fill(sx, getY()+slotSize-1, sx+slotSize, getY()+slotSize, border);
                        g.fill(sx, getY(), sx+1, getY()+slotSize, border);
                        g.fill(sx+slotSize-1, getY(), sx+slotSize, getY()+slotSize, border);
                    }
                    Minecraft mc = Minecraft.getInstance();
                    ItemStack[] ex = {
                            new ItemStack(Items.DIAMOND),
                            new ItemStack(Items.IRON_INGOT),
                            new ItemStack(Items.GOLD_INGOT)
                    };
                    for(int i=0;i<3;i++){
                        int sx = getX()+i*(slotSize+spacing);
                        int ix = sx + (slotSize-16)/2;
                        int iy = getY() + (slotSize-16)/2;
                        g.renderItem(ex[i], ix, iy);
                        g.renderItemDecorations(mc.font, ex[i], ix, iy);
                    }
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n){}
            };
            list.add(preview);
        }
        else if ("HUD Label Background".equals(groupTitle)) {
            float[] textArr = Config.hudLabelTextColor();
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt){
                    int bgCol = ((int)(rgba[3]*255)<<24)
                            |((int)(rgba[0]*255)<<16)
                            |((int)(rgba[1]*255)<<8)
                            |(int)(rgba[2]*255);
                    int txtCol = ((int)(textArr[3]*255)<<24)
                            |((int)(textArr[0]*255)<<16)
                            |((int)(textArr[1]*255)<<8)
                            |(int)(textArr[2]*255);
                    g.fill(getX(), getY(), getX()+getWidth(), getY()+getHeight(), bgCol);
                    g.drawCenteredString(Minecraft.getInstance().font, "Pg 1",
                            getX()+previewW/2, getY()+(previewH-8)/2, txtCol);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n){}
            };
            list.add(preview);
        }
        else if ("HUD Label Text".equals(groupTitle)) {
            float[] bgArr = Config.hudLabelBackgroundColor();
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt){
                    int bgCol = ((int)(bgArr[3]*255)<<24)
                            |((int)(bgArr[0]*255)<<16)
                            |((int)(bgArr[1]*255)<<8)
                            |(int)(bgArr[2]*255);
                    int txtCol = ((int)(rgba[3]*255)<<24)
                            |((int)(rgba[0]*255)<<16)
                            |((int)(rgba[1]*255)<<8)
                            |(int)(rgba[2]*255);
                    g.fill(getX(), getY(), getX()+getWidth(), getY()+getHeight(), bgCol);
                    g.drawCenteredString(Minecraft.getInstance().font, "Pg 1",
                            getX()+previewW/2, getY()+(previewH-8)/2, txtCol);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n){}
            };
            list.add(preview);
        }
        else if ("Hover Slot Border".equals(groupTitle)) {
            AbstractWidget preview = new AbstractWidget(previewX, previewY, previewW, previewH, Component.empty()) {
                @Override protected void renderWidget(GuiGraphics g, int mx, int my, float pt){
                    int slotSize=18;
                    int sx = getX() + (previewW-slotSize)/2;
                    int sy = getY();
                    g.fill(sx, sy, sx+slotSize, sy+slotSize, 0x88000000);
                    ItemStack example = new ItemStack(Items.DIAMOND);
                    int ix = sx + (slotSize-16)/2;
                    int iy = sy + (slotSize-16)/2;
                    g.renderItem(example, ix, iy);
                    g.renderItemDecorations(Minecraft.getInstance().font, example, ix, iy);
                    float[] arr = rgba;
                    int r = (int)(arr[0]*255), gcol = (int)(arr[1]*255),
                            b = (int)(arr[2]*255), a = (int)(arr[3]*255);
                    int col = (a<<24)|(r<<16)|(gcol<<8)|b;
                    int t = 1;
                    g.fill(sx, sy, sx+slotSize, sy+t, col);
                    g.fill(sx, sy+slotSize-t, sx+slotSize, sy+slotSize, col);
                    g.fill(sx, sy, sx+t, sy+slotSize, col);
                    g.fill(sx+slotSize-t, sy, sx+slotSize, sy+slotSize, col);
                }
                @Override protected void updateWidgetNarration(NarrationElementOutput n){}
            };
            list.add(preview);
        }

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

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
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
