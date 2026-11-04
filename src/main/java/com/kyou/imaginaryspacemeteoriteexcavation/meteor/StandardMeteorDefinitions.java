package com.kyou.imaginaryspacemeteoriteexcavation.meteor;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Version-pinned source descriptors. They do not register or enable any Blood Magic recipe. */
public final class StandardMeteorDefinitions {

    public static final String RESOURCE = "/assets/imaginaryspacemeteoriteexcavation/data/standard_meteors.json";
    public static final String PACK_COMMIT = "79f9a03b5dd9e492d28998b9dee0ea1d1822a6b1";

    private StandardMeteorDefinitions() {}

    public static List<Definition> load() {
        InputStream stream = StandardMeteorDefinitions.class.getResourceAsStream(RESOURCE);
        if (stream == null) throw new IllegalStateException("Missing standard meteor source mapping");
        try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(reader)
                .getAsJsonObject();
            if (root.get("format")
                .getAsInt() != 1
                || !PACK_COMMIT.equals(
                    root.get("packCommit")
                        .getAsString())) {
                throw new IllegalArgumentException("Unexpected standard meteor source version");
            }
            List<Definition> definitions = new ArrayList<>();
            Set<String> ids = new HashSet<>();
            for (JsonElement value : root.getAsJsonArray("definitions")) {
                Definition definition = new Definition(value.getAsJsonObject());
                if (!ids.add(definition.id)) throw new IllegalArgumentException("Duplicate stable meteor ID");
                definitions.add(definition);
            }
            if (definitions.size() != 57)
                throw new IllegalArgumentException("Incomplete standard meteor source mapping");
            return Collections.unmodifiableList(definitions);
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read standard meteor source mapping", error);
        }
    }

    public static final class Definition {

        public final String id;
        public final String source;
        public final String sourceSha256;
        public final String focus;
        public final int radius;
        public final int lpCost;
        public final int fillerChance;
        public final List<String> ores;
        public final List<String> filler;

        private Definition(JsonObject value) {
            id = value.get("id")
                .getAsString();
            source = value.get("source")
                .getAsString();
            sourceSha256 = value.get("sha256")
                .getAsString();
            JsonObject data = value.getAsJsonObject("definition");
            focus = data.get("focusItem")
                .getAsString();
            radius = data.get("radius")
                .getAsInt();
            lpCost = data.get("cost")
                .getAsInt();
            fillerChance = data.has("fillerChance") ? data.get("fillerChance")
                .getAsInt() : 0;
            ores = strings(data, "ores");
            filler = strings(data, "filler");
            MeteorGeometry.positions(radius);
            if (lpCost < 0 || fillerChance < 0 || fillerChance > 100) {
                throw new IllegalArgumentException("Invalid standard meteor parameters: " + id);
            }
        }

        private static List<String> strings(JsonObject data, String name) {
            List<String> result = new ArrayList<>();
            if (data.has(name)) {
                for (JsonElement value : data.getAsJsonArray(name)) result.add(value.getAsString());
            }
            return Collections.unmodifiableList(result);
        }
    }
}
