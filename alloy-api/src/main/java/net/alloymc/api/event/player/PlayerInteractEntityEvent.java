package net.alloymc.api.event.player;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a player right-clicks an entity.
 */
public class PlayerInteractEntityEvent extends PlayerEvent implements Cancellable {

    private final Entity rightClicked;
    private boolean cancelled;

    public PlayerInteractEntityEvent(Player player, Entity rightClicked) {
        super(player);
        this.rightClicked = rightClicked;
    }

    public Entity rightClicked() { return rightClicked; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
