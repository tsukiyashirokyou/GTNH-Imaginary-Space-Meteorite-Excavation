package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.StringTranslate;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationCycle;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationWaila;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.PaidBatch;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.BoundedNbtSize;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Actual server extension hook + client formatter; no World, HUD overlay or full GT proxy lifecycle. */
public final class WailaTest {

    private static Item ore;
    private static MeteoriteExcavation prototype;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        ore = new Item().setHasSubtypes(true)
            .setUnlocalizedName("isme_test_waila");
        Item.itemRegistry.addObject(30001, "minecraft:isme_waila_ore", ore);
        gregtech.api.GregTechAPI.sPreloadStarted = true;
        gregtech.api.GregTechAPI.sPostloadStarted = false;
        prototype = new MeteoriteExcavation(31978, "isme_waila_controller", "Waila test controller");
        StringTranslate
            .inject(WailaTest.class.getResourceAsStream("/assets/imaginaryspacemeteoriteexcavation/lang/en_US.lang"));
        StringTranslate.inject(WailaTest.class.getResourceAsStream("/assets/gregtech/lang/en_US.lang"));
        StringTranslate
            .inject(new ByteArrayInputStream("item.isme_test_waila.name=Test Ore\n".getBytes(StandardCharsets.UTF_8)));
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        String original = Thread.currentThread()
            .getName();
        try {
            Thread.currentThread()
                .setName("Server thread");
            HeadlessForgeFixture.run(getClass().getName(), method);
        } finally {
            Thread.currentThread()
                .setName(original);
        }
        return true;
    }

    private static Map<ItemIdentity, Long> outputs(int types) {
        Map<ItemIdentity, Long> map = new LinkedHashMap<>();
        for (int i = 0; i < types; i++)
            map.put(new ItemIdentity(new ItemStack(ore, 1, i)), (long) Integer.MAX_VALUE + 11);
        return map;
    }

    private static NBTTagCompound state(ExcavationCycle cycle) {
        NBTTagCompound data = new NBTTagCompound();
        data.setBoolean("makePowerfailEvents", false); // Same no-game native callback prerequisite as lifecycle tests.
        data.setTag("ISMECycle", cycle.save());
        if (cycle.running() != null) {
            data.setInteger("mProgresstime", 12);
            data.setInteger("mMaxProgresstime", cycle.running().duration);
            data.setLong("mEUt", -cycle.running().eut);
            data.setInteger("mEfficiency", 10000);
        }
        // Stale, internally consistent historical GUI summary must never become a current HUD batch.
        NBTTagCompound old = new NBTTagCompound();
        old.setString("recipe", "stale_recipe");
        old.setInteger("batch", 99);
        old.setLong("kept", 10);
        old.setLong("drilling", 10);
        old.setLong("voided", 0);
        data.setTag("ISMESummary", old);
        return data;
    }

    private static MeteoriteExcavation machine(PublicTileFixture tile, NBTTagCompound data) {
        MeteoriteExcavation machine = (MeteoriteExcavation) prototype.newMetaEntity(null);
        tile.bind(machine);
        machine.loadNBTData(data);
        machine.mMachine = true;
        machine.mStartUpCheck = -1;
        machine.mWrench = machine.mScrewdriver = machine.mSoftMallet = machine.mHardHammer = machine.mSolderingTool = machine.mCrowbar = true;
        return machine;
    }

    private static NBTTagCompound hud(MeteoriteExcavation machine) {
        NBTTagCompound packet = new NBTTagCompound();
        machine.getExtraWailaNBT(null, null, packet, null, 0, 0, 0);
        return packet;
    }

    private static List<String> lines(NBTTagCompound packet) {
        List<String> tip = new ArrayList<>();
        ExcavationWaila.append(tip, packet);
        return tip;
    }

    @Test
    public void idlePausedAndUnformedNeverDisplayHistoricalBatch() throws Exception {
        if (delegated("idlePausedAndUnformedNeverDisplayHistoricalBatch")) return;
        PublicTileFixture tile = new PublicTileFixture();
        MeteoriteExcavation machine = machine(tile, state(new ExcavationCycle()));
        for (int mode = 0; mode < 3; mode++) {
            machine.mMachine = mode != 1;
            tile.allowed = mode != 2;
            NBTTagCompound packet = hud(machine);
            NBTTagCompound own = packet.getCompoundTag(ExcavationWaila.KEY);
            assertFalse(own.getBoolean("hasBatch"));
            assertFalse(own.hasKey("batch"));
            assertFalse(own.hasKey("radius"));
            assertFalse(own.hasKey("preview"));
            assertEquals(0, own.getLong("pending"));
            String text = lines(packet).toString();
            assertFalse(text.contains("stale_recipe"));
            assertFalse(text.contains("Batch:"));
            if (mode == 1) assertTrue(packet.getBoolean("incompleteStructure"));
            if (mode == 2) assertEquals("paused", own.getString("state"));
        }
    }

    @Test
    public void currentPaidBatchHasRadiusProgressLongCountsAndZeroPausedDraw() throws Exception {
        if (delegated("currentPaidBatchHasRadiusProgressLongCountsAndZeroPausedDraw")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        Map<ItemIdentity, Long> output = outputs(5);
        long total = output.values()
            .stream()
            .mapToLong(Long::longValue)
            .sum();
        cycle.begin(new PaidBatch("current", 3, 30, 3072, 12, total + 100, output, 2));
        PublicTileFixture tile = new PublicTileFixture();
        MeteoriteExcavation machine = machine(tile, state(cycle));
        NBTTagCompound before = new NBTTagCompound();
        machine.saveNBTData(before);
        NBTTagCompound packet = hud(machine), own = packet.getCompoundTag(ExcavationWaila.KEY);
        assertEquals("running", own.getString("state"));
        assertTrue(packet.getBoolean("isActive"));
        assertEquals(3072, packet.getLong("energyUsage"));
        assertEquals(2, own.getInteger("radius"));
        assertEquals(12, own.getInteger("progress"));
        assertEquals(30, own.getInteger("duration"));
        assertEquals(total, own.getLong("kept"));
        assertEquals(0, own.getInteger("towers"));
        assertTrue(
            lines(packet).toString()
                .contains("Radius: 2; pylons: 0 / 18"));
        NBTTagList preview = own.getCompoundTag("preview")
            .getTagList("items", 10);
        assertEquals(3, preview.tagCount());
        assertEquals(
            (long) Integer.MAX_VALUE + 11,
            preview.getCompoundTagAt(0)
                .getLong("amount"));
        preview.getCompoundTagAt(0)
            .setLong("amount", 1);
        assertEquals(
            (long) Integer.MAX_VALUE + 11,
            hud(machine).getCompoundTag(ExcavationWaila.KEY)
                .getCompoundTag("preview")
                .getTagList("items", 10)
                .getCompoundTagAt(0)
                .getLong("amount"));
        NBTTagCompound after = new NBTTagCompound();
        machine.saveNBTData(after);
        assertEquals(before, after);
        tile.allowed = false;
        packet = hud(machine);
        assertFalse(packet.getBoolean("isActive"));
        assertEquals(
            "paused",
            packet.getCompoundTag(ExcavationWaila.KEY)
                .getString("state"));
        assertTrue(
            lines(packet).toString()
                .contains("0 EU/t"));
        assertEquals(12, machine.progressTicks());
        assertEquals(total, machine.retainedCount());
    }

    @Test
    public void completedPendingAndFaultUseOwnedRemainderWithoutGhostRunningInfo() throws Exception {
        if (delegated("completedPendingAndFaultUseOwnedRemainderWithoutGhostRunningInfo")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.pending()
            .complete(outputs(4));
        MeteoriteExcavation machine = machine(new PublicTileFixture(), state(cycle));
        NBTTagCompound own = hud(machine).getCompoundTag(ExcavationWaila.KEY);
        assertEquals("output_wait", own.getString("state"));
        assertFalse(own.getBoolean("hasBatch"));
        assertFalse(own.hasKey("batch"));
        assertEquals(4L * (Integer.MAX_VALUE + 11L), own.getLong("pending"));
        cycle.pending()
            .flush(
                1,
                offered -> Collections.singletonMap(
                    offered.keySet()
                        .iterator()
                        .next(),
                    9L));
        machine = machine(new PublicTileFixture(), state(cycle));
        assertEquals(
            4L * (Integer.MAX_VALUE + 11L) - 9,
            hud(machine).getCompoundTag(ExcavationWaila.KEY)
                .getLong("pending"));
        cycle.pending()
            .flush(2, offered -> { throw new IllegalStateException("uncertain"); });
        machine = machine(new PublicTileFixture(), state(cycle));
        NBTTagCompound packet = hud(machine);
        own = packet.getCompoundTag(ExcavationWaila.KEY);
        assertEquals("fault", own.getString("state"));
        assertEquals(-1, own.getLong("pending"));
        assertFalse(own.hasKey("preview"));
        assertTrue(
            own.getString("diagnostic")
                .length() <= 128);
        assertTrue(
            lines(packet).toString()
                .contains("Unknown"));
    }

    @Test
    public void legacyRadiusIsUnknownAndShutdownReasonUsesNativeClientProtocol() throws Exception {
        if (delegated("legacyRadiusIsUnknownAndShutdownReasonUsesNativeClientProtocol")) return;
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(new PaidBatch("legacy", 3, 30, 3072, 12, Integer.MAX_VALUE + 11L, outputs(1)));
        PublicTileFixture tile = new PublicTileFixture();
        MeteoriteExcavation machine = machine(tile, state(cycle));
        assertEquals(
            -1,
            hud(machine).getCompoundTag(ExcavationWaila.KEY)
                .getInteger("radius"));
        assertTrue(
            lines(hud(machine)).toString()
                .contains("Unknown"));
        machine.stopMachine(ShutDownReasonRegistry.POWER_LOSS);
        NBTTagCompound own = hud(machine).getCompoundTag(ExcavationWaila.KEY);
        assertEquals("failed", own.getString("state"));
        assertFalse(own.getBoolean("hasBatch"));
        assertEquals(
            "power_loss",
            own.getCompoundTag("shutdown")
                .getCompoundTag("data")
                .getString("key"));
        assertTrue(lines(hud(machine)).size() >= 3);
        NBTTagCompound malformed = state(new ExcavationCycle());
        malformed.setTag("ISMECycle", new NBTTagString("bad"));
        assertEquals(
            "fault",
            hud(machine(tile, malformed)).getCompoundTag(ExcavationWaila.KEY)
                .getString("state"));
    }

    @Test
    public void previewIsBoundedAndLargeItemNbtRemainsInProductionOnly() throws Exception {
        if (delegated("previewIsBoundedAndLargeItemNbtRemainsInProductionOnly")) return;
        ItemStack sample = new ItemStack(ore);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setByteArray("large", new byte[10000]);
        sample.setTagCompound(tag);
        Map<ItemIdentity, Long> map = new LinkedHashMap<>();
        ItemIdentity large = new ItemIdentity(sample);
        map.put(large, 123L);
        map.putAll(outputs(5));
        NBTTagCompound preview = ExcavationWaila.preview(map);
        assertEquals(6, preview.getInteger("types"));
        assertEquals(
            3,
            preview.getTagList("items", 10)
                .tagCount());
        NBTTagCompound first = preview.getTagList("items", 10)
            .getCompoundTagAt(0);
        assertTrue(first.getBoolean("simplified"));
        assertFalse(first.hasKey("tag"));
        assertTrue(BoundedNbtSize.measure(preview, 8192) <= 8192);
        assertEquals(
            10000,
            large.save(123)
                .getCompoundTag("tag")
                .getByteArray("large").length);
        NBTTagCompound own = new NBTTagCompound();
        own.setString("state", "output_wait");
        own.setLong("pending", 123);
        own.setTag("preview", preview);
        NBTTagCompound root = new NBTTagCompound();
        root.setTag(ExcavationWaila.KEY, own);
        List<String> tip = lines(root);
        assertTrue(
            tip.toString()
                .contains("Test Ore"));
        assertFalse(
            tip.toString()
                .contains(net.minecraft.util.EnumChatFormatting.AQUA + "minecraft:isme_waila_ore"));
        assertTrue(tip.size() <= 8);
    }
}
