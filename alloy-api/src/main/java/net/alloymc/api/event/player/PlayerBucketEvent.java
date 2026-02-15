package net.alloymc.api.event.player;

import net.alloymc.api.block.Block;
import net.alloymc.api.entity.Player;
import net.alloymc.api.event.Cancellable;
import net.alloymc.api.inventory.Material;

/**
 * Fired when a player uses a bucket (fill or empty).
 */
public class PlayerBucketEvent extends PlayerEvent implements Cancellable {

    private final Block block;
    private final Material bucket;
    private final boolean filling;
    private boolean cancelled;

    public PlayerBucketEvent(Player player, Block block, Material bucket, boolean filling) {
        super(player);
        this.block = block;
        this.bucket = bucket;
        this.filling = filling;
    }

    public Block block() { return block; }
    public Material bucket() { return bucket; }
    public boolean isFilling() { return filling; }
    public boolean isEmptying() { return !filling; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
