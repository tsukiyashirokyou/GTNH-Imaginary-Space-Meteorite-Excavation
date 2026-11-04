package com.kyou.imaginaryspacemeteoriteexcavation.processing;

/** Read-only binding data. The runtime adapter, not a GUI request, decides whether an orb is Armok. */
public final class BoundNetwork {

    private final String owner;
    private final String orbSignature;
    private final boolean free;

    public BoundNetwork(String owner, String orbSignature, boolean free) {
        if (owner == null || owner.isEmpty() || orbSignature == null || orbSignature.isEmpty()) {
            throw new IllegalArgumentException("Missing bound orb identity");
        }
        this.owner = owner;
        this.orbSignature = orbSignature;
        this.free = free;
    }

    public String owner() {
        return owner;
    }

    public String orbSignature() {
        return orbSignature;
    }

    public boolean free() {
        return free;
    }
}
