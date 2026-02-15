package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Cancellable;

import java.util.List;

/**
 * Fired when a block explosion destroys blocks.
 */
public class BlockExplodeEvent extends BlockEvent implements Cancellable {

    private final List<Block> affectedBlocks;
    private boolean cancelled;

    public BlockExplodeEvent(Block block, List<Block> affectedBlocks) {
        super(block);
        this.affectedBlocks = affectedBlocks;
    }

    /**
     * Mutable list of blocks that will be destroyed. Remove blocks to protect them.
     */
    public List<Block> affectedBlocks() { return affectedBlocks; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
