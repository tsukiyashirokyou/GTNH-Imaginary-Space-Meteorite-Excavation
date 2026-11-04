package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** Immutable explicit list: selected means kept in whitelist mode and rejected in blacklist mode. */
public final class FilterPage {

    public enum Mode {
        WHITELIST,
        BLACKLIST
    }

    private final String focus;
    private final String hash;
    private final Mode mode;
    private final Set<String> selected;

    public FilterPage(String focus, String hash, Mode mode, Collection<String> selected) {
        this.focus = FilterDefinition.required(focus);
        this.hash = FilterDefinition.required(hash);
        if (mode == null) throw new IllegalArgumentException("Missing filter mode");
        this.mode = mode;
        TreeSet<String> sorted = new TreeSet<>();
        for (String item : selected) {
            if (!sorted.add(FilterDefinition.required(item)))
                throw new IllegalArgumentException("Duplicate filter item");
        }
        this.selected = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    public static FilterPage defaults(FilterDefinition definition) {
        return new FilterPage(definition.focus(), definition.hash(), Mode.WHITELIST, definition.items());
    }

    public boolean compatible(FilterDefinition definition) {
        return focus.equals(definition.focus()) && hash.equals(definition.hash())
            && definition.items()
                .containsAll(selected);
    }

    public boolean keeps(String identity) {
        return selected.contains(identity) == (mode == Mode.WHITELIST);
    }

    public String focus() {
        return focus;
    }

    public String hash() {
        return hash;
    }

    public Mode mode() {
        return mode;
    }

    public Set<String> selected() {
        return selected;
    }
}
