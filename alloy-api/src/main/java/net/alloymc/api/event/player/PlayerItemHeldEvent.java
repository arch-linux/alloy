package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a player changes the item slot they have selected.
 */
public class PlayerItemHeldEvent extends PlayerEvent implements Cancellable {

    private final int previousSlot;
    private final int newSlot;
    private boolean cancelled;

    public PlayerItemHeldEvent(Player player, int previousSlot, int newSlot) {
        super(player);
        this.previousSlot = previousSlot;
        this.newSlot = newSlot;
    }

    public int previousSlot() { return previousSlot; }
    public int newSlot() { return newSlot; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
