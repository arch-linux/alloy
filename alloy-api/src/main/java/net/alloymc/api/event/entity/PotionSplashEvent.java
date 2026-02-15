package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.entity.LivingEntity;
import net.alloymc.api.event.Cancellable;

import java.util.Collection;

/**
 * Fired when a splash potion hits entities.
 */
public class PotionSplashEvent extends EntityEvent implements Cancellable {

    private final Collection<LivingEntity> affectedEntities;
    private boolean cancelled;

    public PotionSplashEvent(Entity potion, Collection<LivingEntity> affectedEntities) {
        super(potion);
        this.affectedEntities = affectedEntities;
    }

    public Collection<LivingEntity> affectedEntities() { return affectedEntities; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
