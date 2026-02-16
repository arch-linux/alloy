package net.alloymc.api.permission;

import java.util.UUID;

/**
 * Pluggable backend for permission evaluation.
 *
 * <p>The default implementation is file-based (provided by alloy-core).
 * Third-party mods can replace it via {@link PermissionRegistry#setProvider(PermissionProvider)}.
 * Only one provider is active at a time; the last registered wins.
 */
public interface PermissionProvider {

    /**
     * Checks whether the given player has the specified permission.
     *
     * @param playerId   the player's UUID
     * @param playerName the player's name (for display/logging)
     * @param permission the permission node to check
     * @return true if the player has the permission
     */
    boolean hasPermission(UUID playerId, String playerName, String permission);

    /**
     * Checks whether the given player is an operator.
     *
     * @param playerId the player's UUID
     * @return true if the player is an operator
     */
    boolean isOp(UUID playerId);

    /**
     * Called when this provider becomes the active provider.
     */
    default void onEnable() {}

    /**
     * Called when this provider is replaced by another provider.
     */
    default void onDisable() {}
}
