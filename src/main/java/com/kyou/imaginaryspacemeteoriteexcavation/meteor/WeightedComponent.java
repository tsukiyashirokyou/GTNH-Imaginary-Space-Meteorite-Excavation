package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.util.Objects;

/** K must be an immutable final-item identity, including metadata and NBT. */
public final class WeightedComponent<K> {

    private final K item;
    private final int weight;

    public WeightedComponent(K item, int weight) {
        this.item = Objects.requireNonNull(item, "item");
        if (weight < 0) throw new IllegalArgumentException("Negative component weight");
        this.weight = weight;
    }

    public K item() {
        return item;
    }

    public int weight() {
        return weight;
    }
}
