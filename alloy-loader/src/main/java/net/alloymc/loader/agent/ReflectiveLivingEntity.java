package net.alloymc.loader.agent;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.EntityType;
import net.alloymc.api.entity.LivingEntity;
import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Implements {@link LivingEntity} by wrapping a Minecraft LivingEntity via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, LivingEntity = chl, Entity = cgk):
 * <pre>
 *   getHealth()    -> chl.eZ() returns float
 *   getMaxHealth() -> chl.fq() returns float
 *   setHealth(f)   -> chl.x(float)
 *   isDeadOrDying()-> chl.fa() returns boolean
 *   hurtServer()   -> chl.a(ServerLevel, DamageSource, float) returns boolean
 *   getUUID()      -> cgk.cY()
 *   position()     -> cgk.dI()
 *   level()        -> cgk.ao()
 *   isAlive()      -> cgk.cb()
 *   discard()      -> cgk.aC()
 *   getType()      -> cgk.ay()
 * </pre>
 */
public class ReflectiveLivingEntity implements LivingEntity {

    protected final Object handle;

    public ReflectiveLivingEntity(Object handle) {
        this.handle = handle;
    }

    public Object handle() { return handle; }

    // =================== LivingEntity ===================

    @Override
    public double health() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "eZ"); // getHealth -> float
            if (result instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 20.0;
    }

    @Override
    public double maxHealth() {
        try {
            // getMaxHealth: fq() returns float
            Object result = EventFiringHook.invokeNoArgs(handle, "fq");
            if (result instanceof Number n) return n.doubleValue();
        } catch (Exception ignored) {}
        return 20.0;
    }

    @Override
    public void setHealth(double health) {
        try {
            // chl.x(float) = setHealth
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("x") && m.getParameterCount() == 1
                        && m.getParameterTypes()[0] == float.class) {
                    m.setAccessible(true);
                    m.invoke(handle, (float) health);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void damage(double amount) {
        // Use generic damage source — difficult without a proper DamageSource instance.
        // Best-effort: call hurt with a generic source from the entity's level.
        try {
            Object level = EventFiringHook.invokeNoArgs(handle, "ao");
            if (level == null) return;

            // Get the damageSources() from level
            Object damageSources = EventFiringHook.invokeNoArgs(level, "ad_");
            if (damageSources == null) return;

            // DamageSources.generic() -> a() (returns DamageSource for GENERIC)
            Object genericSource = EventFiringHook.invokeNoArgs(damageSources, "a");
            if (genericSource == null) return;

            // cfq.a(DamageSource, float) = hurt
            for (Method m : handle.getClass().getMethods()) {
                if (m.getName().equals("a") && m.getParameterCount() == 2
                        && m.getParameterTypes()[1] == float.class
                        && m.getReturnType() == boolean.class
                        && m.getParameterTypes()[0].isInstance(genericSource)) {
                    m.setAccessible(true);
                    m.invoke(handle, genericSource, (float) amount);
                    return;
                }
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void damage(double amount, Entity source) {
        // For simplicity, delegate to the no-source version
        damage(amount);
    }

    @Override
    public boolean isDead() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "fa"); // isDeadOrDying
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public LivingEntity target() {
        // getTarget -> cfq.eK() or similar; varies by mob type
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "eK");
            if (result != null) {
                Entity wrapped = EntityWrapperFactory.wrap(result);
                if (wrapped instanceof LivingEntity le) return le;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public void setTarget(LivingEntity target) {
        // No-op for now — requires Mob-specific setTarget
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

            // Also extract yaw/pitch for LivingEntity
            float yaw = getFloatMethod(handle, "ec");  // getYRot
            float pitch = getFloatMethod(handle, "ee"); // getXRot

            return new Location(world(), x, y, z, yaw, pitch);
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

    protected static double getDoubleField(Object obj, String fieldName) {
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            return f.getDouble(obj);
        } catch (Exception e) {
            return 0.0;
        }
    }

    protected static float getFloatMethod(Object obj, String methodName) {
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0
                        && (m.getReturnType() == float.class || m.getReturnType() == Float.class)) {
                    m.setAccessible(true);
                    return (float) m.invoke(obj);
                }
            }
        } catch (Exception ignored) {}
        return 0f;
    }
}
