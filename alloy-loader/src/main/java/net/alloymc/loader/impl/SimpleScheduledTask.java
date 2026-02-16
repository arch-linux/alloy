package net.alloymc.loader.impl;

import net.alloymc.api.scheduler.ScheduledTask;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Simple {@link ScheduledTask} implementation backed by an optional {@link ScheduledFuture}.
 */
public class SimpleScheduledTask implements ScheduledTask {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> future;

    public SimpleScheduledTask() {}

    public SimpleScheduledTask(ScheduledFuture<?> future) {
        this.future = future;
    }

    public void setFuture(ScheduledFuture<?> future) {
        this.future = future;
        if (cancelled.get() && future != null) {
            future.cancel(false);
        }
    }

    @Override
    public void cancel() {
        if (cancelled.compareAndSet(false, true)) {
            ScheduledFuture<?> f = this.future;
            if (f != null) {
                f.cancel(false);
            }
        }
    }

    @Override
    public boolean isCancelled() {
        return cancelled.get();
    }
}
