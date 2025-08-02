package org.MegaNoob.ultimatehotbars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtIo;
import org.MegaNoob.ultimatehotbars.network.PacketHandler;
import org.MegaNoob.ultimatehotbars.network.SyncHotbarPacket;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HotbarManager {

    // Holds the virtual hotbars: pages.get(pageIndex).get(hotbarIndex)
    private static final List<List<Hotbar>> pages     = new ArrayList<>();
    // Holds the display names for each page
    private static final List<String>       pageNames = new ArrayList<>();
    private static boolean dirty = false;
    private static int currentPage   = 0;
    private static int currentHotbar = 0;
    private static int currentSlot   = 0;

    private static final File SAVE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars_hotbars.dat").toFile();

    // Debug tracking
    public static String lastHotbarSource = "";
    public static int    lastHotbarSet    = -1;
    public static int    lastSavedHotbar  = -1;
    public static int    lastSavedPage    = -1;
    public static int    lastSavedSlot    = -1;

    static {
        // On class load, either load existing pages, or create one default page
        if (SAVE_FILE.exists()) {
            loadHotbars();
        } else {
            addPageInternal();
        }
    }

    // ================================================================
    // Page & Name Management
    // ================================================================

    /** Returns how many pages are currently available. */
    public static int getPageCount() {
        return pages.size();
    }

    public static void clearCurrentHotbar() {
        List<Hotbar> hotbars = pages.get(currentPage);
        hotbars.get(currentHotbar).clear();
        // ◀ flag & persist
        markDirty();
        saveHotbars();
        // ◀ push the cleared state into the real inventory
        syncToGame();
    }


    /** Returns an immutable copy of all page names. */
    public static List<String> getPageNames() {
        return new ArrayList<>(pageNames);
    }

    /**
     * Finds “Page N” where N is the smallest positive integer not yet used.
     */
    public static String getNextAvailablePageName() {
        int index = 1;
        while (pageNames.contains("Page " + index)) {
            index++;
        }
        return "Page " + index;
    }

    /** Adds a new empty page at the end, default named "Page N". */
    public static void addPage() {
        addPageInternal();
    }

    /** INTERNAL: create one new empty page + default name, starting with a single hotbar. */
    private static void addPageInternal() {
        // Compute unique name
        String newPageName = getNextAvailablePageName();
        // Add to names list
        pageNames.add(newPageName);
        // Create a new page with one Hotbar
        List<Hotbar> newPage = new ArrayList<>();
        newPage.add(new Hotbar());
        // Add to pages
        pages.add(newPage);
        // Persist
        saveHotbars();
    }

    /** Removes the page at index (except index=0), then rewraps if needed. */
    public static void removePage(int index) {
        if (index <= 0 || index >= pages.size()) return;
        pages.remove(index);
        pageNames.remove(index);
        if (currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }
        clampHotbarIndex();
    }

    /**
     * Renames the page at idx to the given newName.
     * Returns true if successful, false if newName is empty or duplicate.
     */
    public static boolean renamePage(int idx, String newName) {
        newName = newName.trim();
        if (newName.isEmpty() || pageNames.contains(newName)) {
            return false;
        }
        pageNames.set(idx, newName);
        saveHotbars();
        return true;
    }

    // ================================================================
    // Hotbar manipulation
    // ================================================================

    /**
     * Adds one new empty hotbar to the current page, up to the configured max.
     */
    public static void addHotbarToCurrentPage() {
        List<Hotbar> page = pages.get(currentPage);
        if (page.size() < Config.getMaxHotbarsPerPage()) {
            page.add(new Hotbar());
            clampHotbarIndex();
            saveHotbars();
        }
    }

    /**
     * Removes the currently selected hotbar from the current page.
     * Never removes the last remaining hotbar on the page.
     */
    public static void removeSelectedHotbarFromCurrentPage() {
        List<Hotbar> page = pages.get(currentPage);
        if (page.size() <= 1) {
            return;
        }
        page.remove(currentHotbar);
        if (currentHotbar >= page.size()) {
            currentHotbar = page.size() - 1;
        }
        clampHotbarIndex();
        saveHotbars();
        syncToGame();
    }

    /** Defensive: always returns a valid hotbar. */
    public static Hotbar getCurrentHotbar() {
        List<Hotbar> page = pages.get(currentPage);
        int idx = currentHotbar;
        if (page.isEmpty()) {
            page.add(new Hotbar());
            idx = 0;
        }
        if (idx < 0) idx = 0;
        if (idx >= page.size()) idx = page.size() - 1;
        return page.get(idx);
    }

    public static List<Hotbar> getCurrentPageHotbars() {
        return new ArrayList<>(pages.get(currentPage));
    }

    public static int getPage()   { return currentPage; }
    public static int getHotbar() { return currentHotbar; }
    public static int getSlot()   { return currentSlot; }



    public static void markDirty() {
        dirty = true;
    }


    /** Returns true if there are unsaved changes */
    public static boolean isDirty() {
        return dirty;
    }


    /** Selects a new page (and clamps the hotbar index), then syncs. */
    public static void setPage(int page, int resetHotbar) {
        currentPage = Mth.clamp(page, 0, pages.size() - 1);
        List<Hotbar> hotbars = pages.get(currentPage);
        currentHotbar = Mth.clamp(resetHotbar, 0, hotbars.size() - 1);
        lastHotbarSet    = currentHotbar;
        lastHotbarSource = "page switch";
        clampHotbarIndex();
        // ← no markDirty() here
        syncToGame();
    }

    /**
     * Switch to the given hotbar index (hb), wrapping within the current page only.
     */
    public static void setHotbar(int hb, String sourceTag) {
        if (lastHotbarSource.equals("arrow page switch") && !"arrow page switch".equals(sourceTag)) {
            System.out.println("Blocked setHotbar(" + hb + ") from " + sourceTag);
            return;
        }

        List<Hotbar> hotbars = pages.get(currentPage);
        int count = hotbars.size();
        currentHotbar = ((hb % count) + count) % count;
        lastHotbarSet    = currentHotbar;
        lastHotbarSource = sourceTag;

        if (!"arrow page switch".equals(sourceTag) && !"page switch".equals(sourceTag)) {
            clampHotbarIndex();
            // ← no markDirty() here
            syncToGame();
        }
    }


    /** Defensive clamp: ensure currentHotbar is valid after hotbar/page changes. */
    public static void clampHotbarIndex() {
        int max = Math.max(0, getCurrentPageHotbars().size() - 1);
        int idx = getHotbar();
        if (idx > max) setHotbar(max, "clamp");
        if (idx < 0) setHotbar(0, "clamp");
    }

    public static void setSlot(int slot) {
        currentSlot = Math.max(0, Math.min(slot, Hotbar.SLOT_COUNT - 1));
    }

    // ================================================================
    // Syncing to Game & Networking
    // ================================================================

    public static void syncToGame() {
        clampHotbarIndex();
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // 1) Clear the real hotbar
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            mc.player.getInventory().setItem(i, ItemStack.EMPTY);
        }

        // 2) Populate from our virtual hotbar
        Hotbar vb = getCurrentHotbar();
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            ItemStack s = vb.getSlot(i);
            if (s != null && !s.isEmpty()) {
                mc.player.getInventory().setItem(i, s.copy());
            }
        }

        // 3) Send to server
        ItemStack[] stacks = new ItemStack[Hotbar.SLOT_COUNT];
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            stacks[i] = vb.getSlot(i).copy();
        }
        PacketHandler.CHANNEL.sendToServer(
                new SyncHotbarPacket(getPage(), getHotbar(), stacks)
        );
    }

    /**
     * Pulls whatever the player currently has in their real hotbar
     * into the current virtual hotbar, and marks it dirty so it can be saved later.
     */
    public static void syncFromGame() {
        clampHotbarIndex();
        var mcPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (mcPlayer == null) return;

        Hotbar vb = getCurrentHotbar();
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            vb.setSlot(i, mcPlayer.getInventory().getItem(i).copy());
        }

        // Mark that we’ve modified virtual data so saveHotbars() will write it
        markDirty();
    }

    /**
     * Like syncFromGame(), but only updates (and marks dirty) if
     * the real hotbar actually differs from our virtual one.
     * @return true if we saw any differences and updated.
     */
    public static boolean syncFromGameIfChanged() {
        clampHotbarIndex();
        var mcPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (mcPlayer == null) return false;

        Hotbar vb = getCurrentHotbar();
        boolean changed = false;
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            ItemStack real = mcPlayer.getInventory().getItem(i);
            ItemStack virt = vb.getSlot(i);
            // Use ItemStack.isSameItem to allow stacked counts etc
            if (!ItemStack.matches(virt, real)) {
                vb.setSlot(i, real.copy());
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }

    // ================================================================
    // Persistence: saveHotbars + loadHotbars include pageNames
    // ================================================================

    /** Persist hotbars **only** if something has changed. */
    public static void saveHotbars() {
        // ▶️ Skip the write if nothing’s dirty
        if (!dirty) return;

        try {
            CompoundTag root = new CompoundTag();
            CompoundTag map  = new CompoundTag();

            // Save each hotbar slot
            for (int pi = 0; pi < pages.size(); pi++) {
                List<Hotbar> page = pages.get(pi);
                for (int hi = 0; hi < page.size(); hi++) {
                    int idx = pi * Config.getMaxHotbarsPerPage() + hi;
                    map.put(String.valueOf(idx), page.get(hi).serializeNBT());
                }
            }
            root.put("HotbarsMap", map);

            // Save pageNames
            CompoundTag namesTag = new CompoundTag();
            for (int i = 0; i < pageNames.size(); i++) {
                namesTag.putString(String.valueOf(i), pageNames.get(i));
            }
            root.put("PageNames", namesTag);

            NbtIo.write(root, SAVE_FILE);

            // Debug tracking
            lastSavedPage   = getPage();
            lastSavedHotbar = getHotbar();
            lastSavedSlot   = getSlot();
            HotbarState.saveState(getPage(), getHotbar(), getSlot());

            // ▶️ Clear the dirty flag now that we’ve successfully saved
            dirty = false;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadHotbars() {
        if (!SAVE_FILE.exists()) return;
        try {
            CompoundTag root = NbtIo.read(SAVE_FILE);
            if (root == null || !root.contains("HotbarsMap", Tag.TAG_COMPOUND)) return;
            CompoundTag map = root.getCompound("HotbarsMap");

            // Determine how many pages were saved
            int maxIdx    = map.getAllKeys().stream().mapToInt(Integer::parseInt).max().orElse(-1);
            int pageCount = (maxIdx / Config.getMaxHotbarsPerPage()) + 1;

            // Reinitialize pages & names
            pages.clear();
            pageNames.clear();
            for (int pi = 0; pi < pageCount; pi++) {
                pages.add(new ArrayList<>());
                pageNames.add("Page " + (pi + 1));
            }

            // Populate hotbars
            for (String key : map.getAllKeys()) {
                int idx = Integer.parseInt(key);
                int pi  = idx / Config.getMaxHotbarsPerPage();
                int hi  = idx % Config.getMaxHotbarsPerPage();
                List<Hotbar> page = pages.get(pi);
                while (page.size() <= hi) {
                    page.add(new Hotbar());
                }
                page.set(hi, Hotbar.deserializeNBT(map.getCompound(key)));
            }

            // Restore custom page names
            if (root.contains("PageNames", Tag.TAG_COMPOUND)) {
                CompoundTag namesTag = root.getCompound("PageNames");
                for (String k : namesTag.getAllKeys()) {
                    int pi = Integer.parseInt(k);
                    if (pi >= 0 && pi < pageNames.size()) {
                        pageNames.set(pi, namesTag.getString(k));
                    }
                }
            }

            // Restore last-known state
            HotbarState.loadState();
            var mcPlayer = net.minecraft.client.Minecraft.getInstance().player;
            if (mcPlayer != null) {
                mcPlayer.getInventory().selected = currentSlot;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void resetAllHotbars() {
        // Clear existing data
        pages.clear();
        pageNames.clear();
        currentPage   = 0;
        currentHotbar = 0;
        currentSlot   = 0;

        // Create one default page (uses addPageInternal, which also calls saveHotbars)
        addPageInternal();

        // Force a write of the new empty structure
        saveHotbars();

        // Push that empty hotbar to the player
        syncToGame();
    }

}
