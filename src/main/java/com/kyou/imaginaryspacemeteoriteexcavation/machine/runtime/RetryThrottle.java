package com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime;

/** One attempt per tick; failed remote-capacity/LP checks retry at most once per second. Not persisted. */
public final class RetryThrottle {

    private long last = Long.MIN_VALUE;
    private long next = Long.MIN_VALUE;

    public boolean acquire(long tick) {
        if (tick == last || (tick > last && tick < next)) return false;
        last = tick;
        next = tick + 20;
        return true;
    }

    public void progressed(long tick) {
        next = tick + 1;
    }
}
