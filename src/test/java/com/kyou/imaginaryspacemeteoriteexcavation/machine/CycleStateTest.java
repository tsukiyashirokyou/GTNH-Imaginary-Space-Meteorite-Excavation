package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static org.junit.Assert.*;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationCycle;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.PaidBatch;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

public final class CycleStateTest {

    private static Item ore;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        ore = new Item();
        Item.itemRegistry.addObject(30001, "minecraft:isme_stage7_ore", ore);
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), method);
        return true;
    }

    private static PaidBatch paid(long count) {
        return new PaidBatch(
            "standard:test",
            3,
            30,
            3072,
            12,
            count + 100,
            Collections.singletonMap(new ItemIdentity(new ItemStack(ore, 1, 7)), count));
    }

    @Test
    public void paidPlanResumesThenCompletesExactlyOnce() throws Exception {
        if (delegated("paidPlanResumesThenCompletesExactlyOnce")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(paid((long) Integer.MAX_VALUE + 3));
        assertFalse(cycle.canStart());
        ExcavationCycle loaded = ExcavationCycle.load(cycle.save(), 12, 30, -3072);
        assertEquals("", loaded.fault());
        assertEquals(3, loaded.running().batch);
        assertTrue(loaded.finish());
        assertFalse(loaded.finish());
        assertEquals(
            (long) Integer.MAX_VALUE + 3,
            loaded.pending()
                .total());
        assertFalse(loaded.canStart());
        assertFalse(
            loaded.save()
                .hasKey("running"));
    }

    @Test
    public void partialOutputReloadDoesNotRegenerate() throws Exception {
        if (delegated("partialOutputReloadDoesNotRegenerate")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(paid(100));
        cycle.finish();
        cycle.pending()
            .flush(
                1,
                offer -> Collections.singletonMap(
                    offer.keySet()
                        .iterator()
                        .next(),
                    25L));
        ExcavationCycle loaded = ExcavationCycle.load(cycle.save(), 0, 0, 0);
        assertNull(loaded.running());
        assertEquals(
            75,
            loaded.pending()
                .total());
        assertFalse(loaded.finish());
        assertEquals(
            75,
            loaded.pending()
                .flush(2, offer -> offer));
        assertTrue(loaded.canStart());
    }

    @Test
    public void failureDiscardsOnlyUnfinishedOutput() throws Exception {
        if (delegated("failureDiscardsOnlyUnfinishedOutput")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(paid(100));
        cycle.abort("power_loss");
        assertNull(cycle.running());
        assertEquals(
            0,
            cycle.pending()
                .total());
        assertFalse(cycle.finish());
        cycle.begin(paid(80));
        cycle.finish();
        cycle.abort("structure_incomplete");
        assertEquals(
            80,
            cycle.pending()
                .total());
        assertFalse(cycle.canStart());
    }

    @Test
    public void mismatchedProgressAndCorruptionPreserveOriginal() throws Exception {
        if (delegated("mismatchedProgressAndCorruptionPreserveOriginal")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(paid(100));
        for (int mode = 0; mode < 5; mode++) {
            NBTTagCompound saved = cycle.save();
            if (mode == 3) saved.getCompoundTag("pending")
                .setInteger("schema", 99);
            if (mode == 4) saved.setInteger("schema", 99);
            ExcavationCycle loaded = ExcavationCycle
                .load(saved, mode == 0 ? 30 : 3, mode == 1 ? 31 : 30, mode == 2 ? 3072 : -3072);
            assertFalse(
                loaded.fault()
                    .isEmpty());
            assertFalse(loaded.canStart());
            assertFalse(loaded.finish());
            assertEquals(saved, loaded.save());
        }
    }

    @Test
    public void corruptCompletedPendingIsNeverEmptyAndReady() throws Exception {
        if (delegated("corruptCompletedPendingIsNeverEmptyAndReady")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(paid(10));
        cycle.finish();
        NBTTagCompound saved = cycle.save();
        saved.getCompoundTag("pending")
            .setInteger("schema", 99);
        ExcavationCycle loaded = ExcavationCycle.load(saved, 0, 0, 0);
        assertFalse(
            loaded.fault()
                .isEmpty());
        assertFalse(loaded.canStart());
        assertEquals(saved, loaded.save());
    }

    @Test
    public void fullFilteringStillOwnsPaidProcessingUntilCompletion() throws Exception {
        if (delegated("fullFilteringStillOwnsPaidProcessingUntilCompletion")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(new PaidBatch("standard:test", 3, 30, 3072, 12, 56, Collections.emptyMap()));
        assertFalse(cycle.canStart());
        assertEquals(56, cycle.running().drilling);
        assertTrue(cycle.finish());
        assertTrue(cycle.canStart());
        assertEquals(
            0,
            cycle.pending()
                .total());
    }

    @Test
    public void onlyIdleLegacyStateCanLoadWithoutAnOwnedPlan() throws Exception {
        if (delegated("onlyIdleLegacyStateCanLoadWithoutAnOwnedPlan")) return;
        assertTrue(
            ExcavationCycle.load(null, 0, 0, 0)
                .canStart());
        assertFalse(
            ExcavationCycle.load(null, 0, 30, -3072)
                .fault()
                .isEmpty());
    }
}
