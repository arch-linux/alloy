package net.alloymc.loader.agent;

import net.alloymc.api.inventory.CustomInventory;
import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;

import java.lang.reflect.Method;

/**
 * Implements {@link CustomInventory} by wrapping a Minecraft SimpleContainer (cdk).
 *
 * <p>Mapping reference (MC 1.21.11, SimpleContainer = cdk):
 * <pre>
 *   constructor(int)      -> cdk(int)
 *   getContainerSize()    -> b() returns int
 *   getItem(int)          -> a(int) returns ItemStack
 *   setItem(int, ItemStack) -> a(int, ItemStack) void
 *   isEmpty()             -> c() returns boolean
 *   clearContent()        -> a() void (no-arg)
 * </pre>
 */
public final class ReflectiveCustomInventory implements CustomInventory {

    private final String title;
    private final int rows;
    private final Object handle; // MC SimpleContainer instance

    ReflectiveCustomInventory(String title, int rows, Object mcSimpleContainer) {
        this.title = title;
        this.rows = rows;
        this.handle = mcSimpleContainer;
    }

    /** Returns the underlying MC SimpleContainer object. */
    public Object handle() { return handle; }

    @Override
    public String title() { return title; }

    @Override
    public int rows() { return rows; }

    @Override
    public int size() { return rows * 9; }

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
            } else if (item == null) {
                // Set to empty/air
                mcItem = getEmptyItemStack();
                if (mcItem == null) return;
            } else {
                return;
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
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "c"); // isEmpty on SimpleContainer
            if (result instanceof Boolean b) return b;
        } catch (Exception ignored) {}
        // Fallback
        int sz = size();
        for (int i = 0; i < sz; i++) {
            ItemStack is = item(i);
            if (is != null && !is.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack addItem(ItemStack item) {
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
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 0
                        && m.getReturnType() == void.class) {
                    m.setAccessible(true);
                    m.invoke(handle);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    private Object getEmptyItemStack() {
        try {
            Class<?> itemStackClass = handle.getClass().getClassLoader().loadClass("dlt");
            java.lang.reflect.Field emptyField = itemStackClass.getDeclaredField("b");
            emptyField.setAccessible(true);
            return emptyField.get(null);
        } catch (Exception e) {
            return null;
        }
    }
}
