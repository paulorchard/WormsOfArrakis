package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Owns the sand disturbances (one per chunk), ticks them once per world tick together with the placed-block tracker,
 * and decides when the server is too busy for the rumble. Not part of the hunt: the aggro and the worm events know
 * nothing about it.
 */
public final class DisturbanceManager extends TickingSystem<EntityStore> implements PlacedBlockTracker.Listener {

    private final PlacedBlockTracker tracker = new PlacedBlockTracker();
    private final Map<String, Map<Long, SandDisturbance>> active = new ConcurrentHashMap<>();
    private final Map<String, LagMonitor> lag = new ConcurrentHashMap<>();
    private volatile boolean forced;

    DisturbanceManager() {
        tracker.setListener(this);
    }

    PlacedBlockTracker tracker() {
        return tracker;
    }

    boolean isForced() {
        return forced;
    }

    void setForced(boolean on) {
        forced = on;
    }

    // ------------------------------------------------------------------ lag

    /** Why the rumble should be dropped now, or null if it can run. */
    String lagReason(World world, int columns, int viewers) {
        WormsOfArrakisConfig cfg = WormsOfArrakisPlugin.get().config();
        if (forced) {
            return "forced with /worm disturb fallback on";
        }
        if (!cfg.isDisturbanceLagFallback()) {
            return null;
        }
        LagMonitor monitor = lag.get(world.getName());
        double limit = limitMillis(world, cfg);
        if (monitor != null && monitor.lagging(limit)) {
            return String.format(Locale.ROOT, "the average time between ticks over the last 2 s is %.1f ms, above the limit of %.1f ms",
                    monitor.averageMillis(), limit);
        }
        return costReason(columns, viewers);
    }

    /** The limit in milliseconds: DisturbanceLagMillis, or 1.5 times the world's normal tick length if that is 0. */
    static double limitMillis(World world, WormsOfArrakisConfig cfg) {
        return cfg.getDisturbanceLagMillis() > 0 ? cfg.getDisturbanceLagMillis() : 1.5 * world.getTickStepNanos() / 1e6;
    }

    String costReason(int columns, int viewers) {
        double max = WormsOfArrakisPlugin.get().config().getDisturbanceMaxCost();
        long cost = (long) columns * viewers;
        return cost > max ? String.format(Locale.ROOT, "the estimated cost, %d columns times %d viewers = %d, is above DisturbanceMaxCost %.0f",
                columns, viewers, cost, max) : null;
    }

    // ------------------------------------------------------------------ the disturbances

    private Map<Long, SandDisturbance> of(String world) {
        return active.computeIfAbsent(world, w -> new ConcurrentHashMap<>());
    }

    SandDisturbance at(World world, int cx, int cz) {
        Map<Long, SandDisturbance> m = active.get(world.getName());
        return m == null ? null : m.get(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(cx, cz));
    }

    /** Starts a disturbance in a chunk with whatever the tracker has recorded there. Returns null if one is running. */
    SandDisturbance startIn(World world, Store<EntityStore> store, int cx, int cz) {
        int sand = WormsOfArrakisPlugin.get().arrakisSandId();
        if (sand == WormsOfArrakisPlugin.NOT_INSTALLED || at(world, cx, cz) != null) {
            return null;
        }
        PlacedBlockTracker.ChunkRecord record = tracker.recordFor(world.getName(), cx, cz);
        SandDisturbance d = new SandDisturbance(world, cx, cz, tracker, record, this, sand, new Random().nextLong());
        of(world.getName()).put(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(cx, cz), d);
        d.start(store, null);
        return d;
    }

    @Override
    public void placed(World world, PlacedBlockTracker.ChunkRecord record, PlacedBlockTracker.Placed block) {
        SandDisturbance d = at(world, record.cx, record.cz);
        if (d != null) {
            d.placed(block); // joins the list; the clock does not restart
            return;
        }
        startIn(world, world.getEntityStore().getStore(), record.cx, record.cz); // the first block is in the world already
    }

    @Override
    public void forgotten(World world, PlacedBlockTracker.ChunkRecord record, PlacedBlockTracker.Placed block) {
        SandDisturbance d = at(world, record.cx, record.cz);
        if (d != null) {
            d.forgotten(block);
        }
    }

    /** Lines for /worm disturb status. */
    List<String> status() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, Map<Long, SandDisturbance>> w : active.entrySet()) {
            for (SandDisturbance d : w.getValue().values()) {
                lines.add(w.getKey() + " " + d.status());
            }
        }
        return lines;
    }

    /** Ends disturbances early: restored, nothing removed, nobody killed. Returns how many. */
    int stop(World only, Integer cx, Integer cz, String why) {
        int n = 0;
        for (Map<Long, SandDisturbance> m : active.values()) {
            for (SandDisturbance d : new ArrayList<>(m.values())) {
                if (only != null && (d.world != only || (cx != null && (d.cx != cx || d.cz != cz)))) {
                    continue;
                }
                end(d, why);
                n++;
            }
        }
        return n;
    }

    private void end(SandDisturbance d, String why) {
        Map<Long, SandDisturbance> m = active.get(d.world.getName());
        if (m != null) {
            m.remove(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(d.cx, d.cz));
        }
        try {
            d.abort(d.world.getEntityStore().getStore(), why);
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not end a sand disturbance");
        }
    }

    /** Server stop or plugin shutdown. */
    void abortAll() {
        stop(null, null, null, "the server is stopping");
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        lag.computeIfAbsent(world.getName(), w -> new LagMonitor()).sample(System.nanoTime());
        try {
            tracker.tick(world);
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Placed block tracking failed");
        }
        Map<Long, SandDisturbance> mine = active.get(world.getName());
        if (mine == null) {
            return;
        }
        for (SandDisturbance d : new ArrayList<>(mine.values())) {
            boolean keep;
            try {
                keep = d.tick(dt, store);
            } catch (Throwable t) {
                WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Sand disturbance %s failed", d.cx + "," + d.cz);
                keep = false;
                end(d, "it failed");
                continue;
            }
            if (!keep) {
                mine.remove(com.hypixel.hytale.math.util.ChunkUtil.indexChunk(d.cx, d.cz));
            }
        }
    }
}
