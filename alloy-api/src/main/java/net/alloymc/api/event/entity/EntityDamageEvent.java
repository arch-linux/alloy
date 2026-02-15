package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when an entity takes damage.
 */
public class EntityDamageEvent extends EntityEvent implements Cancellable {

    private final DamageCause cause;
    private double damage;
    private boolean cancelled;

    public EntityDamageEvent(Entity entity, DamageCause cause, double damage) {
        super(entity);
        this.cause = cause;
        this.damage = damage;
    }

    public DamageCause cause() { return cause; }
    public double damage() { return damage; }
    public void setDamage(double damage) { this.damage = damage; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum DamageCause {
        ENTITY_ATTACK,
        ENTITY_SWEEP_ATTACK,
        PROJECTILE,
        BLOCK_EXPLOSION,
        ENTITY_EXPLOSION,
        FIRE,
        FIRE_TICK,
        LAVA,
        DROWNING,
        SUFFOCATION,
        FALL,
        CONTACT,
        VOID,
        LIGHTNING,
        POISON,
        WITHER,
        MAGIC,
        FALLING_BLOCK,
        THORNS,
        CRAMMING,
        FLY_INTO_WALL,
        FREEZE,
        SONIC_BOOM,
        CUSTOM
    }
}
