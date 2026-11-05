package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeSnapshot;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.WeightedComponent;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.BoundNetwork;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MachinePower;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.MeteorProcessPlanner;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.PreparedProcess;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessFailure;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.LocalStartResources;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime.NativePowerSupply;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEHatchEnergy;
import gregtech.api.metatileentity.implementations.MTEHatchEnergyDebug;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug;
import gregtech.api.metatileentity.implementations.MTEHatchInputDebug;
import tectech.thing.metaTileEntity.hatch.MTEHatchCreativeMaintenance;

/** Actual fixed-version hatch classes and public callbacks, without World or a rendered structure. */
public final class DebugHatchCompatibilityTest {

    private static Item focus, ore, filler;
    private static Fluid drilling, otherFluid;
    private static MeteoriteExcavation prototype;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        focus = item(30001, "focus");
        ore = item(30002, "ore");
        filler = item(30003, "filler");
        drilling = new Fluid("isme_debug_drilling");
        FluidRegistry.registerFluid(drilling);
        otherFluid = new Fluid("isme_debug_other");
        FluidRegistry.registerFluid(otherFluid);
        ItemList.sDrillingFluid = drilling;
        gregtech.api.GregTechAPI.sPreloadStarted = true;
        gregtech.api.GregTechAPI.sPostloadStarted = false;
        prototype = new MeteoriteExcavation(31976, "isme_debug_controller", "Debug compatibility test");
    }

    private static Item item(int id, String name) {
        Item result = new Item();
        Item.itemRegistry.addObject(id, "minecraft:isme_debug_" + name, result);
        return result;
    }

    private boolean delegated(String name) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        String previous = Thread.currentThread()
            .getName();
        try {
            Thread.currentThread()
                .setName("Server thread");
            HeadlessForgeFixture.run(getClass().getName(), name);
        } finally {
            Thread.currentThread()
                .setName(previous);
        }
        return true;
    }

    private static MTEHatchInputBusDebug debug(int amount, boolean finite) {
        MTEHatchInputBusDebug bus = new MTEHatchInputBusDebug(
            "debug_input",
            (byte) 1,
            new String[0],
            new ITexture[0][][]);
        new PublicTileFixture().bind(bus);
        bus.stackSizeParseAs = amount;
        bus.finiteMode = finite;
        ItemStack sample = new ItemStack(focus);
        sample.setTagCompound(new NBTTagCompound());
        sample.getTagCompound()
            .setString("identity", "original");
        bus.phantomHolder.setStackInSlot(0, sample);
        return bus;
    }

    private static MTEHatchInput fluid(int amount) {
        MTEHatchInput hatch = new MTEHatchInput("debug_test_fluid", 1, new String[0], new ITexture[0][][]);
        new PublicTileFixture().bind(hatch);
        hatch.mFluid = new FluidStack(drilling, amount);
        return hatch;
    }

    private static MTEHatchInputDebug debugFluid() {
        MTEHatchInputDebug hatch = new MTEHatchInputDebug("debug_fluid", 14, new String[0], new ITexture[0][][]);
        new PublicTileFixture().bind(hatch);
        return hatch;
    }

    private static LocalStartResources resources(MTEHatchInputBusDebug bus, MTEHatchInput fluid) {
        return LocalStartResources.ordinary(Collections.singletonList(bus), Collections.singletonList(fluid));
    }

    private static final class Network implements ProcessingPorts.LpAccess {

        private int amount = 100, charges;

        public long balance(String owner) {
            return amount;
        }

        public boolean debit(String owner, int cost) {
            amount -= cost;
            charges++;
            return true;
        }
    }

    private static PreparedProcess<ItemIdentity> plan(LocalStartResources resources, Network network) {
        RecipeCatalogue.Entry<ItemIdentity> recipe = new RecipeCatalogue.Entry<>(
            "standard:debug_test",
            "test",
            "source",
            new RecipeSnapshot<>(
                ItemIdentity.focusSignature(new ItemStack(focus)),
                1,
                4,
                0,
                Arrays.asList(
                    new WeightedComponent<>(new ItemIdentity(new ItemStack(ore)), 8),
                    new WeightedComponent<>(new ItemIdentity(new ItemStack(filler)), 87)),
                Collections.emptyList(),
                ItemIdentity::signature));
        MeteorProcessPlanner<ItemIdentity> planner = new MeteorProcessPlanner<>((ticks, power, choose, advance) -> {
            int count = choose.applyAsInt(3);
            advance.accept(count);
            return ProcessingPorts.Schedule.success(count, 100, 3072);
        });
        return planner.prepare(
            recipe,
            12,
            () -> new BoundNetwork("owner", "orb", false),
            resources,
            network,
            MachinePower.fromHatches(1, 0, new long[] { 8192 }, new long[] { 2 }),
            item -> true,
            items -> true,
            () -> true).process;
    }

    @Test
    public void nativeDebugHatchesPreserveInputPowerAndMaintenanceContracts() throws Exception {
        if (delegated("nativeDebugHatchesPreserveInputPowerAndMaintenanceContracts")) return;
        // Share one isolated native class loader across the related native hatch scenarios.
        finiteDebugSlotZeroAndOrdinaryInputChargeTheSameWholeBatch();
        infiniteOffersAndFiniteLimitsRemainReusable();
        debugConfigurationChangesRejectStartBeforeLpOrFluidCharges();
        debugWhitelistDoesNotAdmitUnknownInputSubclasses();
        debugEnergyUsesConfiguredAmperageButNormalSingleHatchStaysOneAmp();
        nativeEnergyAdderAndRuntimeDrainAcceptDebugHatch();
        nativeDebugMaintenanceRepairsAndResetsItsToolFlags();
        debugFluidUsesAllSlotsAndPreservesSamplesAcrossBatches();
        debugFluidAndOrdinaryHatchesRespectAllocationOrder();
        debugFluidLongTotalsAndFullValueRevalidation();
        debugFluidWhitelistAndNonDrillingFluidsStayIsolated();
    }

    private void finiteDebugSlotZeroAndOrdinaryInputChargeTheSameWholeBatch() {
        MTEHatchInputBusDebug debug = debug(4, true);
        MeteoriteExcavation machine = (MeteoriteExcavation) prototype.newMetaEntity(null);
        new PublicTileFixture().bind(machine);
        assertTrue(machine.addInputBusToMachineList(debug.getBaseMetaTileEntity(), 62));
        debug.setInventorySlotContents(debug.getCircuitSlot(), new ItemStack(focus, 50));
        assertEquals(16, LocalStartResources.recipeCircuitSlot(debug));
        // Native circuit setters normalize to zero; even a nonzero saved circuit must remain excluded.
        assertEquals(0, debug.getStackInSlot(16).stackSize);
        debug.getStackInSlot(16).stackSize = 50;
        assertEquals(50, debug.getStackInSlot(16).stackSize);
        MTEHatchInputBus real = new MTEHatchInputBus("real_input", 1, 2, new String[0], new ITexture[0][][]);
        new PublicTileFixture().bind(real);
        real.setInventorySlotContents(0, new ItemStack(focus, 2));
        MTEHatchInput fluid = fluid(100);
        LocalStartResources local = LocalStartResources
            .ordinary(Arrays.asList(real, debug), Collections.singletonList(fluid));
        assertEquals(
            6,
            local.capture(ItemIdentity.focusSignature(new ItemStack(focus)))
                .focuses());
        Network network = new Network();
        PreparedProcess<ItemIdentity> prepared = plan(local, network);
        assertNotNull(prepared);
        assertEquals(3, prepared.schedule().batch);
        assertEquals(
            56,
            prepared.prefilterYield()
                .prefilterTotal());
        assertEquals(ProcessFailure.NONE, prepared.start());
        assertEquals(0, real.getStackInSlot(0).stackSize);
        assertEquals(4, debug.getStackInSlot(0).stackSize);
        assertEquals(1, debug.phantomHolder.getStackInSlot(0).stackSize);
        assertEquals(50, debug.getStackInSlot(16).stackSize);
        assertEquals(44, fluid.getFluid().amount);
        assertEquals(88, network.amount);
        assertEquals(1, network.charges);
        assertEquals(ProcessFailure.ALREADY_DECIDED, prepared.start());
        assertEquals(1, network.charges);
    }

    private void infiniteOffersAndFiniteLimitsRemainReusable() {
        MTEHatchInputBusDebug debug = debug(2, false);
        assertNotSame(debug.getStackInSlot(0), debug.getStackInSlot(0));
        ProcessingPorts.Snapshot snapshot = resources(debug, fluid(100))
            .capture(ItemIdentity.focusSignature(new ItemStack(focus)));
        assertEquals(Integer.MAX_VALUE, snapshot.focuses());
        ProcessingPorts.Debit debit = snapshot.prepareDebit(Integer.MAX_VALUE, 0);
        assertTrue(debit.unchanged());
        debit.apply();
        assertFalse(debit.unchanged());
        assertEquals(Integer.MAX_VALUE, debug.getStackInSlot(0).stackSize);
        assertEquals(1, debug.phantomHolder.getStackInSlot(0).stackSize);
        debug.finiteMode = true;
        PreparedProcess<ItemIdentity> finite = plan(resources(debug, fluid(100)), new Network());
        assertEquals(2, finite.schedule().batch);
        assertEquals(ProcessFailure.NONE, finite.start());
        assertEquals(2, debug.getStackInSlot(0).stackSize);
    }

    private void debugConfigurationChangesRejectStartBeforeLpOrFluidCharges() {
        for (int change = 0; change < 4; change++) {
            MTEHatchInputBusDebug debug = debug(4, true);
            MTEHatchInput fluid = fluid(100);
            Network network = new Network();
            PreparedProcess<ItemIdentity> prepared = plan(resources(debug, fluid), network);
            assertNotNull(prepared);
            if (change == 0) debug.stackSizeParseAs = 1;
            if (change == 1) debug.finiteMode = false;
            if (change == 2) debug.phantomHolder.getStackInSlot(0)
                .getTagCompound()
                .setString("identity", "changed");
            if (change == 3) debug.phantomHolder.setStackInSlot(0, null);
            assertEquals(ProcessFailure.CONDITIONS_CHANGED, prepared.start());
            assertEquals(100, network.amount);
            assertEquals(0, network.charges);
            assertEquals(100, fluid.getFluid().amount);
        }
    }

    private void debugWhitelistDoesNotAdmitUnknownInputSubclasses() {
        MTEHatchInputBus unknown = new MTEHatchInputBus("unknown", 1, 2, new String[0], new ITexture[0][][]) {};
        assertFalse(LocalStartResources.supportsItemBus(unknown));
        assertTrue(LocalStartResources.supportsItemBus(debug(1, true)));
        assertThrows(
            UnsupportedOperationException.class,
            () -> LocalStartResources.ordinary(Collections.singletonList(unknown), Collections.emptyList()));
    }

    private void debugEnergyUsesConfiguredAmperageButNormalSingleHatchStaysOneAmp() {
        MTEHatchEnergyDebug debug = new MTEHatchEnergyDebug(
            "debug_energy",
            (byte) 5,
            new String[0],
            new ITexture[0][][]);
        PublicTileFixture tile = new PublicTileFixture();
        tile.voltage = debug.maxEUInput();
        tile.bind(debug);
        MachinePower defaults = NativePowerSupply.capture(Collections.singletonList(debug), Collections.emptyList());
        assertEquals(8589934592L, defaults.voltage());
        assertEquals(2, defaults.amperage());
        assertEquals(17179869184L, defaults.eut());
        debug.setVoltageTier(5);
        debug.setAmperage(8);
        tile.voltage = debug.maxEUInput();
        MachinePower power = NativePowerSupply.capture(Collections.singletonList(debug), Collections.emptyList());
        assertEquals(8192, power.voltage());
        assertEquals(8, power.amperage());
        assertEquals(65536, power.eut());
        debug.setAmperage(16);
        assertEquals(
            16,
            NativePowerSupply.capture(Collections.singletonList(debug), Collections.emptyList())
                .amperage());
        MTEHatchEnergy normal = new MTEHatchEnergy("normal_energy", 5, new String[0], new ITexture[0][][]);
        new PublicTileFixture().bind(normal);
        assertEquals(
            1,
            NativePowerSupply.capture(Collections.singletonList(normal), Collections.emptyList())
                .amperage());
        assertThrows(
            IllegalArgumentException.class,
            () -> MachinePower.fromHatches(1, 1, new long[] { 8192, 8192 }, new long[] { 8, 8 }, true));
        debug.setAmperage(0);
        assertThrows(
            IllegalArgumentException.class,
            () -> NativePowerSupply.capture(Collections.singletonList(debug), Collections.emptyList()));
    }

    private void nativeEnergyAdderAndRuntimeDrainAcceptDebugHatch() {
        MeteoriteExcavation machine = (MeteoriteExcavation) prototype.newMetaEntity(null);
        new PublicTileFixture().bind(machine);
        MTEHatchEnergyDebug debug = new MTEHatchEnergyDebug(
            "debug_energy",
            (byte) 5,
            new String[0],
            new ITexture[0][][]);
        PublicTileFixture supply = new PublicTileFixture();
        supply.energy = 10000;
        assertTrue(machine.addEnergyInputToMachineList(supply.bind(debug), 62));
        assertEquals(1, machine.normalEnergyHatches());
        assertTrue(machine.drainEnergyInput(3072));
        assertEquals(6928, supply.energy);
    }

    private void nativeDebugMaintenanceRepairsAndResetsItsToolFlags() {
        MeteoriteExcavation machine = (MeteoriteExcavation) prototype.newMetaEntity(null);
        new PublicTileFixture().bind(machine);
        MTEHatchCreativeMaintenance debug = new MTEHatchCreativeMaintenance(
            "debug_maintenance",
            1,
            new String[0],
            new ITexture[0][][]);
        assertTrue(machine.addMaintenanceToMachineList(new PublicTileFixture().bind(debug), 62));
        assertEquals(0, machine.getRepairStatus());
        machine.checkMaintenance();
        assertEquals(6, machine.getRepairStatus());
        assertTrue(
            debug.mWrench && debug.mScrewdriver
                && debug.mSoftMallet
                && debug.mHardHammer
                && debug.mSolderingTool
                && debug.mCrowbar);
        machine.mWrench = false;
        machine.checkMaintenance();
        assertEquals(6, machine.getRepairStatus());
        assertTrue(debug.mWrench);
    }

    private void debugFluidUsesAllSlotsAndPreservesSamplesAcrossBatches() {
        MTEHatchInputDebug hatch = debugFluid();
        hatch.fluidList[0] = new FluidStack(otherFluid, 1);
        hatch.fluidList[15] = new FluidStack(drilling, 1);
        assertEquals(
            otherFluid,
            hatch.getFluid()
                .getFluid());
        MeteoriteExcavation machine = (MeteoriteExcavation) prototype.newMetaEntity(null);
        new PublicTileFixture().bind(machine);
        assertTrue(machine.addInputHatchToMachineList(hatch.getBaseMetaTileEntity(), 62));
        LocalStartResources local = resources(debug(4, true), hatch);
        assertEquals(
            Integer.MAX_VALUE,
            local.capture(ItemIdentity.focusSignature(new ItemStack(focus)))
                .drillingLiters());
        Network network = new Network();
        for (int batch = 0; batch < 2; batch++) {
            PreparedProcess<ItemIdentity> prepared = plan(local, network);
            assertNotNull(prepared);
            assertEquals(3, prepared.schedule().batch);
            assertEquals(
                56,
                prepared.prefilterYield()
                    .prefilterTotal());
            assertEquals(ProcessFailure.NONE, prepared.start());
            assertEquals(ProcessFailure.ALREADY_DECIDED, prepared.start());
        }
        assertEquals(76, network.amount);
        assertEquals(2, network.charges);
        assertEquals(1, hatch.fluidList[0].amount);
        assertEquals(1, hatch.fluidList[15].amount);
        NBTTagCompound saved = new NBTTagCompound();
        hatch.saveNBTData(saved);
        MTEHatchInputDebug restored = debugFluid();
        restored.loadNBTData(saved);
        assertEquals(
            Integer.MAX_VALUE,
            resources(debug(1, true), restored).capture(ItemIdentity.focusSignature(new ItemStack(focus)))
                .drillingLiters());
        assertEquals(1, restored.fluidList[15].amount);
    }

    private void debugFluidAndOrdinaryHatchesRespectAllocationOrder() {
        for (boolean virtualFirst : new boolean[] { false, true }) {
            MTEHatchInputDebug hatch = debugFluid();
            hatch.fluidList[15] = new FluidStack(drilling, 1);
            MTEHatchInput real = fluid(20);
            LocalStartResources local = LocalStartResources.ordinary(
                Collections.singletonList(debug(4, true)),
                virtualFirst ? Arrays.asList(hatch, real) : Arrays.asList(real, hatch));
            Network network = new Network();
            PreparedProcess<ItemIdentity> prepared = plan(local, network);
            assertNotNull(prepared);
            assertEquals(ProcessFailure.NONE, prepared.start());
            assertEquals(virtualFirst ? 20 : 0, real.getFluid().amount);
            assertEquals(1, hatch.fluidList[15].amount);
            assertEquals(88, network.amount);
            assertEquals(1, network.charges);
        }
    }

    private void debugFluidLongTotalsAndFullValueRevalidation() {
        String signature = ItemIdentity.focusSignature(new ItemStack(focus));
        MTEHatchInputDebug hatch = debugFluid();
        hatch.fluidList[0] = new FluidStack(drilling, 1);
        hatch.fluidList[15] = new FluidStack(drilling, 1);
        ProcessingPorts.Snapshot snapshot = resources(debug(4, true), hatch).capture(signature);
        assertEquals(2L * Integer.MAX_VALUE, snapshot.drillingLiters());
        ProcessingPorts.Debit debit = snapshot.prepareDebit(0, 2L * Integer.MAX_VALUE);
        assertTrue(debit.unchanged());
        // Replacing the phantom object with the same exposed value is safe; it is not physical storage.
        hatch.fluidList[15] = hatch.fluidList[15].copy();
        assertTrue(debit.unchanged());
        debit.apply();
        assertFalse(debit.unchanged());
        assertEquals(1, hatch.fluidList[0].amount);
        assertEquals(1, hatch.fluidList[15].amount);
        for (int change = 0; change < 4; change++) {
            MTEHatchInputDebug changed = debugFluid();
            changed.fluidList[15] = new FluidStack(drilling, 1);
            MTEHatchInput real = fluid(20);
            Network network = new Network();
            LocalStartResources local = LocalStartResources
                .ordinary(Collections.singletonList(debug(4, true)), Arrays.asList(real, changed));
            PreparedProcess<ItemIdentity> prepared = plan(local, network);
            assertNotNull(prepared);
            if (change == 0) changed.fluidList[15] = null;
            if (change == 1) changed.fluidList[15] = new FluidStack(otherFluid, 1);
            if (change == 2) {
                changed.fluidList[15].tag = new NBTTagCompound();
                changed.fluidList[15].tag.setString("identity", "changed");
            }
            if (change == 3) changed.fluidList[0] = new FluidStack(drilling, 1);
            assertEquals(ProcessFailure.CONDITIONS_CHANGED, prepared.start());
            assertEquals(100, network.amount);
            assertEquals(0, network.charges);
            assertEquals(20, real.getFluid().amount);
        }
    }

    private void debugFluidWhitelistAndNonDrillingFluidsStayIsolated() {
        MTEHatchInputDebug hatch = debugFluid();
        assertTrue(LocalStartResources.supportsFluidHatch(hatch));
        assertEquals(
            0,
            resources(debug(1, true), hatch).capture(ItemIdentity.focusSignature(new ItemStack(focus)))
                .drillingLiters());
        hatch.fluidList[0] = new FluidStack(otherFluid, 1);
        assertEquals(
            0,
            resources(debug(1, true), hatch).capture(ItemIdentity.focusSignature(new ItemStack(focus)))
                .drillingLiters());
        MTEHatchInput unknown = new MTEHatchInput("unknown_fluid", 1, new String[0], new ITexture[0][][]) {};
        assertFalse(LocalStartResources.supportsFluidHatch(unknown));
        assertThrows(
            UnsupportedOperationException.class,
            () -> LocalStartResources.ordinary(Collections.emptyList(), Collections.singletonList(unknown)));
    }
}
