package com.kyou.imaginaryspacemeteoriteexcavation.output;

/** Fixed safety/work bounds, independent of the number of individual generated blocks. */
public final class OutputLimits {

    public static final int IDENTITIES = 1024;
    public static final long PER_IDENTITY = 1L << 40;
    public static final long TOTAL = 1L << 40;
    public static final int IDENTITY_BYTES = 64 * 1024;
    public static final int SERIALIZED_BYTES = 8 * 1024 * 1024;
    public static final int BUSSES = 64;
    public static final int ORDINARY_SLOTS = 4096;
    public static final int OFFERS_PER_TICK = 64;
    public static final int OFFER_AMOUNT = 1 << 20;
    public static final int ITEMS_PER_TICK = 1 << 24;

    private OutputLimits() {}
}
