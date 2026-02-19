package net.alloymc.api.event.inventory;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.event.Event;
import net.alloymc.api.inventory.CustomInventory;
import net.alloymc.api.inventory.ItemStack;

import java.util.Set;

/**
 * Fired when a player drags items across multiple slots in a {@link CustomInventory}.
 * Cancelling this event prevents the drag operation.
 */
public class InventoryDragEvent extends Event implements Cancellable {

    private final Player player;
    private final CustomInventory inventory;
    private final Set<Integer> slots;
    private final ItemStack cursor;
    private final DragType dragType;
    private boolean cancelled;

    public InventoryDragEvent(Player player, CustomInventory inventory,
                              Set<Integer> slots, ItemStack cursor, DragType dragType) {
        this.player = player;
        this.inventory = inventory;
        this.slots = slots;
        this.cursor = cursor;
        this.dragType = dragType;
    }

    public Player player() { return player; }
    public CustomInventory inventory() { return inventory; }

    /**
     * The set of slot indices involved in the drag.
     */
    public Set<Integer> slots() { return slots; }

    /**
     * The item being dragged (cursor item).
     */
    public ItemStack cursor() { return cursor; }

    /**
     * The type of drag (EVEN or SINGLE).
     */
    public DragType dragType() { return dragType; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
