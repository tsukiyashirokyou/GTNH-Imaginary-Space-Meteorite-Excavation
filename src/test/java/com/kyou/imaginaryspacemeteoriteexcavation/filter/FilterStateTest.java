package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class FilterStateTest {

    private final FilterDefinition first = new FilterDefinition(
        "pack:first",
        "focus:0",
        "hash-1",
        Arrays.asList("ore", "filler"));
    private final FilterDefinition second = new FilterDefinition(
        "pack:second",
        "focus:1",
        "hash-2",
        Arrays.asList("ore", "other"));

    @Test
    public void defaultsContainEveryOutputAndRitualEditsAreIsolated() {
        FilterBookState original = FilterBookState.defaults();
        assertEquals(
            first.items(),
            original.page(first)
                .selected());
        assertEquals(
            second.items(),
            original.page(second)
                .selected());
        FilterBookState changed = original.toggle(first, "ore");
        assertFalse(
            changed.page(first)
                .keeps("ore"));
        assertTrue(
            changed.page(first)
                .keeps("filler"));
        assertTrue(
            changed.page(second)
                .keeps("ore"));
        assertTrue(
            original.page(first)
                .keeps("ore"));
        assertEquals(1, changed.revision());
        assertTrue(changed.configured());
        assertTrue(
            original.pages()
                .isEmpty());
    }

    @Test
    public void emptyWhitelistIsExplicitAndBlacklistInvertsOnlyItsOwnPage() {
        FilterBookState empty = FilterBookState.defaults()
            .toggle(first, "ore")
            .toggle(first, "filler");
        assertTrue(
            empty.page(first)
                .selected()
                .isEmpty());
        assertFalse(
            empty.page(first)
                .keeps("ore"));
        FilterBookState black = empty.changeMode(first);
        assertTrue(
            black.page(first)
                .keeps("ore"));
        assertTrue(
            black.page(first)
                .keeps("filler"));
        assertEquals(
            FilterPage.Mode.WHITELIST,
            black.page(second)
                .mode());
        FilterBookState one = black.toggle(first, "ore");
        assertFalse(
            one.page(first)
                .keeps("ore"));
        assertTrue(
            one.page(first)
                .keeps("filler"));
    }

    @Test
    public void changedRitualRequiresExplicitResetWithoutDamagingOtherPages() {
        FilterBookState edited = FilterBookState.defaults()
            .toggle(first, "ore")
            .toggle(second, "other");
        FilterDefinition changed = new FilterDefinition(first.id(), first.focus(), "changed-hash", first.items());
        reject(() -> edited.page(changed));
        FilterBookState reset = edited.reset(changed);
        assertEquals(
            changed.items(),
            reset.page(changed)
                .selected());
        assertFalse(
            reset.page(second)
                .keeps("other"));
        assertEquals(3, reset.revision());
        assertTrue(reset.configured());
        assertEquals(
            1,
            reset.pages()
                .size());
    }

    @Test
    public void foreignItemsChangedFocusAndRevisionOverflowAreRejected() {
        reject(
            () -> FilterBookState.defaults()
                .toggle(first, "not-an-output"));
        FilterBookState edited = FilterBookState.defaults()
            .toggle(first, "ore");
        FilterDefinition differentFocus = new FilterDefinition(first.id(), "new-focus:0", first.hash(), first.items());
        reject(() -> edited.changeMode(differentFocus));
        FilterBookState maximum = new FilterBookState(true, Long.MAX_VALUE, Collections.emptyMap());
        try {
            maximum.toggle(first, "ore");
            fail("Expected revision overflow");
        } catch (ArithmeticException expected) {}
    }

    private static void reject(Runnable action) {
        try {
            action.run();
            fail("Expected invalid filter rejection");
        } catch (IllegalArgumentException expected) {}
    }
}
