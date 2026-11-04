package com.kyou.imaginaryspacemeteoriteexcavation.meteor.runtime;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kyou.imaginaryspacemeteoriteexcavation.ImaginarySpaceMeteoriteExcavation;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.ExactFraction;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeCatalogue;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.RecipeSnapshot;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.StandardMeteorDefinitions;
import com.kyou.imaginaryspacemeteoriteexcavation.meteor.WeightedComponent;

import WayofTime.alchemicalWizardry.common.summoning.meteor.Meteor;
import WayofTime.alchemicalWizardry.common.summoning.meteor.MeteorComponent;
import WayofTime.alchemicalWizardry.common.summoning.meteor.MeteorRegistry;
import cpw.mods.fml.common.registry.GameRegistry;

/** Rebuilds only at lifecycle boundaries, then publishes an immutable directory for later stages. */
public final class MeteorCatalogueService {

    private static volatile RecipeCatalogue<ItemIdentity> catalogue = new RecipeCatalogue.Builder<ItemIdentity>()
        .build();

    private MeteorCatalogueService() {}

    public static RecipeCatalogue<ItemIdentity> catalogue() {
        return catalogue;
    }

    public static void refresh(File configDirectory) {
        RecipeCatalogue.Builder<ItemIdentity> builder = new RecipeCatalogue.Builder<>();
        JsonArray components = new JsonArray();
        int observedCount = 0;
        try {
            for (StandardMeteorDefinitions.Definition source : StandardMeteorDefinitions.load()) {
                try {
                    List<MeteorComponent> ores = resolveComponents(source, "ores", source.ores, builder, components);
                    List<MeteorComponent> filler = resolveComponents(
                        source,
                        "filler",
                        source.filler,
                        builder,
                        components);
                    ItemStack focus = resolveFocus(source.focus);
                    Meteor expected = new Meteor(
                        focus,
                        source.radius,
                        source.lpCost,
                        source.fillerChance,
                        ores,
                        filler);
                    expected.validate();
                    builder.expect(
                        new RecipeCatalogue.Entry<>(source.id, source.source, source.sourceSha256, snapshot(expected)));
                } catch (RuntimeException error) {
                    builder.diagnose("UNSUPPORTED_SOURCE " + source.id + ": " + describe(error));
                }
            }
            List<Meteor> registered = new ArrayList<>(MeteorRegistry.meteorList);
            observedCount = registered.size();
            for (int index = 0; index < registered.size(); index++) {
                Meteor meteor = registered.get(index);
                String focus = "invalid-runtime-entry-" + index;
                try {
                    if (meteor == null) throw new IllegalArgumentException("Null registered meteor");
                    focus = ItemIdentity.focusSignature(meteor.focusItem);
                    if (meteor.focusItem.getItemDamage() == OreDictionary.WILDCARD_VALUE) {
                        throw new IllegalArgumentException("Wildcard focus requires explicit adaptation");
                    }
                    builder.observe(focus, snapshot(meteor), null);
                } catch (RuntimeException error) {
                    builder.observe(focus, null, describe(error));
                }
            }
        } catch (RuntimeException error) {
            // Publish a closed directory if the source map/registry itself is unavailable.
            builder = new RecipeCatalogue.Builder<>();
            builder.diagnose("CATALOGUE_UNAVAILABLE: " + describe(error));
        }
        RecipeCatalogue<ItemIdentity> result = builder.build();
        catalogue = result;
        writeReport(configDirectory, result, components, observedCount);
        ImaginarySpaceMeteoriteExcavation.LOG.info(
            "Meteor catalogue: {} supported, {} diagnostics (no production enabled)",
            result.recipes()
                .size(),
            result.diagnostics()
                .size());
    }

    private static String describe(RuntimeException error) {
        return error.getClass()
            .getSimpleName() + ": "
            + error.getMessage();
    }

    private static ItemStack resolveFocus(String text) {
        String[] parts = text.split(":", -1);
        if (parts.length < 2 || parts.length > 3)
            throw new IllegalArgumentException("Invalid focus descriptor: " + text);
        ItemStack stack = GameRegistry.findItemStack(parts[0], parts[1], 1);
        if (stack == null) throw new IllegalArgumentException("Missing focus: " + text);
        if (parts.length == 3) stack.setItemDamage(Integer.parseInt(parts[2]));
        return stack;
    }

    private static List<MeteorComponent> resolveComponents(StandardMeteorDefinitions.Definition source, String branch,
        List<String> descriptors, RecipeCatalogue.Builder<ItemIdentity> builder, JsonArray audit) {
        List<MeteorComponent> result = new ArrayList<>();
        for (int index = 0; index < descriptors.size(); index++) {
            String text = descriptors.get(index);
            JsonObject record = new JsonObject();
            record.addProperty("recipe", source.id);
            record.addProperty("branch", branch);
            record.addProperty("index", index);
            record.addProperty("descriptor", text);
            MeteorComponent component = MeteorComponent.parseString(text);
            if (component == null) {
                record.addProperty("status", "OMITTED_BY_NATIVE_PARSER");
                builder.diagnose("SOURCE_COMPONENT_OMITTED " + source.id + " " + text);
            } else {
                result.add(component);
                record.addProperty("status", "RESOLVED");
                record.addProperty("input", new ItemIdentity(component.getBlock()).signature());
                try {
                    record.addProperty(
                        "output",
                        BlockItemResolver.resolve(component.getBlock())
                            .signature());
                } catch (RuntimeException error) {
                    record.addProperty("status", "UNSUPPORTED_BLOCK_ITEM");
                    record.addProperty("error", describe(error));
                }
            }
            audit.add(record);
        }
        return result;
    }

    private static RecipeSnapshot<ItemIdentity> snapshot(Meteor meteor) {
        if (meteor.focusItem == null || meteor.ores == null || meteor.filler == null) {
            throw new IllegalArgumentException("Incomplete native recipe");
        }
        return new RecipeSnapshot<>(
            ItemIdentity.focusSignature(meteor.focusItem),
            meteor.radius,
            meteor.cost,
            meteor.fillerChance,
            branch(meteor.ores),
            branch(meteor.filler),
            ItemIdentity::signature);
    }

    private static List<WeightedComponent<ItemIdentity>> branch(List<MeteorComponent> source) {
        List<MeteorComponent> detached = new ArrayList<>(source);
        for (MeteorComponent component : detached) {
            if (component == null || component.getRequiredReagents() == null
                || !component.getRequiredReagents()
                    .isEmpty()) {
                throw new IllegalArgumentException("Null or reagent-qualified component requires explicit adaptation");
            }
        }
        List<MeteorComponent> effective = Meteor.removeBlocksMissingRequiredReagents(detached, Collections.emptyList());
        List<WeightedComponent<ItemIdentity>> result = new ArrayList<>();
        for (MeteorComponent component : effective) {
            if (component == null) throw new IllegalArgumentException("Missing native default meteor block");
            result.add(new WeightedComponent<>(BlockItemResolver.resolve(component.getBlock()), component.getWeight()));
        }
        return result;
    }

    private static void writeReport(File directory, RecipeCatalogue<ItemIdentity> result, JsonArray components,
        int observedCount) {
        JsonObject report = new JsonObject();
        report.addProperty("format", 1);
        report.addProperty("packTarget", "GTNH 2.9.0 stable");
        report.addProperty("packSourceCommit", StandardMeteorDefinitions.PACK_COMMIT);
        report.addProperty("registeredCount", observedCount);
        report.addProperty(
            "supportedCount",
            result.recipes()
                .size());
        report.addProperty("productionEnabled", false);
        JsonArray diagnostics = new JsonArray();
        for (String issue : result.diagnostics()) diagnostics.add(new com.google.gson.JsonPrimitive(issue));
        report.add("diagnostics", diagnostics);
        report.add("sourceComponents", components);
        JsonArray recipes = new JsonArray();
        for (RecipeCatalogue.Entry<ItemIdentity> entry : result.recipes()
            .values()) {
            JsonObject recipe = new JsonObject();
            recipe.addProperty("id", entry.id());
            recipe.addProperty("source", entry.source());
            recipe.addProperty("sourceSha256", entry.sourceSha256());
            recipe.addProperty(
                "configurationHash",
                entry.recipe()
                    .configurationHash());
            recipe.addProperty(
                "focus",
                entry.recipe()
                    .focusSignature());
            recipe.addProperty(
                "radius",
                entry.recipe()
                    .radius());
            recipe.addProperty(
                "lpCost",
                entry.recipe()
                    .lpCost());
            recipe.addProperty(
                "latticePositions",
                entry.recipe()
                    .yield()
                    .positions());
            JsonArray outputs = new JsonArray();
            for (Map.Entry<ItemIdentity, ExactFraction> item : entry.recipe()
                .yield()
                .expectations()
                .entrySet()) {
                JsonObject output = new JsonObject();
                output.addProperty(
                    "item",
                    item.getKey()
                        .signature());
                output.addProperty(
                    "expectation",
                    item.getValue()
                        .toString());
                outputs.add(output);
            }
            recipe.add("expectedItems", outputs);
            recipes.add(recipe);
        }
        report.add("recipes", recipes);
        File file = new File(directory, ImaginarySpaceMeteoriteExcavation.MODID + "/meteor-catalogue.json");
        try {
            Files.createDirectories(
                file.toPath()
                    .getParent());
            try (Writer writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting()
                    .create()
                    .toJson(report, writer);
            }
        } catch (IOException error) {
            ImaginarySpaceMeteoriteExcavation.LOG.warn("Cannot write meteor catalogue report {}", file, error);
        }
    }
}
