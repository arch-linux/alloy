package net.alloymc.api.permission;

/**
 * An object that can have permissions (typically a Player).
 */
public interface PermissionHolder {

    /**
     * Check if this holder has the given permission node.
     */
    boolean hasPermission(String permission);

    /**
     * Check if this holder is an operator (admin).
     */
    boolean isOp();
}
