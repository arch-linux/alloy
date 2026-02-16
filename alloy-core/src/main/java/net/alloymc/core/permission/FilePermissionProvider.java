package net.alloymc.core.permission;

import net.alloymc.api.permission.PermissionProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * File-based permission provider backed by {@code permissions.json}.
 *
 * <p>Evaluation order:
 * <ol>
 *   <li>User-specific permission overrides</li>
 *   <li>User's groups (with parent group inheritance)</li>
 *   <li>Default group</li>
 * </ol>
 *
 * <p>Supports wildcard permissions ("*") and negated permissions (prefixed with "-").
 */
public class FilePermissionProvider implements PermissionProvider {

    private final Path configFile;
    private volatile PermissionConfig config;

    public FilePermissionProvider(Path dataDir) {
        this.configFile = dataDir.resolve("permissions.json");
    }

    @Override
    public void onEnable() {
        reload();
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
    }

    @Override
    public boolean hasPermission(UUID playerId, String playerName, String permission) {
        PermissionConfig cfg = this.config;
        if (cfg == null) return false;

        // 1. Check user-specific overrides
        PermissionUser user = findUser(cfg, playerId, playerName);
        if (user != null) {
            // Check direct user permission overrides
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
        return checkGroupPermission(cfg, "default", permission, new HashSet<>());
    }

    @Override
    public boolean isOp(UUID playerId) {
        PermissionConfig cfg = this.config;
        if (cfg == null) return false;

        for (Map.Entry<String, PermissionUser> entry : cfg.users().entrySet()) {
            // Match by UUID string or player name
            if (entry.getKey().equalsIgnoreCase(playerId.toString())) {
                return entry.getValue().op();
            }
        }
        return false;
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

    private void saveQuietly() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            System.err.println("[AlloyCore] Failed to save permissions: " + e.getMessage());
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
