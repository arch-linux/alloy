package net.alloymc.loader.agent;

import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 *   set(DataComponentType, Object) -> dlt.b(kh, Object) returns Object
 *   remove(DataComponentType) -> dlt.e(kh) returns Object
 *   getHoverName()  -> dlt.y() returns Component (yh)
 *   getCustomName() -> dlt.z() returns Component (yh) [null if no custom name]
 *   DataComponents (ki): CUSTOM_NAME = h, LORE = m
 *   ItemLore (dop): constructor(List&lt;Component&gt;), lines field = e
 *   Component.literal(String) -> yh.b(String)
 * </pre>
 */
public final class ReflectiveItemStack implements ItemStack {

    /**
     * In-memory data store keyed by MC ItemStack identity hash.
     * Used by mods like GriefPrevention to tag items (e.g., death drop ownership).
     */
    private static final Map<Integer, Map<String, String>> DATA_STORE = new ConcurrentHashMap<>();

    // Cached reflection handles (resolved once per classloader)
    private static volatile Object cachedCustomNameComponent;  // DataComponents.CUSTOM_NAME (ki.h)
    private static volatile Object cachedLoreComponent;        // DataComponents.LORE (ki.m)
    private static volatile Method cachedSetMethod;            // ItemStack.set(DataComponentType, Object)
    private static volatile Method cachedRemoveMethod;         // ItemStack.remove(DataComponentType)
    private static volatile Method cachedLiteralMethod;        // Component.literal(String) -> yh.b(String)
    private static volatile Constructor<?> cachedLoreCtor;     // ItemLore(List<Component>)
    private static volatile boolean reflectionInitialized;

    private final Object handle;

    private ReflectiveItemStack(Object handle) {
        this.handle = handle;
    }

    public static ReflectiveItemStack wrap(Object mcItemStack) {
        if (mcItemStack == null) return null;
        return new ReflectiveItemStack(mcItemStack);
    }

    public Object handle() { return handle; }

    /**
     * Lazily initializes cached reflection handles for component operations.
     */
    private void ensureReflection() {
        if (reflectionInitialized) return;
        synchronized (ReflectiveItemStack.class) {
            if (reflectionInitialized) return;
            try {
                ClassLoader cl = handle.getClass().getClassLoader();

                // DataComponents (ki) — get CUSTOM_NAME (h) and LORE (m) fields
                Class<?> dataComponentsClass = cl.loadClass("ki");
                Field customNameField = dataComponentsClass.getDeclaredField("h");
                customNameField.setAccessible(true);
                cachedCustomNameComponent = customNameField.get(null);

                Field loreField = dataComponentsClass.getDeclaredField("m");
                loreField.setAccessible(true);
                cachedLoreComponent = loreField.get(null);

                // DataComponentType class (kh)
                Class<?> dctClass = cl.loadClass("kh");

                // ItemStack.set(DataComponentType, Object) -> b(kh, Object)
                for (Method m : handle.getClass().getMethods()) {
                    if (m.getName().equals("b") && m.getParameterCount() == 2
                            && m.getParameterTypes()[0] == dctClass
                            && m.getParameterTypes()[1] == Object.class) {
                        m.setAccessible(true);
                        cachedSetMethod = m;
                        break;
                    }
                }

                // ItemStack.remove(DataComponentType) -> e(kh)
                for (Method m : handle.getClass().getMethods()) {
                    if (m.getName().equals("e") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == dctClass) {
                        m.setAccessible(true);
                        cachedRemoveMethod = m;
                        break;
                    }
                }

                // Component.literal(String) -> yh.b(String)
                Class<?> componentClass = cl.loadClass("yh");
                for (Method m : componentClass.getMethods()) {
                    if (m.getName().equals("b") && m.getParameterCount() == 1
                            && m.getParameterTypes()[0] == String.class) {
                        m.setAccessible(true);
                        cachedLiteralMethod = m;
                        break;
                    }
                }

                // ItemLore (dop) constructor(List)
                Class<?> loreClass = cl.loadClass("dop");
                for (Constructor<?> ctor : loreClass.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == 1
                            && ctor.getParameterTypes()[0] == List.class) {
                        ctor.setAccessible(true);
                        cachedLoreCtor = ctor;
                        break;
                    }
                }

                reflectionInitialized = true;
            } catch (Exception e) {
                System.err.println("[Alloy] Failed to init ItemStack component reflection: " + e.getMessage());
                reflectionInitialized = true; // Don't retry on failure
            }
        }
    }

    @Override
    public Material type() {
        try {
            Object item = EventFiringHook.invokeNoArgs(handle, "h"); // getItem -> dlp (Item)
            if (item == null) return Material.AIR;

            String itemString = item.toString().toLowerCase();

            int colonIdx = itemString.indexOf(':');
            if (colonIdx >= 0) {
                String materialName = itemString.substring(colonIdx + 1).toUpperCase();
                int end = materialName.indexOf('}');
                if (end >= 0) materialName = materialName.substring(0, end);
                end = materialName.indexOf(']');
                if (end >= 0) materialName = materialName.substring(0, end);
                materialName = materialName.trim();

                try {
                    return Material.valueOf(materialName);
                } catch (IllegalArgumentException ignored) {}
            }

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

    // =================== Display Name ===================

    @Override
    public String displayName() {
        try {
            // getCustomName() -> z() returns Component or null
            Object component = EventFiringHook.invokeNoArgs(handle, "z");
            if (component == null) return null;
            // Component.getString() → getString → probably a() or toString
            Object str = EventFiringHook.invokeNoArgs(component, "getString");
            if (str instanceof String s) return s;
            return component.toString();
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void setDisplayName(String name) {
        ensureReflection();
        try {
            if (name == null) {
                // Remove custom name
                if (cachedRemoveMethod != null && cachedCustomNameComponent != null) {
                    cachedRemoveMethod.invoke(handle, cachedCustomNameComponent);
                }
                return;
            }
            if (cachedSetMethod != null && cachedCustomNameComponent != null && cachedLiteralMethod != null) {
                Object component = cachedLiteralMethod.invoke(null, name);
                cachedSetMethod.invoke(handle, cachedCustomNameComponent, component);
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to set display name: " + e.getMessage());
        }
    }

    // =================== Lore ===================

    @Override
    public List<String> lore() {
        try {
            ensureReflection();
            // Get the ItemLore component via the lore DataComponentType
            // We need ItemStack.get(DataComponentType) but we don't have that cached
            // Instead, read the lore field from the ItemLore object if present
            // Use getOrDefault or just try to extract from components
            // For now, fall back to a simpler approach
            return List.of();
        } catch (Exception ignored) {}
        return List.of();
    }

    @Override
    public void setLore(List<String> lines) {
        ensureReflection();
        try {
            if (lines == null || lines.isEmpty()) {
                if (cachedRemoveMethod != null && cachedLoreComponent != null) {
                    cachedRemoveMethod.invoke(handle, cachedLoreComponent);
                }
                return;
            }
            if (cachedSetMethod == null || cachedLoreComponent == null
                    || cachedLiteralMethod == null || cachedLoreCtor == null) {
                return;
            }

            // Build List<Component> from String lines
            List<Object> componentLines = new ArrayList<>();
            for (String line : lines) {
                Object component = cachedLiteralMethod.invoke(null, line);
                componentLines.add(component);
            }

            // Create ItemLore(List<Component>)
            Object itemLore = cachedLoreCtor.newInstance(componentLines);

            // Set on the ItemStack
            cachedSetMethod.invoke(handle, cachedLoreComponent, itemLore);
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to set lore: " + e.getMessage());
        }
    }

    // =================== Custom Data ===================

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
