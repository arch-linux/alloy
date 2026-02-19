package net.alloymc.loader.agent;

import net.alloymc.api.entity.Player;
import net.alloymc.api.gui.DataProperty;
import net.alloymc.api.gui.MenuInstance;
import net.alloymc.api.gui.MenuLayout;
import net.alloymc.api.inventory.ItemStack;

/**
 * Implements {@link MenuInstance} by wrapping a Minecraft container opened via
 * a {@link MenuLayout}. Delegates item access to a backing {@link ReflectiveCustomInventory}
 * and property access to the layout's {@link DataProperty} list.
 */
public final class ReflectiveMenuInstance implements MenuInstance {

    private final MenuLayout layout;
    private final ReflectivePlayer viewer;
    private final ReflectiveCustomInventory backing;

    ReflectiveMenuInstance(MenuLayout layout, ReflectivePlayer viewer,
                           ReflectiveCustomInventory backing) {
        this.layout = layout;
        this.viewer = viewer;
        this.backing = backing;
    }

    @Override
    public MenuLayout layout() { return layout; }

    @Override
    public Player viewer() { return viewer; }

    @Override
    public ItemStack item(int slot) {
        return backing.item(slot);
    }

    @Override
    public void setItem(int slot, ItemStack item) {
        backing.setItem(slot, item);
    }

    @Override
    public int getProperty(String name) {
        DataProperty prop = layout.property(name);
        if (prop == null) throw new IllegalArgumentException("Unknown property: " + name);
        return prop.get();
    }

    @Override
    public void setProperty(String name, int value) {
        DataProperty prop = layout.property(name);
        if (prop == null) throw new IllegalArgumentException("Unknown property: " + name);
        prop.set(value);
    }

    @Override
    public void close() {
        viewer.closeInventory();
    }

    /**
     * Returns the backing custom inventory for event tracking.
     */
    ReflectiveCustomInventory backing() { return backing; }
}
