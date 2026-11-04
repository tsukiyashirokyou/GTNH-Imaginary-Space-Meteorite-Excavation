package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

/** Exact lattice geometry for the source-audited standard radius domain. */
public final class MeteorGeometry {

    public static final int MAX_STANDARD_RADIUS = 32;
    private static final long[] VOLUMES = new long[MAX_STANDARD_RADIUS + 1];

    static {
        for (int r = 1; r <= MAX_STANDARD_RADIUS; r++) {
            int limit = r * (r + 1);
            long total = 0;
            for (int x = -r; x <= r; x++) {
                for (int y = -r; y <= r; y++) {
                    int remaining = limit - x * x - y * y;
                    if (remaining < 0) continue;
                    int z = (int) Math.sqrt(remaining);
                    while ((z + 1) * (z + 1) <= remaining) z++;
                    while (z * z > remaining) z--;
                    total += 2L * Math.min(r, z) + 1L;
                }
            }
            VOLUMES[r] = total;
        }
    }

    private MeteorGeometry() {}

    public static long positions(int radius) {
        if (radius < 1 || radius > MAX_STANDARD_RADIUS) {
            throw new IllegalArgumentException("Radius outside audited standard domain: " + radius);
        }
        return VOLUMES[radius];
    }

    public static boolean hasEnoughTowers(int radius, int towers) {
        positions(radius);
        return towers >= 0 && 3L * (radius + 1L) <= towers;
    }
}
