package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a block spreads (fire, mushroom, etc).
 */
public class BlockSpreadEvent extends BlockEvent implements Cancellable {

    private final Block source;
    private boolean cancelled;

    public BlockSpreadEvent(Block block, Block source) {
        super(block);
        this.source = source;
    }

    public Block source() { return source; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
