package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.lwjgl.glfw.GLFW;

public class PageListWidget extends ObjectSelectionList<PageListWidget.Entry> {
    private final HotbarGuiScreen parent;
    private final Font font;

    public PageListWidget(HotbarGuiScreen parent, Minecraft mc, Font font,
                          int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
        this.parent = parent;
        this.font = font;
    }

    // This will keep the vanilla scrollbar on the right edge (default is -6 from right)
    @Override
    protected int getScrollbarPosition() {
        return this.x0 + this.width - 6; // or adjust for more/less padding if desired
    }

    @Override
    public int getRowWidth() {
        return this.width;
    }

    public Entry getHoveredEntry() {
        return this.getHovered();
    }

    public void updatePages() {
        this.clearEntries();
        var names = HotbarManager.getPageNames();
        int currentPage = HotbarManager.getPage();

        for (int i = 0; i < names.size(); i++) {
            this.addEntry(new Entry(names.get(i), i));
        }

        if (currentPage >= 0 && currentPage < this.children().size()) {
            this.setSelected(this.children().get(currentPage));
            this.ensureVisible(this.children().get(currentPage));
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.isMouseOver(mouseX, mouseY)) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        return false;
    }

    public class Entry extends ObjectSelectionList.Entry<Entry> {
        private final String name;
        private final int index;

        public Entry(String name, int index) {
            this.name = name;
            this.index = index;
        }

        @Override
        public void render(GuiGraphics g, int entryIdx, int y, int x, int listW, int entryH,
                           int mouseX, int mouseY, boolean isSelected, float pt) {
            int color = isSelected ? 0xFFFFFFFF : 0xFFAAAAAA;
            if (!isSelected && mouseX >= x && mouseX < x + listW && mouseY >= y && mouseY < y + entryH) {
                float[] c = Config.highlightColor();
                color = ((int)(c[3] * 255) << 24)
                        | ((int)(c[0] * 255) << 16)
                        | ((int)(c[1] * 255) << 8)
                        |  (int)(c[2] * 255);
            }
            g.drawString(PageListWidget.this.font, name, x + 2, y + (entryH - font.lineHeight) / 2, color, false);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            if (btn == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // --- CRITICAL: Save everything before switching ---
                HotbarManager.syncFromGame();
                if (HotbarManager.isDirty()) {
                    HotbarManager.saveHotbars();
                }

                HotbarManager.setPage(index, 0);

                HotbarManager.syncToGame();

                parent.updatePageInput();
                return true;
            }
            return false;
        }



        @Override public void updateNarration(net.minecraft.client.gui.narration.NarrationElementOutput n) {}
        @Override public Component getNarration() {
            return Component.literal(name);
        }
    }
}
