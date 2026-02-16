package net.alloymc.loader.agent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global metadata store keyed by entity UUID.
 * Provides per-entity key-value storage for mods (equivalent to Bukkit's metadata system).
 */
public final class MetadataStore {

    private static final ConcurrentHashMap<UUID, ConcurrentHashMap<String, Object>> STORE =
            new ConcurrentHashMap<>();

    private MetadataStore() {}

    public static boolean has(UUID entityId, String key) {
        Map<String, Object> map = STORE.get(entityId);
        return map != null && map.containsKey(key);
    }

    public static void set(UUID entityId, String key, Object value) {
        STORE.computeIfAbsent(entityId, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    public static Object get(UUID entityId, String key) {
        Map<String, Object> map = STORE.get(entityId);
        return map != null ? map.get(key) : null;
    }

    public static void remove(UUID entityId, String key) {
        Map<String, Object> map = STORE.get(entityId);
        if (map != null) {
            map.remove(key);
            if (map.isEmpty()) STORE.remove(entityId);
        }
    }

    public static void removeAll(UUID entityId) {
        STORE.remove(entityId);
    }
}
