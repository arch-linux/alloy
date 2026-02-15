package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a liquid flows from one block to another.
 */
public class BlockFromToEvent extends BlockEvent implements Cancellable {

    private final Block toBlock;
    private boolean cancelled;

    public BlockFromToEvent(Block fromBlock, Block toBlock) {
        super(fromBlock);
        this.toBlock = toBlock;
    }

    public Block fromBlock() { return block(); }
    public Block toBlock() { return toBlock; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
