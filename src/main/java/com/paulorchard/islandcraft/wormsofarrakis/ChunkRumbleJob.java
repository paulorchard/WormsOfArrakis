package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The sand of a whole chunk rumbling. It draws the same two cells per column as {@link SinkJob} (the surface block
 * sunk, or the real one with a thin layer above it), through the same fake blocks, the same sender and the same claim
 * rule, so the world is never touched and everything is put back from the real world at the end. What differs is the
 * schedule: {@link RumbleSchedule}, many columns each pulsing on their own, instead of one ring spreading out. Only
 * columns whose top block is Arrakis_Sand with air above are drawn; a column with a placed block on it is not.
 */
final class ChunkRumbleJob {

    private static final class Cell {
        final int index, x, y, z;
        /** The offset the schedule wants now, and the one the clients were last sent. */
        int offset;
        int sentOffset;
        boolean dirty;
        double weight = 1;
        int period = Integer.MIN_VALUE;
        RumbleSchedule.Pulse pulse;
        /** Tick the column was last sent in, so the oldest waiting is sent first when the budget is short. */
        long lastSent;

        Cell(int index, int x, int y, int z) {
            this.index = index;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final World world;
    private final int cx, cz;
    private final RumbleSchedule schedule;
    private final List<PlayerRef> viewers = new ArrayList<>();
    private final Map<UUID, PlayerRef> everSeen = new LinkedHashMap<>();
    private final FakeBlockSender sender;
    private final List<Cell> cells = new ArrayList<>();
    private final Map<Long, Cell> byColumn = new HashMap<>();
    private final int[] sunkIds = SinkJob.blockIds("Arrakis_Worm_Sunk_");
    private final int[] layerIds = SinkJob.blockIds("Arrakis_Worm_Ripple_Layer_");
    private final int maxEdits;
    private double elapsed;
    private double centreX, centreZ;
    private double reach = 45;
    private long tickNumber;
    private boolean ended;
    private int peakEdits;
    private int peakPackets;
    private long peakBytes;
    private long editsSent;
    private long tickNanos;
    private int ticks;
    private int waiting;

    private ChunkRumbleJob(World world, int cx, int cz, RumbleSchedule schedule, int maxEdits) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.schedule = schedule;
        this.maxEdits = Math.max(2, maxEdits);
        this.sender = new FakeBlockSender(world, viewers);
        this.centreX = cx * 32 + 16;
        this.centreZ = cz * 32 + 16;
    }

    /**
     * Returns null if the fake blocks are not loaded or the chunk is not loaded. A chunk with no sand surface gives a
     * job with no columns.
     */
    static ChunkRumbleJob create(World world, int cx, int cz, int sandId, RumbleSchedule schedule, int maxEdits) {
        if (!SinkJob.missingAssets().isEmpty()) {
            return null;
        }
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunk(cx, cz));
        if (chunk == null) {
            return null;
        }
        ChunkRumbleJob job = new ChunkRumbleJob(world, cx, cz, schedule, maxEdits);
        Map<Long, Object> claims = SinkJob.CLAIMS.computeIfAbsent(world, w -> new HashMap<>());
        for (int lz = 0; lz < 32; lz++) {
            for (int lx = 0; lx < 32; lx++) {
                int x = cx * 32 + lx;
                int z = cz * 32 + lz;
                int top = chunk.getHeight(lx, lz);
                if (chunk.getBlock(x, top, z) != sandId || chunk.getBlock(x, top + 1, z) != 0) {
                    continue;
                }
                Cell cell = new Cell(lz * 32 + lx, x, top, z);
                job.cells.add(cell);
                job.byColumn.put(SinkJob.key(x, z), cell);
                claims.put(SinkJob.key(x, z), job);
            }
        }
        return job;
    }

    int columns() {
        return cells.size();
    }

    int viewerCount() {
        return viewers.size();
    }

    boolean ended() {
        return ended;
    }

    /** Players who see the sand move; set once a second by the disturbance. The list is also used to restore at the end. */
    void setViewers(List<PlayerRef> now) {
        viewers.clear();
        viewers.addAll(now);
        for (PlayerRef p : now) {
            everSeen.put(p.getUuid(), p);
        }
    }

    /** Where the placed blocks are (their centre of gravity), and how far the weakest corner is from it. */
    void setCentre(double x, double z) {
        this.centreX = x;
        this.centreZ = z;
        double far = 1;
        for (double[] corner : new double[][] {{cx * 32, cz * 32}, {cx * 32 + 32, cz * 32}, {cx * 32, cz * 32 + 32},
                {cx * 32 + 32, cz * 32 + 32}}) {
            far = Math.max(far, Math.hypot(corner[0] - x, corner[1] - z));
        }
        this.reach = far;
        for (Cell cell : cells) {
            cell.weight = RumbleSchedule.weight(Math.hypot(cell.x + 0.5 - x, cell.z + 0.5 - z), reach, WEIGHT_FLOOR);
        }
    }

    /** Columns at the far corner still move at this share of the strength at the blocks. */
    static final double WEIGHT_FLOOR = 0.35;

    /** A block was placed in this column: stop drawing it, so nothing is drawn over the placed block. */
    void dropColumn(int x, int z) {
        Cell cell = byColumn.remove(SinkJob.key(x, z));
        if (cell == null) {
            return;
        }
        cells.remove(cell);
        Map<Long, Object> claims = SinkJob.CLAIMS.get(world);
        if (claims != null && claims.get(SinkJob.key(x, z)) == this) {
            claims.remove(SinkJob.key(x, z));
            if (cell.dirty) {
                List<FakeBlockSender.Edit> edits = new ArrayList<>();
                edits.add(new FakeBlockSender.Edit(x, cell.y, z, FakeBlockSender.REAL));
                edits.add(new FakeBlockSender.Edit(x, cell.y + 1, z, FakeBlockSender.REAL));
                restoreSender().send(edits);
            }
        }
    }

    /** Moves the columns on by dt and sends what changed, no more than the budget. */
    void tick(float dt) {
        if (ended) {
            return;
        }
        long t0 = System.nanoTime();
        elapsed += dt;
        tickNumber++;
        Map<Long, Object> claims = SinkJob.CLAIMS.get(world);
        int period = (int) Math.floor(elapsed / schedule.pulseSeconds);
        for (Cell cell : cells) {
            if (cell.period != period) {
                cell.period = period;
                cell.pulse = schedule.pulse(cell.index, period, cell.weight);
            }
            int o = Math.max(-SinkSchedule.MAX_PX, Math.min(SinkSchedule.MAX_PX, schedule.offsetAt(cell.pulse, elapsed)));
            cell.offset = o;
        }
        // A column waits while what the clients were last sent differs from what the schedule wants.
        List<Cell> due = new ArrayList<>();
        for (Cell cell : cells) {
            if (cell.sentOffset != cell.offset) {
                due.add(cell);
            }
        }
        due.sort((a, b) -> Long.compare(a.lastSent, b.lastSent));
        List<FakeBlockSender.Edit> edits = new ArrayList<>();
        int budgetColumns = Math.max(1, maxEdits / 2);
        int sentColumns = 0;
        for (Cell cell : due) {
            if (sentColumns >= budgetColumns) {
                break;
            }
            if (claims == null || claims.get(SinkJob.key(cell.x, cell.z)) != this) {
                cell.sentOffset = cell.offset; // a newer ripple draws this column
                continue;
            }
            cell.sentOffset = cell.offset;
            cell.lastSent = tickNumber;
            cell.dirty = true;
            SinkJob.addEdits(edits, cell.x, cell.y, cell.z, cell.offset, sunkIds, layerIds);
            sentColumns++;
        }
        waiting = Math.max(0, due.size() - sentColumns);
        peakEdits = Math.max(peakEdits, edits.size());
        long bytesBefore = sender.bytes;
        int packets = sender.send(edits);
        editsSent += edits.size();
        peakPackets = Math.max(peakPackets, packets);
        peakBytes = Math.max(peakBytes, sender.bytes - bytesBefore);
        tickNanos += System.nanoTime() - t0;
        ticks++;
    }

    /** A sender for everyone who ever saw the sand move, so nobody keeps a fake block. */
    private FakeBlockSender restoreSender() {
        return new FakeBlockSender(world, new ArrayList<>(everSeen.values()));
    }

    /** Puts the real blocks back in every column this job changed and still owns. Safe to call twice. */
    void end() {
        if (ended) {
            return;
        }
        ended = true;
        Map<Long, Object> claims = SinkJob.CLAIMS.get(world);
        List<FakeBlockSender.Edit> edits = new ArrayList<>();
        for (Cell cell : cells) {
            long k = SinkJob.key(cell.x, cell.z);
            if (claims != null && claims.get(k) == this) {
                claims.remove(k);
                if (cell.dirty) {
                    edits.add(new FakeBlockSender.Edit(cell.x, cell.y, cell.z, FakeBlockSender.REAL));
                    edits.add(new FakeBlockSender.Edit(cell.x, cell.y + 1, cell.z, FakeBlockSender.REAL));
                }
            }
            cell.dirty = false;
            cell.offset = 0;
            cell.sentOffset = 0;
        }
        if (claims != null && claims.isEmpty()) {
            SinkJob.CLAIMS.remove(world);
        }
        try {
            restoreSender().send(edits);
        } catch (IllegalThreadStateException e) {
            // The world has already stopped (the server is shutting down): the clients are gone, nothing to put back.
        }
    }

    /** Columns this job still holds the claim on (0 once it has ended). */
    int claimsHeld() {
        Map<Long, Object> claims = SinkJob.CLAIMS.get(world);
        int n = 0;
        if (claims != null) {
            for (Object owner : claims.values()) {
                if (owner == this) {
                    n++;
                }
            }
        }
        return n;
    }

    int dirtyCount() {
        int n = 0;
        for (Cell cell : cells) {
            if (cell.dirty) {
                n++;
            }
        }
        return n;
    }

    int peakEdits() {
        return peakEdits;
    }

    int peakPackets() {
        return peakPackets;
    }

    long peakBytes() {
        return peakBytes;
    }

    int waitingColumns() {
        return waiting;
    }

    /** For measuring with nobody online: count packets as if this many viewers had the sections loaded. */
    void simulateViewers(int n) {
        sender.simulatedViewers = n;
    }

    String summary() {
        return String.format(Locale.ROOT,
                "chunk rumble %d,%d: %d columns, %.2f s, %d edits in %d packets (%d bytes) to %d viewer(s), peak %d edits, %d packets and %d bytes in one tick, %.3f ms per tick",
                cx, cz, cells.size(), elapsed, editsSent, sender.packets, sender.bytes, viewers.size(), peakEdits, peakPackets,
                peakBytes, ticks == 0 ? 0 : tickNanos / 1e6 / ticks);
    }
}
