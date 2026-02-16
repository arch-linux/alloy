package net.alloymc.loader.agent;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.EntityType;
import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Implements {@link Entity} by wrapping a Minecraft Entity via reflection.
 * This is the base wrapper for entities that don't match a more specific type.
 *
 * <p>Use {@link EntityWrapperFactory#wrap(Object)} to get the correct wrapper type.
 *
 * <p>Mapping reference (MC 1.21.11, Entity = cgk):
 * <pre>
 *   getUUID() -> cY()          (returns UUID)
 *   position() -> dI()         (returns Vec3 with x/y/z = g/h/i)
 *   level() -> ao()            (returns Level)
 *   isAlive() -> cb()          (returns boolean)
 *   discard() -> aC()          (void)
 *   getType() -> ay()          (returns EntityType)
 * </pre>
 */
public class ReflectiveEntity implements Entity {

    protected final Object handle; // Minecraft Entity instance

    ReflectiveEntity(Object handle) {
        this.handle = handle;
    }

    /** Returns the underlying Minecraft Entity object. */
    public Object handle() { return handle; }

    @Override
    public UUID uniqueId() {
        try {
            Object uuid = EventFiringHook.invokeNoArgs(handle, "cY"); // getUUID
            if (uuid instanceof UUID u) return u;
        } catch (Exception ignored) {}
        return new UUID(0, System.identityHashCode(handle));
    }

    @Override
    public EntityType type() {
        // Determine entity type from the Minecraft EntityType registry name
        try {
            Object entityType = EventFiringHook.invokeNoArgs(handle, "ay"); // getType()
            if (entityType != null) {
                String typeName = entityType.toString().toUpperCase();
                // Try to match against our EntityType enum
                for (EntityType et : EntityType.values()) {
                    if (typeName.contains(et.name())) {
                        return et;
                    }
                }
            }
        } catch (Exception ignored) {}
        return EntityType.UNKNOWN;
    }

    @Override
    public Location location() {
        try {
            Object pos = EventFiringHook.invokeNoArgs(handle, "dI"); // position() -> Vec3
            if (pos == null) return null;
            double x = getDoubleField(pos, "g"); // Vec3.x
            double y = getDoubleField(pos, "h"); // Vec3.y
            double z = getDoubleField(pos, "i"); // Vec3.z
            return new Location(world(), x, y, z, 0f, 0f);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public World world() {
        try {
            Object level = EventFiringHook.invokeNoArgs(handle, "ao"); // level()
            if (level == null) return null;
            return ReflectiveWorld.wrap(level);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isValid() {
        try {
            Object alive = EventFiringHook.invokeNoArgs(handle, "cb"); // isAlive()
            return alive instanceof Boolean b && b;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void remove() {
        try {
            EventFiringHook.invokeNoArgs(handle, "aC"); // discard()
        } catch (Exception ignored) {}
    }

    @Override
    public void teleport(Location destination) {
        try {
            if (destination == null) return;
            // Entity.moveTo(double, double, double, float, float) — sets position + rotation
            for (Method m : handle.getClass().getMethods()) {
                if (m.getReturnType() == void.class && m.getParameterCount() == 5) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == double.class && params[1] == double.class
                            && params[2] == double.class
                            && params[3] == float.class && params[4] == float.class) {
                        m.setAccessible(true);
                        m.invoke(handle, destination.x(), destination.y(), destination.z(),
                                destination.yaw(), destination.pitch());
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[Alloy] Failed to teleport entity: " + e.getMessage());
        }
    }

    @Override public String name() { return type().name().toLowerCase(); }
    @Override public String displayName() { return name(); }

    @Override
    public boolean hasMetadata(String key) {
        return MetadataStore.has(uniqueId(), key);
    }

    @Override
    public void setMetadata(String key, Object value) {
        MetadataStore.set(uniqueId(), key, value);
    }

    @Override
    public Object getMetadata(String key) {
        return MetadataStore.get(uniqueId(), key);
    }

    @Override
    public void removeMetadata(String key) {
        MetadataStore.remove(uniqueId(), key);
    }

    // =================== Reflection helpers ===================

    static double getDoubleField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getDouble(obj);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
