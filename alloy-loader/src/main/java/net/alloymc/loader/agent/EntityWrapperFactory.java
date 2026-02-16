package net.alloymc.loader.agent;

import net.alloymc.api.entity.Entity;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory that inspects a Minecraft entity's class hierarchy and returns
 * the most specific Alloy wrapper type.
 *
 * <p>This ensures {@code instanceof Player}, {@code instanceof Projectile}, etc.
 * work correctly in mod code. The factory caches classification results per class
 * to avoid repeated hierarchy inspection.
 *
 * <p>Mapping reference (MC 1.21.11):
 * <pre>
 *   ServerPlayer  = axg (extends chl = LivingEntity)
 *   Projectile    = dec (abstract, extends cgk = Entity)
 *   TamableAnimal = cii (extends Animal, extends LivingEntity = chl)
 *   LivingEntity  = chl (extends cgk = Entity)
 * </pre>
 */
public final class EntityWrapperFactory {

    private EntityWrapperFactory() {}

    private enum EntityCategory {
        PLAYER,
        PROJECTILE,
        TAMEABLE,
        LIVING,
        ENTITY
    }

    private static final Map<String, EntityCategory> CLASS_CACHE = new ConcurrentHashMap<>();

    // Obfuscated class names for MC 1.21.11
    private static final String SERVER_PLAYER_CLASS = "axg";
    private static final String LIVING_ENTITY_CLASS = "chl";
    private static final String PROJECTILE_CLASS = "dec";
    private static final String TAMABLE_ANIMAL_CLASS = "cii";
    private static final String VEHICLE_ENTITY_CLASS = "dga";
    private static final String HANGING_ENTITY_CLASS = "czb";

    /**
     * Wraps any Minecraft entity object, returning the most specific Alloy wrapper.
     * Returns null if entity is null.
     */
    public static Entity wrap(Object entity) {
        if (entity == null) return null;

        EntityCategory category = classify(entity);
        return switch (category) {
            case PLAYER -> ReflectivePlayer.wrap(entity);
            case PROJECTILE -> new ReflectiveProjectile(entity);
            case TAMEABLE -> new ReflectiveTameableEntity(entity);
            case LIVING -> new ReflectiveLivingEntity(entity);
            case ENTITY -> new ReflectiveEntity(entity);
        };
    }

    private static EntityCategory classify(Object entity) {
        String className = entity.getClass().getName();
        EntityCategory cached = CLASS_CACHE.get(className);
        if (cached != null) return cached;

        EntityCategory result = classifyByHierarchy(entity.getClass());
        CLASS_CACHE.put(className, result);
        return result;
    }

    private static EntityCategory classifyByHierarchy(Class<?> clazz) {
        // Check in order from most specific to least specific
        if (isAssignableFrom(clazz, SERVER_PLAYER_CLASS)) return EntityCategory.PLAYER;
        if (isAssignableFrom(clazz, TAMABLE_ANIMAL_CLASS)) return EntityCategory.TAMEABLE;
        if (isAssignableFrom(clazz, PROJECTILE_CLASS)) return EntityCategory.PROJECTILE;
        if (isAssignableFrom(clazz, LIVING_ENTITY_CLASS)) return EntityCategory.LIVING;
        return EntityCategory.ENTITY;
    }

    /**
     * Checks if clazz extends/implements the obfuscated target class by walking
     * the class hierarchy. We check by simple name since the MC classes are in
     * the default package.
     */
    private static boolean isAssignableFrom(Class<?> clazz, String targetSimpleName) {
        Class<?> current = clazz;
        while (current != null) {
            if (current.getName().equals(targetSimpleName)) return true;
            // Also check interfaces (for Projectile which is an abstract class but could be interface)
            for (Class<?> iface : current.getInterfaces()) {
                if (matchesOrExtends(iface, targetSimpleName)) return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean matchesOrExtends(Class<?> iface, String targetSimpleName) {
        if (iface.getName().equals(targetSimpleName)) return true;
        for (Class<?> parent : iface.getInterfaces()) {
            if (matchesOrExtends(parent, targetSimpleName)) return true;
        }
        return false;
    }

    /** Checks if the given MC entity is a vehicle (VehicleEntity hierarchy). */
    public static boolean isVehicle(Object entity) {
        return isAssignableFrom(entity.getClass(), VEHICLE_ENTITY_CLASS);
    }

    /** Checks if the given MC entity is a hanging entity (painting, item frame). */
    public static boolean isHangingEntity(Object entity) {
        return isAssignableFrom(entity.getClass(), HANGING_ENTITY_CLASS);
    }
}
