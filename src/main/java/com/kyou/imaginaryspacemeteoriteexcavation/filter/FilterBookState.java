package com.kyou.imaginaryspacemeteoriteexcavation.filter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Sparse immutable overrides. Missing pages are full default whitelists, never empty whitelists. */
public final class FilterBookState {

    private final boolean configured;
    private final long revision;
    private final Map<String, FilterPage> pages;

    public FilterBookState(boolean configured, long revision, Map<String, FilterPage> pages) {
        if (revision < 0 || (!configured && (revision != 0 || !pages.isEmpty()))) {
            throw new IllegalArgumentException("Invalid book revision/default marker");
        }
        TreeMap<String, FilterPage> sorted = new TreeMap<>();
        for (Map.Entry<String, FilterPage> page : pages.entrySet()) {
            if (page.getValue() == null) throw new IllegalArgumentException("Missing filter page");
            sorted.put(FilterDefinition.required(page.getKey()), page.getValue());
        }
        this.configured = configured;
        this.revision = revision;
        this.pages = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static FilterBookState defaults() {
        return new FilterBookState(false, 0, Collections.emptyMap());
    }

    public FilterPage page(FilterDefinition definition) {
        FilterPage page = pages.get(definition.id());
        if (page == null) return FilterPage.defaults(definition);
        if (!page.compatible(definition)) throw new IllegalArgumentException("Stale or invalid ritual page");
        return page;
    }

    public FilterBookState toggle(FilterDefinition definition, String item) {
        if (!definition.items()
            .contains(item)) throw new IllegalArgumentException("Item is not on this ritual page");
        FilterPage page = page(definition);
        Set<String> selected = new LinkedHashSet<>(page.selected());
        if (!selected.remove(item)) selected.add(item);
        return replace(definition.id(), new FilterPage(page.focus(), page.hash(), page.mode(), selected));
    }

    public FilterBookState changeMode(FilterDefinition definition) {
        FilterPage page = page(definition);
        FilterPage.Mode mode = page.mode() == FilterPage.Mode.WHITELIST ? FilterPage.Mode.BLACKLIST
            : FilterPage.Mode.WHITELIST;
        return replace(definition.id(), new FilterPage(page.focus(), page.hash(), mode, page.selected()));
    }

    public FilterBookState reset(FilterDefinition definition) {
        return replace(definition.id(), null);
    }

    private FilterBookState replace(String id, FilterPage page) {
        Map<String, FilterPage> changed = new LinkedHashMap<>(pages);
        if (page == null) changed.remove(id);
        else changed.put(id, page);
        return new FilterBookState(true, Math.addExact(revision, 1L), changed);
    }

    public boolean configured() {
        return configured;
    }

    public long revision() {
        return revision;
    }

    public Map<String, FilterPage> pages() {
        return pages;
    }
}
