// src/main/java/com/yourname/ultimatehotbars/Hotbar.java
package org.MegaNoob.ultimatehotbars;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

public class Hotbar {
    public static final int SLOT_COUNT = 9;
    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private boolean isEmpty = true;

    public Hotbar() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    public ItemStack getSlot(int index) {
        return slots[index];
    }

    public void setSlot(int index, ItemStack stack) {
        slots[index] = stack;
        isEmpty = checkEmpty();
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    public void clear() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        isEmpty = true;
    }

    private boolean checkEmpty() {
        for (ItemStack st : slots)
            if (!st.isEmpty()) return false;
        return true;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        if (isEmpty) {
            tag.putBoolean("Empty", true);
        } else {
            ListTag list = new ListTag();
            for (int i = 0; i < SLOT_COUNT; i++) {
                if (!slots[i].isEmpty()) {
                    CompoundTag stTag = new CompoundTag();
                    stTag.putByte("Slot", (byte)i);
                    stTag.put("Item", slots[i].save(new CompoundTag()));
                    list.add(stTag);
                }
            }
            tag.put("Items", list);
        }
        return tag;
    }

    public static Hotbar deserializeNBT(CompoundTag tag) {
        Hotbar hb = new Hotbar();
        if (tag.contains("Empty")) {
            return hb; // all slots already empty
        }
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (var element : list) {
            CompoundTag stTag = (CompoundTag)element;
            int slot = stTag.getByte("Slot");
            hb.slots[slot] = ItemStack.of(stTag.getCompound("Item"));
        }
        hb.isEmpty = hb.checkEmpty();
        return hb;
    }
}
