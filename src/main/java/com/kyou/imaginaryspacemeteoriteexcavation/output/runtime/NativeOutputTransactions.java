package com.kyou.imaginaryspacemeteoriteexcavation.output.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;

import net.minecraft.item.ItemStack;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.output.PendingOutputs;

import gregtech.api.interfaces.IOutputBus;
import gregtech.api.util.ItemEjectionHelper;

/** All native snapshots are local variables; no transaction or helper can escape this call. */
final class NativeOutputTransactions {

    private NativeOutputTransactions() {}

    static Map<ItemIdentity, Long> execute(Map<ItemIdentity, Long> items, List<IOutputBus> busses, boolean simulate,
        LongSupplier tick, BooleanSupplier unchanged) {
        if (!PendingOutputs.fits(items)) throw new IllegalArgumentException("Unbounded output request");
        if (items.isEmpty()) return Collections.emptyMap();
        long started = tick.getAsLong();
        if (!unchanged.getAsBoolean()) throw new OutputUnavailableException("Output endpoints changed");
        ItemEjectionHelper helper = new ItemEjectionHelper(busses, true, simulate);
        List<ItemIdentity> order = new ArrayList<>(items.keySet());
        // Stable first-fit order rather than a quantity-dependent identity order in candidate batch searches.
        order.sort(Comparator.comparing(ItemIdentity::signature));
        Map<ItemIdentity, Long> accepted = new LinkedHashMap<>();
        for (ItemIdentity id : order) {
            long remaining = items.get(id);
            long inserted = 0;
            while (remaining > 0) {
                int amount = (int) Math.min(Integer.MAX_VALUE, remaining);
                ItemStack part = id.template();
                part.stackSize = amount;
                int got = helper.ejectStack(part);
                if (got < 0 || got > amount || part.stackSize != amount - got) {
                    throw new IllegalStateException("Invalid native ejection count");
                }
                inserted += got;
                remaining -= got;
                if (got < amount) break;
            }
            if (inserted > 0) accepted.put(id, inserted);
        }
        if (!unchanged.getAsBoolean() || tick.getAsLong() != started) {
            throw new OutputUnavailableException("Output transaction crossed tick or changed endpoints");
        }
        if (!simulate && !accepted.isEmpty()) helper.commit();
        return Collections.unmodifiableMap(accepted);
    }
}
