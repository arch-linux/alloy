package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockPosition;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

import java.util.List;

/**
 * Fired when a structure (tree, mushroom) grows.
 */
public class StructureGrowEvent extends BlockEvent implements Cancellable {

    private final Player player;
    private final List<BlockPosition> affectedPositions;
    private boolean cancelled;

    public StructureGrowEvent(Block block, Player player, List<BlockPosition> affectedPositions) {
        super(block);
        this.player = player;
        this.affectedPositions = affectedPositions;
    }

    public Player player() { return player; }
    public List<BlockPosition> affectedPositions() { return affectedPositions; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
