package org.MegaNoob.ultimatehotbars.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.MegaNoob.ultimatehotbars.Config;
import org.MegaNoob.ultimatehotbars.Hotbar;
import org.MegaNoob.ultimatehotbars.HotbarManager;
import org.MegaNoob.ultimatehotbars.ultimatehotbars;
import net.minecraft.world.item.ItemStack;

/**
 * Queues wheel-based hotbar switches and commits them on END client tick.
 * Uses a short guard to suppress background "pull-from-game" during the switch,
 * preventing neighbor-row overwrites on rapid oscillation.
 */
@Mod.EventBusSubscriber(modid = ultimatehotbars.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WheelSwitchCoordinator {
    private WheelSwitchCoordinator() {}

    // Latest requested target (coalesces rapid oscillation). -1 = none
    private static volatile int pendingHotbar = -1;

    // Throttle anchor for last committed switch
    private static long lastCommitMs = 0L;

    // Simple reentrancy/busy flag
    private static volatile boolean committing = false;

    /** Queue a target hotbar; commit happens on a safe tick. */
    public static void request(int targetIndex) {
        pendingHotbar = targetIndex;                  // always keep latest intent
        HotbarManager.setWheelSwitchPending(true);    // suppress background pulls until commit
    }

    /** For UI: show pending selection immediately. -1 means none. */
    public static int getPendingTarget() {
        return pendingHotbar;
    }

    /** Optional: other systems can skip extra work if we’re busy. */
    public static boolean isBusy() {
        return committing || pendingHotbar >= 0;
    }

    @SubscribeEvent
    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (pendingHotbar < 0) return;

        long now = System.currentTimeMillis();
        if (now - lastCommitMs < Config.getScrollThrottleMs()) return;

        committing = true;
        try {
            // 1) Snapshot CURRENT row from the real hotbar into its virtual row.
            var mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                var vb = HotbarManager.getCurrentHotbar();
                boolean changed = false;
                for (int i = 0; i < Hotbar.SLOT_COUNT; i++) {
                    ItemStack real = mc.player.getInventory().getItem(i);
                    ItemStack virt = vb.getSlot(i);
                    if (!ItemStack.matches(virt, real)) {
                        vb.setSlot(i, real.copy());
                        changed = true;
                    }
                }
                if (changed) {
                    HotbarManager.markDirty();
                    HotbarManager.saveHotbars();
                }
            }

            // 2) Resolve target with wrap (in case count changed since queue)
            int totalBars = HotbarManager.getCurrentPageHotbars().size();
            if (totalBars <= 0) {
                pendingHotbar = -1;
                return;
            }
            int target = Math.floorMod(pendingHotbar, totalBars);

            // 3) Switch to target (syncs to game/server inside setHotbar)
            HotbarManager.setHotbar(target, "wheel-queued");

        } finally {
            lastCommitMs = System.currentTimeMillis();
            pendingHotbar = -1;
            committing = false;
            HotbarManager.setWheelSwitchPending(false);
        }
    }

}
