package net.alloymc.api.scheduler;

/**
 * Schedules tasks to run on the server tick thread or asynchronously.
 */
public interface Scheduler {

    /**
     * Run a task on the next server tick.
     */
    ScheduledTask runTask(Runnable task);

    /**
     * Run a task after a delay (in ticks, 20 ticks = 1 second).
     */
    ScheduledTask runTaskLater(Runnable task, long delayTicks);

    /**
     * Run a task repeatedly at a fixed interval.
     *
     * @param task        the task to run
     * @param delayTicks  ticks before first execution
     * @param periodTicks ticks between subsequent executions
     */
    ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks);

    /**
     * Run a task asynchronously (off the main thread).
     */
    ScheduledTask runAsync(Runnable task);

    /**
     * Run an async task after a delay.
     */
    ScheduledTask runAsyncLater(Runnable task, long delayTicks);

    /**
     * Run an async task repeatedly at a fixed interval.
     */
    ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks);
}
