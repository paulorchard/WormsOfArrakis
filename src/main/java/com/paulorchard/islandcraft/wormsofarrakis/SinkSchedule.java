package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The numbers of one sink-and-rebound ripple, with no engine in it so it can be tested and printed. Offsets are whole
 * pixels, negative below ground. Phase 1: rings + 1 descent steps; the centre drops {@code increment} on each, ring k
 * starts at step k + 1 and drops a random amount on every step after. Phase 2: every block springs back like a damped
 * spring from its own depth, on its own random sequence, with a cosine ease between extremes.
 */
final class SinkSchedule {

    /** The thin layer and sunk block models stop at this many pixels. */
    static final int MAX_PX = 16;

    static final class Params {
        int increment = 2;
        int rings = 3;
        double stepSeconds = 0.10;
        double swingSeconds = 0.16;
        double swingJitter = 0.15;
        double dropMin = 0.5;
        double dropMax = 1.5;
        double reboundMin = 0.75;
        double reboundMax = 0.88;
        double decayMin = 0.55;
        double decayMax = 0.85;

        static Params fromConfig(WormsOfArrakisConfig c) {
            Params p = new Params();
            p.increment = clampIncrement((int) Math.round(c.getSinkIncrement()));
            p.rings = Math.max(1, Math.min(8, (int) Math.round(c.getSinkRings())));
            p.stepSeconds = c.getSinkStepSeconds();
            p.swingSeconds = c.getSinkSwingSeconds();
            p.swingJitter = c.getSinkSwingJitter();
            p.dropMin = c.getSinkDropMin();
            p.dropMax = c.getSinkDropMax();
            p.reboundMin = c.getSinkReboundMin();
            p.reboundMax = c.getSinkReboundMax();
            p.decayMin = c.getSinkDecayMin();
            p.decayMax = c.getSinkDecayMax();
            return p;
        }
    }

    static int clampIncrement(int increment) {
        return Math.max(1, Math.min(4, increment));
    }

    static final class Cell {
        final int dx, dz, ring;
        /** Depth in px after descent step 1, 2, ... (positive numbers, 0 before the ring starts). */
        int[] depthAfterStep;
        /** Signed targets of the rebound swings: +a1, -a2, +a3, ... and a final 0. Empty if the block never moved. */
        int[] extremes = new int[0];
        double swingSeconds;

        Cell(int dx, int dz, int ring) {
            this.dx = dx;
            this.dz = dz;
            this.ring = ring;
        }

        int depth() {
            return depthAfterStep[depthAfterStep.length - 1];
        }
    }

    final Params params;
    final long seed;
    final List<Cell> cells = new ArrayList<>();
    /** The descent steps; the rebound starts when they are done. */
    final int steps;
    final double descentSeconds;
    final double totalSeconds;
    /** True if some depth had to be cut to the longest model. */
    boolean clamped;

    SinkSchedule(Params p, long seed) {
        this.params = p;
        this.seed = seed;
        this.steps = p.rings + 1;
        this.descentSeconds = steps * p.stepSeconds;
        Random random = new Random(seed);
        int lo = Math.max(1, (int) Math.round(p.increment * p.dropMin));
        int hi = Math.max(lo, (int) Math.round(p.increment * p.dropMax));
        double total = descentSeconds;
        for (int dz = -p.rings; dz <= p.rings; dz++) {
            for (int dx = -p.rings; dx <= p.rings; dx++) {
                int ring = Math.max(Math.abs(dx), Math.abs(dz));
                Cell cell = new Cell(dx, dz, ring);
                cell.depthAfterStep = new int[steps];
                int depth = 0;
                for (int s = 1; s <= steps; s++) {
                    if (ring == 0) {
                        depth += p.increment;
                    } else if (s >= ring + 1) {
                        depth += lo + random.nextInt(hi - lo + 1);
                    }
                    if (depth > MAX_PX) {
                        depth = MAX_PX;
                        clamped = true;
                    }
                    cell.depthAfterStep[s - 1] = depth;
                }
                cell.swingSeconds = p.swingSeconds * (1 + (random.nextDouble() * 2 - 1) * p.swingJitter);
                cell.extremes = rebound(random, depth, p);
                total = Math.max(total, descentSeconds + cell.extremes.length * cell.swingSeconds);
                cells.add(cell);
            }
        }
        this.totalSeconds = total;
    }

    private static int[] rebound(Random random, int depth, Params p) {
        List<Integer> out = new ArrayList<>();
        if (depth > 0) {
            int a = (int) Math.round(depth * (p.reboundMin + random.nextDouble() * (p.reboundMax - p.reboundMin)));
            // A little less than the depth, as described: a block 1 or 2 px down swings up by 1.
            a = Math.min(a, Math.max(1, depth - 1));
            int sign = 1;
            while (a >= 1) {
                out.add(sign * a);
                int next = (int) Math.round(a * (p.decayMin + random.nextDouble() * (p.decayMax - p.decayMin)));
                if (next >= a) {
                    next = a - 1;
                }
                a = next;
                sign = -sign;
            }
            out.add(0);
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    Cell centre() {
        return cells.get(cells.size() / 2);
    }

    /** The offset in whole pixels of a cell t seconds after the start. */
    int offsetAt(Cell cell, double t) {
        if (t < descentSeconds) {
            int step = Math.min(steps, (int) (t / params.stepSeconds) + 1);
            return -cell.depthAfterStep[step - 1];
        }
        if (cell.extremes.length == 0) {
            return 0;
        }
        double tau = t - descentSeconds;
        int seg = (int) (tau / cell.swingSeconds);
        if (seg >= cell.extremes.length) {
            return 0;
        }
        int from = seg == 0 ? -cell.depth() : cell.extremes[seg - 1];
        int to = cell.extremes[seg];
        double f = (tau - seg * cell.swingSeconds) / cell.swingSeconds;
        return (int) Math.round(from + (to - from) * (1 - Math.cos(Math.PI * f)) / 2);
    }

    /** Times of the centre's first drop and its first two rebound peaks (-1 if there is no second). */
    double[] beatTimes() {
        Cell c = centre();
        double first = descentSeconds + c.swingSeconds;
        double second = c.extremes.length > 2 ? descentSeconds + 3 * c.swingSeconds : -1;
        return new double[] {0, first, second};
    }

    /** One line per ring for /wormtest ripple debug, using the first block of each ring as the example. */
    List<String> describe() {
        List<String> lines = new ArrayList<>();
        for (int ring = 0; ring <= params.rings; ring++) {
            Cell c = null;
            for (Cell cell : cells) {
                if (cell.ring == ring) {
                    c = cell;
                    break;
                }
            }
            StringBuilder b = new StringBuilder("ring ").append(ring).append(" (starts at step ").append(ring + 1)
                    .append("): depth after each step");
            for (int d : c.depthAfterStep) {
                b.append(' ').append(d == 0 ? "0" : "-" + d);
            }
            b.append(" | rebound");
            for (int e : c.extremes) {
                b.append(' ').append(e > 0 ? "+" + e : String.valueOf(e));
            }
            b.append(String.format(java.util.Locale.ROOT, " (swing %.2f s)", c.swingSeconds));
            lines.add(b.toString());
        }
        lines.add(String.format(java.util.Locale.ROOT, "descent %.2f s, rebound %.2f s, total %.2f s%s", descentSeconds,
                totalSeconds - descentSeconds, totalSeconds, clamped ? " (some depth cut to " + MAX_PX + " px)" : ""));
        return lines;
    }
}
