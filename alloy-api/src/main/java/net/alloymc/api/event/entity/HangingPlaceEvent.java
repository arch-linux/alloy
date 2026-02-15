package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a hanging entity (painting, item frame) is placed.
 */
public class HangingPlaceEvent extends EntityEvent implements Cancellable {

    private final Player player;
    private boolean cancelled;

    public HangingPlaceEvent(Entity entity, Player player) {
        super(entity);
        this.player = player;
    }

    public Player player() { return player; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
