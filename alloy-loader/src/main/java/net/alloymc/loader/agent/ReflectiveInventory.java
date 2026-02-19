package net.alloymc.loader.agent;

import net.alloymc.api.inventory.Inventory;
import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;

import java.lang.reflect.Method;

/**
 * Implements {@link Inventory} by wrapping a Minecraft Container/Inventory via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, net.minecraft.world.entity.player.Inventory):
 * <pre>
 *   getContainerSize() -> b() returns int (36 for player inventory)
 *   getItem(int)       -> a(int) returns ItemStack
 *   setItem(int, IS)   -> a(int, ItemStack) void
 *   clearContent()     -> a() void (no-arg overload)
 * </pre>
 */
public final class ReflectiveInventory implements Inventory {

    private final Object handle;

    private ReflectiveInventory(Object handle) {
        this.handle = handle;
    }

    public static ReflectiveInventory wrap(Object mcInventory) {
        if (mcInventory == null) return null;
        return new ReflectiveInventory(mcInventory);
    }

    public Object handle() { return handle; }

    @Override
    public int size() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "b"); // getContainerSize
            if (result instanceof Number n) return n.intValue();
        } catch (Exception ignored) {}
        return 36; // default player inventory size
    }

    @Override
    public ItemStack item(int slot) {
        try {
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == int.class
                        && m.getReturnType() != void.class
                        && m.getReturnType() != boolean.class
                        && m.getReturnType() != int.class) {
                    m.setAccessible(true);
                    Object item = m.invoke(handle, slot);
                    if (item != null) return ReflectiveItemStack.wrap(item);
                    break;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        try {
            Object mcItem;
            if (item instanceof ReflectiveItemStack ris) {
                mcItem = ris.handle();
            } else {
                // null or non-reflective → use ItemStack.EMPTY (dlt.l)
                Class<?> itemStackClass = handle.getClass().getClassLoader().loadClass("dlt");
                java.lang.reflect.Field emptyField = itemStackClass.getDeclaredField("l");
                emptyField.setAccessible(true);
                mcItem = emptyField.get(null);
            }

            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 2
                        && m.getParameterTypes()[0] == int.class
                        && m.getParameterTypes()[1].isInstance(mcItem)) {
                    m.setAccessible(true);
                    m.invoke(handle, slot, mcItem);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public boolean contains(Material material) {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            ItemStack is = item(i);
            if (is != null && is.type() == material) return true;
        }
        return false;
    }

    @Override
    public boolean isEmpty() {
        int sz = size();
        for (int i = 0; i < sz; i++) {
            ItemStack is = item(i);
            if (is != null && !is.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack addItem(ItemStack item) {
        // Simplified: find first empty or stackable slot
        // Real implementation would handle stacking logic
        if (item == null || item.isEmpty()) return null;
        int sz = size();
        for (int i = 0; i < sz; i++) {
            ItemStack existing = item(i);
            if (existing == null || existing.isEmpty()) {
                setItem(i, item);
                return null; // all fit
            }
        }
        return item; // overflow
    }

    @Override
    public void clear() {
        try {
            // clearContent() → a() (no-arg void method)
            // Note: "a" is overloaded on Container — we need the no-arg void one
            for (java.lang.reflect.Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 0
                        && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    m.invoke(handle);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }
}
