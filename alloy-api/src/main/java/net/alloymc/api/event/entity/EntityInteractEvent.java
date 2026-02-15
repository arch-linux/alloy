package net.alloymc.api.event.entity;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when an entity interacts with a block (e.g., trampling farmland).
 */
public class EntityInteractEvent extends EntityEvent implements Cancellable {

    private final Block block;
    private boolean cancelled;

    public EntityInteractEvent(Entity entity, Block block) {
        super(entity);
        this.block = block;
    }

    public Block block() { return block; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
