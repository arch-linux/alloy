package net.alloymc.api.event.entity;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.Material;

/**
 * Fired when an entity changes a block (enderman picking up blocks, sheep eating grass, etc).
 */
public class EntityChangeBlockEvent extends EntityEvent implements Cancellable {

    private final Block block;
    private final Material toType;
    private boolean cancelled;

    public EntityChangeBlockEvent(Entity entity, Block block, Material toType) {
        super(entity);
        this.block = block;
        this.toType = toType;
    }

    public Block block() { return block; }
    public Material toType() { return toType; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
