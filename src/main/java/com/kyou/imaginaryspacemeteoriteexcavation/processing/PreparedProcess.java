package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.BatchYield;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;

/** A same-tick, single-use start commitment, not a persistent output or crash-recovery transaction. */
public final class PreparedProcess<K> {

    private final RecipeCatalogue.Entry<K> recipe;
    private final BoundNetwork network;
    private final BatchYield<K> yield;
    private final Map<K, Long> retained;
    private final ProcessingPorts.Schedule schedule;
    private final int lpCost;
    private final ProcessingPorts.Debit debit;
    private final ProcessingPorts.LpAccess lp;
    private final ProcessingPorts.OutputGate<K> output;
    private final BooleanSupplier conditionsUnchanged;
    private boolean decided;
    private boolean started;

    PreparedProcess(RecipeCatalogue.Entry<K> recipe, BoundNetwork network, BatchYield<K> yield, Map<K, Long> retained,
        ProcessingPorts.Schedule schedule, int lpCost, ProcessingPorts.Debit debit, ProcessingPorts.LpAccess lp,
        ProcessingPorts.OutputGate<K> output, BooleanSupplier conditionsUnchanged) {
        this.recipe = recipe;
        this.network = network;
        this.yield = yield;
        this.retained = Collections.unmodifiableMap(new LinkedHashMap<>(retained));
        this.schedule = schedule;
        this.lpCost = lpCost;
        this.debit = debit;
        this.lp = lp;
        this.output = output;
        this.conditionsUnchanged = conditionsUnchanged;
    }

    /** Must run on the server thread in the same tick as planning, after native power/structure post-checks. */
    public synchronized ProcessFailure start() {
        if (decided) return ProcessFailure.ALREADY_DECIDED;
        decided = true;
        if (!conditionsUnchanged.getAsBoolean()) return ProcessFailure.CONDITIONS_CHANGED;
        if (!output.canAccept(retained)) return ProcessFailure.OUTPUT_FULL;
        // Check after all external preflight callbacks, so their side effects cannot invalidate the local snapshot.
        if (!conditionsUnchanged.getAsBoolean() || !debit.unchanged()) return ProcessFailure.CONDITIONS_CHANGED;
        if (lpCost > 0 && !lp.debit(network.owner(), lpCost)) return ProcessFailure.INSUFFICIENT_LP;
        debit.apply();
        started = true;
        return ProcessFailure.NONE;
    }

    public RecipeCatalogue.Entry<K> recipe() {
        return recipe;
    }

    public BoundNetwork network() {
        return network;
    }

    public BatchYield<K> prefilterYield() {
        return yield;
    }

    public Map<K, Long> retainedItems() {
        return retained;
    }

    public int lpCost() {
        return lpCost;
    }

    public ProcessingPorts.Schedule schedule() {
        return schedule;
    }

    public boolean started() {
        return started;
    }
}
