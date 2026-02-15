package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockFace;
import net.alloymc.api.event.Cancellable;

import java.util.List;

/**
 * Fired when a piston extends or retracts, moving blocks.
 */
public class BlockPistonEvent extends BlockEvent implements Cancellable {

    private final BlockFace direction;
    private final List<Block> movedBlocks;
    private final boolean extending;
    private boolean cancelled;

    public BlockPistonEvent(Block pistonBlock, BlockFace direction, List<Block> movedBlocks, boolean extending) {
        super(pistonBlock);
        this.direction = direction;
        this.movedBlocks = movedBlocks;
        this.extending = extending;
    }

    public BlockFace direction() { return direction; }
    public List<Block> movedBlocks() { return movedBlocks; }
    public boolean isExtending() { return extending; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
