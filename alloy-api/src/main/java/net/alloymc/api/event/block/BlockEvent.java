package net.alloymc.api.event.block;

import net.alloymc.api.block.Block;
import net.alloymc.api.event.Event;

/**
 * Base event for block-related events.
 */
public abstract class BlockEvent extends Event {

    private final Block block;

    protected BlockEvent(Block block) {
        this.block = block;
    }

    public Block block() {
        return block;
    }
}
