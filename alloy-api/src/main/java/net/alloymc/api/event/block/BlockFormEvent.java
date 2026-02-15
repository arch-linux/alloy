package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.Material;

/**
 * Fired when a block forms naturally (cobblestone from lava+water, snow layers, etc).
 */
public class BlockFormEvent extends BlockEvent implements Cancellable {

    private final Material newType;
    private boolean cancelled;

    public BlockFormEvent(Block block, Material newType) {
        super(block);
        this.newType = newType;
    }

    public Material newType() { return newType; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
