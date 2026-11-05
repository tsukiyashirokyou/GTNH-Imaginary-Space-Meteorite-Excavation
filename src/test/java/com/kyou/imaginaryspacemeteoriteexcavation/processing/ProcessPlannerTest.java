package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeSnapshot;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.WeightedComponent;

/** Controlled scheduler/receiver fixtures exercise resource semantics, not a copy of GT's scheduling algorithm. */
public final class ProcessPlannerTest {

    private static final MachinePower POWER = MachinePower.fromHatches(1, 0, new long[] { 8192 }, new long[] { 2 });

    @Test
    public void towerFailureRunsBeforeAnyOtherPort() {
        Memory memory = new Memory(10, 1000, 1000);
        MeteorProcessPlanner.Result<String> result = new MeteorProcessPlanner<String>(steps()).prepare(
            recipe(10),
            11,
            () -> { throw new AssertionError("orb read before tower check"); },
            focus -> { throw new AssertionError("resources read before tower check"); },
            memory,
            POWER,
            item -> { throw new AssertionError("filter read before tower check"); },
            items -> { throw new AssertionError("output read before tower check"); },
            () -> true);
        assertEquals(ProcessFailure.INSUFFICIENT_TOWERS, result.failure);
        assertEquals(0, memory.reads);
        assertEquals(0, memory.debits);
    }

    @Test
    public void preparationIsReadOnlyAndCommitChargesOneWholeBatch() {
        Memory memory = new Memory(10, 1000, 1000);
        PreparedProcess<String> plan = prepare(memory, 10, false, item -> true, items -> true, () -> true).process;
        assertEquals(3, plan.schedule().batch);
        assertEquals(
            Long.valueOf(4),
            plan.retainedItems()
                .get("ore"));
        assertEquals(
            Long.valueOf(52),
            plan.retainedItems()
                .get("filler"));
        assertEquals(
            56,
            plan.prefilterYield()
                .prefilterTotal());
        assertEquals(10, memory.focus);
        assertEquals(1000, memory.fluid);
        assertEquals(1000, memory.lp);
        assertEquals(ProcessFailure.NONE, plan.start());
        assertTrue(plan.started());
        assertEquals(7, memory.focus);
        assertEquals(944, memory.fluid);
        assertEquals(970, memory.lp);
        assertEquals(1, memory.debits);
        assertEquals(ProcessFailure.ALREADY_DECIDED, plan.start());
        assertEquals(1, memory.debits);
    }

    @Test
    public void nonlinearFluidAndOutputLimitsConstrainTotalBatch() {
        Memory memory = new Memory(10, 55, 1000);
        PreparedProcess<String> limited = prepare(memory, 10, false, item -> true, items -> true, () -> true).process;
        assertEquals(2, limited.schedule().batch);
        assertEquals(
            37,
            limited.prefilterYield()
                .prefilterTotal());
        // D(1)=18, D(2)=37, D(3)=56; the increment from 1 to 3 is 38, not D(2).
        Memory roomy = new Memory(10, 1000, 1000);
        PreparedProcess<String> outputLimited = prepare(
            roomy,
            10,
            false,
            "ore"::equals,
            items -> items.getOrDefault("ore", 0L) <= 3,
            () -> true).process;
        assertEquals(2, outputLimited.schedule().batch);
        assertEquals(
            37,
            outputLimited.prefilterYield()
                .prefilterTotal());
        assertEquals(
            1,
            outputLimited.retainedItems()
                .size());
    }

    @Test
    public void allFilteredStillPaysPrefilterDrilling() {
        Memory memory = new Memory(10, 1000, 1000);
        PreparedProcess<String> plan = prepare(
            memory,
            10,
            false,
            item -> false,
            items -> items.isEmpty(),
            () -> true).process;
        assertTrue(
            plan.retainedItems()
                .isEmpty());
        assertEquals(
            56,
            plan.prefilterYield()
                .prefilterTotal());
        assertEquals(ProcessFailure.NONE, plan.start());
        assertEquals(944, memory.fluid);
    }

    @Test
    public void armokAndOrdinaryZeroCostNeverTouchLpPorts() {
        for (boolean free : new boolean[] { false, true }) {
            Memory memory = new Memory(10, 1000, -1);
            memory.forbidLp = true;
            PreparedProcess<String> plan = prepare(
                memory,
                free ? Integer.MAX_VALUE : 0,
                free,
                item -> true,
                items -> true,
                () -> true).process;
            assertEquals(0, plan.lpCost());
            assertEquals(ProcessFailure.NONE, plan.start());
            assertEquals(0, memory.reads);
            assertEquals(0, memory.debits);
        }
    }

    @Test
    public void lpIntBoundaryAndResourceFailuresDoNotConsume() {
        Memory memory = new Memory(10, 1000, Integer.MAX_VALUE);
        PreparedProcess<String> plan = prepare(
            memory,
            1000000001,
            false,
            item -> true,
            items -> true,
            () -> true).process;
        assertEquals(2, plan.schedule().batch);
        assertEquals(2000000002, plan.lpCost());
        assertEquals(ProcessFailure.NONE, plan.start());
        assertEquals(Integer.MAX_VALUE - 2000000002L, memory.lp);
        assertEquals(
            ProcessFailure.INSUFFICIENT_LP,
            prepare(new Memory(10, 1000, 9), 10, false, item -> true, items -> true, () -> true).failure);
        assertEquals(
            ProcessFailure.INSUFFICIENT_DRILLING_FLUID,
            prepare(new Memory(10, 17, 1000), 10, false, item -> true, items -> true, () -> true).failure);
        assertEquals(
            ProcessFailure.OUTPUT_FULL,
            prepare(new Memory(10, 1000, 1000), 10, false, item -> true, items -> false, () -> true).failure);
        assertEquals(
            ProcessFailure.INSUFFICIENT_INPUT,
            prepare(new Memory(0, 1000, 1000), 10, false, item -> true, items -> true, () -> true).failure);
        assertEquals(
            ProcessFailure.NETWORK_UNAVAILABLE,
            prepare(new Memory(10, 1000, -1), 10, false, item -> true, items -> true, () -> true).failure);
    }

    @Test
    public void finalRechecksRejectChangedInventoryNetworkConditionsAndCapacity() {
        Memory changed = new Memory(10, 1000, 1000);
        PreparedProcess<String> inventory = prepare(
            changed,
            10,
            false,
            item -> true,
            items -> true,
            () -> true).process;
        changed.focus--;
        assertEquals(ProcessFailure.CONDITIONS_CHANGED, inventory.start());
        assertEquals(0, changed.debits);
        Memory competition = new Memory(10, 1000, 1000);
        PreparedProcess<String> network = prepare(
            competition,
            10,
            false,
            item -> true,
            items -> true,
            () -> true).process;
        competition.lp = 0;
        assertEquals(ProcessFailure.INSUFFICIENT_LP, network.start());
        assertEquals(10, competition.focus);
        assertEquals(1000, competition.fluid);
        boolean[] ready = { true };
        Memory guard = new Memory(10, 1000, 1000);
        PreparedProcess<String> guarded = prepare(
            guard,
            10,
            false,
            item -> true,
            items -> true,
            () -> ready[0]).process;
        ready[0] = false;
        assertEquals(ProcessFailure.CONDITIONS_CHANGED, guarded.start());
        assertEquals(0, guard.debits);
        boolean[] capacity = { true };
        Memory output = new Memory(10, 1000, 1000);
        PreparedProcess<String> full = prepare(
            output,
            10,
            false,
            item -> true,
            items -> capacity[0],
            () -> true).process;
        capacity[0] = false;
        assertEquals(ProcessFailure.OUTPUT_FULL, full.start());
        assertEquals(0, output.debits);
        assertEquals(10, output.focus);
    }

    @Test
    public void largestNativeCandidateDoesNotOverflowBinarySearch() {
        ProcessingPorts.Scheduler maximal = (ticks, power, choose, advance) -> {
            int batch = choose.applyAsInt(Integer.MAX_VALUE);
            advance.accept(batch);
            return ProcessingPorts.Schedule.success(batch, 1, 3072);
        };
        Memory memory = new Memory(Integer.MAX_VALUE, Long.MAX_VALUE, 0);
        PreparedProcess<String> plan = new MeteorProcessPlanner<String>(maximal).prepare(
            recipe(0),
            12,
            () -> new BoundNetwork("owner", "orb", true),
            memory,
            memory,
            POWER,
            item -> true,
            items -> true,
            () -> true).process;
        assertEquals(Integer.MAX_VALUE, plan.schedule().batch);
        assertTrue(
            plan.prefilterYield()
                .prefilterTotal() > Integer.MAX_VALUE);
    }

    @Test
    public void timeAndNativeEnergyAggregationHaveExplicitBounds() {
        assertEquals(45, ProcessingTime.baseTicks(1));
        assertEquals(1020, ProcessingTime.baseTicks(8));
        assertEquals(24375, ProcessingTime.baseTicks(32));
        assertEquals(1, POWER.amperage());
        MachinePower multiple = MachinePower.fromHatches(2, 0, new long[] { 8192, 32768 }, new long[] { 2, 2 });
        assertEquals(20480, multiple.voltage());
        assertEquals(4, multiple.amperage());
        assertEquals(81920, multiple.eut());
        MachinePower capped = MachinePower.fromHatches(2, 0, new long[] { 32768, 8192 }, new long[] { 1, 16 });
        assertTrue(capped.eut() <= 32768 + 8192 * 16);
        reject(() -> MachinePower.fromHatches(1, 1, new long[] { 8192, 8192 }, new long[] { 2, 16 }));
        reject(() -> MachinePower.fromHatches(0, 2, new long[] { 8192, 8192 }, new long[] { 16, 16 }));
        try {
            MachinePower.fromHatches(1, 0, new long[] { Long.MAX_VALUE }, new long[] { 2 });
            fail("Expected power overflow");
        } catch (ArithmeticException expected) {}
    }

    @Test
    public void pureCalculationsAreReusedWhileFinalOutputRemainsLive() {
        Memory memory = new Memory(10, 1000, 1000);
        AtomicInteger filters = new AtomicInteger();
        List<Map<String, Long>> probes = new ArrayList<>();
        boolean[] capacity = { true };
        PreparedProcess<String> plan = prepare(memory, 10, false, item -> {
            filters.incrementAndGet();
            return true;
        }, items -> {
            probes.add(items);
            return capacity[0];
        }, () -> true).process;
        assertEquals(3, plan.schedule().batch);
        assertEquals(2, filters.get());
        assertEquals(3, probes.size()); // Initial B=1, native first B=1, upper-bound B=3.
        assertSame(probes.get(0), probes.get(1)); // Same immutable pure plan, fresh capacity call each time.
        capacity[0] = false;
        assertEquals(ProcessFailure.OUTPUT_FULL, plan.start());
        assertEquals(4, probes.size());
        assertEquals(2, filters.get());
        assertEquals(0, memory.debits);
        assertEquals(10, memory.focus);
        assertEquals(1000, memory.fluid);
    }

    @Test
    public void feasibleIntMaximumNeedsOneUpperBoundProbeAndStillRechecksBeforeDebit() {
        ProcessingPorts.Scheduler maximal = (ticks, power, choose, advance) -> {
            int batch = choose.applyAsInt(Integer.MAX_VALUE);
            advance.accept(batch);
            return ProcessingPorts.Schedule.success(batch, 1, 3072);
        };
        Memory memory = new Memory(Integer.MAX_VALUE, Long.MAX_VALUE, 0);
        AtomicInteger probes = new AtomicInteger();
        PreparedProcess<String> plan = new MeteorProcessPlanner<String>(maximal).prepare(
            recipe(0),
            12,
            () -> new BoundNetwork("owner", "orb", true),
            memory,
            memory,
            POWER,
            item -> true,
            items -> {
                probes.incrementAndGet();
                return true;
            },
            () -> true).process;
        assertEquals(Integer.MAX_VALUE, plan.schedule().batch);
        assertEquals(2, probes.get());
        assertEquals(ProcessFailure.NONE, plan.start());
        assertEquals(3, probes.get());
        assertEquals(0, memory.focus);
        long total = 8L * Integer.MAX_VALUE / 5 + 87L * Integer.MAX_VALUE / 5;
        assertEquals(Long.MAX_VALUE - total, memory.fluid);
    }

    @Test
    public void optimizedSearchMatchesExhaustiveRoundedResourceAndOutputLimits() {
        ProcessingPorts.Scheduler upTo128 = (ticks, power, choose, advance) -> {
            int first = choose.applyAsInt(1);
            if (first == 0) return ProcessingPorts.Schedule.failure(ProcessFailure.INSUFFICIENT_INPUT);
            advance.accept(first);
            int extra = choose.applyAsInt(127);
            advance.accept(extra);
            return ProcessingPorts.Schedule.success(first + extra, 100, 3072);
        };
        for (int focus : new int[] { 1, 2, 3, 8, 127, 128 }) {
            for (long balance : new long[] { 0, 7, 10, 20, 50, 1000 }) {
                for (long fluid : new long[] { 0, 17, 18, 37, 55, 56, 200, 5000 }) {
                    for (long output : new long[] { 0, 1, 3, 52, 255, Long.MAX_VALUE }) {
                        for (boolean oresOnly : new boolean[] { false, true }) {
                            int expected = 0;
                            for (int b = 1; b <= Math.min(128, focus); b++) {
                                long ore = 8L * b / 5, filler = 87L * b / 5;
                                if (10L * b <= balance && ore + filler <= fluid
                                    && ore + (oresOnly ? 0 : filler) <= output) expected = b;
                            }
                            Memory memory = new Memory(focus, fluid, balance);
                            MeteorProcessPlanner.Result<String> result = new MeteorProcessPlanner<String>(upTo128)
                                .prepare(
                                    recipe(10),
                                    12,
                                    () -> new BoundNetwork("owner", "orb", false),
                                    memory,
                                    memory,
                                    POWER,
                                    item -> !oresOnly || "ore".equals(item),
                                    items -> items.values()
                                        .stream()
                                        .mapToLong(Long::longValue)
                                        .sum() <= output,
                                    () -> true);
                            if (expected == 0) {
                                assertTrue(result.failure != ProcessFailure.NONE);
                            } else {
                                assertEquals(expected, result.process.schedule().batch);
                                assertEquals(ProcessFailure.NONE, result.process.start());
                                assertEquals(focus - expected, memory.focus);
                                assertEquals(balance - 10L * expected, memory.lp);
                                assertEquals(fluid - 8L * expected / 5 - 87L * expected / 5, memory.fluid);
                            }
                        }
                    }
                }
            }
        }
    }

    private static MeteorProcessPlanner.Result<String> prepare(Memory memory, int cost, boolean free,
        Predicate<String> keep, ProcessingPorts.OutputGate<String> output, BooleanSupplier guard) {
        return new MeteorProcessPlanner<String>(steps()).prepare(
            recipe(cost),
            12,
            () -> new BoundNetwork("owner", "orb", free),
            memory,
            memory,
            POWER,
            keep,
            output,
            guard);
    }

    private static ProcessingPorts.Scheduler steps() {
        return (ticks, power, choose, advance) -> {
            int first = choose.applyAsInt(1);
            if (first <= 0) return ProcessingPorts.Schedule.failure(ProcessFailure.INSUFFICIENT_INPUT);
            advance.accept(first);
            int extra = choose.applyAsInt(2);
            advance.accept(extra);
            return ProcessingPorts.Schedule.success(first + extra, 100, 3072);
        };
    }

    private static RecipeCatalogue.Entry<String> recipe(int cost) {
        RecipeSnapshot<String> recipe = new RecipeSnapshot<>(
            "focus:0",
            1,
            cost,
            0,
            Arrays.asList(new WeightedComponent<>("ore", 8), new WeightedComponent<>("filler", 87)),
            Collections.emptyList(),
            value -> value);
        return new RecipeCatalogue.Entry<>("test", "test.json", "test-hash", recipe);
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            fail("Expected rejection");
        } catch (IllegalArgumentException expected) {}
    }

    private static final class Memory implements ProcessingPorts.Resources, ProcessingPorts.LpAccess {

        private long focus;
        private long fluid;
        private long lp;
        private int reads;
        private int debits;
        private boolean forbidLp;

        private Memory(long focus, long fluid, long lp) {
            this.focus = focus;
            this.fluid = fluid;
            this.lp = lp;
        }

        @Override
        public long balance(String owner) {
            if (forbidLp) throw new AssertionError("LP queried in free branch");
            reads++;
            return lp;
        }

        @Override
        public boolean debit(String owner, int amount) {
            if (forbidLp) throw new AssertionError("LP debited in free branch");
            debits++;
            if (amount < 0 || amount > lp) return false;
            lp -= amount;
            return true;
        }

        @Override
        public ProcessingPorts.Snapshot capture(String signature) {
            long originalFocus = focus;
            long originalFluid = fluid;
            return new ProcessingPorts.Snapshot() {

                @Override
                public long focuses() {
                    return originalFocus;
                }

                @Override
                public long drillingLiters() {
                    return originalFluid;
                }

                @Override
                public ProcessingPorts.Debit prepareDebit(int count, long liters) {
                    return new ProcessingPorts.Debit() {

                        @Override
                        public boolean unchanged() {
                            return focus == originalFocus && fluid == originalFluid;
                        }

                        @Override
                        public void apply() {
                            focus -= count;
                            fluid -= liters;
                        }
                    };
                }
            };
        }
    }
}
