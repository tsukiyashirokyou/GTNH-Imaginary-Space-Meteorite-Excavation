package com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.oredict.OreDictionary;

import com.gtnewhorizon.gtnhlib.util.data.ImmutableBlockMeta;

import gregtech.common.blocks.GTBlockOre;
import gregtech.common.ores.IOreAdapter;
import gregtech.common.ores.OreInfo;
import gregtech.common.ores.OreManager;

/** Direct block items only. No placement, World, drop queries, fortune, crushing or random sampling. */
public final class BlockItemResolver {

    private BlockItemResolver() {}

    public static ItemIdentity resolve(ItemStack source) {
        if (source == null || !(source.getItem() instanceof ItemBlock)) {
            throw new IllegalArgumentException("Component is not an ItemBlock");
        }
        if (source.getItemDamage() < 0 || source.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
            throw new IllegalArgumentException("Component has no concrete metadata");
        }
        Block block = ((ItemBlock) source.getItem()).field_150939_a;
        if (block == null || block == Blocks.air)
            throw new IllegalArgumentException("Component has no non-air block identity");
        ItemStack output = source.copy();
        output.stackSize = 1;
        try (OreInfo<?> info = OreManager.getOreInfo(source)) {
            if (info != null) {
                info.isNatural = false;
                IOreAdapter<?> adapter = OreManager.getAdapter(info);
                ImmutableBlockMeta mapped = adapter == null ? null : adapter.getBlock(info);
                if (mapped == null || mapped.getBlock() == Blocks.air) {
                    throw new IllegalArgumentException("Ore has no direct block-item mapping");
                }
                // No getStack fallback to another stone type and no damageDropped/getDrops calls.
                output = new ItemStack(mapped.getBlock(), 1, mapped.getBlockMeta());
                if (source.hasTagCompound()) output.setTagCompound(
                    (NBTTagCompound) source.getTagCompound()
                        .copy());
            } else if (block instanceof GTBlockOre) {
                throw new IllegalArgumentException("Invalid GT ore metadata");
            }
        }
        if (!(output.getItem() instanceof ItemBlock)) throw new IllegalArgumentException("Missing block item");
        return new ItemIdentity(output);
    }
}
