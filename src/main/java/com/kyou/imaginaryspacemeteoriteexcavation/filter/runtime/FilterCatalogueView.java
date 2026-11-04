package com.kyou.imaginaryspacemeteoriteexcavation.filter.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.kyou.imaginaryspacemeteoriteexcavation.filter.FilterDefinition;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.ItemIdentity;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime.MeteorCatalogueService;

/** One bounded view per immutable runtime catalogue, rebuilt only when that catalogue changes. */
public final class FilterCatalogueView {

    private static volatile FilterCatalogueView cached;
    private final RecipeCatalogue<ItemIdentity> source;
    private final List<Ritual> rituals;
    private final Map<String, Ritual> byId;

    private FilterCatalogueView(RecipeCatalogue<ItemIdentity> source) {
        this.source = source;
        Map<String, Ritual> indexed = new LinkedHashMap<>();
        for (RecipeCatalogue.Entry<ItemIdentity> entry : source.recipes()
            .values()) {
            Ritual ritual = new Ritual(entry);
            indexed.put(ritual.definition.id(), ritual);
        }
        byId = Collections.unmodifiableMap(indexed);
        rituals = Collections.unmodifiableList(new ArrayList<>(indexed.values()));
    }

    public static FilterCatalogueView current() {
        RecipeCatalogue<ItemIdentity> source = MeteorCatalogueService.catalogue();
        FilterCatalogueView view = cached;
        if (view == null || view.source != source) {
            synchronized (FilterCatalogueView.class) {
                view = cached;
                if (view == null || view.source != source) cached = view = new FilterCatalogueView(source);
            }
        }
        return view;
    }

    public List<Ritual> rituals() {
        return rituals;
    }

    public Ritual forId(String id) {
        return byId.get(id);
    }

    public static final class Ritual {

        private final RecipeCatalogue.Entry<ItemIdentity> entry;
        private final FilterDefinition definition;
        private final List<ItemIdentity> items;

        private Ritual(RecipeCatalogue.Entry<ItemIdentity> entry) {
            this.entry = entry;
            Map<String, ItemIdentity> sorted = new TreeMap<>();
            for (ItemIdentity item : entry.recipe()
                .yield()
                .expectations()
                .keySet()) sorted.put(item.signature(), item);
            items = Collections.unmodifiableList(new ArrayList<>(sorted.values()));
            definition = new FilterDefinition(
                entry.id(),
                entry.recipe()
                    .focusSignature(),
                entry.recipe()
                    .configurationHash(),
                sorted.keySet());
        }

        public FilterDefinition definition() {
            return definition;
        }

        public RecipeCatalogue.Entry<ItemIdentity> entry() {
            return entry;
        }

        public List<ItemIdentity> items() {
            return items;
        }
    }
}
