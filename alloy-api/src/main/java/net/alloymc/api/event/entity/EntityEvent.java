package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Event;

/**
 * Base event for entity-related events.
 */
public abstract class EntityEvent extends Event {

    private final Entity entity;

    protected EntityEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity entity() {
        return entity;
    }
}
