package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.world.Location;

/**
 * Fired when a player moves (position or look direction changes).
 */
public class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private final Location from;
    private Location to;
    private boolean cancelled;

    public PlayerMoveEvent(Player player, Location from, Location to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location from() { return from; }
    public Location to() { return to; }
    public void setTo(Location to) { this.to = to; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
