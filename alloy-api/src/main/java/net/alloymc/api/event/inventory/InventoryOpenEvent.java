package net.alloymc.api.event.inventory;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.event.Event;
import net.alloymc.api.inventory.CustomInventory;

/**
 * Fired when a player opens a {@link CustomInventory}.
 * Cancelling this event prevents the inventory from opening.
 */
public class InventoryOpenEvent extends Event implements Cancellable {

    private final Player player;
    private final CustomInventory inventory;
    private boolean cancelled;

    public InventoryOpenEvent(Player player, CustomInventory inventory) {
        this.player = player;
        this.inventory = inventory;
    }

    public Player player() { return player; }
    public CustomInventory inventory() { return inventory; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
