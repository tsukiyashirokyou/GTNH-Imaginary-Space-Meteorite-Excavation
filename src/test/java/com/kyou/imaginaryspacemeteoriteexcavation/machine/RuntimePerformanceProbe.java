package com.kyou.imaginaryspacemeteoriteexcavation.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.GsonBuilder;
import com.kyou.imaginaryspacemeteoriteexcavation.machine.runtime.RetryThrottle;
import com.kyou.imaginaryspacemeteoriteexcavation.output.OutputLimits;
import com.kyou.imaginaryspacemeteoriteexcavation.output.PendingOutputs;

/** Synthetic Java module probe. No Minecraft tick, World, native destination, TPS or MSPT measurement. */
public final class RuntimePerformanceProbe {

    private static final long SAMPLE_AMOUNT = 1L << 29;

    private RuntimePerformanceProbe() {}

    public static void main(String[] args) {
        List<Map<String, Object>> samples = new ArrayList<>();
        for (int warmup = 0; warmup < 3; warmup++) run(false);
        for (int i = 0; i < 5; i++) {
            samples.add(run(false));
            samples.add(run(true));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scope", "Synthetic 100 machines x 200 iterations; no World/native output/TPS/MSPT");
        result.put("java", System.getProperty("java.version"));
        result.put("samples", samples);
        System.out.println(
            new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(result));
    }

    private static Map<String, Object> run(boolean recover) {
        Map<String, Long> initial = new LinkedHashMap<>();
        for (int i = 0; i < 256; i++) initial.put("item" + i, SAMPLE_AMOUNT);
        List<PendingOutputs<String>> machines = new ArrayList<>();
        List<RetryThrottle> retries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            PendingOutputs<String> pending = new PendingOutputs<>();
            pending.complete(initial);
            machines.add(pending);
            retries.add(new RetryThrottle());
        }
        long[] calls = { 0 }, entries = { 0 }, accepted = { 0 };
        long started = System.nanoTime();
        for (int tick = 0; tick < 200; tick++) {
            final boolean accept = recover && tick >= 100;
            for (int i = 0; i < machines.size(); i++) {
                PendingOutputs<String> pending = machines.get(i);
                RetryThrottle retry = retries.get(i);
                if (!pending.blocksStart() || !retry.acquire(tick)) continue;
                long got = pending.flush(tick, offered -> {
                    calls[0]++;
                    entries[0] += offered.size();
                    if (offered.size() > OutputLimits.OFFERS_PER_TICK) throw new AssertionError("Identity budget");
                    for (long count : offered.values()) {
                        if (count != SAMPLE_AMOUNT) throw new AssertionError("Quantity was throttled");
                    }
                    return accept ? offered : Collections.emptyMap();
                });
                accepted[0] += got;
                if (got > 0) retry.progressed(tick);
            }
        }
        long elapsed = System.nanoTime() - started;
        long remaining = 0;
        for (PendingOutputs<String> machine : machines) remaining += machine.total();
        long original = 100L * 256 * SAMPLE_AMOUNT;
        if (accepted[0] + remaining != original || (recover && remaining != 0))
            throw new AssertionError("Conservation");
        if (!recover && calls[0] != 1000) throw new AssertionError("Blocked retry budget");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", recover ? "blocked_then_recovered" : "blocked");
        result.put("elapsed_ns", elapsed);
        result.put("sink_calls", calls[0]);
        result.put("offered_identities", entries[0]);
        result.put("accepted", accepted[0]);
        result.put("remaining", remaining);
        result.put("initial", original);
        return result;
    }
}
