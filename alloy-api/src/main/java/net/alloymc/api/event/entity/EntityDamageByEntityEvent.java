package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;

/**
 * Fired when an entity is damaged by another entity (including projectiles).
 */
public class EntityDamageByEntityEvent extends EntityDamageEvent {

    private final Entity damager;

    public EntityDamageByEntityEvent(Entity entity, Entity damager, DamageCause cause, double damage) {
        super(entity, cause, damage);
        this.damager = damager;
    }

    /**
     * The entity that caused the damage (the attacker or projectile).
     */
    public Entity damager() { return damager; }
}
