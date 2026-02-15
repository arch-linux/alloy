package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;

/**
 * Fired when a player joins the server.
 */
public class PlayerJoinEvent extends PlayerEvent {

    private String joinMessage;

    public PlayerJoinEvent(Player player, String joinMessage) {
        super(player);
        this.joinMessage = joinMessage;
    }

    public String joinMessage() { return joinMessage; }
    public void setJoinMessage(String message) { this.joinMessage = message; }
}
