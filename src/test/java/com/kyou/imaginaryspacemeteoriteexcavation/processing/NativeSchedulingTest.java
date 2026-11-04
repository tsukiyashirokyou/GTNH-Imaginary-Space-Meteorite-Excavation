package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import gregtech.api.util.OverclockCalculator;

/** Actual native OC functions. Full ParallelHelper execution requires the final FML game lifecycle. */
public final class NativeSchedulingTest {

    @Test
    public void actualNativePerfectOverclockMatchesReference() {
        OverclockCalculator iv = calculator(24375, 8192, 1).calculate();
        assertEquals(24375, iv.getDuration());
        assertEquals(3072, iv.getConsumption());
        OverclockCalculator luv = calculator(168, 32768, 1).calculate();
        assertEquals(42, luv.getDuration());
        assertEquals(12288, luv.getConsumption());
    }

    @Test
    public void actualNativeSubtickMultiplierAndPowerRemainBounded() {
        OverclockCalculator high = calculator(45, 33554432, 64);
        assertTrue(high.calculateMultiplierUnderOneTick() > 1);
        high.calculate();
        assertEquals(1, high.getDuration());
        assertTrue(high.getConsumption() > 3072);
        assertTrue(high.getConsumption() <= 33554432L * 64);
    }

    private static OverclockCalculator calculator(int ticks, long voltage, long amps) {
        return new OverclockCalculator().setRecipeEUt(3072)
            .setDuration(ticks)
            .setEUt(voltage)
            .setAmperage(amps)
            .setParallel(1)
            .setMaxTierSkips(1)
            .setAmperageOC(true)
            .enablePerfectOC();
    }
}
