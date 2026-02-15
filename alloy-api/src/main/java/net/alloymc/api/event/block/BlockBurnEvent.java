package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a block is destroyed by fire.
 */
public class BlockBurnEvent extends BlockEvent implements Cancellable {

    private final Block ignitingBlock;
    private boolean cancelled;

    public BlockBurnEvent(Block block, Block ignitingBlock) {
        super(block);
        this.ignitingBlock = ignitingBlock;
    }

    public Block ignitingBlock() { return ignitingBlock; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
