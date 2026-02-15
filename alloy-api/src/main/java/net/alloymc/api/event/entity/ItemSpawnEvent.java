package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when an item entity spawns in the world.
 */
public class ItemSpawnEvent extends EntityEvent implements Cancellable {

    private boolean cancelled;

    public ItemSpawnEvent(Entity itemEntity) {
        super(itemEntity);
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
