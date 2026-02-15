package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;

/**
 * Fired when a player edits a sign.
 */
public class SignChangeEvent extends BlockEvent implements Cancellable {

    private final Player player;
    private final String[] lines;
    private boolean cancelled;

    public SignChangeEvent(Block block, Player player, String[] lines) {
        super(block);
        this.player = player;
        this.lines = lines;
    }

    public Player player() { return player; }
    public String[] lines() { return lines; }
    public String line(int index) { return lines[index]; }
    public void setLine(int index, String line) { lines[index] = line; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
