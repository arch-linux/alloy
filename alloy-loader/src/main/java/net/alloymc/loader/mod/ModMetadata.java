package net.alloymc.loader.mod;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mod metadata parsed from {@code alloy.mod.json} in the root of a mod JAR.
 *
 * <p>Example alloy.mod.json:
 * <pre>{@code
 * {
 *   "id": "my-mod",
 *   "name": "My Mod",
 *   "version": "1.0.0",
 *   "description": "Does cool things",
 *   "authors": ["Alice", "Bob"],
 *   "license": "MIT",
 *   "entrypoint": "com.example.MyMod",
 *   "dependencies": {
 *     "minecraft": ">=1.21.0",
 *     "alloy": ">=0.1.0"
 *   },
 *   "environment": "both"
 * }
 * }</pre>
 */
public record ModMetadata(
        String id,
        String name,
        String version,
        String description,
        List<String> authors,
        String license,
        String entrypoint,
        Map<String, String> dependencies,
        String environment
) {
    /** The filename mods must include at the JAR root. */
    public static final String FILENAME = "alloy.mod.json";

    /**
     * Parses mod metadata from a JSON input stream.
     *
     * @throws IOException if the JSON is malformed or missing required fields
     */
    public static ModMetadata parse(InputStream in) throws IOException {
        JsonObject root;
        try {
            root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
        } catch (Exception e) {
            throw new IOException("Failed to parse " + FILENAME + ": " + e.getMessage(), e);
        }

        String id = requireString(root, "id");
        String name = requireString(root, "name");
        String version = requireString(root, "version");

        // Validate mod ID format: lowercase alphanumeric + hyphens
        if (!id.matches("[a-z0-9][a-z0-9-]*[a-z0-9]") && !id.matches("[a-z0-9]")) {
            throw new IOException("Invalid mod ID: '" + id + "'. "
                    + "Must be lowercase alphanumeric with hyphens (e.g., 'my-cool-mod').");
        }

        // Validate version is parseable
        try {
            SemanticVersion.parse(version);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid version '" + version + "' in mod '" + id + "': "
                    + e.getMessage());
        }

        String description = optionalString(root, "description", "");
        String license = optionalString(root, "license", "");
        String entrypoint = optionalString(root, "entrypoint", "");
        String environment = optionalString(root, "environment", "both");

        if (!environment.equals("client") && !environment.equals("server")
                && !environment.equals("both")) {
            throw new IOException("Invalid environment '" + environment + "' in mod '" + id + "'. "
                    + "Must be 'client', 'server', or 'both'.");
        }

        // Parse authors
        List<String> authors = new ArrayList<>();
        if (root.has("authors") && root.get("authors").isJsonArray()) {
            for (JsonElement el : root.getAsJsonArray("authors")) {
                authors.add(el.getAsString());
            }
        }

        // Parse dependencies
        Map<String, String> dependencies = new LinkedHashMap<>();
        if (root.has("dependencies") && root.get("dependencies").isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : root.getAsJsonObject("dependencies").entrySet()) {
                dependencies.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return new ModMetadata(
                id, name, version, description,
                Collections.unmodifiableList(authors),
                license, entrypoint,
                Collections.unmodifiableMap(dependencies),
                environment
        );
    }

    private static String requireString(JsonObject obj, String key) throws IOException {
        if (!obj.has(key) || !obj.get(key).isJsonPrimitive()) {
            throw new IOException("Missing required field '" + key + "' in " + FILENAME);
        }
        return obj.get(key).getAsString();
    }

    private static String optionalString(JsonObject obj, String key, String defaultValue) {
        if (obj.has(key) && obj.get(key).isJsonPrimitive()) {
            return obj.get(key).getAsString();
        }
        return defaultValue;
    }
}
