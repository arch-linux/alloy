package net.alloymc.api.event.entity;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

import java.util.List;

/**
 * Fired when an entity explosion destroys blocks (creeper, TNT, etc).
 */
public class EntityExplodeEvent extends EntityEvent implements Cancellable {

    private final List<Block> affectedBlocks;
    private boolean cancelled;

    public EntityExplodeEvent(Entity entity, List<Block> affectedBlocks) {
        super(entity);
        this.affectedBlocks = affectedBlocks;
    }

    /**
     * Mutable list. Remove blocks to prevent their destruction.
     */
    public List<Block> affectedBlocks() { return affectedBlocks; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
