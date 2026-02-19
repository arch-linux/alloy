package net.alloymc.api.event.inventory;

/**
 * The type of drag performed across inventory slots.
 */
public enum DragType {
    /**
     * Items are distributed evenly across all dragged slots (left-click drag).
     */
    EVEN,

    /**
     * One item is placed in each dragged slot (right-click drag).
     */
    SINGLE
}
