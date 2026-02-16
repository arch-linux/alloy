package net.alloymc.api.permission;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for permission nodes declared by mods.
 * Allows mods to register their permissions with descriptions and defaults.
 *
 * <p>Also manages a pluggable {@link PermissionProvider} backend.
 * Only one provider is active at a time; the last registered wins.
 */
public class PermissionRegistry {

    public record PermissionInfo(String node, String description, PermissionDefault defaultValue) {}

    public enum PermissionDefault {
        TRUE,
        FALSE,
        OP
    }

    private final Map<String, PermissionInfo> permissions = new ConcurrentHashMap<>();
    private volatile PermissionProvider provider;

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

    /**
     * Replaces the active permission provider. Calls {@code onDisable()} on the old
     * provider and {@code onEnable()} on the new one.
     *
     * @param provider the new permission provider
     */
    public void setProvider(PermissionProvider provider) {
        PermissionProvider old = this.provider;
        if (old != null) {
            old.onDisable();
        }
        this.provider = provider;
        if (provider != null) {
            provider.onEnable();
        }
    }

    /**
     * Returns the active permission provider, or a minimal default that evaluates
     * based on {@link PermissionDefault} values.
     */
    public PermissionProvider provider() {
        PermissionProvider p = this.provider;
        if (p != null) return p;

        // Minimal fallback: evaluate based on PermissionDefault only
        return new PermissionProvider() {
            @Override
            public boolean hasPermission(java.util.UUID playerId, String playerName, String permission) {
                PermissionInfo info = get(permission);
                if (info == null) return false;
                return switch (info.defaultValue()) {
                    case TRUE -> true;
                    case FALSE -> false;
                    case OP -> false; // no way to check OP without a real provider
                };
            }

            @Override
            public boolean isOp(java.util.UUID playerId) {
                return false;
            }
        };
    }
}
