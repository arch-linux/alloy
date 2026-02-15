package net.alloymc.api.entity;

/**
 * An entity that has health and can take damage.
 */
public interface LivingEntity extends Entity {

    double health();

    double maxHealth();

    void setHealth(double health);

    void damage(double amount);

    void damage(double amount, Entity source);

    boolean isDead();

    /**
     * Returns the entity this living entity is currently targeting, if any.
     */
    LivingEntity target();

    void setTarget(LivingEntity target);
}
