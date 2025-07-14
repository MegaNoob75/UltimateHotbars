package org.MegaNoob.ultimatehotbars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.File;

public class HotbarState {
    private static final File STATE_FILE =
            FMLPaths.CONFIGDIR.get().resolve("ultimatehotbars_state.dat").toFile();

    public static void saveState(int page, int hotbar, int slot) {
        try {
            System.out.println("[HotbarState] Saving state: page=" + page + ", hotbar=" + hotbar + ", slot=" + slot);
            CompoundTag tag = new CompoundTag();
            tag.putInt("page", page);
            tag.putInt("hotbar", hotbar);
            tag.putInt("slot", slot);
            NbtIo.write(tag, STATE_FILE);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void loadState() {
        if (!STATE_FILE.exists()) return;
        try {
            CompoundTag tag = NbtIo.read(STATE_FILE);
            if (tag != null) {
                if (tag.contains("page")) HotbarManager.setPage(tag.getInt("page"));
                HotbarManager.setHotbar(tag.getInt("hotbar"), "HotbarState.loadState");
                if (tag.contains("slot")) HotbarManager.setSlot(tag.getInt("slot"));

                // 🔽 Force reapply hotbar visually after all values are set
                HotbarManager.syncToGame();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
