package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a player sends a chat message.
 */
public class PlayerChatEvent extends PlayerEvent implements Cancellable {

    private String message;
    private boolean cancelled;

    public PlayerChatEvent(Player player, String message) {
        super(player, true);
        this.message = message;
    }

    public String message() { return message; }
    public void setMessage(String message) { this.message = message; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
