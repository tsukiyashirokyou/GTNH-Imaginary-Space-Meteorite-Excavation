package com.kyou.imaginaryspacemeteoriteexcavation.processing.runtime;

import java.util.ArrayList;
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

/** Snapshots ordinary local storage; never drains a remote ME/dual/multi-fluid inventory. */
public final class LocalStartResources implements ProcessingPorts.Resources {

    private final List<Supplier<ItemStack>> itemCells;
    private final List<Supplier<FluidStack>> fluidCells;
    private final Fluid drilling;
    private final Runnable markDirty;

    private LocalStartResources(List<Supplier<ItemStack>> itemCells, List<Supplier<FluidStack>> fluidCells,
        Fluid drilling, Runnable markDirty) {
        this.itemCells = itemCells;
        this.fluidCells = fluidCells;
        this.drilling = drilling;
        this.markDirty = markDirty;
    }

    public static LocalStartResources ordinary(List<MTEHatchInputBus> busses, List<MTEHatchInput> hatches) {
        List<MTEHatchInputBus> localBusses = new ArrayList<>(busses);
        List<MTEHatchInput> localHatches = new ArrayList<>(hatches);
        List<Supplier<ItemStack>> items = new ArrayList<>();
        List<Supplier<FluidStack>> fluids = new ArrayList<>();
        for (MTEHatchInputBus bus : localBusses) {
            if (bus.getClass() != MTEHatchInputBus.class)
                throw new UnsupportedOperationException("Non-ordinary item input");
            for (int slot = 0; slot < bus.getSizeInventory(); slot++) {
                if (slot == bus.getCircuitSlot()) continue;
                final int index = slot;
                items.add(() -> bus.getStackInSlot(index));
            }
        }
        for (MTEHatchInput hatch : localHatches) {
            if (hatch.getClass() != MTEHatchInput.class)
                throw new UnsupportedOperationException("Non-ordinary fluid input");
            fluids.add(hatch::getFluid);
        }
        return new LocalStartResources(items, fluids, ItemList.sDrillingFluid, () -> {
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
        return new LocalStartResources(itemCells, fluidCells, drilling, dirty);
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
                        if (itemCells.get(i)
                            .get() != itemRefs[i] || !ItemStack.areItemStacksEqual(itemRefs[i], itemCopies[i]))
                            return false;
                    }
                    for (int i = 0; i < fluidRefs.length; i++) {
                        if (fluidCells.get(i)
                            .get() != fluidRefs[i]) return false;
                        if (fluidRefs[i] != null && !fluidRefs[i].isFluidStackIdentical(fluidCopies[i])) return false;
                    }
                    return true;
                }

                @Override
                public void apply() {
                    if (applied) throw new IllegalStateException("Local debit already applied");
                    applied = true;
                    // No callback or possibly-partial native drain between LP deduction and these local field writes.
                    for (int i = 0; i < itemRefs.length; i++) {
                        if (itemDebits[i] > 0) itemRefs[i].stackSize -= itemDebits[i];
                    }
                    for (int i = 0; i < fluidRefs.length; i++) {
                        if (fluidDebits[i] > 0) fluidRefs[i].amount -= fluidDebits[i];
                    }
                    markDirty.run();
                }
            };
        }
    }
}
