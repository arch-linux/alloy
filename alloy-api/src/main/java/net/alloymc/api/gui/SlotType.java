package net.alloymc.api.gui;

/**
 * Defines the behavior of a slot in a {@link MenuLayout}.
 *
 * <ul>
 *   <li>{@link #NORMAL} — Players can freely insert and extract items.</li>
 *   <li>{@link #INPUT} — Players can insert items; extraction requires shift-click or automation.</li>
 *   <li>{@link #OUTPUT} — Players can only take items out (e.g., furnace result slot).</li>
 *   <li>{@link #FUEL} — Accepts only fuel items (coal, charcoal, etc.).</li>
 *   <li>{@link #DISPLAY} — No interaction allowed; purely visual.</li>
 * </ul>
 */
public enum SlotType {
    NORMAL,
    INPUT,
    OUTPUT,
    FUEL,
    DISPLAY
}
