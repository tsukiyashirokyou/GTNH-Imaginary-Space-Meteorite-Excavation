package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.LocalStartResources;

/** Actual development ItemStack/FluidStack cells; no hatch tile, World or game instance is created. */
public final class LocalResourceTest {

    private static Item focus;
    private static Fluid drilling;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        focus = new Item();
        Item.itemRegistry.addObject(30000, "isme_test:focus", focus);
        drilling = new Fluid("isme_test_drilling");
        FluidRegistry.registerFluid(drilling);
    }

    @Test
    public void localSplitDebitPreservesNbtAndSupportsLongFluidTotals() throws Exception {
        if (!HeadlessForgeFixture.inside()) {
            HeadlessForgeFixture.run(getClass().getName(), "localSplitDebitPreservesNbtAndSupportsLongFluidTotals");
            return;
        }
        ItemStack first = new ItemStack(focus, 2, 0);
        first.setTagCompound(new NBTTagCompound());
        first.getTagCompound()
            .setString("name", "keep");
        ItemStack[] items = { first, new ItemStack(focus, 4, 0) };
        FluidStack[] fluids = { new FluidStack(drilling, 2000000000), new FluidStack(drilling, 2000000000) };
        int[] dirty = { 0 };
        ProcessingPorts.Snapshot snapshot = LocalStartResources.arrays(items, fluids, drilling, () -> dirty[0]++)
            .capture(ItemIdentity.focusSignature(first));
        assertEquals(6, snapshot.focuses());
        assertEquals(4000000000L, snapshot.drillingLiters());
        ProcessingPorts.Debit debit = snapshot.prepareDebit(3, 3000000000L);
        assertEquals(2, first.stackSize);
        assertTrue(debit.unchanged());
        debit.apply();
        assertEquals(0, items[0].stackSize);
        assertEquals(3, items[1].stackSize);
        assertEquals(
            "keep",
            first.getTagCompound()
                .getString("name"));
        assertEquals(0, fluids[0].amount);
        assertEquals(1000000000, fluids[1].amount);
        assertEquals(1, dirty[0]);
        assertFalse(debit.unchanged());
    }

    @Test
    public void replacementCountsNbtAndFluidTagsInvalidateSnapshot() throws Exception {
        if (!HeadlessForgeFixture.inside()) {
            HeadlessForgeFixture.run(getClass().getName(), "replacementCountsNbtAndFluidTagsInvalidateSnapshot");
            return;
        }
        ItemStack[] items = { new ItemStack(focus, 3, 0) };
        FluidStack[] fluids = { new FluidStack(drilling, 100) };
        LocalStartResources resources = LocalStartResources.arrays(items, fluids, drilling, () -> {});
        String key = ItemIdentity.focusSignature(items[0]);
        ProcessingPorts.Debit replaced = resources.capture(key)
            .prepareDebit(1, 10);
        items[0] = items[0].copy();
        assertFalse(replaced.unchanged());
        ProcessingPorts.Debit changed = resources.capture(key)
            .prepareDebit(1, 10);
        items[0].stackSize++;
        assertFalse(changed.unchanged());
        ProcessingPorts.Debit nbt = resources.capture(key)
            .prepareDebit(1, 10);
        items[0].setTagCompound(new NBTTagCompound());
        items[0].getTagCompound()
            .setString("changed", "yes");
        assertFalse(nbt.unchanged());
        ProcessingPorts.Debit fluid = resources.capture(key)
            .prepareDebit(1, 10);
        fluids[0].tag = new NBTTagCompound();
        assertFalse(fluid.unchanged());
        assertEquals(
            0,
            resources.capture(key)
                .drillingLiters());
    }
}
