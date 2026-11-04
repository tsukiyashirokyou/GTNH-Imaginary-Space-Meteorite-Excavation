package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Matches explicitly adapted standard definitions to observed runtime entries; never invents missing recipes. */
public final class RecipeCatalogue<K> {

    private final Map<String, Entry<K>> recipes;
    private final Map<String, Entry<K>> byFocus;
    private final List<String> diagnostics;

    private RecipeCatalogue(Map<String, Entry<K>> recipes, List<String> diagnostics) {
        this.recipes = Collections.unmodifiableMap(new LinkedHashMap<>(recipes));
        Map<String, Entry<K>> index = new HashMap<>();
        for (Entry<K> recipe : recipes.values()) index.put(
            recipe.recipe()
                .focusSignature(),
            recipe);
        byFocus = Collections.unmodifiableMap(index);
        this.diagnostics = Collections.unmodifiableList(new ArrayList<>(diagnostics));
    }

    public Map<String, Entry<K>> recipes() {
        return recipes;
    }

    public Optional<Entry<K>> forFocus(String focusSignature) {
        return Optional.ofNullable(byFocus.get(focusSignature));
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public static final class Entry<K> {

        private final String id;
        private final String source;
        private final String sourceSha256;
        private final RecipeSnapshot<K> recipe;

        public Entry(String id, String source, String sourceSha256, RecipeSnapshot<K> recipe) {
            if (id == null || id.isEmpty() || source == null || sourceSha256 == null || recipe == null) {
                throw new IllegalArgumentException("Incomplete source mapping");
            }
            this.id = id;
            this.source = source;
            this.sourceSha256 = sourceSha256;
            this.recipe = recipe;
        }

        public String id() {
            return id;
        }

        public String source() {
            return source;
        }

        public String sourceSha256() {
            return sourceSha256;
        }

        public RecipeSnapshot<K> recipe() {
            return recipe;
        }
    }

    public static final class Builder<K> {

        private final List<Entry<K>> expected = new ArrayList<>();
        private final Map<String, List<RecipeSnapshot<K>>> observed = new HashMap<>();
        private final List<String> diagnostics = new ArrayList<>();

        public void expect(Entry<K> entry) {
            expected.add(entry);
        }

        /** A null snapshot records an invalid entry and still participates in duplicate-focus rejection. */
        public void observe(String focus, RecipeSnapshot<K> snapshot, String error) {
            if (focus == null || focus.isEmpty()) throw new IllegalArgumentException("Missing observed focus");
            if (snapshot != null && !focus.equals(snapshot.focusSignature())) {
                throw new IllegalArgumentException("Observed focus does not match snapshot");
            }
            observed.computeIfAbsent(focus, ignored -> new ArrayList<>())
                .add(snapshot);
            if (error != null) diagnose("INVALID_RUNTIME " + focus + ": " + error);
        }

        public void diagnose(String message) {
            diagnostics.add(message);
        }

        public RecipeCatalogue<K> build() {
            List<String> issues = new ArrayList<>(diagnostics);
            Map<String, Integer> focusCounts = new HashMap<>();
            Map<String, Integer> idCounts = new HashMap<>();
            for (Entry<K> source : expected) {
                focusCounts.merge(
                    source.recipe()
                        .focusSignature(),
                    1,
                    Integer::sum);
                idCounts.merge(source.id(), 1, Integer::sum);
            }
            Map<String, Entry<K>> accepted = new TreeMap<>();
            for (Entry<K> source : expected) {
                String focus = source.recipe()
                    .focusSignature();
                List<RecipeSnapshot<K>> candidates = observed.get(focus);
                if (focusCounts.get(focus) != 1 || idCounts.get(source.id()) != 1) {
                    issues.add("AMBIGUOUS_SOURCE " + source.id());
                } else if (candidates == null) {
                    issues.add("MISSING_RUNTIME " + source.id());
                } else if (candidates.size() != 1) {
                    issues.add("DUPLICATE_RUNTIME_FOCUS " + focus);
                } else if (candidates.get(0) == null) {
                    issues.add("UNSUPPORTED_RUNTIME " + source.id());
                } else if (!source.recipe()
                    .configurationHash()
                    .equals(
                        candidates.get(0)
                            .configurationHash())) {
                                issues.add(
                                    "CHANGED_RUNTIME " + source.id()
                                        + " expected="
                                        + source.recipe()
                                            .configurationHash()
                                        + " observed="
                                        + candidates.get(0)
                                            .configurationHash());
                            } else {
                                accepted.put(
                                    source.id(),
                                    new Entry<>(
                                        source.id(),
                                        source.source(),
                                        source.sourceSha256(),
                                        candidates.get(0)));
                            }
            }
            for (String focus : new TreeMap<>(observed).keySet()) {
                if (!focusCounts.containsKey(focus)) issues.add("UNADAPTED_RUNTIME_FOCUS " + focus);
            }
            return new RecipeCatalogue<>(accepted, issues);
        }
    }
}
