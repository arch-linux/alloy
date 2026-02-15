package net.alloymc.api.entity;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockFace;
import net.alloymc.api.inventory.Inventory;
import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.permission.PermissionHolder;
import net.alloymc.api.world.Location;

/**
 * Represents a connected player.
 */
public interface Player extends LivingEntity, PermissionHolder {

    /**
     * The player's display name.
     */
    @Override
    String displayName();

    /**
     * Send a chat message to this player.
     */
    void sendMessage(String message);

    /**
     * Send a colored message using text codes.
     */
    void sendMessage(String message, MessageType type);

    /**
     * The item currently in the player's main hand.
     */
    ItemStack itemInMainHand();

    /**
     * The player's full inventory.
     */
    Inventory inventory();

    /**
     * Whether the player is sneaking.
     */
    boolean isSneaking();

    /**
     * Whether the player is in creative mode.
     */
    boolean isCreativeMode();

    /**
     * Whether the player is in survival mode.
     */
    boolean isSurvivalMode();

    /**
     * Whether the player is in spectator mode.
     */
    boolean isSpectatorMode();

    /**
     * Returns the block the player is looking at (within reach distance).
     */
    Block targetBlock(int maxDistance);

    /**
     * Returns which block face the player is targeting.
     */
    BlockFace targetBlockFace(int maxDistance);

    /**
     * Returns the direction the player is facing as a BlockFace (NORTH, SOUTH, EAST, WEST, UP, DOWN).
     */
    BlockFace facing();

    /**
     * Sends a fake block change to this player (client-side only).
     */
    void sendBlockChange(Location location, Material material);

    /**
     * Kicks the player from the server.
     */
    void kick(String reason);

    /**
     * Whether this player has played before (not a first join).
     */
    boolean hasPlayedBefore();

    /**
     * Returns the time in milliseconds the player first joined.
     */
    long firstPlayed();

    /**
     * Returns the time in milliseconds the player last joined.
     */
    long lastPlayed();

    /**
     * Whether the player is currently online.
     */
    boolean isOnline();

    /**
     * Returns the player's IP address as a string.
     */
    String address();

    /**
     * Returns the player's game level (experience level).
     */
    int level();

    enum MessageType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
