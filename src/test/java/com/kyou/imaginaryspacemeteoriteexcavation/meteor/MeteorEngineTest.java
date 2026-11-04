package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Executes production math and catalogue logic; string keys deliberately do not imitate a live registry. */
public final class MeteorEngineTest {

    @Test
    public void fractionsReduceAndFloorWithoutFloatingError() {
        assertEquals(ExactFraction.of(1, 2), ExactFraction.of(5, 10));
        assertEquals(
            ExactFraction.of(5, 6),
            ExactFraction.of(1, 2)
                .add(ExactFraction.of(1, 3)));
        assertEquals(
            4,
            ExactFraction.of(8, 5)
                .multiply(3)
                .floorLongExact());
        assertEquals(
            -2,
            ExactFraction.of(-8, 5)
                .floorLongExact());
        assertEquals(
            ExactFraction.of(2, 3),
            ExactFraction.of(1, 3)
                .complement());
        reject(IllegalArgumentException.class, () -> ExactFraction.of(1, 0));
        reject(
            ArithmeticException.class,
            () -> ExactFraction.of(Long.MAX_VALUE, 1)
                .multiply(2)
                .floorLongExact());
    }

    @Test
    public void everySupportedSphereMatchesTheSourceFloatBoundary() {
        for (int radius = 1; radius <= MeteorGeometry.MAX_STANDARD_RADIUS; radius++) {
            long expected = 0;
            float edge = (radius + .50f) * (radius + .50f);
            for (int x = -radius; x <= radius; x++) {
                for (int y = -radius; y <= radius; y++) {
                    for (int z = -radius; z <= radius; z++) {
                        if (x * x + y * y + z * z < edge) expected++;
                    }
                }
            }
            assertEquals(expected, MeteorGeometry.positions(radius));
            assertFalse(MeteorGeometry.hasEnoughTowers(radius, 3 * (radius + 1) - 1));
            assertTrue(MeteorGeometry.hasEnoughTowers(radius, 3 * (radius + 1)));
            assertTrue(MeteorGeometry.hasEnoughTowers(radius, Integer.MAX_VALUE));
            assertFalse(MeteorGeometry.hasEnoughTowers(radius, -1));
        }
        assertEquals(19, MeteorGeometry.positions(1));
        assertEquals(143761, MeteorGeometry.positions(32));
        reject(IllegalArgumentException.class, () -> MeteorGeometry.positions(0));
        reject(IllegalArgumentException.class, () -> MeteorGeometry.positions(33));
    }

    @Test
    public strictfp void floatThresholdMatchesExhaustive24BitSupport() {
        long filling = 0;
        for (int value = 0; value < (1 << 24); value++) {
            float sample = (float) value / (1 << 24);
            if (sample * 100f < 20f) filling++;
        }
        assertEquals(ExactFraction.of(filling, 1 << 24), FillerProbability.fromPercent(20));
        assertEquals(ExactFraction.of(838861, 4194304), FillerProbability.fromPercent(20));
        assertFalse(
            ExactFraction.of(1, 5)
                .equals(FillerProbability.fromPercent(20)));
        assertEquals(ExactFraction.ZERO, FillerProbability.fromPercent(0));
        assertEquals(ExactFraction.ONE, FillerProbability.fromPercent(100));
        assertEquals(ExactFraction.of(1, 2), FillerProbability.fromPercent(50));
        reject(IllegalArgumentException.class, () -> FillerProbability.fromPercent(Float.NaN));
        reject(IllegalArgumentException.class, () -> FillerProbability.fromPercent(Float.POSITIVE_INFINITY));
    }

    @Test
    public void mutualBranchesAndDuplicateIdentitiesMergeBeforeFlooring() {
        List<WeightedComponent<String>> ores = Arrays
            .asList(component("shared", 3), component("shared", 3), component("other", 89));
        ExpectedYield<String> model = new ExpectedYield<>(1, 0, ores, Collections.emptyList());
        assertEquals(
            ExactFraction.of(6, 5),
            model.expectations()
                .get("shared"));
        assertEquals(
            Long.valueOf(1),
            model.forBatch(1)
                .quantities()
                .get("shared"));
        assertEquals(
            Long.valueOf(3),
            model.forBatch(3)
                .quantities()
                .get("shared"));
        assertFalse(
            model.expectations()
                .containsKey("unused filler"));
        ExpectedYield<String> combined = new ExpectedYield<>(
            1,
            20,
            Arrays.asList(component("shared", 2), component("ore", 1)),
            Arrays.asList(component("shared", 3), component("filler", 1)));
        ExactFraction q = FillerProbability.fromPercent(20);
        ExactFraction expected = q.complement()
            .multiply(ExactFraction.of(2, 3))
            .add(q.multiply(ExactFraction.of(3, 4)))
            .multiply(19);
        assertEquals(
            expected,
            combined.expectations()
                .get("shared"));
        assertEquals(ExactFraction.of(19, 1), sum(combined.expectations()));
        reject(
            UnsupportedOperationException.class,
            () -> combined.expectations()
                .clear());
        reject(
            UnsupportedOperationException.class,
            () -> combined.forBatch(3)
                .quantities()
                .clear());
    }

    @Test
    public void approvedBatchExampleAndPrefilterBasisHaveNoCarry() {
        Map<String, ExactFraction> expected = new LinkedHashMap<>();
        expected.put("ore", ExactFraction.of(8, 5));
        expected.put("filler", ExactFraction.of(27, 10));
        BatchYield<String> one = new BatchYield<>(1, expected);
        BatchYield<String> three = new BatchYield<>(3, expected);
        assertEquals(
            Long.valueOf(4),
            three.quantities()
                .get("ore"));
        assertEquals(
            Long.valueOf(8),
            three.quantities()
                .get("filler"));
        assertEquals(12, three.prefilterTotal());
        assertEquals(1, three.prefilterTotal() - one.prefilterTotal() - new BatchYield<>(2, expected).prefilterTotal());
        assertEquals(9, three.prefilterTotal() - one.prefilterTotal());
        assertEquals(three.quantities(), new BatchYield<>(3, expected).quantities());
        assertEquals(
            Long.valueOf(1),
            new BatchYield<>(1, expected).quantities()
                .get("ore"));
    }

    @Test
    public void invalidWeightsCountsAndOverflowAreRejected() {
        reject(IllegalArgumentException.class, () -> component("negative", -1));
        reject(
            IllegalArgumentException.class,
            () -> new ExpectedYield<>(1, 0, Collections.singletonList(component("zero", 0)), Collections.emptyList()));
        reject(
            IllegalArgumentException.class,
            () -> new ExpectedYield<>(
                1,
                0,
                Arrays.asList(component("a", Integer.MAX_VALUE), component("b", 1)),
                Collections.emptyList()));
        ExpectedYield<String> model = new ExpectedYield<>(
            1,
            0,
            Collections.singletonList(component("stone", 1)),
            Collections.emptyList());
        reject(IllegalArgumentException.class, () -> model.forBatch(0));
        reject(IllegalArgumentException.class, () -> model.forBatch(-1));
        reject(ArithmeticException.class, () -> model.forBatch(Long.MAX_VALUE));
        Map<String, ExactFraction> overflow = new LinkedHashMap<>();
        overflow.put("first", ExactFraction.of(Long.MAX_VALUE, 1));
        overflow.put("second", ExactFraction.ONE);
        reject(ArithmeticException.class, () -> new BatchYield<>(1, overflow));
        long safe = Long.MAX_VALUE / 19;
        assertEquals(
            Math.multiplyExact(safe, 19),
            model.forBatch(safe)
                .prefilterTotal());
    }

    @Test
    public void catalogueRejectsMissingChangedDuplicateAndUnknownEntries() {
        RecipeSnapshot<String> base = snapshot("focus:0", "ore", 1);
        RecipeCatalogue.Entry<String> source = new RecipeCatalogue.Entry<>("stable", "file.json", "source-hash", base);
        RecipeCatalogue.Builder<String> valid = new RecipeCatalogue.Builder<>();
        valid.expect(source);
        valid.observe(base.focusSignature(), base, null);
        RecipeCatalogue<String> accepted = valid.build();
        assertEquals(
            1,
            accepted.recipes()
                .size());
        assertEquals(
            "stable",
            accepted.forFocus("focus:0")
                .get()
                .id());
        assertFalse(
            accepted.forFocus("focus:1")
                .isPresent());
        assertEquals(Collections.emptyList(), accepted.diagnostics());
        reject(
            UnsupportedOperationException.class,
            () -> accepted.recipes()
                .clear());

        RecipeCatalogue.Builder<String> missing = new RecipeCatalogue.Builder<>();
        missing.expect(source);
        assertTrue(
            missing.build()
                .diagnostics()
                .get(0)
                .startsWith("MISSING_RUNTIME"));
        RecipeCatalogue.Builder<String> changed = new RecipeCatalogue.Builder<>();
        changed.expect(source);
        changed.observe("focus:0", snapshot("focus:0", "different", 1), null);
        assertTrue(
            changed.build()
                .recipes()
                .isEmpty());
        assertTrue(
            changed.build()
                .diagnostics()
                .get(0)
                .startsWith("CHANGED_RUNTIME"));
        RecipeCatalogue.Builder<String> duplicate = new RecipeCatalogue.Builder<>();
        duplicate.expect(source);
        duplicate.observe("focus:0", base, null);
        duplicate.observe("focus:0", null, "bad second registration");
        assertTrue(
            duplicate.build()
                .recipes()
                .isEmpty());
        assertTrue(
            duplicate.build()
                .diagnostics()
                .stream()
                .anyMatch(s -> s.startsWith("DUPLICATE_RUNTIME")));
        RecipeCatalogue.Builder<String> unknown = new RecipeCatalogue.Builder<>();
        unknown.observe("new:0", snapshot("new:0", "ore", 1), null);
        assertTrue(
            unknown.build()
                .recipes()
                .isEmpty());
        assertTrue(
            unknown.build()
                .diagnostics()
                .get(0)
                .startsWith("UNADAPTED_RUNTIME"));
        RecipeCatalogue.Builder<String> ambiguous = new RecipeCatalogue.Builder<>();
        ambiguous.expect(source);
        ambiguous.expect(new RecipeCatalogue.Entry<>("other-id", "file2.json", "hash2", base));
        ambiguous.observe("focus:0", base, null);
        assertTrue(
            ambiguous.build()
                .recipes()
                .isEmpty());
    }

    @Test
    public void sourceMappingAndAllLiteralModelsMatchIndependentPythonReference() throws Exception {
        List<StandardMeteorDefinitions.Definition> definitions = StandardMeteorDefinitions.load();
        assertEquals(57, definitions.size());
        JsonObject reference;
        try (InputStreamReader reader = new InputStreamReader(
            getClass().getResourceAsStream("/meteor-literal-reference.json"),
            StandardCharsets.UTF_8)) {
            reference = new JsonParser().parse(reader)
                .getAsJsonObject();
        }
        Map<String, JsonObject> records = new LinkedHashMap<>();
        for (JsonElement value : reference.getAsJsonArray("records")) {
            JsonObject record = value.getAsJsonObject();
            records.put(
                record.get("id")
                    .getAsString(),
                record);
        }
        RecipeCatalogue.Builder<String> catalogue = new RecipeCatalogue.Builder<>();
        List<RecipeSnapshot<String>> observed = new ArrayList<>();
        int components = 0;
        for (StandardMeteorDefinitions.Definition source : definitions) {
            components += source.ores.size() + source.filler.size();
            RecipeSnapshot<String> model = new RecipeSnapshot<>(
                source.focus,
                source.radius,
                source.lpCost,
                source.fillerChance,
                literalBranch(source.ores),
                literalBranch(source.filler),
                Function.identity());
            catalogue.expect(new RecipeCatalogue.Entry<>(source.id, source.source, source.sourceSha256, model));
            observed.add(model);
            JsonObject record = records.get(source.id);
            assertEquals(
                record.get("positions")
                    .getAsLong(),
                model.yield()
                    .positions());
            JsonObject expectations = record.getAsJsonObject("expectations");
            assertEquals(
                expectations.entrySet()
                    .size(),
                model.yield()
                    .expectations()
                    .size());
            for (Map.Entry<String, JsonElement> item : expectations.entrySet()) {
                assertEquals(
                    source.id + " " + item.getKey(),
                    item.getValue()
                        .getAsString(),
                    model.yield()
                        .expectations()
                        .get(item.getKey())
                        .toString());
            }
            for (JsonElement value : record.getAsJsonArray("batches")) {
                JsonObject batch = value.getAsJsonObject();
                BatchYield<String> result = model.yield()
                    .forBatch(
                        batch.get("batch")
                            .getAsLong());
                assertEquals(
                    batch.get("total")
                        .getAsLong(),
                    result.prefilterTotal());
                JsonObject quantities = batch.getAsJsonObject("quantities");
                assertEquals(
                    quantities.entrySet()
                        .size(),
                    result.quantities()
                        .size());
                for (Map.Entry<String, JsonElement> item : quantities.entrySet()) {
                    assertEquals(
                        Long.valueOf(
                            item.getValue()
                                .getAsLong()),
                        result.quantities()
                            .get(item.getKey()));
                }
            }
        }
        Collections.reverse(observed);
        for (RecipeSnapshot<String> entry : observed) catalogue.observe(entry.focusSignature(), entry, null);
        assertEquals(703, components);
        assertEquals(
            57,
            catalogue.build()
                .recipes()
                .size());
        assertTrue(
            catalogue.build()
                .diagnostics()
                .isEmpty());
    }

    @Test
    public void observedFocusCannotMislabelARecipe() {
        RecipeCatalogue.Builder<String> builder = new RecipeCatalogue.Builder<>();
        RecipeSnapshot<String> value = snapshot("focus:0", "ore", 1);
        reject(IllegalArgumentException.class, () -> builder.observe("other:0", value, null));
        reject(IllegalArgumentException.class, () -> builder.observe(null, value, null));
        assertTrue(
            builder.build()
                .recipes()
                .isEmpty());
    }

    private static ExactFraction sum(Map<String, ExactFraction> values) {
        ExactFraction result = ExactFraction.ZERO;
        for (ExactFraction value : values.values()) result = result.add(value);
        return result;
    }

    private static WeightedComponent<String> component(String key, int weight) {
        return new WeightedComponent<>(key, weight);
    }

    private static RecipeSnapshot<String> snapshot(String focus, String item, int radius) {
        return new RecipeSnapshot<>(
            focus,
            radius,
            10,
            0,
            Collections.singletonList(component(item, 1)),
            Collections.emptyList(),
            Function.identity());
    }

    private static List<WeightedComponent<String>> literalBranch(List<String> descriptors) {
        List<WeightedComponent<String>> result = new ArrayList<>();
        for (String text : descriptors) {
            int separator = text.lastIndexOf(':');
            result.add(component(text.substring(0, separator), Integer.parseInt(text.substring(separator + 1))));
        }
        if (result.isEmpty()) result.add(component("minecraft:stone:0", 1));
        return result;
    }

    private static void reject(Class<? extends RuntimeException> expected, Runnable operation) {
        try {
            operation.run();
            fail("Expected " + expected.getSimpleName());
        } catch (RuntimeException error) {
            if (!expected.isInstance(error)) throw error;
        }
    }
}
