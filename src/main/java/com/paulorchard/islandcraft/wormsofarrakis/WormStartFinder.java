package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;

/**
 * Chooses where a worm event starts: in open sand, preferably on top of a dune. Candidates are sampled in the ring
 * WormStartMinDistance to WormStartMaxDistance round the target; chunks that are not loaded are skipped.
 * Rules, best first: open sand on a crest, open sand, any sand, and last the old behaviour (the first random
 * point on sand, or the first point if nothing could be checked).
 */
final class WormStartFinder {

    /** One sampled point. */
    record Candidate(double x, double z, int height, boolean open, boolean crest) {
    }

    /** What happened, for the log. */
    static final class Result {
        Vector3d start;
        String rule;
        int sampled;
        int unloaded;
        int sandOnly;
        int open;
        int chosenHeight = Integer.MIN_VALUE;
        final List<Integer> heights = new ArrayList<>();
        double millis;

        String describe() {
            List<Integer> sorted = new ArrayList<>(heights);
            sorted.sort(Comparator.reverseOrder());
            return String.format(Locale.ROOT,
                    "worm start rule '%s': height %s at %.0f %.0f; %d sampled, %d not loaded, %d sand, %d open sand; other heights %s; %.1f ms",
                    rule, chosenHeight == Integer.MIN_VALUE ? "unknown" : String.valueOf(chosenHeight), start.x, start.z,
                    sampled, unloaded, sandOnly, open, sorted.subList(0, Math.min(sorted.size(), 16)), millis);
        }
    }

    private WormStartFinder() {
    }

    /**
     * Of the candidates, the highest one, or a random one among those within {@code tolerance} blocks of the highest,
     * preferring crests. Null if the list is empty.
     */
    static Candidate choose(List<Candidate> list, double tolerance, Random random) {
        if (list.isEmpty()) {
            return null;
        }
        int top = Integer.MIN_VALUE;
        for (Candidate c : list) {
            top = Math.max(top, c.height());
        }
        List<Candidate> near = new ArrayList<>();
        List<Candidate> crests = new ArrayList<>();
        for (Candidate c : list) {
            if (c.height() >= top - tolerance) {
                near.add(c);
                if (c.crest()) {
                    crests.add(c);
                }
            }
        }
        List<Candidate> pool = crests.isEmpty() ? near : crests;
        return pool.get(random.nextInt(pool.size()));
    }

    static Result find(World world, Vector3d player, WormsOfArrakisConfig cfg, Set<Integer> sand, Random random) {
        long t0 = System.nanoTime();
        Result result = new Result();
        double min = cfg.getWormStartMinDistance();
        double max = Math.max(min, cfg.getWormStartMaxDistance());
        int count = (int) Math.max(1, Math.round(cfg.getWormStartCandidates()));
        int openRadius = (int) Math.max(0, Math.round(cfg.getWormStartOpenRadius()));
        List<Candidate> openOnes = new ArrayList<>();
        List<Candidate> sandOnes = new ArrayList<>();
        if (world != null) {
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double distance = min + random.nextDouble() * (max - min);
                double x = player.x + Math.cos(angle) * distance;
                double z = player.z + Math.sin(angle) * distance;
                result.sampled++;
                int height = surfaceHeight(world, x, z);
                if (height == Integer.MIN_VALUE) {
                    result.unloaded++;
                    continue;
                }
                result.heights.add(height);
                if (!isSand(world, x, z, height, sand)) {
                    continue;
                }
                result.sandOnly++;
                boolean crest = isCrest(world, x, z, height);
                Candidate c = new Candidate(x, z, height, false, crest);
                sandOnes.add(c);
                if (isOpen(world, x, z, height, openRadius, sand)) {
                    result.open++;
                    openOnes.add(new Candidate(x, z, height, true, crest));
                }
            }
        }
        double tolerance = cfg.getWormStartHeightTolerance();
        Candidate pick = choose(openOnes, tolerance, random);
        if (pick != null) {
            result.rule = pick.crest() ? "open sand, crest" : "open sand";
        } else {
            pick = choose(sandOnes, tolerance, random);
            result.rule = "sand only";
        }
        if (pick != null) {
            result.start = new Vector3d(pick.x(), player.y, pick.z());
            result.chosenHeight = pick.height();
        } else {
            result.rule = "previous behaviour";
            result.start = legacy(world, player, cfg, sand, random);
        }
        result.millis = (System.nanoTime() - t0) / 1e6;
        return result;
    }

    /** The behaviour before: the first random point in the ring whose surface is sand, or not loaded to check. */
    private static Vector3d legacy(World world, Vector3d player, WormsOfArrakisConfig cfg, Set<Integer> sand, Random random) {
        double min = cfg.getWormStartMinDistance();
        Vector3d start = new Vector3d();
        for (int attempt = 0; attempt < 16; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = min + random.nextDouble() * Math.max(0, cfg.getWormStartMaxDistance() - min);
            start.set(player.x + Math.cos(angle) * distance, player.y, player.z + Math.sin(angle) * distance);
            Boolean sandy = world == null ? null : AggroManager.surfaceIsSand(world, start.x, start.z, sand);
            if (sandy == null || sandy) {
                break;
            }
        }
        return start;
    }

    /** Height of the top block of the column, or Integer.MIN_VALUE if its chunk is not loaded. */
    static int surfaceHeight(World world, double x, double z) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return Integer.MIN_VALUE;
        }
        return chunk.getHeight((int) Math.floor(x) & ChunkUtil.SIZE_MASK, (int) Math.floor(z) & ChunkUtil.SIZE_MASK);
    }

    private static boolean isSand(World world, double x, double z, int height, Set<Integer> sand) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        return chunk != null && sand.contains(chunk.getBlock((int) Math.floor(x), height, (int) Math.floor(z)));
    }

    /** Every surface block within the radius is sand, and the air above the middle and four edge points is clear for 6 blocks. */
    private static boolean isOpen(World world, double x, double z, int height, int radius, Set<Integer> sand) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int h = surfaceHeight(world, bx + dx, bz + dz);
                if (h == Integer.MIN_VALUE || !isSand(world, bx + dx, bz + dz, h, sand)) {
                    return false;
                }
            }
        }
        int[][] spots = {{0, 0}, {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius}};
        for (int[] spot : spots) {
            int px = bx + spot[0];
            int pz = bz + spot[1];
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(px, pz));
            int h = surfaceHeight(world, px, pz);
            if (chunk == null || h == Integer.MIN_VALUE) {
                return false;
            }
            for (int y = h + 1; y <= h + 6; y++) {
                if (chunk.getBlock(px, y, pz) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Higher than the average of 8 surface samples 12 blocks round it (at least 4 of them loaded). */
    private static boolean isCrest(World world, double x, double z, int height) {
        double sum = 0;
        int n = 0;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4;
            int h = surfaceHeight(world, x + Math.cos(a) * 12, z + Math.sin(a) * 12);
            if (h != Integer.MIN_VALUE) {
                sum += h;
                n++;
            }
        }
        return n >= 4 && height > sum / n;
    }
}
