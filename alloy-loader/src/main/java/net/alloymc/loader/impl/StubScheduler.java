package net.alloymc.loader.impl;

import net.alloymc.api.scheduler.Scheduler;
import net.alloymc.api.scheduler.ScheduledTask;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stub {@link Scheduler} for the loader bootstrap phase.
 *
 * <p>Async tasks run on a real {@link ScheduledExecutorService}.
 * Sync (tick-thread) tasks are logged and queued on the same executor
 * since there is no game tick loop yet.
 */
public class StubScheduler implements Scheduler {

    private static final Logger LOGGER = Logger.getLogger("AlloyScheduler");
    private static final long TICK_MS = 50; // 20 ticks per second

    private final ScheduledExecutorService executor =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "Alloy-Scheduler");
                t.setDaemon(true);
                return t;
            });

    @Override
    public ScheduledTask runTask(Runnable task) {
        return runTaskLater(task, 0);
    }

    @Override
    public ScheduledTask runTaskLater(Runnable task, long delayTicks) {
        LOGGER.fine("Sync task scheduled (delay=" + delayTicks + " ticks) — running on scheduler thread");
        SimpleScheduledTask handle = new SimpleScheduledTask();
        ScheduledFuture<?> future = executor.schedule(
                wrapTask(task), delayTicks * TICK_MS, TimeUnit.MILLISECONDS);
        handle.setFuture(future);
        return handle;
    }

    @Override
    public ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        LOGGER.fine("Sync repeating task scheduled (delay=" + delayTicks
                + ", period=" + periodTicks + " ticks)");
        SimpleScheduledTask handle = new SimpleScheduledTask();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                wrapTask(task), delayTicks * TICK_MS, periodTicks * TICK_MS, TimeUnit.MILLISECONDS);
        handle.setFuture(future);
        return handle;
    }

    @Override
    public ScheduledTask runAsync(Runnable task) {
        return runAsyncLater(task, 0);
    }

    @Override
    public ScheduledTask runAsyncLater(Runnable task, long delayTicks) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        ScheduledFuture<?> future = executor.schedule(
                wrapTask(task), delayTicks * TICK_MS, TimeUnit.MILLISECONDS);
        handle.setFuture(future);
        return handle;
    }

    @Override
    public ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        ScheduledFuture<?> future = executor.scheduleAtFixedRate(
                wrapTask(task), delayTicks * TICK_MS, periodTicks * TICK_MS, TimeUnit.MILLISECONDS);
        handle.setFuture(future);
        return handle;
    }

    private Runnable wrapTask(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Scheduled task threw an exception", e);
            }
        };
    }
}
