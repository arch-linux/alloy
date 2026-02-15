package net.alloymc.api.event.entity;

import net.alloymc.api.entity.Entity;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a vehicle entity takes damage.
 */
public class VehicleDamageEvent extends EntityEvent implements Cancellable {

    private final Entity attacker;
    private double damage;
    private boolean cancelled;

    public VehicleDamageEvent(Entity vehicle, Entity attacker, double damage) {
        super(vehicle);
        this.attacker = attacker;
        this.damage = damage;
    }

    public Entity attacker() { return attacker; }
    public double damage() { return damage; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
