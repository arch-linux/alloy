package net.alloymc.loader.agent;

import net.alloymc.api.inventory.CustomInventory;
import net.alloymc.api.inventory.InventoryFactory;

import java.lang.reflect.Constructor;

/**
 * Factory that creates {@link ReflectiveCustomInventory} instances backed by MC SimpleContainer.
 *
 * <p>Mapping reference (MC 1.21.11):
 * <pre>
 *   SimpleContainer (cdk): constructor(int size) → cdk(int)
 * </pre>
 */
public final class ReflectiveInventoryFactory implements InventoryFactory {

    private final ClassLoader mcClassLoader;
    private Constructor<?> simpleContainerConstructor;

    public ReflectiveInventoryFactory(Object minecraftServer) {
        this.mcClassLoader = minecraftServer.getClass().getClassLoader();
        initReflection();
    }

    private void initReflection() {
        try {
            Class<?> simpleContainerClass = mcClassLoader.loadClass("cdk");
            simpleContainerConstructor = simpleContainerClass.getConstructor(int.class);
            simpleContainerConstructor.setAccessible(true);
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to init ReflectiveInventoryFactory: " + e.getMessage());
        }
    }

    @Override
    public CustomInventory create(String title, int rows) {
        if (rows < 1 || rows > 6) {
            throw new IllegalArgumentException("Rows must be 1-6, got: " + rows);
        }
        try {
            int slotCount = rows * 9;
            Object mcContainer = simpleContainerConstructor.newInstance(slotCount);
            return new ReflectiveCustomInventory(title, rows, mcContainer);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create custom inventory: " + e.getMessage(), e);
        }
    }
}
