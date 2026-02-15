package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a player issues a command (before execution).
 */
public class PlayerCommandEvent extends PlayerEvent implements Cancellable {

    private String command;
    private boolean cancelled;

    public PlayerCommandEvent(Player player, String command) {
        super(player);
        this.command = command;
    }

    public String command() { return command; }
    public void setCommand(String command) { this.command = command; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
