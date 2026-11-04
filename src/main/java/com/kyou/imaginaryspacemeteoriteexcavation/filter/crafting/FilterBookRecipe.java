package com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

import com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime.FilterBookStore;

/** Exactly two input cells for copy; exactly one for clear. Both paths consume normally, without container items. */
public final class FilterBookRecipe implements IRecipe {

    private final FilterBookStore books;
    private final boolean copy;

    public FilterBookRecipe(FilterBookStore books, boolean copy) {
        this.books = books;
        this.copy = copy;
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return getCraftingResult(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        if (!BookCraftingGrid.supported(grid.getClass())) return null;
        ItemStack source = null;
        int configured = 0;
        int defaults = 0;
        int count = 0;
        for (int slot = 0; slot < grid.getSizeInventory(); slot++) {
            ItemStack stack = grid.getStackInSlot(slot);
            if (stack == null) continue;
            if (!books.isBook(stack)) return null;
            count++;
            if (!copy) continue;
            try {
                if (books.read(stack)
                    .configured()) {
                    source = stack;
                    configured++;
                } else defaults++;
            } catch (IllegalArgumentException invalid) {
                return null;
            }
        }
        if (!copy) return count == 1 && !CopyContinuationGuard.suppressesClear(grid) ? books.defaults(1) : null;
        if (count != 2 || configured != 1 || defaults != 1) return null;
        ItemStack output = source.copy();
        output.stackSize = 2;
        return output;
    }

    @Override
    public int getRecipeSize() {
        return copy ? 2 : 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return books.defaults(copy ? 2 : 1);
    }
}
