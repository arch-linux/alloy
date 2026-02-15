package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.world.Location;

/**
 * Fired when a player respawns after death.
 */
public class PlayerRespawnEvent extends PlayerEvent {

    private Location respawnLocation;

    public PlayerRespawnEvent(Player player, Location respawnLocation) {
        super(player);
        this.respawnLocation = respawnLocation;
    }

    public Location respawnLocation() { return respawnLocation; }
    public void setRespawnLocation(Location location) { this.respawnLocation = location; }
}
