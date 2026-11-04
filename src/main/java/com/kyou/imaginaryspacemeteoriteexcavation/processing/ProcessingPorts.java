package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import java.util.Map;
import java.util.function.IntConsumer;
import java.util.function.IntUnaryOperator;

/** Explicit integration boundaries; there is no always-accepting production output implementation. */
public final class ProcessingPorts {

    private ProcessingPorts() {}

    public interface LpAccess {

        /** -1 means the server network is unavailable. Values are never orb-capacity-clamped. */
        long balance(String owner);

        /** Native all-or-nothing debit; no resource/inventory callbacks may run inside this operation. */
        boolean debit(String owner, int amount);
    }

    public interface Resources {

        Snapshot capture(String focusSignature);
    }

    public interface Snapshot {

        long focuses();

        long drillingLiters();

        Debit prepareDebit(int focuses, long drillingLiters);
    }

    public interface Debit {

        boolean unchanged();

        /** Precomputed local field writes only, after unchanged(). No failing drain or external callbacks. */
        void apply();
    }

    public interface OutputGate<K> {

        /** Same-tick preflight, not a reservation. Capacity must be monotone for increasing item quantities. */
        boolean canAccept(Map<K, Long> retainedItems);
    }

    public interface Scheduler {

        Schedule calculate(int baseTicks, MachinePower power, IntUnaryOperator admissibleIncrement,
            IntConsumer plannedIncrement);
    }

    public static final class Schedule {

        public final int batch;
        public final int ticks;
        public final long eut;
        public final ProcessFailure failure;

        private Schedule(int batch, int ticks, long eut, ProcessFailure failure) {
            this.batch = batch;
            this.ticks = ticks;
            this.eut = eut;
            this.failure = failure;
        }

        public static Schedule success(int batch, int ticks, long eut) {
            if (batch <= 0 || ticks <= 0 || eut <= 0) throw new IllegalArgumentException("Invalid native schedule");
            return new Schedule(batch, ticks, eut, ProcessFailure.NONE);
        }

        public static Schedule failure(ProcessFailure failure) {
            if (failure == ProcessFailure.NONE) throw new IllegalArgumentException("Missing failure");
            return new Schedule(0, 0, 0, failure);
        }
    }
}
