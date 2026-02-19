package net.alloymc.loader.agent;

import net.alloymc.api.inventory.ItemFactory;
import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Creates MC ItemStacks from Material + count using reflection.
 *
 * <p>Mapping reference (MC 1.21.11):
 * <pre>
 *   BuiltInRegistries (mi): ITEM field = h (DefaultedRegistry)
 *   ResourceLocation (amo): parse(String) = a(String)
 *   Registry.get(ResourceLocation) -> a(ResourceLocation) returns Object
 *   ItemStack (dlt): constructor (ItemLike, int) = (dwn, int)
 *   ItemLike (dwn): interface implemented by Item (dlp)
 * </pre>
 */
public final class ReflectiveItemFactory implements ItemFactory {

    private final ClassLoader mcClassLoader;

    // Cached reflection handles
    private Object itemRegistry;
    private Method identifierParse;
    private Constructor<?> itemStackConstructor;
    private Class<?> itemLikeClass;

    public ReflectiveItemFactory(Object minecraftServer) {
        this.mcClassLoader = minecraftServer.getClass().getClassLoader();
        initReflection();
    }

    private void initReflection() {
        try {
            // BuiltInRegistries.ITEM -> mi.h
            Class<?> registriesClass = mcClassLoader.loadClass("mi");
            Field itemField = registriesClass.getDeclaredField("h");
            itemField.setAccessible(true);
            itemRegistry = itemField.get(null);

            // ResourceLocation.parse(String) -> amo.a(String)
            Class<?> idClass = mcClassLoader.loadClass("amo");
            identifierParse = idClass.getMethod("a", String.class);

            // ItemLike interface -> dwn
            itemLikeClass = mcClassLoader.loadClass("dwn");

            // ItemStack(ItemLike, int) -> dlt(dwn, int)
            Class<?> itemStackClass = mcClassLoader.loadClass("dlt");
            for (Constructor<?> ctor : itemStackClass.getDeclaredConstructors()) {
                if (ctor.getParameterCount() == 2
                        && ctor.getParameterTypes()[0] == itemLikeClass
                        && ctor.getParameterTypes()[1] == int.class) {
                    ctor.setAccessible(true);
                    itemStackConstructor = ctor;
                    break;
                }
            }

            if (itemStackConstructor == null) {
                System.err.println("[Alloy] Warning: Could not find ItemStack(ItemLike, int) constructor");
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to init ReflectiveItemFactory: " + e.getMessage());
        }
    }

    @Override
    public ItemStack create(Material type, int amount) {
        if (type == null || type == Material.AIR) {
            return createEmpty();
        }
        try {
            // Convert Material to MC registry name: DIAMOND -> "minecraft:diamond"
            String mcName = "minecraft:" + type.name().toLowerCase();

            // Look up in registry
            Object identifier = identifierParse.invoke(null, mcName);
            Object mcItem = lookupRegistry(identifier);

            if (mcItem == null) {
                System.err.println("[Alloy] Item not found in registry: " + mcName);
                return createEmpty();
            }

            // Create MC ItemStack via constructor
            if (itemStackConstructor != null && itemLikeClass.isInstance(mcItem)) {
                Object mcItemStack = itemStackConstructor.newInstance(mcItem, amount);
                return ReflectiveItemStack.wrap(mcItemStack);
            }

            System.err.println("[Alloy] Cannot create ItemStack: constructor unavailable for " + mcName);
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to create ItemStack for " + type + ": " + e.getMessage());
        }
        return createEmpty();
    }

    private Object lookupRegistry(Object identifier) {
        try {
            // Registry.get(ResourceLocation) -> a(ResourceLocation)
            for (Method m : itemRegistry.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0].isInstance(identifier)
                        && !m.getReturnType().isPrimitive()
                        && m.getReturnType() != void.class) {
                    m.setAccessible(true);
                    Object result = m.invoke(itemRegistry, identifier);
                    if (result != null) return result;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private ItemStack createEmpty() {
        // Return an empty ItemStack by calling ItemStack.EMPTY or creating one with AIR
        try {
            Class<?> itemStackClass = mcClassLoader.loadClass("dlt");
            // ItemStack has a static EMPTY field -> b
            Field emptyField = itemStackClass.getDeclaredField("b");
            emptyField.setAccessible(true);
            Object emptyStack = emptyField.get(null);
            return ReflectiveItemStack.wrap(emptyStack);
        } catch (Exception e) {
            return null;
        }
    }
}
