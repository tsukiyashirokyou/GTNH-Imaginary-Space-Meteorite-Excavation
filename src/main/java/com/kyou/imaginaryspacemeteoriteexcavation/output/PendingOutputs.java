package com.kyou.imaginaryspacemeteoriteexcavation.output;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Completed outputs only. Server-thread owner; no resource charging or recipe generation lives here. */
public final class PendingOutputs<K> {

    public interface Sink<K> {

        /** Return only quantities whose ownership was actually committed during this call. */
        Map<K, Long> transfer(Map<K, Long> offered);
    }

    private final LinkedHashMap<K, Long> remaining = new LinkedHashMap<>();
    private int cursor;
    private long revision;
    private long lastTick = Long.MIN_VALUE;
    private boolean transferring;
    private String fault = "";

    public static boolean fits(Map<?, Long> items) {
        if (items == null || items.size() > OutputLimits.IDENTITIES) return false;
        long total = 0;
        for (Map.Entry<?, Long> entry : items.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) return false;
            long count = entry.getValue();
            if (count <= 0 || count > OutputLimits.PER_IDENTITY || count > OutputLimits.TOTAL - total) return false;
            total += count;
        }
        return true;
    }

    public boolean canStart(Map<K, Long> items) {
        return !transferring && fault.isEmpty() && remaining.isEmpty() && fits(items);
    }

    /** Called once by the completion owner, never by the output retry path. */
    public void complete(Map<K, Long> items) {
        if (!canStart(items)) throw new IllegalStateException("Pending output is occupied, faulted or over capacity");
        remaining.putAll(items);
        cursor = 0;
        revision++;
    }

    public long flush(long serverTick, Sink<K> sink) {
        if (transferring || !fault.isEmpty() || remaining.isEmpty() || serverTick == lastTick) return 0;
        lastTick = serverTick;
        List<K> keys = new ArrayList<>(remaining.keySet());
        LinkedHashMap<K, Long> offers = new LinkedHashMap<>();
        int budget = OutputLimits.ITEMS_PER_TICK;
        int visited = 0;
        int start = cursor % keys.size();
        while (visited < keys.size() && visited < OutputLimits.OFFERS_PER_TICK && budget > 0) {
            K key = keys.get((start + visited) % keys.size());
            long amount = Math.min(remaining.get(key), Math.min(budget, OutputLimits.OFFER_AMOUNT));
            offers.put(key, amount);
            budget -= (int) amount;
            visited++;
        }
        // Rotate even when a type is blocked, so it cannot permanently starve other types.
        cursor = (start + visited) % keys.size();
        transferring = true;
        try {
            Map<K, Long> accepted = sink.transfer(Collections.unmodifiableMap(offers));
            if (accepted == null) throw new IllegalStateException("Missing output receipt");
            long total = 0;
            for (Map.Entry<K, Long> entry : accepted.entrySet()) {
                Long offered = offers.get(entry.getKey());
                Long count = entry.getValue();
                if (offered == null || count == null || count < 0 || count > offered) {
                    throw new IllegalStateException("Invalid output receipt");
                }
                total += count;
            }
            for (Map.Entry<K, Long> entry : accepted.entrySet()) {
                long rest = remaining.get(entry.getKey()) - entry.getValue();
                if (rest == 0) remaining.remove(entry.getKey());
                else remaining.put(entry.getKey(), rest);
            }
            if (total > 0) revision++;
            return total;
        } catch (RuntimeException uncertain) {
            // A native commit has no rollback. Retrying an uncertain receipt could duplicate items.
            fault = "Uncertain output: " + uncertain.getClass()
                .getName();
            revision++;
            return 0;
        } finally {
            transferring = false;
        }
    }

    public Map<K, Long> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(remaining));
    }

    public boolean blocksStart() {
        return transferring || !fault.isEmpty() || !remaining.isEmpty();
    }

    public long total() {
        long total = 0;
        for (long count : remaining.values()) total += count;
        return total;
    }

    public String fault() {
        return fault;
    }

    public long revision() {
        return revision;
    }

    public int cursor() {
        return cursor;
    }

    public static <K> PendingOutputs<K> restore(Map<K, Long> items, int cursor, String fault) {
        if (!fits(items) || cursor < 0 || cursor >= OutputLimits.IDENTITIES || fault == null || fault.length() > 256) {
            throw new IllegalArgumentException("Invalid saved output");
        }
        PendingOutputs<K> pending = new PendingOutputs<>();
        pending.remaining.putAll(items);
        pending.cursor = cursor;
        pending.fault = fault;
        return pending;
    }
}
