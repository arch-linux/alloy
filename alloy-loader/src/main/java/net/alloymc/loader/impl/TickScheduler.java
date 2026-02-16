package net.alloymc.loader.impl;

import net.alloymc.api.scheduler.Scheduler;
import net.alloymc.api.scheduler.ScheduledTask;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Tick-synchronized scheduler that runs sync tasks on the Minecraft server thread.
 *
 * <p>Sync tasks are queued and executed during the server tick via {@link #tick()}.
 * Async tasks run on a shared thread pool immediately.
 *
 * <p>One game tick = 50ms (20 ticks per second).
 */
public final class TickScheduler implements Scheduler {

    private static final TickScheduler INSTANCE = new TickScheduler();

    private final ConcurrentLinkedQueue<ScheduledEntry> pendingSync = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService asyncPool =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "Alloy-Async-Scheduler");
                t.setDaemon(true);
                return t;
            });

    private final AtomicLong currentTick = new AtomicLong(0);

    public static TickScheduler instance() {
        return INSTANCE;
    }

    /**
     * Called once per server tick from the ASM hook in MinecraftServer.tickServer().
     */
    public static void tick() {
        INSTANCE.processTick();
    }

    private void processTick() {
        long now = currentTick.incrementAndGet();

        // Process all ready sync tasks
        var it = pendingSync.iterator();
        while (it.hasNext()) {
            ScheduledEntry entry = it.next();
            if (entry.task.isCancelled()) {
                it.remove();
                continue;
            }
            if (entry.executeAtTick <= now) {
                it.remove();
                try {
                    entry.runnable.run();
                } catch (Exception e) {
                    System.err.println("[Alloy] Scheduler task error: " + e.getMessage());
                }
                // Re-enqueue repeating tasks
                if (entry.periodTicks > 0 && !entry.task.isCancelled()) {
                    entry.executeAtTick = now + entry.periodTicks;
                    pendingSync.add(entry);
                }
            }
        }
    }

    // =================== Scheduler interface ===================

    @Override
    public ScheduledTask runTask(Runnable task) {
        return runTaskLater(task, 0);
    }

    @Override
    public ScheduledTask runTaskLater(Runnable task, long delayTicks) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        ScheduledEntry entry = new ScheduledEntry(
                task, handle, currentTick.get() + Math.max(0, delayTicks), 0);
        pendingSync.add(entry);
        return handle;
    }

    @Override
    public ScheduledTask runTaskTimer(Runnable task, long delayTicks, long periodTicks) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        ScheduledEntry entry = new ScheduledEntry(
                task, handle, currentTick.get() + Math.max(0, delayTicks), periodTicks);
        pendingSync.add(entry);
        return handle;
    }

    @Override
    public ScheduledTask runAsync(Runnable task) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        asyncPool.submit(() -> {
            if (!handle.isCancelled()) {
                try {
                    task.run();
                } catch (Exception e) {
                    System.err.println("[Alloy] Async task error: " + e.getMessage());
                }
            }
        });
        return handle;
    }

    @Override
    public ScheduledTask runAsyncLater(Runnable task, long delayTicks) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        asyncPool.schedule(() -> {
            if (!handle.isCancelled()) {
                try {
                    task.run();
                } catch (Exception e) {
                    System.err.println("[Alloy] Async task error: " + e.getMessage());
                }
            }
        }, delayTicks * 50L, TimeUnit.MILLISECONDS);
        return handle;
    }

    @Override
    public ScheduledTask runAsyncTimer(Runnable task, long delayTicks, long periodTicks) {
        SimpleScheduledTask handle = new SimpleScheduledTask();
        asyncPool.scheduleAtFixedRate(() -> {
            if (handle.isCancelled()) return;
            try {
                task.run();
            } catch (Exception e) {
                System.err.println("[Alloy] Async timer error: " + e.getMessage());
            }
        }, delayTicks * 50L, periodTicks * 50L, TimeUnit.MILLISECONDS);
        return handle;
    }

    // =================== Internal ===================

    private static class ScheduledEntry {
        final Runnable runnable;
        final SimpleScheduledTask task;
        long executeAtTick;
        final long periodTicks; // 0 = one-shot

        ScheduledEntry(Runnable runnable, SimpleScheduledTask task, long executeAtTick, long periodTicks) {
            this.runnable = runnable;
            this.task = task;
            this.executeAtTick = executeAtTick;
            this.periodTicks = periodTicks;
        }
    }
}
