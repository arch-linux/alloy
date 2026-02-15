package net.alloymc.api.event.player;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockFace;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.ItemStack;

/**
 * Fired when a player interacts (left or right click) with a block or air.
 */
public class PlayerInteractEvent extends PlayerEvent implements Cancellable {

    private final Action action;
    private final Block clickedBlock;
    private final BlockFace blockFace;
    private final ItemStack item;
    private boolean cancelled;

    public PlayerInteractEvent(Player player, Action action, Block clickedBlock, BlockFace blockFace, ItemStack item) {
        super(player);
        this.action = action;
        this.clickedBlock = clickedBlock;
        this.blockFace = blockFace;
        this.item = item;
    }

    public Action action() { return action; }
    public Block clickedBlock() { return clickedBlock; }
    public BlockFace blockFace() { return blockFace; }
    public ItemStack item() { return item; }
    public boolean hasBlock() { return clickedBlock != null; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public enum Action {
        LEFT_CLICK_BLOCK,
        RIGHT_CLICK_BLOCK,
        LEFT_CLICK_AIR,
        RIGHT_CLICK_AIR,
        PHYSICAL
    }
}
