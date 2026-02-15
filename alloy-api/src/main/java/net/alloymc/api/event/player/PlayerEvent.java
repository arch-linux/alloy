package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Event;

/**
 * Base event for player-related events.
 */
public abstract class PlayerEvent extends Event {

    private final Player player;

    protected PlayerEvent(Player player) {
        this.player = player;
    }

    protected PlayerEvent(Player player, boolean async) {
        super(async);
        this.player = player;
    }

    public Player player() {
        return player;
    }
}
