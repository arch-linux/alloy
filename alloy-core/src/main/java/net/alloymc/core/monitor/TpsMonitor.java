package net.alloymc.core.monitor;

/**
 * Monitors server tick performance using a rolling window.
 *
 * <p>Tracks the duration of each server tick over a 100-tick window (5 seconds)
 * and computes average TPS and MSPT.
 */
public class TpsMonitor {

    private static final int WINDOW_SIZE = 100;
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;
    private static final double NANOS_PER_MILLI = 1_000_000.0;

    private final long[] tickDurations = new long[WINDOW_SIZE];
    private int index = 0;
    private int count = 0;
    private long tickStartNanos;

    /**
     * Call at the start of each server tick.
     */
    public void onTickStart() {
        tickStartNanos = System.nanoTime();
    }

    /**
     * Call at the end of each server tick.
     */
    public void onTickEnd() {
        long duration = System.nanoTime() - tickStartNanos;
        tickDurations[index] = duration;
        index = (index + 1) % WINDOW_SIZE;
        if (count < WINDOW_SIZE) {
            count++;
        }
    }

    /**
     * Returns the average TPS over the rolling window, capped at 20.0.
     */
    public double tps() {
        if (count == 0) return 20.0;
        double avgNanos = averageNanos();
        if (avgNanos <= 0) return 20.0;
        return Math.min(20.0, NANOS_PER_SECOND / avgNanos);
    }

    /**
     * Returns the average milliseconds per tick over the rolling window.
     */
    public double mspt() {
        if (count == 0) return 0.0;
        return averageNanos() / NANOS_PER_MILLI;
    }

    private double averageNanos() {
        long total = 0;
        for (int i = 0; i < count; i++) {
            total += tickDurations[i];
        }
        return (double) total / count;
    }
}
