package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static org.junit.Assert.*;

import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagString;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.ExcavationCycle;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.PaidBatch;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.util.shutdown.ShutDownReasonRegistry;

/** Actual controller/native persistence and energy callbacks, public tile doubles, no World/structure formation. */
public final class ControllerLifecycleTest {

    private static Item ore;
    private static MeteoriteExcavation prototype;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        ore = new Item();
        Item.itemRegistry.addObject(30001, "minecraft:isme_controller_ore", ore);
        // Public registration-phase prerequisites inside this discarded isolated loader; not a full FML lifecycle.
        gregtech.api.GregTechAPI.sPreloadStarted = true;
        gregtech.api.GregTechAPI.sPostloadStarted = false;
        prototype = new MeteoriteExcavation(31979, "isme_headless_controller", "Headless controller");
    }

    private boolean delegated(String name) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        String original = Thread.currentThread()
            .getName();
        try {
            Thread.currentThread()
                .setName("Server thread");
            HeadlessForgeFixture.run(getClass().getName(), name);
        } finally {
            Thread.currentThread()
                .setName(original);
        }
        return true;
    }

    private static NBTTagCompound runningState() {
        ExcavationCycle cycle = new ExcavationCycle();
        cycle.begin(
            new PaidBatch(
                "standard:test",
                3,
                30,
                3072,
                12,
                150,
                Collections.singletonMap(new ItemIdentity(new ItemStack(ore)), 100L)));
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setTag("ISMECycle", cycle.save());
        nbt.setInteger("mProgresstime", 12);
        nbt.setInteger("mMaxProgresstime", 30);
        nbt.setLong("mEUt", -3072);
        nbt.setInteger("mEfficiency", 10000);
        nbt.setBoolean("makePowerfailEvents", false);
        return nbt;
    }

    private static MeteoriteExcavation machine(PublicTileFixture tile, NBTTagCompound data) {
        MeteoriteExcavation machine = (MeteoriteExcavation) prototype.newMetaEntity(null);
        tile.bind(machine);
        machine.loadNBTData(data);
        return machine;
    }

    @Test
    public void normalSaveAndUnloadPreservePaidProgress() throws Exception {
        if (delegated("normalSaveAndUnloadPreservePaidProgress")) return;
        MeteoriteExcavation machine = machine(new PublicTileFixture(), runningState());
        assertEquals(12, machine.progressTicks());
        assertEquals(30, machine.durationTicks());
        machine.onUnload();
        assertEquals(100, machine.getmStartUpCheck());
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        MeteoriteExcavation restored = machine(new PublicTileFixture(), saved);
        assertEquals(12, restored.progressTicks());
        assertEquals(100, restored.retainedCount());
        assertEquals(0, restored.pendingCount());
        assertEquals(0, saved.getInteger("mOutputItemsLength"));
    }

    @Test
    public void completionCallbackCapturesOnlyOnceAndPersistsPending() throws Exception {
        if (delegated("completionCallbackCapturesOnlyOnceAndPersistsPending")) return;
        MeteoriteExcavation machine = machine(new PublicTileFixture(), runningState());
        assertTrue(machine.addItemOutputs(new ItemStack[0]));
        assertFalse(machine.addItemOutputs(new ItemStack[0]));
        assertEquals(100, machine.pendingCount());
        // Simulate the already-verified native completion resetting progress, without running a World tick.
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        saved.setInteger("mProgresstime", 0);
        saved.setInteger("mMaxProgresstime", 0);
        saved.setLong("mEUt", 0);
        assertEquals(100, machine(new PublicTileFixture(), saved).pendingCount());
    }

    @Test
    public void itemNbtCarriesOnePlanAndPauseState() throws Exception {
        if (delegated("itemNbtCarriesOnePlanAndPauseState")) return;
        PublicTileFixture tile = new PublicTileFixture();
        tile.allowed = false;
        MeteoriteExcavation machine = machine(tile, runningState());
        NBTTagCompound item = new NBTTagCompound();
        machine.setItemNBT(item);
        assertTrue(item.getBoolean("mWorks"));
        assertEquals(12, item.getInteger("mProgresstime"));
        assertTrue(
            item.getCompoundTag("ISMECycle")
                .hasKey("running"));
        assertTrue(item.hasKey("ISMEInventory", 10));
        assertEquals(100, machine(new PublicTileFixture(), item).retainedCount());
    }

    @Test
    public void nativeEnergyDrainPauseAndPowerLossFollowOwnedPlan() throws Exception {
        if (delegated("nativeEnergyDrainPauseAndPowerLossFollowOwnedPlan")) return;
        PublicTileFixture tile = new PublicTileFixture();
        MeteoriteExcavation machine = machine(tile, runningState());
        MTEHatchEnergy energy = new MTEHatchEnergy("isme_energy", 4, new String[0], new ITexture[0][][]);
        PublicTileFixture buffer = new PublicTileFixture();
        buffer.energy = 10000;
        buffer.bind(energy);
        machine.mEnergyHatches.add(energy);
        tile.allowed = false;
        assertFalse(machine.onRunningTick(null));
        assertEquals(10000, buffer.energy);
        assertEquals(12, machine.progressTicks());
        tile.allowed = true;
        assertTrue(machine.onRunningTick(null));
        assertEquals(6928, buffer.energy);
        buffer.energy = 0;
        tile.dirty = 0;
        assertFalse(machine.onRunningTick(null));
        assertTrue(tile.dirty > 0);
        assertEquals(0, machine.durationTicks());
        assertEquals(0, machine.pendingCount());
        assertEquals(ShutDownReasonRegistry.POWER_LOSS, tile.shutdown);
        assertFalse(tile.allowed);
        assertFalse(machine.addItemOutputs(new ItemStack[0]));
    }

    @Test
    public void invalidPresentCycleTagAndNativeMismatchStayBlocked() throws Exception {
        if (delegated("invalidPresentCycleTagAndNativeMismatchStayBlocked")) return;
        NBTTagCompound invalid = runningState();
        invalid.setTag("ISMECycle", new NBTTagString("corrupt type"));
        MeteoriteExcavation machine = machine(new PublicTileFixture(), invalid);
        assertEquals("fault", machine.runtimeState());
        assertEquals(0, machine.durationTicks());
        NBTTagCompound saved = new NBTTagCompound();
        machine.saveNBTData(saved);
        assertEquals(invalid.getTag("ISMECycle"), saved.getTag("ISMECycle"));
        invalid = runningState();
        invalid.setLong("mEUt", 3072);
        assertEquals("fault", machine(new PublicTileFixture(), invalid).runtimeState());
    }
}
