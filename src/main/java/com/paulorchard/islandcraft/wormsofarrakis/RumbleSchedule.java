package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.Random;

/**
 * The numbers of the chunk-wide rumble, with no engine in it so it can be tested. Every column repeats a short pulse
 * of the sink-and-rebound ripple (two quick drops, then the damped rebound of {@link SinkSchedule}) once in every
 * period, at a random moment of its own, so the sand shudders on a thousand rhythms instead of one wave. The size of
 * the first drop of a pulse rises across the disturbance and is weighted by how close the column is to the placed
 * blocks. Offsets are whole pixels, negative below the surface.
 */
final class RumbleSchedule {

    /** A pulse always has this many descent steps. */
    static final int DROP_STEPS = 2;

    /** One pulse of one column. */
    static final class Pulse {
        final double start;
        final int[] depthAfterStep;
        final int[] extremes;
        final double swingSeconds;
        final double length;

        Pulse(double start, int[] depthAfterStep, int[] extremes, double swingSeconds, double length) {
            this.start = start;
            this.depthAfterStep = depthAfterStep;
            this.extremes = extremes;
            this.swingSeconds = swingSeconds;
            this.length = length;
        }
    }

    final SinkSchedule.Params params;
    final double pulseSeconds;
    final double totalSeconds;
    final double incrementStart;
    final double incrementEnd;
    final long seed;

    RumbleSchedule(SinkSchedule.Params params, double pulseSeconds, double totalSeconds, double incrementStart,
                   double incrementEnd, long seed) {
        this.params = params;
        this.pulseSeconds = Math.max(0.2, pulseSeconds);
        this.totalSeconds = Math.max(0.1, totalSeconds);
        this.incrementStart = incrementStart;
        this.incrementEnd = incrementEnd;
        this.seed = seed;
    }

    /** The increment (pixels of the first drop) at the centre of the placed blocks, {@code t} seconds in. */
    double incrementAt(double t) {
        double q = Math.max(0, Math.min(1, t / totalSeconds));
        return incrementStart + (incrementEnd - incrementStart) * q;
    }

    /** How strongly a column at this distance from the placed blocks moves: 1 at the blocks, {@code floor} at {@code reach}. */
    static double weight(double distance, double reach, double floor) {
        double q = Math.max(0, Math.min(1, distance / Math.max(reach, 1e-6)));
        return 1 - (1 - floor) * q;
    }

    /**
     * Pulse number {@code period} of column {@code cell}. Always the same for the same arguments, and it ends before the
     * next period begins, so a column never has two pulses at once.
     *
     * @param weight 0 to 1, from {@link #weight}
     */
    Pulse pulse(int cell, int period, double weight) {
        Random random = new Random(seed * 0x9E3779B97F4A7C15L + cell * 1_000_003L + period * 7_919L);
        double periodStart = period * pulseSeconds;
        double strength = incrementAt(periodStart + pulseSeconds * 0.5) * Math.max(0, weight);
        int[] depths = new int[DROP_STEPS];
        int depth = 0;
        for (int s = 0; s < DROP_STEPS; s++) {
            double factor = params.dropMin + random.nextDouble() * (params.dropMax - params.dropMin);
            depth += Math.max(1, (int) Math.round(strength * factor));
            depth = Math.min(depth, SinkSchedule.MAX_PX);
            depths[s] = depth;
        }
        double swing = params.swingSeconds * (1 + (random.nextDouble() * 2 - 1) * params.swingJitter);
        int[] extremes = SinkSchedule.rebound(random, depth, params);
        double length = DROP_STEPS * params.stepSeconds + extremes.length * swing;
        double room = Math.max(0, pulseSeconds - length);
        return new Pulse(periodStart + random.nextDouble() * room, depths, extremes, swing, length);
    }

    /** The offset of a pulse {@code t} seconds into the disturbance: 0 before it starts and after it ends. */
    int offsetAt(Pulse pulse, double t) {
        double local = t - pulse.start;
        if (local < 0 || local >= pulse.length) {
            return 0;
        }
        return SinkSchedule.offsetAt(pulse.depthAfterStep, pulse.extremes, pulse.swingSeconds, params.stepSeconds, local);
    }

    /** The offset of a column at time {@code t}. */
    int offsetAt(int cell, double weight, double t) {
        int period = (int) Math.floor(t / pulseSeconds);
        return period < 0 ? 0 : offsetAt(pulse(cell, period, weight), t);
    }
}
