package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.BeforeClass;
import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.PublicTileFixture;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.OutputLimits;
import com.kyou.imaginaryspacemeteoriteexcavation.output.PendingOutputs;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.HeadlessForgeFixture;

import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.interfaces.ITexture;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

/** Executes the exact target ItemEjectionHelper; destinations are explicit fault-injection test doubles. */
public final class NativeOutputTest {

    private static Item ore, filler;

    @BeforeClass
    public static void registerTestFixtures() {
        if (!HeadlessForgeFixture.inside()) return;
        Block.registerBlocks();
        Item.registerItems();
        ore = new Item();
        filler = new Item();
        Item.itemRegistry.addObject(30001, "minecraft:isme_test_output_ore", ore);
        Item.itemRegistry.addObject(30002, "minecraft:isme_test_output_filler", filler);
    }

    private boolean delegated(String method) throws Exception {
        if (HeadlessForgeFixture.inside()) return false;
        HeadlessForgeFixture.run(getClass().getName(), method);
        return true;
    }

    private static ItemIdentity id(Item item) {
        return new ItemIdentity(new ItemStack(item));
    }

    private static Map<ItemIdentity, Long> pair(long a, long b) {
        Map<ItemIdentity, Long> items = new LinkedHashMap<>();
        items.put(id(ore), a);
        items.put(id(filler), b);
        return items;
    }

    private static IOutputBus bounded(FakeBus bus) {
        return new BoundedOutputBus(bus, () -> bus.capacity - bus.stored, () -> bus.safe);
    }

    private static MTEHatchOutputBusME nativeMe() {
        MTEHatchOutputBusME me = new MTEHatchOutputBusME("test_me", 4, new String[0], new ITexture[0][][]);
        me.getProvider()
            .onPostTick(new PublicTileFixture().bind(me), 1);
        return me;
    }

    private static NativeItemOutput port(PendingItemOutput pending, MTEHatchOutputBus... buses) {
        return new NativeItemOutput(pending, () -> Arrays.asList(buses), () -> 1, () -> true);
    }

    @Test
    public void targetOrdinaryBusTransactionPreservesInventoryUntilCommit() throws Exception {
        if (delegated("targetOrdinaryBusTransactionPreservesInventoryUntilCommit")) return;
        MTEHatchOutputBus bus = new MTEHatchOutputBus("test_output", 1, 2, new String[0], new ITexture[0][][]);
        bus.mInventory[0] = new ItemStack(ore, 60);
        Map<ItemIdentity, Long> items = pair(64, 5);
        Map<ItemIdentity, Long> simulated = NativeOutputTransactions.execute(
            items,
            Arrays.asList(new BoundedOutputBus(bus, () -> Long.MAX_VALUE, () -> true)),
            true,
            () -> 1,
            () -> true);
        assertEquals(60, bus.mInventory[0].stackSize);
        assertNull(bus.mInventory[1]);
        assertFalse(simulated.equals(items));
        Map<ItemIdentity, Long> receipt = NativeOutputTransactions.execute(
            items,
            Arrays.asList(new BoundedOutputBus(bus, () -> Long.MAX_VALUE, () -> true)),
            false,
            () -> 2,
            () -> true);
        long before = 60;
        long after = 0;
        for (ItemStack stack : bus.mInventory) if (stack != null) after += stack.stackSize;
        assertEquals(
            receipt.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum(),
            after - before);
    }

    @Test
    public void nativeLockedBusAndFullNbtIdentitiesStaySeparate() throws Exception {
        if (delegated("nativeLockedBusAndFullNbtIdentitiesStaySeparate")) return;
        MTEHatchOutputBus locked = new MTEHatchOutputBus("test_locked", 1, 1, new String[0], new ITexture[0][][]);
        MTEHatchOutputBus ordinary = new MTEHatchOutputBus("test_ordinary", 1, 1, new String[0], new ITexture[0][][]);
        ItemStack named = new ItemStack(ore, 1, 3);
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("variant", "one");
        named.setTagCompound(tag);
        ItemStack other = named.copy();
        other.getTagCompound()
            .setString("variant", "two");
        locked.setLockedItem(named);
        Map<ItemIdentity, Long> items = new LinkedHashMap<>();
        items.put(new ItemIdentity(named), 64L);
        items.put(new ItemIdentity(other), 12L);
        assertEquals(
            items,
            NativeOutputTransactions.execute(
                items,
                Arrays.asList(
                    new BoundedOutputBus(locked, () -> Long.MAX_VALUE, () -> true),
                    new BoundedOutputBus(ordinary, () -> Long.MAX_VALUE, () -> true)),
                false,
                () -> 1,
                () -> true));
        assertEquals(
            "one",
            locked.mInventory[0].getTagCompound()
                .getString("variant"));
        assertEquals(
            "two",
            ordinary.mInventory[0].getTagCompound()
                .getString("variant"));
        assertEquals(64, locked.mInventory[0].stackSize);
        assertEquals(12, ordinary.mInventory[0].stackSize);
        assertEquals(
            "one",
            named.getTagCompound()
                .getString("variant"));
    }

    @Test
    public void ordinaryJointCapacityIsMonotoneForNonlinearBatchCandidates() throws Exception {
        if (delegated("ordinaryJointCapacityIsMonotoneForNonlinearBatchCandidates")) return;
        MTEHatchOutputBus locked = new MTEHatchOutputBus("test_locked", 1, 1, new String[0], new ITexture[0][][]);
        MTEHatchOutputBus ordinary = new MTEHatchOutputBus("test_ordinary", 1, 2, new String[0], new ITexture[0][][]);
        locked.setLockedItem(new ItemStack(ore));
        boolean failed = false;
        for (int batch = 1; batch <= 128; batch++) {
            Map<ItemIdentity, Long> items = pair(16L * batch / 10, 27L * batch / 10);
            boolean fits = NativeOutputTransactions
                .execute(
                    items,
                    Arrays.asList(
                        new BoundedOutputBus(locked, () -> Long.MAX_VALUE, () -> true),
                        new BoundedOutputBus(ordinary, () -> Long.MAX_VALUE, () -> true)),
                    true,
                    () -> 1,
                    () -> true)
                .equals(items);
            if (!fits) failed = true;
            if (failed) assertFalse("Capacity became feasible again at B=" + batch, fits);
        }
        assertTrue(failed);
        assertNull(ordinary.mInventory[0]);
        assertNull(locked.mInventory[0]);
    }

    @Test
    public void productionGateRejectsNoCapacityAndTemporaryGuardsCanRecover() throws Exception {
        if (delegated("productionGateRejectsNoCapacityAndTemporaryGuardsCanRecover")) return;
        PendingItemOutput pending = new PendingItemOutput();
        NativeItemOutput port = new NativeItemOutput(pending, Collections::emptyList, () -> 1, () -> false);
        Map<ItemIdentity, Long> items = Collections.singletonMap(id(ore), 10L);
        assertTrue(port.canAccept(Collections.emptyMap()));
        assertFalse(port.canAccept(items));
        pending.complete(items);
        assertEquals(0, pending.flush(1, port));
        assertEquals("", pending.fault());
        assertEquals(items, pending.snapshot());
        assertEquals(10, pending.flush(2, offered -> offered));
        assertFalse(pending.blocksStart());
    }

    @Test
    public void jointSimulationDoesNotDoubleCountSpaceOrCommit() throws Exception {
        if (delegated("jointSimulationDoesNotDoubleCountSpaceOrCommit")) return;
        FakeBus bus = new FakeBus(64, false);
        Map<ItemIdentity, Long> receipt = NativeOutputTransactions
            .execute(pair(64, 64), Arrays.asList(bounded(bus)), true, () -> 1, () -> true);
        assertEquals(
            64,
            receipt.values()
                .stream()
                .mapToLong(Long::longValue)
                .sum());
        assertEquals(0, bus.stored);
        assertEquals(0, bus.commits);
        assertTrue(bus.recipeCheck);
        assertTrue(bus.protectedOutput);
    }

    @Test
    public void freshCompletionTransactionSeesCapacityCompetitionAndActualRemainder() throws Exception {
        if (delegated("freshCompletionTransactionSeesCapacityCompetitionAndActualRemainder")) return;
        FakeBus bus = new FakeBus(100, false);
        Map<ItemIdentity, Long> items = Collections.singletonMap(id(ore), 100L);
        assertEquals(
            items,
            NativeOutputTransactions.execute(items, Arrays.asList(bounded(bus)), true, () -> 1, () -> true));
        bus.stored = 75;
        PendingItemOutput pending = new PendingItemOutput();
        pending.complete(items);
        assertEquals(
            25,
            pending.flush(
                2,
                offered -> NativeOutputTransactions
                    .execute(offered, Arrays.asList(bounded(bus)), false, () -> 2, () -> true)));
        assertEquals(
            Long.valueOf(75),
            pending.snapshot()
                .get(id(ore)));
        assertEquals(100, bus.stored);
        assertEquals(1, bus.commits);
        assertEquals(
            0,
            pending.flush(
                3,
                offered -> NativeOutputTransactions
                    .execute(offered, Arrays.asList(bounded(bus)), false, () -> 3, () -> true)));
        bus.stored = 0;
        assertEquals(
            75,
            pending.flush(
                4,
                offered -> NativeOutputTransactions
                    .execute(offered, Arrays.asList(bounded(bus)), false, () -> 4, () -> true)));
        assertFalse(pending.blocksStart());
        assertEquals(75, bus.stored);
    }

    private static FakeBus modeledMe() {
        FakeBus me = new FakeBus(1600, true);
        me.nativeAdmission = true;
        return me;
    }

    private static IOutputBus nativePolicy(FakeBus me) {
        return new BoundedOutputBus(me, () -> Long.MAX_VALUE - me.stored, () -> me.safe && me.stored >= 0);
    }

    @Test
    public void modeledMeAcceptsLongBatchWithOneFreeItemThenBlocksNextPrecheck() throws Exception {
        if (delegated("modeledMeAcceptsLongBatchWithOneFreeItemThenBlocksNextPrecheck")) return;
        FakeBus me = modeledMe();
        me.stored = 1599;
        long count = 2L * Integer.MAX_VALUE + 51;
        Map<ItemIdentity, Long> items = pair(count, 40);
        assertEquals(
            items,
            NativeOutputTransactions.execute(items, Arrays.asList(nativePolicy(me)), true, () -> 1, () -> true));
        assertEquals(1599, me.stored);
        PendingItemOutput pending = new PendingItemOutput();
        pending.complete(items);
        assertEquals(
            count + 40,
            pending.flush(
                1,
                offered -> NativeOutputTransactions
                    .execute(offered, Arrays.asList(nativePolicy(me)), false, () -> 1, () -> true)));
        assertFalse(pending.blocksStart());
        assertEquals(1599 + count + 40, me.stored);
        assertEquals(Integer.MAX_VALUE, me.maxOffer);
        assertEquals(1, me.commits);
        assertTrue(
            NativeOutputTransactions
                .execute(
                    Collections.singletonMap(id(ore), 1L),
                    Arrays.asList(nativePolicy(me)),
                    true,
                    () -> 1,
                    () -> true)
                .isEmpty());
    }

    @Test
    public void nativeMeCheckModeUsesJointPhysicalCapacityWithoutCommittingSimulation() throws Exception {
        if (delegated("nativeMeCheckModeUsesJointPhysicalCapacityWithoutCommittingSimulation")) return;
        // This exact target transaction branch does not initialize AEStack or require a loaded AE registry.
        MTEHatchOutputBusME me = nativeMe();
        me.getProvider()
            .setCheckMode(true);
        NativeItemOutput port = port(new PendingItemOutput(), me);
        assertFalse(port.canAccept(pair(1600, 1)));
        assertTrue(port.canAccept(pair(1500, 100)));
        assertFalse(port.canAccept(Collections.singletonMap(id(ore), OutputLimits.TOTAL)));
        assertEquals(
            0,
            me.getProvider()
                .getCachedAmount());
    }

    @Test
    public void fullAndOverfullModeledMeDoNotInvalidateOtherOutputBuses() throws Exception {
        if (delegated("fullAndOverfullModeledMeDoNotInvalidateOtherOutputBuses")) return;
        for (long cached : new long[] { 1600, 50000 }) {
            FakeBus me = modeledMe();
            me.stored = cached;
            MTEHatchOutputBus ordinary = new MTEHatchOutputBus(
                "test_fallback",
                1,
                1,
                new String[0],
                new ITexture[0][][]);
            Map<ItemIdentity, Long> items = Collections.singletonMap(id(ore), 64L);
            assertEquals(
                items,
                NativeOutputTransactions
                    .execute(items, Arrays.asList(nativePolicy(me), ordinary), true, () -> 1, () -> true));
            assertEquals(
                items,
                NativeOutputTransactions
                    .execute(items, Arrays.asList(nativePolicy(me), ordinary), false, () -> 1, () -> true));
            assertEquals(64, ordinary.mInventory[0].stackSize);
            assertEquals(cached, me.stored);
        }
    }

    @Test
    public void modeledMeBlockedPendingResumesOnlyItsRemainingLongQuantity() throws Exception {
        if (delegated("modeledMeBlockedPendingResumesOnlyItsRemainingLongQuantity")) return;
        FakeBus me = modeledMe();
        Map<ItemIdentity, Long> items = Collections.singletonMap(id(ore), OutputLimits.TOTAL);
        assertEquals(
            items,
            NativeOutputTransactions.execute(items, Arrays.asList(nativePolicy(me)), true, () -> 1, () -> true));
        me.stores = 0;
        me.stored = 50000;
        PendingItemOutput pending = new PendingItemOutput();
        pending.complete(items);
        PendingOutputs.Sink<ItemIdentity> sink = offered -> NativeOutputTransactions
            .execute(offered, Arrays.asList(nativePolicy(me)), false, () -> me.tick, () -> true);
        assertEquals(0, pending.flush(1, sink));
        assertEquals("", pending.fault());
        assertEquals(items, pending.snapshot());
        me.stored = 0; // Controlled recovery, not a real ME network drain.
        me.tick = 2;
        assertEquals(OutputLimits.TOTAL, pending.flush(2, sink));
        assertFalse(pending.blocksStart());
        assertEquals(OutputLimits.TOTAL, me.stored);
        assertEquals(513, me.stores); // ceil(2^40 / Integer.MAX_VALUE), no per-item/64-stack expansion.
    }

    @Test
    public void modeledSameTickContinuationStillCannotOverflowTheLongCounter() throws Exception {
        if (delegated("modeledSameTickContinuationStillCannotOverflowTheLongCounter")) return;
        FakeBus me = modeledMe();
        Map<ItemIdentity, Long> one = Collections.singletonMap(id(ore), 1L);
        assertEquals(
            one,
            NativeOutputTransactions.execute(one, Arrays.asList(nativePolicy(me)), false, () -> 1, () -> true));
        me.stored = Long.MAX_VALUE - 10;
        assertEquals(
            Collections.singletonMap(id(ore), 10L),
            NativeOutputTransactions.execute(
                Collections.singletonMap(id(ore), 50L),
                Arrays.asList(nativePolicy(me)),
                false,
                () -> 1,
                () -> true));
        assertEquals(Long.MAX_VALUE, me.stored);
        assertTrue(
            NativeOutputTransactions.execute(one, Arrays.asList(nativePolicy(me)), false, () -> 1, () -> true)
                .isEmpty());
        assertEquals(Long.MAX_VALUE, me.stored);
    }

    @Test
    public void longQuantityUsesNativeIntFragmentsAndAccountsForMultipleBuses() throws Exception {
        if (delegated("longQuantityUsesNativeIntFragmentsAndAccountsForMultipleBuses")) return;
        long count = (long) Integer.MAX_VALUE * 2 + 51;
        FakeBus first = new FakeBus(Integer.MAX_VALUE, true);
        FakeBus second = new FakeBus(count, true);
        Map<ItemIdentity, Long> items = Collections.singletonMap(id(ore), count);
        assertEquals(
            items,
            NativeOutputTransactions
                .execute(items, Arrays.asList(bounded(first), bounded(second)), false, () -> 1, () -> true));
        assertEquals(count, first.stored + second.stored);
        assertTrue(first.maxOffer <= Integer.MAX_VALUE);
        assertTrue(second.maxOffer <= Integer.MAX_VALUE);
        assertEquals(1, first.commits);
        assertEquals(1, second.commits);
    }

    @Test
    public void tickChangeOrUnsafeEndpointDiscardsSimulationWithoutWrites() throws Exception {
        if (delegated("tickChangeOrUnsafeEndpointDiscardsSimulationWithoutWrites")) return;
        FakeBus bus = new FakeBus(100, false);
        AtomicLong clock = new AtomicLong(1);
        bus.onStore = () -> clock.incrementAndGet();
        try {
            NativeOutputTransactions.execute(
                Collections.singletonMap(id(ore), 5L),
                Arrays.asList(bounded(bus)),
                false,
                clock::get,
                () -> true);
            fail("Cross-tick copy cannot be committed");
        } catch (IllegalStateException expected) {}
        assertEquals(0, bus.stored);
        assertEquals(0, bus.commits);
        bus.onStore = () -> bus.safe = false;
        try {
            NativeOutputTransactions.execute(
                Collections.singletonMap(id(ore), 5L),
                Arrays.asList(bounded(bus)),
                false,
                () -> 3,
                () -> bus.safe);
            fail("Unsafe configuration cannot commit");
        } catch (IllegalStateException expected) {}
        assertEquals(0, bus.stored);
    }

    @Test
    public void actualStackDeltaIsUsedEvenIfDelegateBooleanIsFalse() throws Exception {
        if (delegated("actualStackDeltaIsUsedEvenIfDelegateBooleanIsFalse")) return;
        FakeBus bus = new FakeBus(5, false);
        bus.falseReturn = true; // Matches the target compressed transaction's boolean behavior.
        Map<ItemIdentity, Long> receipt = NativeOutputTransactions
            .execute(Collections.singletonMap(id(ore), 9L), Arrays.asList(bounded(bus)), false, () -> 1, () -> true);
        assertEquals(Long.valueOf(5), receipt.get(id(ore)));
        assertEquals(5, bus.stored);
    }

    /** Models a shared quantity limit and ME's allow-any-input branch, not an actual AE network. */
    private static final class FakeBus implements IOutputBus {

        private final long capacity;
        private final boolean permissive;
        private long stored;
        private int commits;
        private boolean safe = true;
        private boolean recipeCheck;
        private boolean protectedOutput;
        private boolean falseReturn;
        private int maxOffer;
        private boolean nativeAdmission, checkMode;
        private long tick = 1, lastInputTick = Long.MIN_VALUE;
        private int stores;
        private Runnable onStore = () -> {};

        private FakeBus(long capacity, boolean permissive) {
            this.capacity = capacity;
            this.permissive = permissive;
        }

        public boolean isFiltered() {
            return false;
        }

        public boolean isFilteredToItem(GTUtility.ItemId id) {
            return true;
        }

        public OutputBusType getBusType() {
            return permissive ? OutputBusType.MEUnfiltered : OutputBusType.StandardUnfiltered;
        }

        public boolean storePartial(ItemStack stack, boolean simulate) {
            throw new AssertionError("Native transaction required");
        }

        public IOutputBusTransaction createTransaction() {
            return new Transaction();
        }

        private final class Transaction implements IOutputBusTransaction, IOutputTransaction.IRecipeCheckAware,
            IOutputTransaction.IProtectOutputAware {

            private long accepted;
            private final long initialSpace = capacity - stored;

            public IOutputBus getBus() {
                return FakeBus.this;
            }

            public boolean hasAvailableSpace() {
                if (nativeAdmission) {
                    boolean allowAny = (!recipeCheck && tick == lastInputTick) || (!checkMode && initialSpace > 0);
                    return allowAny || accepted < initialSpace;
                }
                return permissive || stored + accepted < capacity;
            }

            public void setRecipeCheck(boolean check) {
                recipeCheck = check;
            }

            public void setProtectOutput(boolean protect) {
                protectedOutput = protect;
            }

            public boolean storePartial(GTUtility.ItemId id, ItemStack stack, long total, long per) {
                maxOffer = Math.max(maxOffer, stack.stackSize);
                stores++;
                int amount;
                if (nativeAdmission) {
                    amount = !hasAvailableSpace() ? 0
                        : recipeCheck && checkMode
                            ? (int) Math.max(0, Math.min(stack.stackSize, initialSpace - accepted))
                            : stack.stackSize;
                } else {
                    amount = permissive ? stack.stackSize
                        : (int) Math.min(stack.stackSize, capacity - stored - accepted);
                }
                accepted += amount;
                stack.stackSize -= amount;
                onStore.run();
                return !falseReturn && amount > 0;
            }

            public void complete(GTUtility.ItemId id) {}

            public void commit() {
                stored += accepted;
                if (nativeAdmission && accepted > 0) lastInputTick = tick;
                commits++;
            }
        }
    }
}
