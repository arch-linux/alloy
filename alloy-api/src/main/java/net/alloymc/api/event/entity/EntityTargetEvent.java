package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when an entity targets or untargets another entity.
 */
public class EntityTargetEvent extends EntityEvent implements Cancellable {

    private Entity target;
    private boolean cancelled;

    public EntityTargetEvent(Entity entity, Entity target) {
        super(entity);
        this.target = target;
    }

    public Entity target() { return target; }
    public void setTarget(Entity target) { this.target = target; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
