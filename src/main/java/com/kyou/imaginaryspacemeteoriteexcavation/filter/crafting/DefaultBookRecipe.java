package com.kyou.imaginaryspacemeteoriteexcavation.filter.crafting;

import net.minecraft.init.Items;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.world.World;

/** The approved nine-cell pattern, with a targeted rejection of the unsafe MUI2 example wrapper. */
public final class DefaultBookRecipe extends ShapedRecipes {

    public DefaultBookRecipe(ItemStack filter, ItemStack dataOrb, ItemStack book) {
        super(
            3,
            3,
            new ItemStack[] { filter.copy(), dataOrb.copy(), filter.copy(), dataOrb.copy(), new ItemStack(Items.book),
                dataOrb.copy(), filter.copy(), dataOrb.copy(), filter.copy() },
            book.copy());
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return BookCraftingGrid.supported(grid.getClass()) && super.matches(grid, world);
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        return BookCraftingGrid.supported(grid.getClass()) ? super.getCraftingResult(grid) : null;
    }
}
