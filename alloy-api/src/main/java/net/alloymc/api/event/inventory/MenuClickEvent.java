package net.alloymc.api.event.inventory;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.event.Event;
import net.alloymc.api.gui.MenuInstance;
import net.alloymc.api.gui.SlotDefinition;
import net.alloymc.api.inventory.ItemStack;

/**
 * Fired when a player clicks a slot in a {@link net.alloymc.api.gui.MenuLayout} GUI.
 *
 * <p>Unlike {@link InventoryClickEvent} (which handles simple chest GUIs),
 * this event provides access to the {@link MenuInstance} and the clicked
 * {@link SlotDefinition} for menus with typed slots and data properties.
 *
 * <p>The {@link #rawSlot()} value gives the raw container slot index from the
 * packet. For a menu with N rows, slots 0 to (N*9-1) are menu slots, while
 * slots >= N*9 are in the player's inventory. This lets mods handle player
 * inventory clicks (e.g., for stocking a shop by clicking items in your inventory).
 *
 * <p>Cancelling this event prevents the default click behavior.
 */
public class MenuClickEvent extends Event implements Cancellable {

    private final Player player;
    private final MenuInstance menu;
    private final int rawSlot;
    private final SlotDefinition slot;
    private final ItemStack clickedItem;
    private final ClickAction action;
    private boolean cancelled;

    public MenuClickEvent(Player player, MenuInstance menu, int rawSlot,
                          SlotDefinition slot, ItemStack clickedItem,
                          ClickAction action) {
        this.player = player;
        this.menu = menu;
        this.rawSlot = rawSlot;
        this.slot = slot;
        this.clickedItem = clickedItem;
        this.action = action;
    }

    public Player player() { return player; }

    /**
     * The live menu instance that was clicked.
     */
    public MenuInstance menu() { return menu; }

    /**
     * The raw container slot index from the click packet.
     *
     * <p>For a menu with N rows:
     * <ul>
     *   <li>0 to (N*9-1): menu container slots</li>
     *   <li>N*9 to (N*9+26): player main inventory (indices 9-35)</li>
     *   <li>(N*9+27) to (N*9+35): player hotbar (indices 0-8)</li>
     * </ul>
     */
    public int rawSlot() { return rawSlot; }

    /**
     * The slot definition that was clicked, or null if the click was outside
     * defined menu slots (e.g., in the player's inventory area).
     */
    public SlotDefinition slot() { return slot; }

    /**
     * The item in the clicked slot at the time of the click.
     */
    public ItemStack clickedItem() { return clickedItem; }

    /**
     * The type of click action performed.
     */
    public ClickAction action() { return action; }

    /**
     * Returns true if the click was in the player's own inventory
     * (below the menu container), not in the menu itself.
     */
    public boolean isPlayerInventory() {
        return rawSlot >= menu.layout().rows() * 9;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
