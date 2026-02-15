package net.alloymc.api.scheduler;

/**
 * Handle to a scheduled task, allowing cancellation.
 */
public interface ScheduledTask {

    /**
     * Cancel this task.
     */
    void cancel();

    /**
     * Whether this task has been cancelled.
     */
    boolean isCancelled();
}
