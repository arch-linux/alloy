package net.alloymc.api.command;

import net.alloymc.api.permission.PermissionHolder;

/**
 * Something that can send and receive commands (player, console, etc).
 */
public interface CommandSender extends PermissionHolder {

    String name();

    void sendMessage(String message);

    /**
     * Whether this sender is a player (as opposed to console).
     */
    boolean isPlayer();
}
