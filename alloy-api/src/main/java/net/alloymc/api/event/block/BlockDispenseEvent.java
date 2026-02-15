package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.ItemStack;

/**
 * Fired when a dispenser dispenses an item.
 */
public class BlockDispenseEvent extends BlockEvent implements Cancellable {

    private final ItemStack item;
    private boolean cancelled;

    public BlockDispenseEvent(Block block, ItemStack item) {
        super(block);
        this.item = item;
    }

    public ItemStack item() { return item; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
