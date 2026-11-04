package com.kyou.imaginaryspacemeteoriteexcavation.machine;

public final class EnergyLayout {

    private EnergyLayout() {}

    /** Multiple normal hatches OR one multi-amp hatch; never mixed. */
    public static boolean isValid(int normal, int multiAmp) {
        return (normal > 0 && multiAmp == 0) || (normal == 0 && multiAmp == 1);
    }
}
