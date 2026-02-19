package net.alloymc.api.entity;

import net.alloymc.api.block.Block;
import net.alloymc.api.block.BlockFace;
import net.alloymc.api.command.CommandSender;
import net.alloymc.api.gui.MenuInstance;
import net.alloymc.api.gui.MenuLayout;
import net.alloymc.api.inventory.CustomInventory;
import net.alloymc.api.inventory.Inventory;
import net.alloymc.api.inventory.ItemStack;
import net.alloymc.api.inventory.Material;
import net.alloymc.api.permission.PermissionHolder;
import net.alloymc.api.world.Location;

/**
 * Represents a connected player.
 * A Player is always a valid {@link CommandSender}.
 */
public interface Player extends LivingEntity, CommandSender {

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

    /**
     * Opens a custom inventory GUI for this player.
     *
     * @param inventory the custom inventory to display
     */
    void openInventory(CustomInventory inventory);

    /**
     * Closes the player's currently open inventory GUI.
     */
    void closeInventory();

    /**
     * Opens a menu layout GUI for this player.
     *
     * @param layout the menu layout to display
     * @return a live MenuInstance for reading/writing items and properties
     */
    MenuInstance openMenu(MenuLayout layout);

    /**
     * Sends a rich message with click-to-copy, hover text, and optional color.
     *
     * @param displayText the text shown in chat
     * @param copyText    the text copied to clipboard when clicked
     * @param hoverText   the tooltip shown on hover
     * @param colorRgb    RGB color (e.g. 0xFFAA00 for gold), or -1 for default
     */
    void sendRichMessage(String displayText, String copyText, String hoverText, int colorRgb);

    /**
     * Sends a clickable message that copies text to the player's clipboard when clicked.
     * Shows "Click to copy to clipboard" on hover. Uses default chat color.
     *
     * @param displayText the text shown in chat
     * @param copyText    the text copied to clipboard when clicked
     */
    default void sendClickableMessage(String displayText, String copyText) {
        sendRichMessage(displayText, copyText, "Click to copy to clipboard", -1);
    }

    enum MessageType {
        INFO,
        SUCCESS,
        WARNING,
        ERROR
    }
}
