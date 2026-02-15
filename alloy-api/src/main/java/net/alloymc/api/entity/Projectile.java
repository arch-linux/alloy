package net.alloymc.api.entity;

/**
 * Represents a projectile entity (arrow, fireball, trident, etc).
 */
public interface Projectile extends Entity {

    /**
     * Returns the entity that launched this projectile, or null if unknown.
     */
    Entity shooter();
}
