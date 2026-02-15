package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.ItemStack;

/**
 * Fired when an entity picks up an item from the ground.
 */
public class EntityPickupItemEvent extends EntityEvent implements Cancellable {

    private final Entity item;
    private boolean cancelled;

    public EntityPickupItemEvent(Entity entity, Entity item) {
        super(entity);
        this.item = item;
    }

    /**
     * The item entity being picked up.
     */
    public Entity itemEntity() { return item; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
