package net.alloymc.core.permission;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and saves the permissions.json configuration file.
 */
public record PermissionConfig(
        Map<String, PermissionGroup> groups,
        Map<String, PermissionUser> users
) {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Loads from a file, or copies the default from resources if the file doesn't exist.
     */
    public static PermissionConfig load(Path file) throws IOException {
        if (!Files.exists(file)) {
            copyDefault(file);
        }
        String json = Files.readString(file, StandardCharsets.UTF_8);
        return parse(json);
    }

    /**
     * Loads the default configuration from classpath resources.
     */
    public static PermissionConfig loadDefault() throws IOException {
        try (InputStream in = PermissionConfig.class.getResourceAsStream("/default-permissions.json")) {
            if (in == null) {
                throw new IOException("default-permissions.json not found in resources");
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return parse(json);
        }
    }

    /**
     * Saves this configuration to the given file.
     */
    public void save(Path file) throws IOException {
        JsonObject root = new JsonObject();

        // Serialize groups
        JsonObject groupsObj = new JsonObject();
        for (Map.Entry<String, PermissionGroup> entry : groups.entrySet()) {
            JsonObject groupObj = new JsonObject();
            groupObj.add("permissions", GSON.toJsonTree(entry.getValue().permissions()));
            groupObj.add("parents", GSON.toJsonTree(entry.getValue().parents()));
            groupsObj.add(entry.getKey(), groupObj);
        }
        root.add("groups", groupsObj);

        // Serialize users
        JsonObject usersObj = new JsonObject();
        for (Map.Entry<String, PermissionUser> entry : users.entrySet()) {
            JsonObject userObj = new JsonObject();
            userObj.add("groups", GSON.toJsonTree(entry.getValue().groups()));
            userObj.addProperty("op", entry.getValue().op());

            JsonObject permsObj = new JsonObject();
            for (Map.Entry<String, Boolean> perm : entry.getValue().permissions().entrySet()) {
                permsObj.addProperty(perm.getKey(), perm.getValue());
            }
            userObj.add("permissions", permsObj);

            usersObj.add(entry.getKey(), userObj);
        }
        root.add("users", usersObj);

        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(root), StandardCharsets.UTF_8);
    }

    private static PermissionConfig parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        Map<String, PermissionGroup> groups = new LinkedHashMap<>();
        if (root.has("groups")) {
            JsonObject groupsObj = root.getAsJsonObject("groups");
            for (Map.Entry<String, JsonElement> entry : groupsObj.entrySet()) {
                JsonObject groupObj = entry.getValue().getAsJsonObject();
                List<String> permissions = parseStringList(groupObj, "permissions");
                List<String> parents = parseStringList(groupObj, "parents");
                groups.put(entry.getKey(), new PermissionGroup(permissions, parents));
            }
        }

        Map<String, PermissionUser> users = new LinkedHashMap<>();
        if (root.has("users")) {
            JsonObject usersObj = root.getAsJsonObject("users");
            for (Map.Entry<String, JsonElement> entry : usersObj.entrySet()) {
                JsonObject userObj = entry.getValue().getAsJsonObject();
                List<String> userGroups = parseStringList(userObj, "groups");
                boolean op = userObj.has("op") && userObj.get("op").getAsBoolean();

                Map<String, Boolean> permissions = new LinkedHashMap<>();
                if (userObj.has("permissions") && userObj.get("permissions").isJsonObject()) {
                    for (Map.Entry<String, JsonElement> permEntry
                            : userObj.getAsJsonObject("permissions").entrySet()) {
                        permissions.put(permEntry.getKey(), permEntry.getValue().getAsBoolean());
                    }
                }

                users.put(entry.getKey(), new PermissionUser(userGroups, permissions, op));
            }
        }

        return new PermissionConfig(groups, users);
    }

    private static List<String> parseStringList(JsonObject obj, String key) {
        List<String> result = new ArrayList<>();
        if (obj.has(key) && obj.get(key).isJsonArray()) {
            for (JsonElement el : obj.getAsJsonArray(key)) {
                result.add(el.getAsString());
            }
        }
        return result;
    }

    private static void copyDefault(Path target) throws IOException {
        try (InputStream in = PermissionConfig.class.getResourceAsStream("/default-permissions.json")) {
            if (in == null) {
                throw new IOException("default-permissions.json not found in resources");
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target);
        }
    }
}
