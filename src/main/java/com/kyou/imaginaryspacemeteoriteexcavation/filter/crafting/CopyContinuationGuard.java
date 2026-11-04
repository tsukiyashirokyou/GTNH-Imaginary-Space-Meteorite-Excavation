package com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;

/** Prevents vanilla's same-Item shift retry from turning a completed copy into an unintended clear. No items stored. */
public final class CopyContinuationGuard {

    private static final Map<InventoryCrafting, Snapshot> COPIES = new WeakHashMap<>();

    private CopyContinuationGuard() {}

    public static void remember(InventoryCrafting grid) {
        COPIES.put(grid, new Snapshot(grid));
    }

    public static boolean suppressesClear(InventoryCrafting grid) {
        Snapshot snapshot = COPIES.get(grid);
        return snapshot != null && snapshot.matchesAfterConsumption(grid);
    }

    private static final class Snapshot {

        private final ItemStack[] references;
        private final ItemStack[] expected;

        private Snapshot(InventoryCrafting grid) {
            references = new ItemStack[grid.getSizeInventory()];
            expected = new ItemStack[references.length];
            for (int slot = 0; slot < references.length; slot++) {
                ItemStack stack = grid.getStackInSlot(slot);
                references[slot] = stack;
                if (stack != null && stack.stackSize > 1) {
                    expected[slot] = stack.copy();
                    expected[slot].stackSize--;
                }
            }
        }

        private boolean matchesAfterConsumption(InventoryCrafting grid) {
            if (grid.getSizeInventory() != expected.length) return false;
            for (int slot = 0; slot < expected.length; slot++) {
                ItemStack actual = grid.getStackInSlot(slot);
                if (expected[slot] == null) {
                    if (actual != null) return false;
                } else if (actual != references[slot] || !ItemStack.areItemStacksEqual(actual, expected[slot]))
                    return false;
            }
            return true;
        }
    }
}
