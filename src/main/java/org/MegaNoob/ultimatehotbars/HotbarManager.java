package org.MegaNoob.ultimatehotbars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraft.world.item.ItemStack;
import net.minecraft.nbt.NbtIo;
import org.MegaNoob.ultimatehotbars.network.PacketHandler;
import org.MegaNoob.ultimatehotbars.network.SyncHotbarPacket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import java.io.IOException;
import net.minecraft.world.level.storage.LevelResource;
import java.net.SocketAddress;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;


public class HotbarManager {

    // Suppress background pulls while a wheel switch is pending/committing.
    private static volatile boolean wheelSwitchPending = false;
    public static void setWheelSwitchPending(boolean v) { wheelSwitchPending = v; }
    public static boolean isWheelSwitchPending() { return wheelSwitchPending; }


    // Holds the virtual hotbars: pages.get(pageIndex).get(hotbarIndex)
    private static final List<List<Hotbar>> pages     = new ArrayList<>();
    // Holds the display names for each page
    private static final List<String>       pageNames = new ArrayList<>();
    private static boolean dirty = false;
    private static int currentPage   = 0;
    private static int currentHotbar = 0;
    private static int currentSlot   = 0;

    // Debug tracking
    public static String lastHotbarSource = "";
    public static int    lastHotbarSet    = -1;
    public static int    lastSavedHotbar  = -1;
    public static int    lastSavedPage    = -1;
    public static int    lastSavedSlot    = -1;

    static {
        // on class load, either load existing pages from the per-world file, or create a default page
        Path file = hotbarsFile();
        try {
            if (Files.exists(file)) {
                loadHotbars();
            } else {
                addPageInternal();
            }
        } catch (Exception e) {
            // in case of any IO errors, fall back to a default page
            e.printStackTrace();
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
        // ── SANITY: ensure there's always at least one page ──
        if (pages.isEmpty()) {
            addPageInternal();
        }

        // Clamp requested page to [0, pages.size()-1]
        currentPage = Mth.clamp(page, 0, pages.size() - 1);

        // Grab its hotbar list, and ensure it's never empty
        List<Hotbar> hotbars = pages.get(currentPage);
        if (hotbars.isEmpty()) {
            hotbars.add(new Hotbar());
        }

        // Clamp the resetHotbar into [0, hotbars.size()-1]
        currentHotbar = Mth.clamp(resetHotbar, 0, hotbars.size() - 1);

        lastHotbarSet    = currentHotbar;
        lastHotbarSource = "page switch";

        clampHotbarIndex();
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

    // Keep this 1-arg method for updating the *selected slot index* only.
    public static void setSlot(int slot) {
        currentSlot = Math.max(0, Math.min(slot, Hotbar.SLOT_COUNT - 1));
    }

    // Optional overload: set the ItemStack in the *current virtual hotbar*.
// This avoids breaking any spots where you might have started using 2-args.
    public static void setSlot(int index, net.minecraft.world.item.ItemStack stack) {
        clampHotbarIndex();
        if (index < 0 || index >= Hotbar.SLOT_COUNT) return;
        Hotbar hb = getCurrentHotbar();
        // Hotbar.setSlot will copy, but copying here is also fine/safe.
        hb.setSlot(index, (stack == null || stack.isEmpty()) ? net.minecraft.world.item.ItemStack.EMPTY : stack.copy());
        markDirty();
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
    // HotbarManager.java
    public static boolean syncFromGameIfChanged() {
        clampHotbarIndex();

        // ⛔ Do not pull while a scroll-wheel switch is pending/committing
        if (isWheelSwitchPending()) {
            return false;
        }

        var mcPlayer = net.minecraft.client.Minecraft.getInstance().player;
        if (mcPlayer == null) return false;

        Hotbar vb = getCurrentHotbar();
        boolean changed = false;
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            ItemStack real = mcPlayer.getInventory().getItem(i);
            ItemStack virt = vb.getSlot(i);
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



    /**
     * Returns the world-specific config folder, e.g. ".minecraft/saves/<worldName>"
     * or falls back to config/ultimatehotbars if no world is loaded.
     */
    private static Path getWorldConfigDir() {
        Minecraft mc = Minecraft.getInstance();

        // singleplayer (integrated server) branch unchanged…
        MinecraftServer integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            Path worldDir = integrated.getWorldPath(LevelResource.ROOT);
            try { Files.createDirectories(worldDir); }
            catch (IOException e) { e.printStackTrace(); return FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars"); }
            return worldDir;
        }

        // multiplayer branch: use net.minecraft.network.Connection
        ClientPacketListener handler = mc.getConnection();
        if (handler != null) {
            Connection conn = handler.getConnection();          // ← now uses Connection
            SocketAddress addr = conn.channel().remoteAddress();
            String folder = addr.toString().replaceAll("[\\\\/:*?\"<>|]", "_");
            Path cfg = FMLPaths.CONFIGDIR.get()
                    .resolve("ultimatehotbars")
                    .resolve(folder);
            try { Files.createDirectories(cfg); } catch (IOException e) { e.printStackTrace(); }
            return cfg;
        }

        // fallback
        Path cfg = FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars");
        try { Files.createDirectories(cfg); } catch (IOException ignored) {}
        return cfg;
    }



    /** Where we persist hotbar pages. */
    private static Path hotbarsFile() {
        return getWorldConfigDir().resolve("ultimatehotbars_hotbars.dat");
    }

    /** Where we persist UI state (last page, slot, etc). */
    private static Path stateFile() {
        return getWorldConfigDir().resolve("ultimatehotbars_state.dat");
    }


    /** Persist hotbars **only** if something has changed, via an atomic write. */
    // HotbarManager.java
    public static void saveHotbars() {
        if (!dirty) return;  // nothing to do

        try {
            // 1) Build NBT (new hierarchical format)
            CompoundTag root = new CompoundTag();
            ListTag pagesTag = new ListTag();

            for (int pi = 0; pi < pages.size(); pi++) {
                CompoundTag pageTag = new CompoundTag();
                pageTag.putString("Name", pi < pageNames.size() ? pageNames.get(pi) : ("Page " + (pi + 1)));

                List<Hotbar> page = pages.get(pi);
                ListTag hotbarsTag = new ListTag();
                for (int hi = 0; hi < page.size(); hi++) {
                    CompoundTag hbTag = page.get(hi).serializeNBT();
                    hbTag.putInt("Index", hi); // optional, informative
                    hotbarsTag.add(hbTag);
                }
                pageTag.put("Hotbars", hotbarsTag);
                pagesTag.add(pageTag);
            }
            root.put("Pages", pagesTag);

            // 2) Also include legacy PageNames for older readers (harmless)
            CompoundTag namesTag = new CompoundTag();
            for (int i = 0; i < pageNames.size(); i++) {
                namesTag.putString(String.valueOf(i), pageNames.get(i));
            }
            root.put("PageNames", namesTag);

            // 3) Ensure parent dir
            Path savePath = hotbarsFile();
            Files.createDirectories(savePath.getParent());

            // 4) Atomic write
            Path tmp = savePath.resolveSibling(savePath.getFileName() + ".tmp");
            NbtIo.write(root, tmp.toFile());
            Files.move(tmp, savePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            // 5) Track & clear dirty
            lastSavedPage   = getPage();
            lastSavedHotbar = getHotbar();
            lastSavedSlot   = getSlot();
            HotbarState.saveState(getPage(), getHotbar(), getSlot());
            dirty = false;

        } catch (Exception e) {
            System.err.println("[UltimateHotbars] Error saving hotbars:");
            e.printStackTrace();
        }
    }



    // HotbarManager.java
    public static void loadHotbars() {
        Path file = hotbarsFile();
        if (!Files.exists(file)) {
            // default: one empty page
            pages.clear();
            pageNames.clear();
            addPageInternal();
            return;
        }
        try {
            CompoundTag root = NbtIo.read(file.toFile());
            pages.clear();
            pageNames.clear();

            // NEW format first
            if (root != null && root.contains("Pages", Tag.TAG_LIST)) {
                ListTag pagesTag = root.getList("Pages", Tag.TAG_COMPOUND);
                for (int pi = 0; pi < pagesTag.size(); pi++) {
                    CompoundTag pageTag = pagesTag.getCompound(pi);
                    String name = pageTag.contains("Name", Tag.TAG_STRING)
                            ? pageTag.getString("Name")
                            : ("Page " + (pi + 1));
                    pageNames.add(name);

                    List<Hotbar> page = new ArrayList<>();
                    if (pageTag.contains("Hotbars", Tag.TAG_LIST)) {
                        ListTag hotbarsTag = pageTag.getList("Hotbars", Tag.TAG_COMPOUND);
                        for (int hi = 0; hi < hotbarsTag.size(); hi++) {
                            CompoundTag hbTag = hotbarsTag.getCompound(hi);
                            page.add(Hotbar.deserializeNBT(hbTag));
                        }
                    }
                    if (page.isEmpty()) page.add(new Hotbar());
                    pages.add(page);
                }

            } else if (root != null && root.contains("HotbarsMap", Tag.TAG_COMPOUND)) {
                // Legacy fallback (your existing logic)
                CompoundTag map = root.getCompound("HotbarsMap");
                int maxIdx = map.getAllKeys().stream()
                        .mapToInt(Integer::parseInt)
                        .max()
                        .orElse(-1);
                int pageCount = (maxIdx / Config.getMaxHotbarsPerPage()) + 1;

                for (int pi = 0; pi < pageCount; pi++) {
                    pages.add(new ArrayList<>());
                    pageNames.add("Page " + (pi + 1));
                }

                for (String key : map.getAllKeys()) {
                    int idx = Integer.parseInt(key);
                    int pi  = idx / Config.getMaxHotbarsPerPage();
                    int hi  = idx % Config.getMaxHotbarsPerPage();
                    List<Hotbar> page = pages.get(pi);
                    while (page.size() <= hi) page.add(new Hotbar());
                    page.set(hi, Hotbar.deserializeNBT(map.getCompound(key)));
                }
                for (List<Hotbar> pg : pages) if (pg.isEmpty()) pg.add(new Hotbar());
            } else {
                // malformed → reset
                addPageInternal();
                return;
            }

            // Restore saved UI state
            HotbarState.loadState();
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null) player.getInventory().selected = currentSlot;

        } catch (Exception e) {
            System.err.println("[UltimateHotbars] Error loading hotbars:");
            e.printStackTrace();
            pages.clear();
            pageNames.clear();
            addPageInternal();
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

    /**
     * Like syncFromGameIfChanged(), but reads straight from the supplied player
     * (useful during clone events where the “current” Minecraft.player is already wiped).
     *
     * @param player  the source player whose inventory we should snapshot
     * @return        true if we detected any differences and updated virtual hotbar
     */
    public static boolean syncFromPlayerInventory(net.minecraft.world.entity.player.Player player) {
        clampHotbarIndex();

        // Prevent snapshotting during a pending/committing wheel switch
        if (isWheelSwitchPending()) {
            return false;
        }

        if (player == null) return false;

        Hotbar vb = getCurrentHotbar();
        boolean changed = false;
        for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
            var real = player.getInventory().getItem(i);
            var virt = vb.getSlot(i);
            if (!net.minecraft.world.item.ItemStack.matches(virt, real)) {
                vb.setSlot(i, real.copy());
                changed = true;
            }
        }
        if (changed) {
            markDirty();
        }
        return changed;
    }



}
