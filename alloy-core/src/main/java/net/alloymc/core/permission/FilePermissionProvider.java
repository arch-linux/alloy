package net.alloymc.core.permission;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.alloymc.api.AlloyAPI;
import net.alloymc.api.permission.PermissionProvider;
import net.alloymc.api.permission.PermissionRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * File-based permission provider backed by {@code permissions.json}.
 *
 * <p>Evaluation order:
 * <ol>
 *   <li>User-specific permission overrides (can explicitly deny)</li>
 *   <li>User's groups (with parent group inheritance)</li>
 *   <li>Default group</li>
 *   <li>OP status grants all permissions (unless explicitly denied in step 1)</li>
 * </ol>
 *
 * <p>OP status is checked from both {@code permissions.json} and MC's native {@code ops.json}.
 * This ensures that vanilla {@code /op} and {@code /deop} commands work with Alloy's permission system.
 *
 * <p>Supports wildcard permissions ("*") and negated permissions (prefixed with "-").
 */
public class FilePermissionProvider implements PermissionProvider {

    private final Path configFile;
    private final Path opsFile;
    private volatile PermissionConfig config;

    /** Cached set of OP UUIDs from ops.json; refreshed on each isOp() call if file changed */
    private volatile Set<UUID> opsJsonCache = Set.of();
    private volatile long opsJsonLastModified = 0;

    public FilePermissionProvider(Path dataDir) {
        this.configFile = dataDir.resolve("permissions.json");
        this.opsFile = dataDir.resolve("ops.json");
    }

    @Override
    public void onEnable() {
        reload();
        refreshOpsJson();
    }

    @Override
    public void onDisable() {
        // nothing to clean up
    }

    /**
     * Reloads the permission configuration from disk.
     */
    public void reload() {
        try {
            this.config = PermissionConfig.load(configFile);
            System.out.println("[AlloyCore] Loaded permissions from " + configFile);
        } catch (IOException e) {
            System.err.println("[AlloyCore] Failed to load permissions: " + e.getMessage());
            try {
                this.config = PermissionConfig.loadDefault();
            } catch (IOException ex) {
                this.config = new PermissionConfig(Map.of(), Map.of());
            }
        }
        refreshOpsJson();
    }

    @Override
    public boolean hasPermission(UUID playerId, String playerName, String permission) {
        PermissionConfig cfg = this.config;
        if (cfg == null) return isOp(playerId);

        // 1. Check user-specific overrides (highest priority — can deny even for OPs)
        PermissionUser user = findUser(cfg, playerId, playerName);
        if (user != null) {
            Boolean userPerm = checkNodeInMap(user.permissions(), permission);
            if (userPerm != null) return userPerm;

            // Check user's groups
            for (String groupName : user.groups()) {
                if (checkGroupPermission(cfg, groupName, permission, new HashSet<>())) {
                    return true;
                }
            }
        }

        // 2. Check default group
        if (checkGroupPermission(cfg, "default", permission, new HashSet<>())) {
            return true;
        }

        // 3. Check registered permission defaults from the PermissionRegistry.
        //    Mods register permissions with defaults (TRUE = all players, OP = ops only).
        //    This ensures PermissionDefault.TRUE is honored even without explicit group entries.
        PermissionRegistry.PermissionInfo info = AlloyAPI.permissionRegistry().get(permission);
        if (info != null) {
            return switch (info.defaultValue()) {
                case TRUE -> true;
                case FALSE -> false;
                case OP -> isOp(playerId);
            };
        }

        // 4. Unregistered permissions: OP players have all by default
        //    (unless explicitly denied via user overrides above)
        return isOp(playerId);
    }

    @Override
    public boolean isOp(UUID playerId) {
        // Check permissions.json first
        PermissionConfig cfg = this.config;
        if (cfg != null) {
            for (Map.Entry<String, PermissionUser> entry : cfg.users().entrySet()) {
                if (entry.getKey().equalsIgnoreCase(playerId.toString())) {
                    if (entry.getValue().op()) return true;
                }
            }
        }

        // Also check MC's native ops.json
        refreshOpsJson();
        return opsJsonCache.contains(playerId);
    }

    @Override
    public void setOp(UUID playerId, String playerName, boolean op) {
        setUserOp(playerName, op);
        updateOpsJson(playerId, playerName, op);
    }

    /**
     * Sets the op flag in permissions.json for the given user.
     */
    public void setUserOp(String userName, boolean op) {
        PermissionConfig cfg = this.config;
        Map<String, PermissionUser> users = new LinkedHashMap<>(cfg.users());

        PermissionUser existing = users.get(userName);
        if (existing != null) {
            users.put(userName, new PermissionUser(existing.groups(), existing.permissions(), op));
        } else {
            users.put(userName, new PermissionUser(List.of(), Map.of(), op));
        }

        this.config = new PermissionConfig(cfg.groups(), users);
        saveQuietly();
    }

    /**
     * Returns the current permission configuration.
     */
    public PermissionConfig getConfig() {
        return config;
    }

    /**
     * Adds a user to a group and saves.
     */
    public void addUserToGroup(String userName, String group) {
        PermissionConfig cfg = this.config;
        Map<String, PermissionUser> users = new LinkedHashMap<>(cfg.users());

        PermissionUser existing = users.get(userName);
        List<String> groups;
        Map<String, Boolean> perms;
        boolean op;

        if (existing != null) {
            groups = new ArrayList<>(existing.groups());
            perms = existing.permissions();
            op = existing.op();
        } else {
            groups = new ArrayList<>();
            perms = Map.of();
            op = false;
        }

        if (!groups.contains(group)) {
            groups.add(group);
        }

        users.put(userName, new PermissionUser(groups, perms, op));
        this.config = new PermissionConfig(cfg.groups(), users);
        saveQuietly();
    }

    /**
     * Removes a user from a group and saves.
     */
    public void removeUserFromGroup(String userName, String group) {
        PermissionConfig cfg = this.config;
        Map<String, PermissionUser> users = new LinkedHashMap<>(cfg.users());

        PermissionUser existing = users.get(userName);
        if (existing == null) return;

        List<String> groups = new ArrayList<>(existing.groups());
        groups.remove(group);

        users.put(userName, new PermissionUser(groups, existing.permissions(), existing.op()));
        this.config = new PermissionConfig(cfg.groups(), users);
        saveQuietly();
    }

    /**
     * Sets a user-specific permission override and saves.
     */
    public void setUserPermission(String userName, String permission, boolean value) {
        PermissionConfig cfg = this.config;
        Map<String, PermissionUser> users = new LinkedHashMap<>(cfg.users());

        PermissionUser existing = users.get(userName);
        List<String> groups;
        boolean op;

        if (existing != null) {
            groups = existing.groups();
            op = existing.op();
        } else {
            groups = List.of();
            op = false;
        }

        Map<String, Boolean> perms = new LinkedHashMap<>(existing != null ? existing.permissions() : Map.of());
        perms.put(permission, value);

        users.put(userName, new PermissionUser(groups, perms, op));
        this.config = new PermissionConfig(cfg.groups(), users);
        saveQuietly();
    }

    /**
     * Returns the path to the ops.json file (for commands that need to read/write it).
     */
    public Path opsFile() {
        return opsFile;
    }

    // =================== Internal helpers ===================

    private void saveQuietly() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            System.err.println("[AlloyCore] Failed to save permissions: " + e.getMessage());
        }
    }

    /**
     * Re-reads ops.json if the file has been modified since last read.
     */
    private void refreshOpsJson() {
        try {
            if (!Files.exists(opsFile)) return;

            long lastMod = Files.getLastModifiedTime(opsFile).toMillis();
            if (lastMod == opsJsonLastModified) return;

            String json = Files.readString(opsFile, StandardCharsets.UTF_8);
            JsonArray array = JsonParser.parseString(json).getAsJsonArray();

            Set<UUID> ops = new HashSet<>();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                if (entry.has("uuid")) {
                    try {
                        ops.add(UUID.fromString(entry.get("uuid").getAsString()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }

            this.opsJsonCache = Set.copyOf(ops);
            this.opsJsonLastModified = lastMod;
        } catch (Exception e) {
            // Don't crash on parse errors — just keep the old cache
        }
    }

    /**
     * Adds or removes a player from MC's native ops.json.
     */
    private void updateOpsJson(UUID playerId, String playerName, boolean op) {
        try {
            JsonArray array;
            if (Files.exists(opsFile)) {
                String json = Files.readString(opsFile, StandardCharsets.UTF_8);
                array = JsonParser.parseString(json).getAsJsonArray();
            } else {
                array = new JsonArray();
            }

            // Remove existing entry for this UUID
            String uuidStr = playerId.toString();
            JsonArray filtered = new JsonArray();
            for (JsonElement element : array) {
                JsonObject entry = element.getAsJsonObject();
                if (!entry.has("uuid") || !entry.get("uuid").getAsString().equals(uuidStr)) {
                    filtered.add(entry);
                }
            }

            if (op) {
                // Add new entry
                JsonObject entry = new JsonObject();
                entry.addProperty("uuid", uuidStr);
                entry.addProperty("name", playerName);
                entry.addProperty("level", 4);
                entry.addProperty("bypassesPlayerLimit", false);
                filtered.add(entry);
            }

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder().setPrettyPrinting().create();
            Files.writeString(opsFile, gson.toJson(filtered), StandardCharsets.UTF_8);
            opsJsonLastModified = 0; // Force refresh on next read
        } catch (IOException e) {
            System.err.println("[AlloyCore] Failed to update ops.json: " + e.getMessage());
        }
    }

    private PermissionUser findUser(PermissionConfig cfg, UUID playerId, String playerName) {
        // Try UUID first
        PermissionUser user = cfg.users().get(playerId.toString());
        if (user != null) return user;

        // Try player name (case-insensitive)
        for (Map.Entry<String, PermissionUser> entry : cfg.users().entrySet()) {
            if (entry.getKey().equalsIgnoreCase(playerName)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean checkGroupPermission(PermissionConfig cfg, String groupName,
                                         String permission, Set<String> visited) {
        if (!visited.add(groupName)) return false; // prevent circular inheritance

        PermissionGroup group = cfg.groups().get(groupName);
        if (group == null) return false;

        // Check this group's permissions
        if (matchesPermission(group.permissions(), permission)) {
            return true;
        }

        // Check parent groups
        for (String parent : group.parents()) {
            if (checkGroupPermission(cfg, parent, permission, visited)) {
                return true;
            }
        }

        return false;
    }

    private boolean matchesPermission(List<String> permissions, String target) {
        for (String node : permissions) {
            if ("*".equals(node)) return true;
            if (node.equals(target)) return true;
            // Wildcard matching: "alloy.command.*" matches "alloy.command.tps"
            if (node.endsWith(".*")) {
                String prefix = node.substring(0, node.length() - 1);
                if (target.startsWith(prefix)) return true;
            }
        }
        return false;
    }

    private Boolean checkNodeInMap(Map<String, Boolean> permissions, String target) {
        // Check exact match
        Boolean exact = permissions.get(target);
        if (exact != null) return exact;

        // Check wildcard
        Boolean wildcard = permissions.get("*");
        if (wildcard != null) return wildcard;

        // Check prefix wildcards
        for (Map.Entry<String, Boolean> entry : permissions.entrySet()) {
            String node = entry.getKey();
            if (node.endsWith(".*")) {
                String prefix = node.substring(0, node.length() - 1);
                if (target.startsWith(prefix)) return entry.getValue();
            }
        }

        return null;
    }
}
