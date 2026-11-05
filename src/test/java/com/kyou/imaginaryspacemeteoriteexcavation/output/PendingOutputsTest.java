package com.kyou.imaginaryspacemeteoriteexcavation.output;

import static org.junit.Assert.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

public final class PendingOutputsTest {

    private static Map<String, Long> one(long count) {
        return Collections.singletonMap("ore", count);
    }

    @Test
    public void boundsAndPendingBlockNewBatches() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        assertTrue(pending.canStart(one(OutputLimits.TOTAL)));
        assertFalse(pending.canStart(one(OutputLimits.TOTAL + 1)));
        assertFalse(pending.canStart(one(Long.MAX_VALUE)));
        assertFalse(pending.canStart(one(0)));
        assertFalse(pending.canStart(one(-1)));
        Map<String, Long> totalOverflow = new LinkedHashMap<>(one(OutputLimits.TOTAL));
        totalOverflow.put("filler", 1L);
        assertFalse(pending.canStart(totalOverflow));
        pending.complete(one(20));
        assertFalse(pending.canStart(one(1)));
        try {
            pending.complete(one(1));
            fail("Cannot enqueue another completed batch");
        } catch (IllegalStateException expected) {}
        assertEquals(20, pending.total());
    }

    @Test
    public void partialReceiptsConserveItemsAcrossBlockAndRecovery() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        pending.complete(one(100));
        assertEquals(0, pending.flush(1, offered -> Collections.emptyMap()));
        assertEquals(35, pending.flush(2, offered -> one(35)));
        assertEquals(65, pending.total());
        PendingOutputs<String> restored = PendingOutputs.restore(pending.snapshot(), pending.cursor(), pending.fault());
        assertEquals(65, restored.flush(3, offered -> offered));
        assertEquals(0, restored.total());
        assertTrue(restored.canStart(one(1)));
        assertEquals(
            0,
            restored.flush(4, offered -> { throw new AssertionError("No completed output to regenerate"); }));
    }

    @Test
    public void aTickLimitsIdentitiesButOffersTheirFullLongQuantities() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        Map<String, Long> many = new LinkedHashMap<>();
        for (int i = 0; i < OutputLimits.IDENTITIES; i++) many.put("item" + i, 1L << 29);
        pending.complete(many);
        AtomicInteger calls = new AtomicInteger();
        PendingOutputs.Sink<String> sink = offered -> {
            calls.incrementAndGet();
            assertTrue(offered.size() <= OutputLimits.OFFERS_PER_TICK);
            for (long count : offered.values()) {
                assertEquals(1L << 29, count);
            }
            return offered;
        };
        assertEquals(64L << 29, pending.flush(10, sink));
        assertEquals(0, pending.flush(10, sink));
        assertEquals(1, calls.get());
        assertEquals(64L << 29, pending.flush(11, sink));
        assertEquals(2, calls.get());
    }

    @Test
    public void aSingleIdentityCanTransferTheEntirePendingCapacityInOneTick() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        pending.complete(one(OutputLimits.TOTAL));
        assertEquals(OutputLimits.TOTAL, pending.flush(1, offered -> {
            assertEquals(one(OutputLimits.TOTAL), offered);
            return offered;
        }));
        assertFalse(pending.blocksStart());
        assertEquals(0, pending.total());
    }

    @Test
    public void blockedIdentitiesDoNotStarveLaterOnes() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        Map<String, Long> many = new LinkedHashMap<>();
        for (int i = 0; i < OutputLimits.IDENTITIES; i++) many.put("item" + i, 1L);
        pending.complete(many);
        for (int tick = 0; tick < 16; tick++) {
            pending.flush(
                tick,
                offered -> offered.containsKey("item1023") ? Collections.singletonMap("item1023", 1L)
                    : Collections.emptyMap());
        }
        assertFalse(
            pending.snapshot()
                .containsKey("item1023"));
        assertEquals(1023, pending.total());
    }

    @Test
    public void uncertainCommitAndInvalidReceiptsFailClosed() {
        for (int mode = 0; mode < 3; mode++) {
            PendingOutputs<String> pending = new PendingOutputs<>();
            pending.complete(one(10));
            final int failure = mode;
            assertEquals(0, pending.flush(1, offered -> {
                if (failure == 0) throw new IllegalStateException("Injected commit uncertainty");
                if (failure == 1) return one(11);
                return Collections.singletonMap("not_offered", 1L);
            }));
            assertFalse(
                pending.fault()
                    .isEmpty());
            assertEquals(10, pending.total());
            PendingOutputs<String> saved = PendingOutputs
                .restore(pending.snapshot(), pending.cursor(), pending.fault());
            assertEquals(
                0,
                saved.flush(2, offered -> { throw new AssertionError("Must not retry uncertain commit"); }));
            assertTrue(saved.blocksStart());
        }
    }

    @Test
    public void reentrantCallbacksCannotFlushOrCaptureAnotherBatch() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        pending.complete(one(10));
        assertEquals(10, pending.flush(1, offered -> {
            assertTrue(pending.blocksStart());
            assertEquals(0, pending.flush(2, unused -> { throw new AssertionError("Reentrant transfer"); }));
            assertFalse(pending.canStart(one(2)));
            return offered;
        }));
        assertFalse(pending.blocksStart());
    }

    @Test
    public void allFilteredBatchNeedsNoOutputAndSnapshotsAreImmutable() {
        PendingOutputs<String> pending = new PendingOutputs<>();
        pending.complete(Collections.emptyMap());
        assertFalse(pending.blocksStart());
        pending.complete(one(3));
        Map<String, Long> saved = pending.snapshot();
        try {
            saved.put("ore", 999L);
            fail("Mutable ownership snapshot");
        } catch (UnsupportedOperationException expected) {}
        pending.flush(1, offered -> offered);
        assertEquals(Long.valueOf(3), saved.get("ore"));
        assertEquals(0, pending.total());
    }
}
