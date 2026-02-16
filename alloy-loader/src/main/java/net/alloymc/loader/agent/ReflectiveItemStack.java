package net.alloymc.loader.agent;

import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implements {@link ItemStack} by wrapping a Minecraft ItemStack via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, ItemStack = dlt, Item = dlp):
 * <pre>
 *   getItem()       -> dlt.h() returns Item (dlp)
 *   getCount()      -> dlt.N() returns int
 *   isEmpty()       -> dlt.f() returns boolean
 *   copy()          -> dlt.v() returns ItemStack
 *   setCount(int)   -> dlt.e(int) void
 *   Item.toString() -> "minecraft:name" via registry lookup
 * </pre>
 */
public final class ReflectiveItemStack implements ItemStack {

    /**
     * In-memory data store keyed by MC ItemStack identity hash.
     * Used by mods like GriefPrevention to tag items (e.g., death drop ownership).
     * The outer map uses identity hash of the MC handle; the inner map is key→value.
     */
    private static final Map<Integer, Map<String, String>> DATA_STORE = new ConcurrentHashMap<>();

    private final Object handle;

    private ReflectiveItemStack(Object handle) {
        this.handle = handle;
    }

    public static ReflectiveItemStack wrap(Object mcItemStack) {
        if (mcItemStack == null) return null;
        return new ReflectiveItemStack(mcItemStack);
    }

    public Object handle() { return handle; }

    @Override
    public Material type() {
        try {
            Object item = EventFiringHook.invokeNoArgs(handle, "h"); // getItem -> dlp (Item)
            if (item == null) return Material.AIR;

            // Item has a toString that returns something like "minecraft:stone"
            // or we can use the registry name
            String itemString = item.toString().toLowerCase();

            // Try to extract "minecraft:name" from the string
            int colonIdx = itemString.indexOf(':');
            if (colonIdx >= 0) {
                String materialName = itemString.substring(colonIdx + 1).toUpperCase();
                // Strip any trailing characters (brackets, etc.)
                int end = materialName.indexOf('}');
                if (end >= 0) materialName = materialName.substring(0, end);
                end = materialName.indexOf(']');
                if (end >= 0) materialName = materialName.substring(0, end);
                materialName = materialName.trim();

                try {
                    return Material.valueOf(materialName);
                } catch (IllegalArgumentException ignored) {}
            }

            // Fallback: scan all Material values
            for (Material m : Material.values()) {
                if (m == Material.AIR) continue;
                if (itemString.contains(m.name().toLowerCase())) return m;
            }
        } catch (Exception ignored) {}
        return Material.AIR;
    }

    @Override
    public int amount() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "N"); // getCount
            if (result instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 0;
    }

    @Override
    public void setAmount(int amount) {
        try {
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("e") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == int.class) {
                    m.setAccessible(true);
                    m.invoke(handle, amount);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean isEmpty() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "f"); // isEmpty
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {}
        return type() == Material.AIR;
    }

    @Override
    public boolean hasData(String key) {
        Map<String, String> data = DATA_STORE.get(System.identityHashCode(handle));
        return data != null && data.containsKey(key);
    }

    @Override
    public String getData(String key) {
        Map<String, String> data = DATA_STORE.get(System.identityHashCode(handle));
        return data != null ? data.get(key) : null;
    }

    @Override
    public void setData(String key, String value) {
        DATA_STORE.computeIfAbsent(System.identityHashCode(handle),
                k -> new ConcurrentHashMap<>()).put(key, value);
    }

    @Override
    public void removeData(String key) {
        Map<String, String> data = DATA_STORE.get(System.identityHashCode(handle));
        if (data != null) {
            data.remove(key);
            if (data.isEmpty()) DATA_STORE.remove(System.identityHashCode(handle));
        }
    }

    @Override
    public ItemStack copy() {
        try {
            Object copied = EventFiringHook.invokeNoArgs(handle, "v"); // copy
            if (copied != null) return new ReflectiveItemStack(copied);
        } catch (Exception ignored) {}
        return this;
    }
}
