package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a block is ignited (catches fire).
 */
public class BlockIgniteEvent extends BlockEvent implements Cancellable {

    private final IgniteCause cause;
    private final Entity ignitingEntity;
    private final Block ignitingBlock;
    private boolean cancelled;

    public BlockIgniteEvent(Block block, IgniteCause cause, Entity ignitingEntity, Block ignitingBlock) {
        super(block);
        this.cause = cause;
        this.ignitingEntity = ignitingEntity;
        this.ignitingBlock = ignitingBlock;
    }

    public IgniteCause cause() { return cause; }
    public Entity ignitingEntity() { return ignitingEntity; }
    public Block ignitingBlock() { return ignitingBlock; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum IgniteCause {
        LAVA,
        FLINT_AND_STEEL,
        SPREAD,
        LIGHTNING,
        FIREBALL,
        EXPLOSION,
        ARROW
    }
}
