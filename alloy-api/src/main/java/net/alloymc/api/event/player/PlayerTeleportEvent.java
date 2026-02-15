package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.world.Location;

/**
 * Fired when a player teleports.
 */
public class PlayerTeleportEvent extends PlayerMoveEvent {

    private final TeleportCause cause;

    public PlayerTeleportEvent(Player player, Location from, Location to, TeleportCause cause) {
        super(player, from, to);
        this.cause = cause;
    }

    public TeleportCause cause() { return cause; }

    public enum TeleportCause {
        ENDER_PEARL,
        NETHER_PORTAL,
        END_PORTAL,
        CHORUS_FRUIT,
        COMMAND,
        PLUGIN,
        UNKNOWN
    }
}
