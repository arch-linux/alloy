package net.alloymc.api.permission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for permission nodes declared by mods.
 * Allows mods to register their permissions with descriptions and defaults.
 */
public class PermissionRegistry {

    public record PermissionInfo(String node, String description, PermissionDefault defaultValue) {}

    public enum PermissionDefault {
        TRUE,
        FALSE,
        OP
    }

    private final Map<String, PermissionInfo> permissions = new ConcurrentHashMap<>();

    public void register(String node, String description, PermissionDefault defaultValue) {
        permissions.put(node, new PermissionInfo(node, description, defaultValue));
    }

    public void register(String node, String description) {
        register(node, description, PermissionDefault.OP);
    }

    public PermissionInfo get(String node) {
        return permissions.get(node);
    }

    public Set<String> allNodes() {
        return permissions.keySet();
    }
}
