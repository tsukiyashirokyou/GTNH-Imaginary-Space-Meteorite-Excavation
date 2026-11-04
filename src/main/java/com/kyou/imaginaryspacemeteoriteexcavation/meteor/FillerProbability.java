package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

/** Models Java nextFloat's 24-bit support and the source's float multiplication, without an RNG. */
public final class FillerProbability {

    private static final int SAMPLES = 1 << 24;

    private FillerProbability() {}

    public static strictfp ExactFraction fromPercent(float chance) {
        if (Float.isNaN(chance) || Float.isInfinite(chance)) {
            throw new IllegalArgumentException("Non-finite filler chance");
        }
        if (chance <= 0) return ExactFraction.ZERO;
        if (chance >= 100) return ExactFraction.ONE;
        int lower = 0;
        int upper = SAMPLES;
        while (lower < upper) {
            int middle = lower + (upper - lower) / 2;
            float sample = (float) middle / SAMPLES;
            if (sample * 100.0f < chance) lower = middle + 1;
            else upper = middle;
        }
        return ExactFraction.of(lower, SAMPLES);
    }
}
