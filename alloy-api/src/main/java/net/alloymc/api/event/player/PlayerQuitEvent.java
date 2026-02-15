package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;

/**
 * Fired when a player leaves the server.
 */
public class PlayerQuitEvent extends PlayerEvent {

    private String quitMessage;

    public PlayerQuitEvent(Player player, String quitMessage) {
        super(player);
        this.quitMessage = quitMessage;
    }

    public String quitMessage() { return quitMessage; }
    public void setQuitMessage(String message) { this.quitMessage = message; }
}
