package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/** One supported ritual's final item identities, including filling blocks. */
public final class FilterDefinition {

    private final String id;
    private final String focus;
    private final String hash;
    private final Set<String> items;

    public FilterDefinition(String id, String focus, String hash, Collection<String> items) {
        this.id = required(id);
        this.focus = required(focus);
        this.hash = required(hash);
        TreeSet<String> sorted = new TreeSet<>();
        for (String item : items) sorted.add(required(item));
        this.items = Collections.unmodifiableSet(new LinkedHashSet<>(sorted));
    }

    static String required(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Missing filter identity");
        return value;
    }

    public String id() {
        return id;
    }

    public String focus() {
        return focus;
    }

    public String hash() {
        return hash;
    }

    public Set<String> items() {
        return items;
    }
}
