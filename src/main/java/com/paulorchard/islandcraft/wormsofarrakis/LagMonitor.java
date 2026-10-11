package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.ArrayList;

/**
 * How long the world's ticks really take, read from the clock: one sample per world tick, and the average time between
 * ticks over the last two seconds. A tick that overruns, or a world that is starved of time, shows up as a longer
 * interval, which the dt the server hands to a system would not tell us.
 */
final class LagMonitor {

    static final long WINDOW_NANOS = 2_000_000_000L;

    private final ArrayList<Long> stamps = new ArrayList<>();

    /** Records that a tick happened at this time (System.nanoTime). */
    void sample(long nowNanos) {
        stamps.add(nowNanos);
        // Keep one stamp from before the window so the first interval in it is counted.
        while (stamps.size() > 2 && nowNanos - stamps.get(1) > WINDOW_NANOS) {
            stamps.remove(0);
        }
    }

    /** Intervals the average rests on (0 until there are two samples). */
    int intervals() {
        return Math.max(0, stamps.size() - 1);
    }

    /** Average milliseconds between ticks over the last two seconds; 0 if there is not enough to say. */
    double averageMillis() {
        if (stamps.size() < 2) {
            return 0;
        }
        return (stamps.get(stamps.size() - 1) - stamps.get(0)) / 1e6 / (stamps.size() - 1);
    }

    /** True if the average is above {@code limitMillis} and rests on at least a quarter second of ticks. */
    boolean lagging(double limitMillis) {
        return stamps.size() >= 3 && stamps.get(stamps.size() - 1) - stamps.get(0) >= WINDOW_NANOS / 8 && averageMillis() > limitMillis;
    }
}
