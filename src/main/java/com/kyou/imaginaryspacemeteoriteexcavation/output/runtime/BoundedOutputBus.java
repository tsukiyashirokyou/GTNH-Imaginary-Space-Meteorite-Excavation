package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import net.minecraft.item.ItemStack;

import gregtech.api.enums.OutputBusType;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.interfaces.IOutputBusTransaction;
import gregtech.api.interfaces.IOutputTransaction;
import gregtech.api.util.GTUtility;

/** Preserve native admission/filtering; the extra bound guards numeric headroom, not ME physical space. */
final class BoundedOutputBus implements IOutputBus {

    private final IOutputBus bus;
    private final LongSupplier headroom;
    private final BooleanSupplier safe;

    BoundedOutputBus(IOutputBus bus, LongSupplier headroom, BooleanSupplier safe) {
        this.bus = bus;
        this.headroom = headroom;
        this.safe = safe;
    }

    @Override
    public boolean isFiltered() {
        return bus.isFiltered();
    }

    @Override
    public boolean isFilteredToItem(GTUtility.ItemId id) {
        return bus.isFilteredToItem(id);
    }

    @Override
    public OutputBusType getBusType() {
        return bus.getBusType();
    }

    @Override
    public boolean storePartial(ItemStack stack, boolean simulate) {
        throw new UnsupportedOperationException("Use same-call transactions");
    }

    @Override
    public IOutputBusTransaction createTransaction() {
        if (!safe.getAsBoolean()) throw new OutputUnavailableException("Unsafe output configuration");
        return new Transaction(bus.createTransaction());
    }

    private final class Transaction
        implements IOutputBusTransaction, IOutputTransaction.IRecipeCheckAware, IOutputTransaction.IProtectOutputAware {

        private final IOutputBusTransaction delegate;
        private final long capacity;
        private long used;
        private boolean active = true;

        private Transaction(IOutputBusTransaction delegate) {
            this.delegate = delegate;
            capacity = Math.max(0, headroom.getAsLong());
        }

        @Override
        public IOutputBus getBus() {
            return BoundedOutputBus.this;
        }

        @Override
        public void setRecipeCheck(boolean check) {
            if (delegate instanceof IOutputTransaction.IRecipeCheckAware) {
                ((IOutputTransaction.IRecipeCheckAware) delegate).setRecipeCheck(check);
            }
        }

        @Override
        public void setProtectOutput(boolean protect) {
            if (delegate instanceof IOutputTransaction.IProtectOutputAware) {
                ((IOutputTransaction.IProtectOutputAware) delegate).setProtectOutput(protect);
            }
        }

        @Override
        public boolean needsTotalParallelData() {
            return delegate.needsTotalParallelData();
        }

        @Override
        public boolean hasAvailableSpace() {
            return active && used < capacity && delegate.hasAvailableSpace();
        }

        @Override
        public boolean storePartial(GTUtility.ItemId id, ItemStack stack, long totalPerParallel, long perParallel) {
            if (!active) throw new IllegalStateException("Spent output transaction");
            int offer = (int) Math.min(stack.stackSize, capacity - used);
            if (offer <= 0) return false;
            ItemStack part = stack.copy();
            part.stackSize = offer;
            delegate.storePartial(id, part, totalPerParallel, perParallel);
            int accepted = offer - part.stackSize;
            if (accepted < 0 || accepted > offer) throw new IllegalStateException("Invalid native receipt");
            used += accepted;
            stack.stackSize -= accepted;
            return accepted > 0;
        }

        @Override
        public void complete(GTUtility.ItemId id) {
            delegate.complete(id);
        }

        @Override
        public void commit() {
            if (!active || !safe.getAsBoolean() || headroom.getAsLong() < used) {
                throw new IllegalStateException("Output destination changed before commit");
            }
            delegate.commit();
            active = false;
        }
    }
}
