package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.BatchYield;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.MeteorGeometry;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;

/** Nonlinear resource limits around native scheduling. Planning never consumes live resources. */
public final class MeteorProcessPlanner<K> {

    private final ProcessingPorts.Scheduler scheduler;

    public MeteorProcessPlanner(ProcessingPorts.Scheduler scheduler) {
        this.scheduler = Objects.requireNonNull(scheduler);
    }

    public Result<K> prepare(RecipeCatalogue.Entry<K> recipe, int towers, Supplier<BoundNetwork> orb,
        ProcessingPorts.Resources resources, ProcessingPorts.LpAccess lp, MachinePower power, Predicate<K> keep,
        ProcessingPorts.OutputGate<K> output, BooleanSupplier conditionsUnchanged) {
        if (recipe == null) return Result.failure(ProcessFailure.NO_RECIPE);
        // This is deliberately before reading the orb, resource snapshot, or Soul Network.
        if (!MeteorGeometry.hasEnoughTowers(
            recipe.recipe()
                .radius(),
            towers)) {
            return Result.failure(ProcessFailure.INSUFFICIENT_TOWERS);
        }
        BoundNetwork network = orb.get();
        if (network == null) return Result.failure(ProcessFailure.UNBOUND_ORB);
        if (power.eut() < ProcessingTime.BASE_EUT) return Result.failure(ProcessFailure.INSUFFICIENT_POWER);
        ProcessingPorts.Snapshot snapshot;
        try {
            snapshot = resources.capture(
                recipe.recipe()
                    .focusSignature());
        } catch (UnsupportedOperationException unsupported) {
            return Result.failure(ProcessFailure.UNSUPPORTED_INPUT);
        }
        if (snapshot.focuses() <= 0) return Result.failure(ProcessFailure.INSUFFICIENT_INPUT);
        int cost = network.free() ? 0
            : recipe.recipe()
                .lpCost();
        long lpLimit = Integer.MAX_VALUE;
        if (cost > 0) {
            long balance = lp.balance(network.owner());
            if (balance < 0) return Result.failure(ProcessFailure.NETWORK_UNAVAILABLE);
            lpLimit = Math.min(Integer.MAX_VALUE, balance) / cost;
            if (lpLimit == 0) return Result.failure(ProcessFailure.INSUFFICIENT_LP);
        }
        Candidate candidate = new Candidate(recipe, snapshot, Math.min(snapshot.focuses(), lpLimit), keep, output);
        if (!candidate.fits(1)) return Result.failure(candidate.failure);
        ProcessingPorts.Schedule schedule;
        try {
            schedule = scheduler.calculate(
                ProcessingTime.baseTicks(
                    recipe.recipe()
                        .radius()),
                power,
                candidate::maximumIncrement,
                candidate::advance);
        } catch (ArithmeticException overflow) {
            return Result.failure(ProcessFailure.OVERFLOW);
        }
        if (schedule.failure != ProcessFailure.NONE) return Result.failure(schedule.failure);
        if (candidate.planned != schedule.batch || !candidate.fits(schedule.batch)) {
            return Result.failure(
                candidate.failure == ProcessFailure.NONE ? ProcessFailure.CONDITIONS_CHANGED : candidate.failure);
        }
        BatchYield<K> yield = recipe.recipe()
            .yield()
            .forBatch(schedule.batch);
        Map<K, Long> retained = retained(yield, keep);
        int totalLp = Math.toIntExact(Math.multiplyExact((long) cost, schedule.batch));
        ProcessingPorts.Debit debit = snapshot.prepareDebit(schedule.batch, yield.prefilterTotal());
        return Result.success(
            new PreparedProcess<>(
                recipe,
                network,
                yield,
                retained,
                schedule,
                totalLp,
                debit,
                lp,
                output,
                conditionsUnchanged));
    }

    private Map<K, Long> retained(BatchYield<K> yield, Predicate<K> keep) {
        Map<K, Long> items = new LinkedHashMap<>();
        for (Map.Entry<K, Long> entry : yield.quantities()
            .entrySet()) {
            if (keep.test(entry.getKey())) items.put(entry.getKey(), entry.getValue());
        }
        return items;
    }

    private final class Candidate {

        private final RecipeCatalogue.Entry<K> recipe;
        private final ProcessingPorts.Snapshot snapshot;
        private final long countLimit;
        private final Predicate<K> keep;
        private final ProcessingPorts.OutputGate<K> output;
        private int planned;
        private long plannedLiters;
        private ProcessFailure failure = ProcessFailure.NONE;

        private Candidate(RecipeCatalogue.Entry<K> recipe, ProcessingPorts.Snapshot snapshot, long countLimit,
            Predicate<K> keep, ProcessingPorts.OutputGate<K> output) {
            this.recipe = recipe;
            this.snapshot = snapshot;
            this.countLimit = Math.min(Integer.MAX_VALUE, countLimit);
            this.keep = keep;
            this.output = output;
        }

        private boolean fits(int total) {
            if (total <= 0 || total > countLimit) {
                failure = ProcessFailure.INSUFFICIENT_INPUT;
                return false;
            }
            BatchYield<K> yield;
            try {
                yield = recipe.recipe()
                    .yield()
                    .forBatch(total);
            } catch (ArithmeticException overflow) {
                failure = ProcessFailure.OVERFLOW;
                return false;
            }
            if (yield.prefilterTotal() > snapshot.drillingLiters()) {
                failure = ProcessFailure.INSUFFICIENT_DRILLING_FLUID;
                return false;
            }
            if (!output.canAccept(retained(yield, keep))) {
                failure = ProcessFailure.OUTPUT_FULL;
                return false;
            }
            failure = ProcessFailure.NONE;
            return true;
        }

        private int maximumIncrement(int requested) {
            int low = 0;
            int high = (int) Math.max(0, Math.min((long) requested, countLimit - planned));
            while (low < high) {
                int middle = low + (int) (((long) high - low + 1) / 2);
                if (fits(Math.addExact(planned, middle))) low = middle;
                else high = middle - 1;
            }
            return low;
        }

        private void advance(int amount) {
            if (amount < 0) throw new IllegalArgumentException("Negative native increment");
            if (amount == 0) return;
            int total = Math.addExact(planned, amount);
            if (!fits(total)) throw new IllegalStateException("Native selected an infeasible candidate");
            long liters = recipe.recipe()
                .yield()
                .forBatch(total)
                .prefilterTotal();
            long increment = Math.subtractExact(liters, plannedLiters);
            if (increment < 0 || increment > snapshot.drillingLiters() - plannedLiters) {
                throw new IllegalStateException("Invalid nonlinear drilling increment");
            }
            planned = total;
            plannedLiters = liters;
        }
    }

    public static final class Result<K> {

        public final ProcessFailure failure;
        public final PreparedProcess<K> process;

        private Result(ProcessFailure failure, PreparedProcess<K> process) {
            this.failure = failure;
            this.process = process;
        }

        private static <K> Result<K> failure(ProcessFailure failure) {
            return new Result<>(failure, null);
        }

        private static <K> Result<K> success(PreparedProcess<K> process) {
            return new Result<>(ProcessFailure.NONE, process);
        }
    }
}
