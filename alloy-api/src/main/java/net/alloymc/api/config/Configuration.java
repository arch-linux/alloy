package net.alloymc.api.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A hierarchical key-value configuration.
 * Supports nested sections accessed via dot-separated paths.
 */
public class Configuration {

    private final Map<String, Object> data;

    public Configuration() {
        this.data = new LinkedHashMap<>();
    }

    public Configuration(Map<String, Object> data) {
        this.data = new LinkedHashMap<>(data);
    }

    public void set(String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = data;
        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = current.get(parts[i]);
            if (existing instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) existing;
                current = map;
            } else {
                Map<String, Object> newMap = new LinkedHashMap<>();
                current.put(parts[i], newMap);
                current = newMap;
            }
        }
        current.put(parts[parts.length - 1], value);
    }

    public Object get(String path) {
        String[] parts = path.split("\\.");
        Object current = data;
        for (String part : parts) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    public String getString(String path, String defaultValue) {
        Object value = get(path);
        return value != null ? value.toString() : defaultValue;
    }

    public int getInt(String path, int defaultValue) {
        Object value = get(path);
        if (value instanceof Number num) return num.intValue();
        if (value instanceof String s) {
            try { return Integer.parseInt(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    public long getLong(String path, long defaultValue) {
        Object value = get(path);
        if (value instanceof Number num) return num.longValue();
        if (value instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    public double getDouble(String path, double defaultValue) {
        Object value = get(path);
        if (value instanceof Number num) return num.doubleValue();
        if (value instanceof String s) {
            try { return Double.parseDouble(s); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    public boolean getBoolean(String path, boolean defaultValue) {
        Object value = get(path);
        if (value instanceof Boolean b) return b;
        if (value instanceof String s) return Boolean.parseBoolean(s);
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = get(path);
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    public Configuration getSection(String path) {
        Object value = get(path);
        if (value instanceof Map) {
            return new Configuration((Map<String, Object>) value);
        }
        return null;
    }

    public Set<String> keys() {
        return data.keySet();
    }

    public Map<String, Object> toMap() {
        return Collections.unmodifiableMap(data);
    }
}
