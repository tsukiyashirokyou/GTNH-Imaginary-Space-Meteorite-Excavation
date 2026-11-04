package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable cached per-ritual expectations. Does not reference Minecraft, World or Random. */
public final class ExpectedYield<K> {

    private final long positions;
    private final Map<K, ExactFraction> expectations;

    public ExpectedYield(int radius, float fillerChance, List<WeightedComponent<K>> ores,
        List<WeightedComponent<K>> filler) {
        positions = MeteorGeometry.positions(radius);
        ExactFraction fillerProbability = FillerProbability.fromPercent(fillerChance);
        Map<K, ExactFraction> probabilities = new LinkedHashMap<>();
        addBranch(probabilities, ores, fillerProbability.complement());
        addBranch(probabilities, filler, fillerProbability);
        Map<K, ExactFraction> values = new LinkedHashMap<>();
        ExactFraction sum = ExactFraction.ZERO;
        for (Map.Entry<K, ExactFraction> entry : probabilities.entrySet()) {
            ExactFraction expected = entry.getValue()
                .multiply(positions);
            values.put(entry.getKey(), expected);
            sum = sum.add(expected);
        }
        if (!sum.equals(ExactFraction.ONE.multiply(positions))) {
            throw new IllegalArgumentException("Incomplete probability model");
        }
        expectations = Collections.unmodifiableMap(values);
    }

    private static <K> void addBranch(Map<K, ExactFraction> result, List<WeightedComponent<K>> components,
        ExactFraction branch) {
        if (branch.signum() == 0) return;
        if (components == null || components.isEmpty()) throw new IllegalArgumentException("Empty active branch");
        long total = 0;
        for (WeightedComponent<K> component : components) {
            total = Math.addExact(total, component.weight());
        }
        if (total <= 0 || total > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Branch weight outside native nextInt range: " + total);
        }
        for (WeightedComponent<K> component : components) {
            if (component.weight() == 0) continue;
            ExactFraction value = branch.multiply(ExactFraction.of(component.weight(), total));
            result.merge(component.item(), value, ExactFraction::add);
        }
    }

    public long positions() {
        return positions;
    }

    public Map<K, ExactFraction> expectations() {
        return expectations;
    }

    public BatchYield<K> forBatch(long batch) {
        return new BatchYield<>(batch, expectations);
    }
}
