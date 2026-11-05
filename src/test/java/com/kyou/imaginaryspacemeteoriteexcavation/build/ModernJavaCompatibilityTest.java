package com.kyou.imaginaryspacemeteoriteexcavation.build;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Test;

/** Build-system smoke tests, executed by the official downgraded test path; no machine behavior. */
public final class ModernJavaCompatibilityTest {

    private record Entry(String key, int amount) {}

    private static String describe(Object value) {
        return switch (value) {
            case Entry(String key, int amount) when amount > 0 -> "%s=%d".formatted(key, amount);
            default -> "empty";
        };
    }

    @Test
    public void modernPatternsAndRecordsSurviveDowngrading() {
        assertEquals("ore=4", describe(new Entry("ore", 4)));
        assertEquals("empty", describe(new Entry("ore", 0)));
        assertEquals(new Entry("ore", 4), new Entry("ore", 4));
    }

    @Test
    public void supportedModernCollectionAndStringApisWork() {
        List<String> values = List.copyOf(List.of("ore", "filler"));
        assertEquals(
            "filler",
            values.reversed()
                .getFirst());
        assertEquals(
            List.of("ORE", "FILLER"),
            values.stream()
                .map(String::toUpperCase)
                .toList());
        assertThrows(UnsupportedOperationException.class, () -> values.add("extra"));
        assertEquals(
            List.of("ore", "filler"),
            "  ore\nfiller  ".strip()
                .lines()
                .toList());
        assertTrue("  \t".isBlank());
    }

    private static class Base {

        final int value;

        Base(int value) {
            this.value = value;
        }
    }

    private static final class Validated extends Base {

        Validated(int value) {
            if (value < 0) throw new IllegalArgumentException("negative");
            super(value); // Java 25 flexible constructor bodies, without preview features.
        }
    }

    @Test
    public void java25ConstructorBodiesSurviveDowngrading() {
        assertEquals(7, new Validated(7).value);
        assertThrows(IllegalArgumentException.class, () -> new Validated(-1));
    }
}
