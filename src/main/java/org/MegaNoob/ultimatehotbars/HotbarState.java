package org.MegaNoob.ultimatehotbars;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Persists the last-open page/hotbar/slot selection per world or server.
 */
public class HotbarState {
    /**
     * Determines the folder to use:
     * 1) Single-player: the actual world save folder (.minecraft/saves/<world>/)
     * 2) Multiplayer:    a subfolder under config/ultimatehotbars/ named by host_port
     * 3) Fallback:       config/ultimatehotbars/
     */
    private static Path getWorldConfigDir() {
        Minecraft mc = Minecraft.getInstance();

        // 1) Single-player → use the real save folder
        MinecraftServer integrated = mc.getSingleplayerServer();
        if (integrated != null) {
            Path worldDir = integrated.getWorldPath(LevelResource.ROOT);
            try {
                Files.createDirectories(worldDir);
            } catch (IOException e) {
                e.printStackTrace();
                return FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars");
            }
            return worldDir;
        }

        // 2) Multiplayer → use server address for uniqueness
        ClientPacketListener handler = mc.getConnection();
        if (handler != null) {
            Connection conn = handler.getConnection();
            SocketAddress addr = conn.channel().remoteAddress();
            // sanitize "/1.2.3.4:25565" → "1.2.3.4_25565"
            String folderName = addr.toString().replaceAll("[\\\\/:*?\"<>|]", "_");
            Path cfg = FMLPaths.CONFIGDIR.get()
                    .resolve("ultimatehotbars")
                    .resolve(folderName);
            try {
                Files.createDirectories(cfg);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return cfg;
        }

        // 3) Fallback → generic config folder
        Path cfg = FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars");
        try {
            Files.createDirectories(cfg);
        } catch (IOException ignored) {}
        return cfg;
    }

    /** Path to ultimatehotbars_state.dat in the chosen folder. */
    private static Path stateFile() {
        return getWorldConfigDir().resolve("ultimatehotbars_state.dat");
    }

    /**
     * Save the current page/hotbar/slot to the per-world or per-server file.
     */
    public static void saveState(int page, int hotbar, int slot) {
        try {
            CompoundTag tag = new CompoundTag();
            tag.putInt("page",   page);
            tag.putInt("hotbar", hotbar);
            tag.putInt("slot",   slot);

            Path file = stateFile();
            Files.createDirectories(file.getParent());
            NbtIo.write(tag, file.toFile());
        } catch (Exception e) {
            System.err.println("[HotbarState] Error saving state:");
            e.printStackTrace();
        }
    }

    /**
     * Load and apply page/hotbar/slot from the per-world or per-server file, if present.
     */
    public static void loadState() {
        Path file = stateFile();
        if (!Files.exists(file)) return;

        try {
            CompoundTag tag = NbtIo.read(file.toFile());
            if (tag == null) return;

            if (tag.contains("page") && tag.contains("hotbar")) {
                int savedPage   = tag.getInt("page");
                int savedHotbar = tag.getInt("hotbar");
                HotbarManager.setPage(savedPage, savedHotbar);
            }
            if (tag.contains("slot")) {
                HotbarManager.setSlot(tag.getInt("slot"));
            }
            HotbarManager.syncToGame();
        } catch (Exception e) {
            System.err.println("[HotbarState] Error loading state:");
            e.printStackTrace();
        }
    }
}
