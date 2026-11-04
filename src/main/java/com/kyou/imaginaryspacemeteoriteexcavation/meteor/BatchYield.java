package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** A pre-filter integer plan, not a resource transaction or a collection of oversized stacks. */
public final class BatchYield<K> {

    private final long batch;
    private final Map<K, Long> quantities;
    private final long total;

    BatchYield(long batch, Map<K, ExactFraction> expectations) {
        if (batch <= 0) throw new IllegalArgumentException("Batch must be positive");
        this.batch = batch;
        Map<K, Long> result = new LinkedHashMap<>();
        long sum = 0;
        for (Map.Entry<K, ExactFraction> entry : expectations.entrySet()) {
            long quantity = entry.getValue()
                .multiply(batch)
                .floorLongExact();
            if (quantity < 0) throw new IllegalArgumentException("Negative yield");
            if (quantity == 0) continue;
            sum = Math.addExact(sum, quantity);
            result.put(entry.getKey(), quantity);
        }
        quantities = Collections.unmodifiableMap(result);
        total = sum;
    }

    public long batch() {
        return batch;
    }

    public Map<K, Long> quantities() {
        return quantities;
    }

    /** Also the approved drilling-fluid basis in liters; this class performs no deduction. */
    public long prefilterTotal() {
        return total;
    }
}
