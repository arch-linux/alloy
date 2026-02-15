package net.alloymc.api.event.player;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a player drops an item.
 */
public class PlayerDropItemEvent extends PlayerEvent implements Cancellable {

    private final Entity itemDrop;
    private boolean cancelled;

    public PlayerDropItemEvent(Player player, Entity itemDrop) {
        super(player);
        this.itemDrop = itemDrop;
    }

    public Entity itemDrop() { return itemDrop; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
