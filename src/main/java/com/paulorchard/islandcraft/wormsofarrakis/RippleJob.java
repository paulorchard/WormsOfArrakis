package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlocks;
import com.hypixel.hytale.protocol.packets.world.SetBlockCmd;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A wave of sand that exists only on clients. For the top sand block of each column around a point, a fake block
 * is sent to the viewers (a layer 1 to 4 px high on top of the sand), ring by ring moving outward, then the real block
 * is read from the world again and sent back. The world is never touched.
 */
final class RippleJob implements WormTestSystem.Job {

    /** Time for the wave to travel from the centre to the edge, whatever the radius. */
    static final float DURATION = 1.5f;
    /** If the job somehow outlives this, everything is restored and it ends. */
    static final float MAX_LIFE = 6.0f;

    private static final class Cell {
        final int x, y, z, fakeId;
        final float start;
        boolean up;

        Cell(int x, int y, int z, int fakeId, float start) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.fakeId = fakeId;
            this.start = start;
        }
    }

    private final World world;
    private final UUID owner;
    private final List<PlayerRef> viewers;
    private final List<Cell> cells = new ArrayList<>();
    /** Seconds each ring stays up, which is also the gap between rings, so the wave is one block wide. */
    private float step;
    private float elapsed;
    private int packets;
    private int blocksSent;
    private int peakBlocksInTick;
    private long sendNanos;

    private RippleJob(World world, UUID owner, List<PlayerRef> viewers) {
        this.world = world;
        this.owner = owner;
        this.viewers = viewers;
    }

    /**
     * @param centre where the wave starts (the caller's position)
     * @param avoid  positions of everyone who must not have a block appear inside them
     */
    static RippleJob create(World world, UUID owner, List<PlayerRef> viewers, double[] centre, int radius,
                            Set<Integer> sand, int[] layerIds, float duration, List<double[]> avoid) {
        RippleJob job = new RippleJob(world, owner, viewers);
        job.step = duration / (radius + 1.0f);
        java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
        int cx = (int) Math.floor(centre[0]);
        int cz = (int) Math.floor(centre[2]);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double distance = Math.hypot(dx, dz);
                if (distance > radius + 0.5) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    continue;
                }
                int top = chunk.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
                int topId = chunk.getBlock(x, top, z);
                if (!sand.contains(topId)) {
                    continue;
                }
                int ring = (int) Math.round(distance);
                int y = top + 1;
                if (chunk.getBlock(x, y, z) != 0) {
                    continue;
                }
                if (blocked(avoid, x, y, z)) {
                    continue;
                }
                // Each column gets a random height of 1 to 4 px, so it reads as water on a rough surface.
                int fake = layerIds[random.nextInt(layerIds.length)];
                job.cells.add(new Cell(x, y, z, fake > 0 ? fake : topId, ring * job.step));
            }
        }
        return job;
    }

    /** True if a fake block there would appear inside a player (not an issue for the Empty-material layer blocks, kept in case a full block is used). */
    private static boolean blocked(List<double[]> avoid, int x, int y, int z) {
        for (double[] p : avoid) {
            boolean overlapsColumn = Math.abs(p[0] - (x + 0.5)) < 1.0 && Math.abs(p[2] - (z + 0.5)) < 1.0;
            if (!overlapsColumn) {
                continue;
            }
            boolean inTheWay = p[1] < y + 1 && p[1] + 1.8 > y;
            if (inTheWay) {
                return true;
            }
        }
        return false;
    }

    int peakBlocksPerTick() {
        return peakBlocksInTick;
    }

    int cellCount() {
        return cells.size();
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public String kind() {
        return "ripple";
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        elapsed += dt;
        List<Cell> raise = new ArrayList<>();
        List<Cell> restore = new ArrayList<>();
        boolean allDone = true;
        for (Cell cell : cells) {
            boolean shouldBeUp = elapsed >= cell.start && elapsed < cell.start + step;
            if (shouldBeUp && !cell.up) {
                raise.add(cell);
                cell.up = true;
            } else if (!shouldBeUp && cell.up) {
                restore.add(cell);
                cell.up = false;
            }
            if (elapsed < cell.start + step) {
                allDone = false;
            }
        }
        long t0 = System.nanoTime();
        send(raise, true);
        send(restore, false);
        sendNanos += System.nanoTime() - t0;
        peakBlocksInTick = Math.max(peakBlocksInTick, raise.size() + restore.size());
        if (elapsed > MAX_LIFE) {
            abort(store);
            report("timed out and restored");
            return false;
        }
        if (allDone) {
            report("done");
            return false;
        }
        return true;
    }

    @Override
    public void abort(Store<EntityStore> store) {
        List<Cell> still = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.up) {
                still.add(cell);
                cell.up = false;
            }
        }
        send(still, false);
    }

    private void report(String how) {
        if (viewers.isEmpty()) {
            return;
        }
        viewers.get(0).sendMessage(Message.raw(String.format(Locale.ROOT,
                "ripple %s: %d columns, %d block updates in %d packets to %d viewer(s), peak %d per tick, %.2f ms total send time",
                how, cells.size(), blocksSent, packets, viewers.size(), peakBlocksInTick, sendNanos / 1e6)));
    }

    /** Sends the fake block (fake) or the block as the world has it now (real) for each cell. */
    private void send(List<Cell> list, boolean fake) {
        if (list.isEmpty()) {
            return;
        }
        Map<Long, List<SetBlockCmd>> bySection = new HashMap<>();
        Map<Long, int[]> sectionCoords = new HashMap<>();
        for (Cell cell : list) {
            int sx = ChunkUtil.chunkCoordinate(cell.x);
            int sy = ChunkUtil.chunkCoordinate(cell.y);
            int sz = ChunkUtil.chunkCoordinate(cell.z);
            int blockId = cell.fakeId;
            int filler = 0;
            int rotation = 0;
            if (!fake) {
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(cell.x, cell.z));
                if (chunk == null) {
                    continue; // The chunk is gone from the server; the client gets the real chunk when it loads again.
                }
                BlockSection section = chunk.getBlockChunk().getSectionAtBlockY(cell.y);
                int local = ChunkUtil.indexBlock(cell.x & ChunkUtil.SIZE_MASK, cell.y & ChunkUtil.SIZE_MASK,
                        cell.z & ChunkUtil.SIZE_MASK);
                blockId = section.get(local);
                filler = section.getFiller(local);
                rotation = section.getRotationIndex(local);
            }
            long key = ((long) sx & 0x1FFFFF) << 42 | ((long) sy & 0x1FFFFF) << 21 | ((long) sz & 0x1FFFFF);
            sectionCoords.putIfAbsent(key, new int[] {sx, sy, sz});
            bySection.computeIfAbsent(key, k -> new ArrayList<>()).add(new SetBlockCmd(
                    (short) ChunkUtil.indexBlock(cell.x & ChunkUtil.SIZE_MASK, cell.y & ChunkUtil.SIZE_MASK,
                            cell.z & ChunkUtil.SIZE_MASK),
                    blockId, (short) filler, (byte) rotation));
        }
        for (Map.Entry<Long, List<SetBlockCmd>> entry : bySection.entrySet()) {
            int[] s = sectionCoords.get(entry.getKey());
            ServerSetBlocks packet = new ServerSetBlocks(s[0], s[1], s[2], entry.getValue().toArray(new SetBlockCmd[0]));
            for (PlayerRef viewer : viewers) {
                if (viewer.getReference() != null && viewer.getChunkTracker().isLoaded(s[0], s[1], s[2])) {
                    viewer.getPacketHandler().writeNoCache(packet);
                    packets++;
                    blocksSent += entry.getValue().size();
                }
            }
        }
    }
}
