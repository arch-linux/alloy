package net.alloymc.api.event.player;

import net.alloymc.api.entity.Player;
import net.alloymc.api.inventory.ItemStack;

import java.util.List;

/**
 * Fired when a player dies.
 */
public class PlayerDeathEvent extends PlayerEvent {

    private final List<ItemStack> drops;
    private String deathMessage;
    private int droppedExp;
    private boolean keepInventory;
    private boolean keepLevel;

    public PlayerDeathEvent(Player player, List<ItemStack> drops, int droppedExp, String deathMessage) {
        super(player);
        this.drops = drops;
        this.droppedExp = droppedExp;
        this.deathMessage = deathMessage;
    }

    public List<ItemStack> drops() { return drops; }
    public String deathMessage() { return deathMessage; }
    public void setDeathMessage(String message) { this.deathMessage = message; }
    public int droppedExp() { return droppedExp; }
    public void setDroppedExp(int exp) { this.droppedExp = exp; }
    public boolean keepInventory() { return keepInventory; }
    public void setKeepInventory(boolean keep) { this.keepInventory = keep; }
    public boolean keepLevel() { return keepLevel; }
    public void setKeepLevel(boolean keep) { this.keepLevel = keep; }
}
