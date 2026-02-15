package net.alloymc.api.entity;

import net.alloymc.api.world.Location;
import net.alloymc.api.world.World;

import java.util.UUID;

/**
 * Base interface for all entities in the world.
 */
public interface Entity {

    UUID uniqueId();

    EntityType type();

    Location location();

    World world();

    boolean isValid();

    void remove();

    /**
     * Teleport this entity to a location.
     */
    void teleport(Location destination);

    String name();

    /**
     * Returns the entity's custom display name, or the default name if none set.
     */
    String displayName();

    boolean hasMetadata(String key);

    void setMetadata(String key, Object value);

    Object getMetadata(String key);

    void removeMetadata(String key);
}
