package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The sink-and-rebound ripple on clients. A column that is sunk shows a full-collision sand block drawn lower
 * (Arrakis_Worm_Sunk_n) and air above; a raised column shows the real block and a thin layer above. The world is
 * never touched, and the server and the client both still believe a full block is where the real one is.
 */
final class SinkJob implements WormTestSystem.Job {

    static final String SHAKE_ID = "Arrakis_Worm_Ripple";

    /** Everything optional. */
    static final class Options {
        UUID owner;
        Long seed;
        Boolean shake;
        Integer rings;
        Double stepSeconds;
        Double swingSeconds;
        Consumer<String> onFinish;
    }

    private static final class Cell {
        final SinkSchedule.Cell plan;
        final int x, y, z;
        int offset;
        /** True once something other than the real blocks was sent for this column. */
        boolean dirty;

        Cell(SinkSchedule.Cell plan, int x, int y, int z) {
            this.plan = plan;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Which job last claimed a column, so the newer ripple wins where two overlap. */
    static final Map<World, Map<Long, Object>> CLAIMS = new HashMap<>();

    private final World world;
    private final UUID owner;
    private final List<PlayerRef> viewers;
    private final Options options;
    private final SinkSchedule schedule;
    private final FakeBlockSender sender;
    private final List<Cell> cells = new ArrayList<>();
    private final int[] sunkIds;
    private final int[] layerIds;
    private final Vector3d centre;
    private final boolean shake;
    private double elapsed;
    private boolean finished;
    private boolean warnedClamp;
    private int nextBeat;
    private int peakPacketsInTick;
    /** Most block changes queued in one tick (before they are split per section and viewer). */
    int peakEdits;
    private long peakBytesInTick;
    private long tickNanos;
    private int ticks;

    private SinkJob(World world, List<PlayerRef> viewers, Options options, SinkSchedule schedule, Vector3d centre,
                    int[] sunkIds, int[] layerIds, boolean shake) {
        this.world = world;
        this.owner = options.owner != null ? options.owner : UUID.randomUUID();
        this.viewers = viewers;
        this.options = options;
        this.schedule = schedule;
        this.sender = new FakeBlockSender(world, viewers);
        this.centre = centre;
        this.sunkIds = sunkIds;
        this.layerIds = layerIds;
        this.shake = shake;
    }

    /** Names of the fake blocks this ripple needs that the game has not loaded. */
    static List<String> missingAssets() {
        List<String> missing = new ArrayList<>();
        for (int n = 1; n <= SinkSchedule.MAX_PX; n++) {
            for (String prefix : new String[] {"Arrakis_Worm_Sunk_", "Arrakis_Worm_Ripple_Layer_"}) {
                if (BlockType.getAssetMap().getIndex(prefix + n) == Integer.MIN_VALUE) {
                    missing.add(prefix + n);
                }
            }
        }
        return missing;
    }

    /** Returns null if the fake blocks are not loaded. A job with no cells is returned as is (cellCount() 0). */
    static SinkJob create(World world, List<PlayerRef> viewers, Vector3d at, SinkSchedule.Params params,
                          Set<Integer> sand, boolean shake, Options options) {
        if (!missingAssets().isEmpty()) {
            return null;
        }
        int[] sunk = blockIds("Arrakis_Worm_Sunk_");
        int[] layer = blockIds("Arrakis_Worm_Ripple_Layer_");
        long seed = options.seed != null ? options.seed : new java.util.Random().nextLong();
        SinkSchedule schedule = new SinkSchedule(params, seed);
        int cx = (int) Math.floor(at.x);
        int cz = (int) Math.floor(at.z);
        Vector3d centreCell = new Vector3d(cx + 0.5, at.y, cz + 0.5);
        SinkJob job = new SinkJob(world, viewers, options, schedule, centreCell, sunk, layer, shake);
        Map<Long, Object> claims = CLAIMS.computeIfAbsent(world, w -> new HashMap<>());
        WorldAccess access = new WorldAccess(world);
        for (SinkSchedule.Cell plan : schedule.cells) {
            int x = cx + plan.dx;
            int z = cz + plan.dz;
            if (!access.isTicking(x, z)) {
                continue;
            }
            int top = access.height(x, z);
            // The same surface rule as the wave ripple, but nobody is skipped for standing there: the sunk block
            // keeps its collision and the layers have none.
            if (top == WorldAccess.NO_HEIGHT || !sand.contains(access.block(x, top, z)) || access.block(x, top + 1, z) != 0) {
                continue;
            }
            job.cells.add(new Cell(plan, x, top, z));
            claims.put(key(x, z), job);
        }
        return job;
    }

    static long key(int x, int z) {
        return ((long) x & 0xFFFFFFFFL) << 32 | ((long) z & 0xFFFFFFFFL);
    }

    /** Numeric ids of Arrakis_Worm_Sunk_n or Arrakis_Worm_Ripple_Layer_n, indexed by n (1 to MAX_PX). */
    static int[] blockIds(String prefix) {
        int[] ids = new int[SinkSchedule.MAX_PX + 1];
        for (int n = 1; n <= SinkSchedule.MAX_PX; n++) {
            ids[n] = BlockType.getAssetMap().getIndex(prefix + n);
        }
        return ids;
    }

    /**
     * The two edits of one column at an offset in pixels: the surface shows the sunk block or the real one, the cell
     * above a thin layer or air.
     */
    static void addEdits(List<FakeBlockSender.Edit> edits, int x, int y, int z, int offset, int[] sunkIds, int[] layerIds) {
        edits.add(new FakeBlockSender.Edit(x, y, z, offset < 0 ? sunkIds[-offset] : FakeBlockSender.REAL));
        edits.add(new FakeBlockSender.Edit(x, y + 1, z, offset > 0 ? layerIds[offset] : FakeBlockSender.REAL));
    }

    int cellCount() {
        return cells.size();
    }

    SinkSchedule schedule() {
        return schedule;
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
        return "sink";
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        long t0 = System.nanoTime();
        elapsed += dt;
        Map<Long, Object> claims = CLAIMS.get(world);
        List<FakeBlockSender.Edit> edits = new ArrayList<>();
        for (Cell cell : cells) {
            int o = clamp(schedule.offsetAt(cell.plan, elapsed));
            if (o == cell.offset) {
                continue;
            }
            cell.offset = o;
            if (claims == null || claims.get(key(cell.x, cell.z)) != this) {
                continue; // A newer ripple draws this column.
            }
            cell.dirty = true;
            // Both cells in one packet.
            addEdits(edits, cell.x, cell.y, cell.z, o, sunkIds, layerIds);
        }
        peakEdits = Math.max(peakEdits, edits.size());
        long bytesBefore = sender.bytes;
        peakPacketsInTick = Math.max(peakPacketsInTick, sender.send(edits));
        peakBytesInTick = Math.max(peakBytesInTick, sender.bytes - bytesBefore);

        if (shake) {
            double[] beats = schedule.beatTimes();
            float[] strength = {0.6f, 0.8f, 0.3f};
            while (nextBeat < beats.length && (beats[nextBeat] < 0 || beats[nextBeat] <= elapsed)) {
                if (beats[nextBeat] >= 0) {
                    pulse(store, strength[nextBeat]);
                }
                nextBeat++;
            }
        }
        tickNanos += System.nanoTime() - t0;
        ticks++;
        return elapsed < schedule.totalSeconds + 0.001;
    }

    private int clamp(int offset) {
        if (Math.abs(offset) > SinkSchedule.MAX_PX) {
            if (!warnedClamp) {
                warnedClamp = true;
                WormsOfArrakisPlugin.get().getLogger().at(java.util.logging.Level.WARNING)
                        .log("Sink ripple offset %d px cut to %d px", offset, SinkSchedule.MAX_PX);
            }
            return Math.max(-SinkSchedule.MAX_PX, Math.min(SinkSchedule.MAX_PX, offset));
        }
        return offset;
    }

    /** Full strength inside the patch, falling to 30% at rings + 3 blocks, nothing further out. */
    private void pulse(Store<EntityStore> store, float strength) {
        double inner = schedule.params.rings + 0.5;
        double outer = schedule.params.rings + 3;
        double base = WormsOfArrakisPlugin.get().config().getRippleShakeStrength();
        for (PlayerRef viewer : viewers) {
            Ref<EntityStore> ref = viewer.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
            if (t == null) {
                continue;
            }
            double d = Math.hypot(t.getPosition().x - centre.x, t.getPosition().z - centre.z);
            if (d > outer) {
                continue;
            }
            double falloff = d <= inner ? 1 : 1 - 0.7 * (d - inner) / (outer - inner);
            WormEffects.shake(viewer, SHAKE_ID, base * strength * falloff);
        }
    }

    /** Puts the real blocks back in every column this job changed and still owns. */
    @Override
    public void abort(Store<EntityStore> store) {
        Map<Long, Object> claims = CLAIMS.get(world);
        List<FakeBlockSender.Edit> edits = new ArrayList<>();
        for (Cell cell : cells) {
            long k = key(cell.x, cell.z);
            if (claims != null && claims.get(k) == this) {
                claims.remove(k);
                if (cell.dirty) {
                    edits.add(new FakeBlockSender.Edit(cell.x, cell.y, cell.z, FakeBlockSender.REAL));
                    edits.add(new FakeBlockSender.Edit(cell.x, cell.y + 1, cell.z, FakeBlockSender.REAL));
                }
            }
            cell.dirty = false;
            cell.offset = 0;
        }
        if (claims != null && claims.isEmpty()) {
            CLAIMS.remove(world);
        }
        sender.send(edits);
        if (!finished) {
            finished = true;
            if (options.onFinish != null) {
                options.onFinish.accept(summary());
            }
        }
    }

    /** Columns still showing something other than the real blocks (for the self-test). */
    /** Largest offset any column is still drawn at (for the self-test). */
    int maxAbsOffset() {
        int m = 0;
        for (Cell cell : cells) {
            m = Math.max(m, Math.abs(cell.offset));
        }
        return m;
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

    String summary() {
        return String.format(Locale.ROOT,
                "sink ripple %s: %d columns, %.2f s, %d block updates in %d packets (%d bytes) to %d viewer(s), peak %d packets and %d bytes in one tick, %.3f ms per tick",
                elapsed >= schedule.totalSeconds ? "done" : "stopped and restored", cells.size(), elapsed, sender.blocks,
                sender.packets, sender.bytes, viewers.size(), peakPacketsInTick, peakBytesInTick,
                ticks == 0 ? 0 : tickNanos / 1e6 / ticks);
    }
}
