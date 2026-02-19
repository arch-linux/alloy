package net.alloymc.api.event.inventory;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.event.Event;
import net.alloymc.api.inventory.CustomInventory;
import net.alloymc.api.inventory.ItemStack;

/**
 * Fired when a player clicks a slot in a {@link CustomInventory}.
 * Cancelling this event prevents the default click behavior (item pickup/swap).
 */
public class InventoryClickEvent extends Event implements Cancellable {

    private final Player player;
    private final CustomInventory inventory;
    private final int slot;
    private final ItemStack clickedItem;
    private final ClickAction action;
    private boolean cancelled;

    public InventoryClickEvent(Player player, CustomInventory inventory,
                               int slot, ItemStack clickedItem, ClickAction action) {
        this.player = player;
        this.inventory = inventory;
        this.slot = slot;
        this.clickedItem = clickedItem;
        this.action = action;
    }

    public Player player() { return player; }
    public CustomInventory inventory() { return inventory; }
    public int slot() { return slot; }
    public ItemStack clickedItem() { return clickedItem; }
    public ClickAction action() { return action; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
