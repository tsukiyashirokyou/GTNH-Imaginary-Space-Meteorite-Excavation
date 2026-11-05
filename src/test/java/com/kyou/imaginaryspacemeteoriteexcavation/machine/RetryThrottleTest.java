package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import static org.junit.Assert.*;

import org.junit.Test;

import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.RetryThrottle;

public final class RetryThrottleTest {

    @Test
    public void failuresBoundImmediatePushesToOncePerSecond() {
        RetryThrottle retry = new RetryThrottle();
        int attempts = 0;
        for (long tick = 0; tick < 1000; tick++)
            for (int push = 0; push < 100; push++) if (retry.acquire(tick)) attempts++;
        assertEquals(50, attempts);
    }

    @Test
    public void progressAllowsNextTickButNeverTwiceThisTick() {
        RetryThrottle retry = new RetryThrottle();
        assertTrue(retry.acquire(10));
        retry.progressed(10);
        assertFalse(retry.acquire(10));
        assertTrue(retry.acquire(11));
        assertFalse(retry.acquire(12));
        assertTrue(retry.acquire(31));
    }

    @Test
    public void resetClockDoesNotFreezeRetries() {
        RetryThrottle retry = new RetryThrottle();
        assertTrue(retry.acquire(9999));
        assertTrue(retry.acquire(1));
        assertFalse(retry.acquire(1));
        assertTrue(new RetryThrottle().acquire(1));
    }
}
