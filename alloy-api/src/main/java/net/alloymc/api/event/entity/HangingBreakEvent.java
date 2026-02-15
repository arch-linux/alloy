package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a hanging entity (painting, item frame) is broken.
 */
public class HangingBreakEvent extends EntityEvent implements Cancellable {

    private final RemoveCause cause;
    private final Entity remover;
    private boolean cancelled;

    public HangingBreakEvent(Entity entity, RemoveCause cause, Entity remover) {
        super(entity);
        this.cause = cause;
        this.remover = remover;
    }

    public RemoveCause cause() { return cause; }
    public Entity remover() { return remover; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum RemoveCause {
        ENTITY,
        EXPLOSION,
        PHYSICS,
        DEFAULT
    }
}
