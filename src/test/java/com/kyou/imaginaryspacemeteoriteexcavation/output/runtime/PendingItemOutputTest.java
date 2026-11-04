package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.Collections;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.OutputLimits;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

public final class PendingItemOutputTest {

    private static Item ore;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        ore = new Item().setHasSubtypes(true);
        Item.itemRegistry.addObject(30001, "minecraft:isme_test_pending_ore", ore);
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), method);
        return true;
    }

    private static ItemIdentity identity() {
        ItemStack sample = new ItemStack(ore, 1, 7);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("name", "full identity");
        tag.setLong("value", Long.MAX_VALUE);
        tag.setByteArray("bytes", new byte[] { 1, 2, -1 });
        sample.setTagCompound(tag);
        return new ItemIdentity(sample);
    }

    @Test
    public void fullIdentityAndLongQuantityRoundTripThroughActualNbtBytes() throws Exception {
        if (delegated("fullIdentityAndLongQuantityRoundTripThroughActualNbtBytes")) return;
        ItemIdentity id = identity();
        PendingItemOutput pending = new PendingItemOutput();
        long amount = (long) Integer.MAX_VALUE + 100;
        pending.complete(Collections.singletonMap(id, amount));
        pending.flush(1, offered -> Collections.singletonMap(id, 9L));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(pending.save(), new DataOutputStream(bytes));
        NBTTagCompound saved = CompressedStreamTools
            .read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        PendingItemOutput restored = PendingItemOutput.load(saved);
        assertEquals("", restored.fault());
        assertEquals(
            Long.valueOf(amount - 9),
            restored.snapshot()
                .get(id));
        assertTrue(restored.blocksStart());
        assertEquals(
            7,
            restored.snapshot()
                .keySet()
                .iterator()
                .next()
                .template()
                .getItemDamage());
        NBTTagCompound copy = restored.save();
        copy.setString("fault", "edited caller copy");
        assertEquals("", restored.fault());
    }

    @Test
    public void corruptUnknownDuplicateAndWrongListRecordsArePreservedAndBlocked() throws Exception {
        if (delegated("corruptUnknownDuplicateAndWrongListRecordsArePreservedAndBlocked")) return;
        PendingItemOutput valid = new PendingItemOutput();
        valid.complete(Collections.singletonMap(identity(), 100L));
        for (int mode = 0; mode < 7; mode++) {
            NBTTagCompound bad = valid.save();
            NBTTagList list = bad.getTagList("items", 10);
            NBTTagCompound item = list.getCompoundTagAt(0);
            if (mode == 0) bad.setInteger("schema", 99);
            if (mode == 1) item.setLong("amount", -1);
            if (mode == 2) item.setString("registry", "missing:unknown");
            if (mode == 3) list.appendTag(item.copy());
            if (mode == 4) {
                NBTTagList wrong = new NBTTagList();
                wrong.appendTag(new NBTTagString("wrong type"));
                bad.setTag("items", wrong);
            }
            if (mode == 5) item.setInteger("amount", 1);
            if (mode == 6) item.setLong("amount", OutputLimits.TOTAL + 1);
            PendingItemOutput loaded = PendingItemOutput.load(bad);
            assertTrue("mode " + mode, loaded.blocksStart());
            assertFalse(
                "mode " + mode,
                loaded.fault()
                    .isEmpty());
            assertEquals(bad, loaded.save());
            assertEquals(
                0,
                loaded.flush(1, offered -> { throw new AssertionError("Quarantined records must not output"); }));
            assertFalse(loaded.canStart(Collections.emptyMap()));
        }
    }

    @Test
    public void oversizedIdentityIsRejectedBeforeOwnershipCapture() throws Exception {
        if (delegated("oversizedIdentityIsRejectedBeforeOwnershipCapture")) return;
        ItemStack sample = new ItemStack(ore);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("large", new byte[OutputLimits.IDENTITY_BYTES]);
        sample.setTagCompound(tag);
        PendingItemOutput pending = new PendingItemOutput();
        assertFalse(pending.canStart(Collections.singletonMap(new ItemIdentity(sample), 1L)));
        assertFalse(pending.blocksStart());
        assertTrue(pending.canStart(Collections.singletonMap(identity(), OutputLimits.TOTAL)));
    }

    @Test
    public void uncertainNativeCommitFaultSurvivesNormalSaveLoad() throws Exception {
        if (delegated("uncertainNativeCommitFaultSurvivesNormalSaveLoad")) return;
        PendingItemOutput pending = new PendingItemOutput();
        Map<ItemIdentity, Long> items = Collections.singletonMap(identity(), 12L);
        pending.complete(items);
        pending.flush(1, offered -> { throw new IllegalStateException("Injected native commit failure"); });
        PendingItemOutput restored = PendingItemOutput.load(pending.save());
        assertFalse(
            restored.fault()
                .isEmpty());
        assertEquals(items, restored.snapshot());
        assertEquals(0, restored.flush(2, offered -> { throw new AssertionError("Cannot replay uncertain receipt"); }));
    }
}
