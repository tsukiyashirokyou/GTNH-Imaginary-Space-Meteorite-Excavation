package com.kyou.imaginaryspacemeteoriteexcavation.processing;

import com.kyou.imaginaryspacemeteoriteexcavation.meteor.MeteorGeometry;

public final class ProcessingTime {

    public static final int BASE_EUT = 3072;

    private ProcessingTime() {}

    public static int baseTicks(int radius) {
        MeteorGeometry.positions(radius);
        long diameter = 2L * radius + 1;
        long cycles = (diameter + 15) / 16;
        return Math.toIntExact(Math.multiplyExact(15L, Math.multiplyExact(cycles * cycles, diameter)));
    }
}
