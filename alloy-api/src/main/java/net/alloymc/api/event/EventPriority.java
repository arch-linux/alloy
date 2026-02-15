package net.alloymc.api.event;

/**
 * Priority levels for event handlers, executed in ascending ordinal order.
 */
public enum EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR
}
