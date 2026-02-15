package net.alloymc.api.event;

/**
 * An event that can be cancelled by listeners to prevent the default action.
 */
public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
