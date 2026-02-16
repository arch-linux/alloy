package net.alloymc.loader.agent;

import net.alloymc.api.AlloyAPI;
import net.alloymc.api.entity.Player;
import net.alloymc.api.entity.TameableEntity;

import java.util.Optional;
import java.util.UUID;

/**
 * Implements {@link TameableEntity} by wrapping a Minecraft TamableAnimal via reflection.
 *
 * <p>Mapping reference (MC 1.21.11, TamableAnimal = cii, extends Animal, extends LivingEntity = chl):
 * <pre>
 *   isTame()         -> cii.p() returns boolean
 *   getOwnerUUID()   -> cii.h() returns Optional&lt;UUID&gt; (or UUID nullable)
 *   getOwner()       -> cii.P() returns LivingEntity (nullable)
 * </pre>
 */
public class ReflectiveTameableEntity extends ReflectiveLivingEntity implements TameableEntity {

    public ReflectiveTameableEntity(Object handle) {
        super(handle);
    }

    @Override
    public boolean isTamed() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "p"); // isTame
            return result instanceof Boolean b && b;
        } catch (Exception ignored) {}
        return false;
    }

    @Override
    public UUID ownerUniqueId() {
        try {
            Object result = EventFiringHook.invokeNoArgs(handle, "h"); // getOwnerUUID
            if (result instanceof UUID uuid) return uuid;
            // May return Optional<UUID>
            if (result instanceof Optional<?> opt) {
                Object val = opt.orElse(null);
                if (val instanceof UUID uuid) return uuid;
            }
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public Player owner() {
        UUID ownerId = ownerUniqueId();
        if (ownerId == null) return null;
        try {
            Optional<? extends Player> player = AlloyAPI.server().player(ownerId);
            return player.orElse(null);
        } catch (Exception ignored) {}
        return null;
    }
}
