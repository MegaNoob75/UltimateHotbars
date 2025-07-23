package org.MegaNoob.ultimatehotbars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtIo;
import org.MegaNoob.ultimatehotbars.network.PacketHandler;
import org.MegaNoob.ultimatehotbars.network.SyncHotbarPacket;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class HotbarManager {
    public static final int HOTBARS_PER_PAGE = 10;

    // Holds the virtual hotbars: pages.get(pageIndex).get(hotbarIndex)
    private static final List<List<Hotbar>> pages = new ArrayList<>();
    // Holds the display names for each page
    private static final List<String> pageNames = new ArrayList<>();

    private static int currentPage   = 0;
    private static int currentHotbar = 0;
    private static int currentSlot   = 0;

    private static final File SAVE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars_hotbars.dat").toFile();

    // Debug tracking (unchanged) ...
    public static String lastHotbarSource = "";
    public static int lastHotbarSet = -1;
    public static int lastSavedHotbar = -1;
    public static int lastSavedPage = -1;
    public static int lastSavedSlot = -1;

    static {
        // Initialize with a single default page
        addPageInternal();
        // You can call loadHotbars() explicitly on login to overwrite these defaults
    }

    // ================================================================
    // Page & Name Management
    // ================================================================

    /** Returns how many pages are currently available. */
    public static int getPageCount() {
        return pages.size();
    }

    /** Returns an immutable copy of all page names. */
    public static List<String> getPageNames() {
        return new ArrayList<>(pageNames);
    }

    /** Adds a new empty page at the end, default named "Page N". */
    public static void addPage() {
        addPageInternal();
    }

    /** Removes the page at index (except index=0), then rewraps if needed. */
    public static void removePage(int index) {
        if (index <= 0 || index >= pages.size()) return;
        pages.remove(index);
        pageNames.remove(index);
        if (currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }
    }

    /** Renames the page at idx to the given newName. */
    public static void renamePage(int idx, String newName) {
        if (idx >= 0 && idx < pageNames.size()) {
            pageNames.set(idx, newName);
        }
    }

    /** Internal helper: create one new empty page + default name. */
    private static void addPageInternal() {
        List<Hotbar> page = new ArrayList<>(HOTBARS_PER_PAGE);
        for (int i = 0; i < HOTBARS_PER_PAGE; i++) {
            page.add(new Hotbar());
        }
        pages.add(page);
        pageNames.add("Page " + pages.size());
    }

    // ================================================================
    // Accessors for current selection
    // ================================================================

    public static Hotbar getCurrentHotbar() {
        return pages.get(currentPage).get(currentHotbar);
    }

    public static List<Hotbar> getCurrentPageHotbars() {
        return new ArrayList<>(pages.get(currentPage));
    }

    public static int getPage()   { return currentPage; }
    public static int getHotbar() { return currentHotbar; }
    public static int getSlot()   { return currentSlot; }

    public static void setPage(int page) {
        currentPage = Math.max(0, Math.min(page, pages.size() - 1));
        syncToGame();
    }

    public static void setHotbar(int hb, String sourceTag) {
        currentHotbar = ((hb % HOTBARS_PER_PAGE) + HOTBARS_PER_PAGE) % HOTBARS_PER_PAGE;
        lastHotbarSet = currentHotbar;
        lastHotbarSource = sourceTag;
        syncToGame();
    }

    public static void setSlot(int slot) {
        currentSlot = Math.max(0, Math.min(slot, Hotbar.SLOT_COUNT - 1));
    }

    // ================================================================
    // Syncing to Game & Networking (unchanged)
    // ================================================================

    /**
     * Overwrites the player's in-game hotbar slots (0–8) from the virtual hotbar
     * and sends them to the server.
     */
    public static void syncToGame() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;

        // 1) Clear the real hotbar
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            mc.player.getInventory().setItem(i, ItemStack.EMPTY);
        }

        // 2) Populate it from our virtual current hotbar
        Hotbar vb = getCurrentHotbar();
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            ItemStack s = vb.getSlot(i);
            if (s != null && !s.isEmpty()) {
                mc.player.getInventory().setItem(i, s.copy());
            }
        }

        // 3) Send to server so it can update the player's server-side inventory
        ItemStack[] stacks = new ItemStack[Hotbar.SLOT_COUNT];
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            stacks[i] = vb.getSlot(i).copy();
        }
        PacketHandler.CHANNEL.sendToServer(
                new SyncHotbarPacket(
                        getPage(),
                        getHotbar(),
                        stacks            // <-- only three arguments
                )
        );
    }


    public static void syncFromGame() {
        var mcPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (mcPlayer == null) return;
        Hotbar vb = getCurrentHotbar();
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            vb.setSlot(i, mcPlayer.getInventory().getItem(i).copy());
        }
        saveHotbars();
    }

    // ================================================================
    // Persistence: saveHotbars + loadHotbars now include pageNames
    // ================================================================

    public static void saveHotbars() {
        try {
            CompoundTag root = new CompoundTag();
            CompoundTag map  = new CompoundTag();
            // Save each slot under a flat index
            for (int pi = 0; pi < pages.size(); pi++) {
                for (int hi = 0; hi < HOTBARS_PER_PAGE; hi++) {
                    int idx = pi * HOTBARS_PER_PAGE + hi;
                    map.put(String.valueOf(idx),
                            pages.get(pi).get(hi).serializeNBT());
                }
            }
            root.put("HotbarsMap", map);

            // Save the page names
            CompoundTag namesTag = new CompoundTag();
            for (int i = 0; i < pageNames.size(); i++) {
                namesTag.putString(String.valueOf(i), pageNames.get(i));
            }
            root.put("PageNames", namesTag);

            // Write to disk
            NbtIo.write(root, SAVE_FILE);

            // Debug tracking
            lastSavedPage   = getPage();
            lastSavedHotbar = getHotbar();
            lastSavedSlot   = getSlot();
            HotbarState.saveState(getPage(), getHotbar(), getSlot());

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
            int maxIdx = map.getAllKeys().stream()
                    .mapToInt(Integer::parseInt)
                    .max().orElse(-1);
            int pageCount = (maxIdx / HOTBARS_PER_PAGE) + 1;

            // Reinitialize pages & default names
            pages.clear();
            pageNames.clear();
            for (int pi = 0; pi < pageCount; pi++) {
                addPageInternal();
            }

            // Deserialize hotbars
            for (String key : map.getAllKeys()) {
                int idx = Integer.parseInt(key);
                int pi  = idx / HOTBARS_PER_PAGE;
                int hi  = idx % HOTBARS_PER_PAGE;
                CompoundTag htag = map.getCompound(key);
                pages.get(pi).set(hi, Hotbar.deserializeNBT(htag));
            }

            // Load saved page names (if any)
            if (root.contains("PageNames", Tag.TAG_COMPOUND)) {
                CompoundTag namesTag = root.getCompound("PageNames");
                for (String k : namesTag.getAllKeys()) {
                    int pi = Integer.parseInt(k);
                    if (pi >= 0 && pi < pageNames.size()) {
                        pageNames.set(pi, namesTag.getString(k));
                    }
                }
            }

            // Restore last‐known page/hotbar/slot
            HotbarState.loadState();
            var mcPlayer = net.minecraft.client.Minecraft.getInstance().player;
            if (mcPlayer != null) {
                mcPlayer.getInventory().selected = currentSlot;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // … plus any other getters/setters or utility methods you had …
}
