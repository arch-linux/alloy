package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.ItemStack;

/**
 * Fired when a player places a block.
 */
public class BlockPlaceEvent extends BlockEvent implements Cancellable {

    private final Player player;
    private final Block replacedBlock;
    private final ItemStack itemInHand;
    private boolean cancelled;

    public BlockPlaceEvent(Block block, Block replacedBlock, Player player, ItemStack itemInHand) {
        super(block);
        this.replacedBlock = replacedBlock;
        this.player = player;
        this.itemInHand = itemInHand;
    }

    public Player player() { return player; }
    public Block replacedBlock() { return replacedBlock; }
    public ItemStack itemInHand() { return itemInHand; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
