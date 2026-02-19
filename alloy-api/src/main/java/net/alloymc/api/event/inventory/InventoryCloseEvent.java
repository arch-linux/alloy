package net.alloymc.api.event.inventory;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Event;
import net.alloymc.api.inventory.CustomInventory;

/**
 * Fired when a player closes a {@link CustomInventory}.
 */
public class InventoryCloseEvent extends Event {

    private final Player player;
    private final CustomInventory inventory;

    public InventoryCloseEvent(Player player, CustomInventory inventory) {
        this.player = player;
        this.inventory = inventory;
    }

    public Player player() { return player; }
    public CustomInventory inventory() { return inventory; }
}
