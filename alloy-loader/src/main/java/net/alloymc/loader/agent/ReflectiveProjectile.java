package net.alloymc.loader.agent;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.EntityType;
import net.alloymc.api.entity.Projectile;
import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Implements {@link Projectile} by wrapping a Minecraft Projectile via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, Projectile = dec, Entity = cgk):
 * <pre>
 *   getOwner() -> dec.p() returns Entity (the shooter)
 *   getUUID()  -> cgk.cY()
 *   position() -> cgk.dI()
 *   level()    -> cgk.ao()
 *   isAlive()  -> cgk.cb()
 *   discard()  -> cgk.aC()
 *   getType()  -> cgk.ay()
 * </pre>
 */
public class ReflectiveProjectile implements Projectile {

    private final Object handle;

    public ReflectiveProjectile(Object handle) {
        this.handle = handle;
    }

    public Object handle() { return handle; }

    @Override
    public Entity shooter() {
        try {
            Object owner = EventFiringHook.invokeNoArgs(handle, "p"); // getOwner
            if (owner != null) return EntityWrapperFactory.wrap(owner);
        } catch (Exception ignored) {}
        return null;
    }

    // =================== Entity ===================

    @Override
    public UUID uniqueId() {
        try {
            Object uuid = EventFiringHook.invokeNoArgs(handle, "cY");
            if (uuid instanceof UUID u) return u;
        } catch (Exception ignored) {}
        return new UUID(0, System.identityHashCode(handle));
    }

    @Override
    public EntityType type() {
        try {
            Object entityType = EventFiringHook.invokeNoArgs(handle, "ay");
            if (entityType != null) {
                String typeName = entityType.toString().toUpperCase();
                for (EntityType et : EntityType.values()) {
                    if (typeName.contains(et.name())) return et;
                }
            }
        } catch (Exception ignored) {}
        return EntityType.UNKNOWN;
    }

    @Override
    public Location location() {
        try {
            Object pos = EventFiringHook.invokeNoArgs(handle, "dI");
            if (pos == null) return null;
            double x = getDoubleField(pos, "g");
            double y = getDoubleField(pos, "h");
            double z = getDoubleField(pos, "i");
            return new Location(world(), x, y, z, 0f, 0f);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public World world() {
        try {
            Object level = EventFiringHook.invokeNoArgs(handle, "ao");
            if (level == null) return null;
            return ReflectiveWorld.wrap(level);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean isValid() {
        try {
            Object alive = EventFiringHook.invokeNoArgs(handle, "cb");
            return alive instanceof Boolean b && b;
        } catch (Exception e) {
            return true;
        }
    }

    @Override
    public void remove() {
        try {
            EventFiringHook.invokeNoArgs(handle, "aC");
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
            System.err.println("[Alloy] Failed to teleport projectile: " + e.getMessage());
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

    private static double getDoubleField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getDouble(obj);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
