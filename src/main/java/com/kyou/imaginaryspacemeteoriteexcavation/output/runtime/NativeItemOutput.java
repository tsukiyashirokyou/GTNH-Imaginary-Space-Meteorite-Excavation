package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.OutputLimits;
import com.kyou.imaginaryspacemeteoriteexcavation.output.PendingOutputs;
import com.kyou.imaginaryspacemeteoriteexcavation.processing.ProcessingPorts;

import appeng.items.storage.ItemVoidStorageCell;
import gregtech.api.interfaces.IOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBus;
import gregtech.api.metatileentity.implementations.MTEHatchOutputBusCompressed;
import gregtech.common.tileentities.machines.outputme.MTEHatchOutputBusME;

/** Production gate/sink for verified native buses; the stage 7 controller supplies live endpoints and tick guards. */
public final class NativeItemOutput
    implements ProcessingPorts.OutputGate<ItemIdentity>, PendingOutputs.Sink<ItemIdentity> {

    private final PendingItemOutput pending;
    private final Supplier<List<MTEHatchOutputBus>> endpoints;
    private final LongSupplier tick;
    private final BooleanSupplier conditions;
    private String lastFailure = "";

    public NativeItemOutput(PendingItemOutput pending, Supplier<List<MTEHatchOutputBus>> endpoints,
        LongSupplier serverTick, BooleanSupplier conditionsUnchanged) {
        this.pending = pending;
        this.endpoints = endpoints;
        tick = serverTick;
        conditions = conditionsUnchanged;
    }

    @Override
    public boolean canAccept(Map<ItemIdentity, Long> items) {
        if (!pending.canStart(items)) {
            lastFailure = "Pending output occupied, invalid or over capacity";
            return false;
        }
        if (items.isEmpty()) {
            lastFailure = "";
            return true;
        }
        try {
            Map<ItemIdentity, Long> receipt = execute(items, true);
            boolean fits = receipt.equals(items);
            lastFailure = fits ? "" : "Insufficient joint output capacity";
            return fits;
        } catch (RuntimeException unsupported) {
            lastFailure = unsupported.getMessage();
            return false;
        }
    }

    @Override
    public Map<ItemIdentity, Long> transfer(Map<ItemIdentity, Long> offered) {
        try {
            Map<ItemIdentity, Long> receipt = execute(offered, false);
            lastFailure = receipt.equals(offered) ? "" : "Output blocked; remainder retained";
            return receipt;
        } catch (OutputUnavailableException blocked) {
            lastFailure = blocked.getMessage();
            return Collections.emptyMap();
        }
    }

    public String lastFailure() {
        return lastFailure;
    }

    private Map<ItemIdentity, Long> execute(Map<ItemIdentity, Long> items, boolean simulate) {
        List<MTEHatchOutputBus> captured = new ArrayList<>(endpoints.get());
        if (captured.size() > OutputLimits.BUSSES) throw new OutputUnavailableException("Too many output buses");
        Set<MTEHatchOutputBus> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IOutputBus> safeBusses = new ArrayList<>();
        int slots = 0;
        for (MTEHatchOutputBus bus : captured) {
            if (bus == null || !unique.add(bus) || !bus.isValid())
                throw new OutputUnavailableException("Invalid output bus");
            Class<?> type = bus.getClass();
            if (type == MTEHatchOutputBusME.class) {
                MTEHatchOutputBusME me = (MTEHatchOutputBusME) bus;
                if (!safeME(me))
                    throw new OutputUnavailableException("Void/overflow or invalid ME output configuration");
                safeBusses.add(
                    new BoundedOutputBus(
                        me,
                        // Native ME admission may exceed physical capacity. Only prevent its unchecked long sum
                        // from overflowing; a negative physical remainder is normal backpressure after an overshoot.
                        () -> Long.MAX_VALUE - me.getProvider()
                            .getCachedAmount(),
                        () -> safeME(me)));
            } else if (type == MTEHatchOutputBus.class || type == MTEHatchOutputBusCompressed.class) {
                slots += type == MTEHatchOutputBusCompressed.class ? ((MTEHatchOutputBusCompressed) bus).slotCount
                    : bus.getSizeInventory();
                if (slots > OutputLimits.ORDINARY_SLOTS) throw new OutputUnavailableException("Too many output slots");
                safeBusses.add(new BoundedOutputBus(bus, () -> OutputLimits.TOTAL, bus::isValid));
            } else {
                throw new OutputUnavailableException("Unverified output bus: " + type.getName());
            }
        }
        return NativeOutputTransactions.execute(items, safeBusses, simulate, tick, () -> {
            if (!conditions.getAsBoolean()) return false;
            List<MTEHatchOutputBus> current = endpoints.get();
            if (current.size() != captured.size()) return false;
            for (int i = 0; i < captured.size(); i++) {
                MTEHatchOutputBus bus = captured.get(i);
                if (current.get(i) != bus || !bus.isValid()) return false;
                if (bus.getClass() == MTEHatchOutputBusME.class && !safeME((MTEHatchOutputBusME) bus)) return false;
            }
            return true;
        });
    }

    private static boolean safeME(MTEHatchOutputBusME me) {
        ItemStack cell = me.getCellStack();
        return me.isValid() && !me.getProvider()
            .canVoidOverflow()
            && me.getProvider()
                .getCacheCapacity() >= 0
            && me.getProvider()
                .getCacheCapacity() < Long.MAX_VALUE
            && me.getProvider()
                .getCachedAmount() >= 0
            && (cell == null || !(cell.getItem() instanceof ItemVoidStorageCell));
    }
}
