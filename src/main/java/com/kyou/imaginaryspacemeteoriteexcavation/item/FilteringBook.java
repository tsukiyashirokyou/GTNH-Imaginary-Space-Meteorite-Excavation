package com.kyou.imaginaryspacemeteoriteexcavation.item;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

import com.kyou.imaginaryspacemeteoriteexcavation.ImaginarySpaceMeteoriteExcavation;

/** Base item only. Configuration pages and crafting belong to stage 5. */
public final class FilteringBook extends Item {

    public FilteringBook() {
        setUnlocalizedName(ImaginarySpaceMeteoriteExcavation.MODID + ".filtering_book");
        setTextureName("minecraft:book_normal");
        setCreativeTab(CreativeTabs.tabTools);
        setMaxStackSize(64);
    }
}
