package org.MegaNoob.ultimatehotbars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.item.ItemStack;
import java.io.File;
import java.util.ArrayList;
import org.MegaNoob.ultimatehotbars.network.PacketHandler;
import org.MegaNoob.ultimatehotbars.network.SyncHotbarPacket;
import java.util.List;

public class HotbarManager {
    public static final int PAGES = 100;
    public static final int HOTBARS_PER_PAGE = 10;

    // Nested list: pages.get(pageIndex).get(hotbarIndex)
    private static final List<List<Hotbar>> pages = new ArrayList<>();
    private static int currentPage = 0;
    private static int currentHotbar = 0;
    private static int currentSlot = 0;

    // Debug tracking
    public static String lastHotbarSource = "";
    public static int lastHotbarSet = -1;
    public static int lastSavedHotbar = -1;
    public static int lastSavedPage = -1;
    public static int lastSavedSlot = -1;

    private static final File SAVE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars_hotbars.dat").toFile();

    static {
        // Initialize empty structure
        for (int i = 0; i < PAGES; i++) {
            List<Hotbar> page = new ArrayList<>(HOTBARS_PER_PAGE);
            for (int j = 0; j < HOTBARS_PER_PAGE; j++) {
                page.add(new Hotbar());
            }
            pages.add(page);
        }
    }

    /** Returns the Hotbar object for the currently-selected page & hotbar. */
    public static Hotbar getCurrentHotbar() {
        return pages.get(currentPage).get(currentHotbar);
    }

    /** Sets the current page (with wrap-around) and immediately applies it in-game. */
    public static void setPage(int page) {
        currentPage = ((page % PAGES) + PAGES) % PAGES;
        syncToGame();
    }

    /** Overload: Sets the current hotbar index with no source tag. */
    public static void setHotbar(int hb) {
        setHotbar(hb, "unknown");
    }

    /** Sets the current hotbar index with source tag tracking, and applies it. */
    public static void setHotbar(int hb, String sourceTag) {
        currentHotbar = ((hb % HOTBARS_PER_PAGE) + HOTBARS_PER_PAGE) % HOTBARS_PER_PAGE;
        lastHotbarSet = currentHotbar;
        lastHotbarSource = sourceTag;
        syncToGame();
    }

    /** Only tracks which slot (0–8) is selected in-game; does not auto‐apply. */
    public static void setSlot(int slot) {
        currentSlot = Math.max(0, Math.min(Hotbar.SLOT_COUNT - 1, slot));
    }

    public static int getPage()     { return currentPage; }
    public static int getHotbar()   { return currentHotbar; }
    public static int getSlot()     { return currentSlot; }

    // ------------------------------------------------------------------------
    // Persistence & Syncing
    // ------------------------------------------------------------------------

    /** Overwrites the player's in-game hotbar slots (0–8) from the virtual hotbar. */
    public static void syncToGame() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;

        Hotbar vb = getCurrentHotbar();
        // Clear
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            player.getInventory().setItem(i, ItemStack.EMPTY);
        }
        // Populate
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            ItemStack stack = vb.getSlot(i);
            if (stack != null && !stack.isEmpty()) {
                player.getInventory().setItem(i, stack.copy());
            }
        }

        // ── NEW: also sync to server so it places the correct blocks
        ItemStack[] stacks = new ItemStack[Hotbar.SLOT_COUNT];
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            stacks[i] = vb.getSlot(i).copy();
        }

        PacketHandler.CHANNEL.sendToServer(
                new SyncHotbarPacket(getPage(), getHotbar(), stacks)
        );
    }

    /** Reads from the player's in-game hotbar slots into the virtual hotbar and saves. */
    public static void syncFromGame() {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return;
        Hotbar vb = getCurrentHotbar();
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            vb.setSlot(i, player.getInventory().getItem(i).copy());
        }
        saveHotbars();
    }

    /** Loads all pages & hotbars from disk (if present). */
    public static void loadHotbars() {
        if (!SAVE_FILE.exists()) return;
        try {
            CompoundTag root = net.minecraft.nbt.NbtIo.read(SAVE_FILE);
            if (root == null || !root.contains("HotbarsMap", Tag.TAG_COMPOUND)) return;
            CompoundTag map = root.getCompound("HotbarsMap");
            for (int idx = 0; idx < PAGES * HOTBARS_PER_PAGE; idx++) {
                String key = String.valueOf(idx);
                if (map.contains(key, Tag.TAG_COMPOUND)) {
                    CompoundTag htag = map.getCompound(key);
                    int page   = idx / HOTBARS_PER_PAGE;
                    int hbNum  = idx % HOTBARS_PER_PAGE;
                    pages.get(page).set(hbNum, Hotbar.deserializeNBT(htag));
                }
            }

            // 🔽 Load last known page/hotbar/slot AFTER restoring hotbars
            HotbarState.loadState();
            net.minecraft.client.Minecraft.getInstance().player.getInventory().selected = currentSlot;

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Saves every page & hotbar to disk in a single .dat file. */
    public static void saveHotbars() {
        try {
            CompoundTag root = new CompoundTag();
            CompoundTag map  = new CompoundTag();
            for (int idx = 0; idx < PAGES * HOTBARS_PER_PAGE; idx++) {
                int page  = idx / HOTBARS_PER_PAGE;
                int hbNum = idx % HOTBARS_PER_PAGE;
                map.put(String.valueOf(idx),
                        pages.get(page).get(hbNum).serializeNBT());
            }
            root.put("HotbarsMap", map);
            net.minecraft.nbt.NbtIo.write(root, SAVE_FILE);

            // 🔽 Save current page/hotbar/slot after hotbars are saved
            lastSavedPage = getPage();
            lastSavedHotbar = getHotbar();
            lastSavedSlot = getSlot();

            HotbarState.saveState(getPage(), getHotbar(), getSlot());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ------------------------------------------------------------------------
    // New Accessors for GUI rendering
    // ------------------------------------------------------------------------

    /**
     * @return a flat List of length PAGES * HOTBARS_PER_PAGE,
     *         where index = page * HOTBARS_PER_PAGE + hotbar.
     */
    public static List<Hotbar> getHotbars() {
        List<Hotbar> flat = new ArrayList<>(PAGES * HOTBARS_PER_PAGE);
        for (List<Hotbar> page : pages) {
            flat.addAll(page);
        }
        return flat;
    }

    /**
     * @return exactly the HOTBARS_PER_PAGE hotbars on the currently-selected page.
     */
    public static List<Hotbar> getCurrentPageHotbars() {
        return new ArrayList<>(pages.get(currentPage));
    }
}
