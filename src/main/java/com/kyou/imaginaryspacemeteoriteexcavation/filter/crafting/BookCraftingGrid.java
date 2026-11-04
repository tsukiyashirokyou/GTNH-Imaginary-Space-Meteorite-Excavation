package com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting;

import net.minecraft.inventory.InventoryCrafting;

import com.cleanroommc.modularui.widgets.slot.InventoryCraftingWrapper;

/** The target MUI2 test crafting wrapper includes its output cell and has unsafe result callbacks. */
public final class BookCraftingGrid {

    private BookCraftingGrid() {}

    public static boolean supported(Class<? extends InventoryCrafting> type) {
        return !InventoryCraftingWrapper.class.isAssignableFrom(type);
    }
}
