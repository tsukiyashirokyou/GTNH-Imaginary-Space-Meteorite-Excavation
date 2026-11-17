package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;

import gregtech.api.enums.ItemList;
import gregtech.api.metatileentity.implementations.MTEHatchInput;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.metatileentity.implementations.MTEHatchInputBusDebug;
import gregtech.api.metatileentity.implementations.MTEHatchInputDebug;

/** Local storage plus native debug offers; never drains remote ME/dual or unknown multi-fluid inventory. */
public final class LocalStartResources implements ProcessingPorts.Resources {

    private final List<Supplier<ItemStack>> itemCells;
    private final BitSet virtualItems;
    private final List<Supplier<FluidStack>> fluidCells;
    private final BitSet virtualFluids;
    private final Fluid drilling;
    private final Runnable markDirty;

    private LocalStartResources(List<Supplier<ItemStack>> itemCells, BitSet virtualItems,
        List<Supplier<FluidStack>> fluidCells, BitSet virtualFluids, Fluid drilling, Runnable markDirty) {
        this.itemCells = itemCells;
        this.virtualItems = virtualItems;
        this.fluidCells = fluidCells;
        this.virtualFluids = virtualFluids;
        this.drilling = drilling;
        this.markDirty = markDirty;
    }

    public static boolean supportsItemBus(MTEHatchInputBus bus) {
        return bus != null
            && (bus.getClass() == MTEHatchInputBus.class || bus.getClass() == MTEHatchInputBusDebug.class);
    }

    public static boolean supportsFluidHatch(MTEHatchInput hatch) {
        return hatch != null
            && (hatch.getClass() == MTEHatchInput.class || hatch.getClass() == MTEHatchInputDebug.class);
    }

    /** Debug exposes phantom slots 0..15 and moves its physical circuit to virtual slot 16. */
    public static int recipeCircuitSlot(MTEHatchInputBus bus) {
        return bus.getClass() == MTEHatchInputBusDebug.class ? bus.getSizeInventory() - 1 : bus.getCircuitSlot();
    }

    public static LocalStartResources ordinary(List<MTEHatchInputBus> busses, List<MTEHatchInput> hatches) {
        List<MTEHatchInputBus> localBusses = new ArrayList<>(busses);
        List<MTEHatchInput> localHatches = new ArrayList<>(hatches);
        List<Supplier<ItemStack>> items = new ArrayList<>();
        BitSet virtual = new BitSet();
        List<Supplier<FluidStack>> fluids = new ArrayList<>();
        BitSet virtualFluid = new BitSet();
        for (MTEHatchInputBus bus : localBusses) {
            if (!supportsItemBus(bus)) throw new UnsupportedOperationException(
                "Unsupported item input bus: " + bus.getClass()
                    .getName());
            int circuit = recipeCircuitSlot(bus);
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                if (slot == circuit) continue;
                final int index = slot;
                if (bus.getClass() == MTEHatchInputBusDebug.class) virtual.set(items.size());
                items.add(() -> bus.getStackInSlot(index));
            }
        }
        for (MTEHatchInput hatch : localHatches) {
            if (!supportsFluidHatch(hatch)) throw new UnsupportedOperationException(
                "Unsupported fluid input hatch: " + hatch.getClass()
                    .getName());
            if (hatch.getClass() == MTEHatchInputDebug.class) {
                MTEHatchInputDebug debug = (MTEHatchInputDebug) hatch;
                // Native getStoredFluids exposes every configured debug slot, not only getFluid()'s first one.
                for (int slot = 0; slot < debug.getFluidList().length; slot++) {
                    final int index = slot;
                    virtualFluid.set(fluids.size());
                    fluids.add(() -> {
                        FluidStack sample = debug.getFluidList()[index];
                        if (sample == null) return null;
                        FluidStack offer = sample.copy();
                        offer.amount = Integer.MAX_VALUE;
                        return offer;
                    });
                }
            } else {
                fluids.add(hatch::getFluid);
            }
        }
        return new LocalStartResources(items, virtual, fluids, virtualFluid, ItemList.sDrillingFluid, () -> {
            for (MTEHatchInputBus bus : localBusses) bus.markDirty();
            for (MTEHatchInput hatch : localHatches) hatch.markDirty();
        });
    }

    /** Explicit local-array bridge, also used to exercise the actual ItemStack/FluidStack writes headlessly. */
    public static LocalStartResources arrays(ItemStack[] items, FluidStack[] fluids, Fluid drilling, Runnable dirty) {
        List<Supplier<ItemStack>> itemCells = new ArrayList<>();
        List<Supplier<FluidStack>> fluidCells = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            final int index = i;
            itemCells.add(() -> items[index]);
        }
        for (int i = 0; i < fluids.length; i++) {
            final int index = i;
            fluidCells.add(() -> fluids[index]);
        }
        return new LocalStartResources(itemCells, new BitSet(), fluidCells, new BitSet(), drilling, dirty);
    }

    @Override
    public ProcessingPorts.Snapshot capture(String focus) {
        if (drilling == null) throw new UnsupportedOperationException("Native drilling fluid unavailable");
        return new LocalSnapshot(focus);
    }

    private final class LocalSnapshot implements ProcessingPorts.Snapshot {

        private final ItemStack[] itemRefs = new ItemStack[itemCells.size()];
        private final ItemStack[] itemCopies = new ItemStack[itemCells.size()];
        private final FluidStack[] fluidRefs = new FluidStack[fluidCells.size()];
        private final FluidStack[] fluidCopies = new FluidStack[fluidCells.size()];
        private final boolean[] focusCells = new boolean[itemCells.size()];
        private final boolean[] drillingCells = new boolean[fluidCells.size()];
        private long focuses;
        private long liters;

        private LocalSnapshot(String focus) {
            for (int i = 0; i < itemRefs.length; i++) {
                ItemStack stack = itemCells.get(i)
                    .get();
                itemRefs[i] = stack;
                itemCopies[i] = stack == null ? null : stack.copy();
                if (stack == null) continue;
                if (stack.stackSize < 0) throw new UnsupportedOperationException("Negative input stack");
                focusCells[i] = stack.stackSize > 0 && focus.equals(ItemIdentity.focusSignature(stack));
                if (focusCells[i]) focuses = Math.addExact(focuses, stack.stackSize);
                for (int previous = 0; previous < i; previous++) {
                    if (stack == itemRefs[previous]) throw new UnsupportedOperationException("Aliased item storage");
                }
            }
            FluidStack expected = new FluidStack(drilling, 1);
            for (int i = 0; i < fluidRefs.length; i++) {
                FluidStack stack = fluidCells.get(i)
                    .get();
                fluidRefs[i] = stack;
                fluidCopies[i] = stack == null ? null : stack.copy();
                if (stack == null) continue;
                if (stack.amount < 0) throw new UnsupportedOperationException("Negative input fluid");
                drillingCells[i] = stack.isFluidEqual(expected);
                if (drillingCells[i]) liters = Math.addExact(liters, stack.amount);
                for (int previous = 0; previous < i; previous++) {
                    if (stack == fluidRefs[previous]) throw new UnsupportedOperationException("Aliased fluid storage");
                }
            }
        }

        @Override
        public long focuses() {
            return focuses;
        }

        @Override
        public long drillingLiters() {
            return liters;
        }

        @Override
        public ProcessingPorts.Debit prepareDebit(int focusCount, long fluidAmount) {
            if (focusCount < 0 || fluidAmount < 0 || focusCount > focuses || fluidAmount > liters) {
                throw new IllegalArgumentException("Local debit exceeds snapshot");
            }
            int[] itemDebits = new int[itemRefs.length];
            int[] fluidDebits = new int[fluidRefs.length];
            long remainingItems = focusCount;
            long remainingFluid = fluidAmount;
            for (int i = 0; i < itemRefs.length; i++) {
                if (!focusCells[i]) continue;
                itemDebits[i] = (int) Math.min(remainingItems, itemCopies[i].stackSize);
                remainingItems -= itemDebits[i];
            }
            for (int i = 0; i < fluidRefs.length; i++) {
                if (!drillingCells[i]) continue;
                fluidDebits[i] = (int) Math.min(remainingFluid, fluidCopies[i].amount);
                remainingFluid -= fluidDebits[i];
            }
            return new ProcessingPorts.Debit() {

                private boolean applied;

                @Override
                public boolean unchanged() {
                    if (applied) return false;
                    for (int i = 0; i < itemRefs.length; i++) {
                        ItemStack current = itemCells.get(i)
                            .get();
                        // Debug getters return fresh, non-consumable offers. Revalidate their full value instead.
                        if ((!virtualItems.get(i) && current != itemRefs[i])
                            || !ItemStack.areItemStacksEqual(current, itemCopies[i])) return false;
                    }
                    for (int i = 0; i < fluidRefs.length; i++) {
                        FluidStack current = fluidCells.get(i)
                            .get();
                        if (!virtualFluids.get(i) && current != fluidRefs[i]) return false;
                        if (current == null ? fluidCopies[i] != null : !current.isFluidStackIdentical(fluidCopies[i]))
                            return false;
                    }
                    return true;
                }

                @Override
                public void apply() {
                    if (applied) throw new IllegalStateException("Local debit already applied");
                    applied = true;
                    // No callback or possibly-partial native drain between LP deduction and these local field writes.
                    for (int i = 0; i < itemRefs.length; i++) {
                        if (!virtualItems.get(i) && itemDebits[i] > 0) itemRefs[i].stackSize -= itemDebits[i];
                    }
                    for (int i = 0; i < fluidRefs.length; i++) {
                        if (!virtualFluids.get(i) && fluidDebits[i] > 0) fluidRefs[i].amount -= fluidDebits[i];
                    }
                    markDirty.run();
                }
            };
        }
    }
}
