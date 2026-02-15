package net.alloymc.api.entity;

import java.util.UUID;

/**
 * An entity that can be tamed and owned by a player.
 */
public interface TameableEntity extends LivingEntity {

    boolean isTamed();

    UUID ownerUniqueId();

    /**
     * Returns the owner as a Player if online, null otherwise.
     */
    Player owner();
}
