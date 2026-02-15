package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when an entity spawns in the world.
 */
public class EntitySpawnEvent extends EntityEvent implements Cancellable {

    private final SpawnReason reason;
    private boolean cancelled;

    public EntitySpawnEvent(Entity entity, SpawnReason reason) {
        super(entity);
        this.reason = reason;
    }

    public SpawnReason reason() { return reason; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum SpawnReason {
        NATURAL,
        SPAWNER,
        EGG,
        BREEDING,
        BUILD_IRONGOLEM,
        BUILD_SNOWMAN,
        VILLAGE_DEFENSE,
        RAID,
        CUSTOM,
        DEFAULT
    }
}
