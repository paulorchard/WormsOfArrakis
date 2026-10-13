package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.logging.Level;

/**
 * What the sand does when a player builds on it. Not a worm event: it has no phases and no aggro, and the hunt runs
 * beside it. One per chunk, started by the first placement and joined by the rest.
 *
 * <p>Timeline (seconds from the start, defaults): the whole chunk rumbles; at 9.0 everyone standing on Arrakis_Sand or on
 * a placed block is held; at 9.5 the dust and the boom come, every placed block is removed and the held players start
 * to sink; at 10.0 they are killed and it is over. In the lag fallback there is no rumble and the same steps happen once
 * DisturbanceFallbackSeconds have passed.
 */
final class SandDisturbance {

    /** No drops, no engine particles, no sound, the least neighbour work; the block entity and filler parts do go. */
    static final int REMOVE_FLAGS = SetBlockSettings.NO_SEND_PARTICLES | SetBlockSettings.NO_SEND_AUDIO
            | SetBlockSettings.NO_DROP_ITEMS | SetBlockSettings.NO_FIRE_ON_BREAK
            | SetBlockSettings.NO_UPDATE_NEIGHBOR_CONNECTIONS;

    private static final String[] DUST_BURST = {"Block_Break_Dust", "Block_Land_Hard_Dust", "Block_Break_Dirt", "Block_Break_Sand"};

    /** Only for the self-test: a shorter disturbance, because a headless server unloads the chunks in about seven seconds. */
    static volatile Double secondsOverride;

    final World world;
    final int cx, cz;
    private final PlacedBlockTracker tracker;
    private final PlacedBlockTracker.ChunkRecord record;
    private final DisturbanceManager manager;
    private final int sandId;
    private final long seed;

    private final BreachHold hold = new BreachHold();
    private final BreachCamera camera = new BreachCamera();
    private ChunkRumbleJob rumble;

    private double elapsed;
    private final double total;
    private double captureAt, boomAt, killAt;
    private boolean captured, boomed, ended, bottomBurst;
    private boolean fallback;
    private String fallbackReason = "";
    /** Seconds in when the sunk players start coming back up (DevourKills off); -1 until then. */
    private double returnStart = -1;
    private double sinceSweep, sinceViewers = 1e9, sinceSound = 1e9, sinceShake = 1e9, sinceLag;
    private int capturedCount, removedCount, killedCount, viewerCount;
    private String endedAs = "";

    SandDisturbance(World world, int cx, int cz, PlacedBlockTracker tracker, PlacedBlockTracker.ChunkRecord record,
                    DisturbanceManager manager, int sandId, long seed) {
        this.world = world;
        this.cx = cx;
        this.cz = cz;
        this.tracker = tracker;
        this.record = record;
        this.manager = manager;
        this.sandId = sandId;
        this.seed = seed;
        WormsOfArrakisConfig cfg = WormsOfArrakisPlugin.get().config();
        this.total = Math.max(0.5, secondsOverride != null ? secondsOverride : cfg.getDisturbanceSeconds());
        this.captureAt = Math.max(0, total - cfg.getDisturbanceSwallowLead());
        this.boomAt = captureAt + Math.max(0, cfg.getDisturbanceBoomLead());
        this.killAt = Math.max(total, boomAt + Math.max(0.05, cfg.getBreachSinkSeconds()));
    }

    private static void info(String format, Object... args) {
        WormsOfArrakisPlugin.get().getLogger().at(Level.INFO).log("%s", String.format(Locale.ROOT, format, args));
    }

    private WormsOfArrakisConfig cfg() {
        return WormsOfArrakisPlugin.get().config();
    }

    // ------------------------------------------------------------------ geometry

    double centreX() {
        return cx * 32 + 16;
    }

    double centreZ() {
        return cz * 32 + 16;
    }

    boolean inChunk(double x, double z) {
        return ChunkUtil.chunkCoordinate((int) Math.floor(x)) == cx && ChunkUtil.chunkCoordinate((int) Math.floor(z)) == cz;
    }

    private List<PlayerRef> playersWithin(Store<EntityStore> store, double radius) {
        List<PlayerRef> list = new ArrayList<>();
        for (PlayerRef p : world.getPlayerRefs()) {
            Vector3d at = WormEffects.position(store, p);
            if (at != null && Math.hypot(at.x - centreX(), at.z - centreZ()) <= radius) {
                list.add(p);
            }
        }
        return list;
    }

    /** The centre of gravity of the placed blocks, or the middle of the chunk if there are none. */
    private double[] centreOfBlocks() {
        if (record.blocks.isEmpty()) {
            return new double[] {centreX(), centreZ()};
        }
        double sx = 0, sz = 0;
        for (PlacedBlockTracker.Placed b : record.blocks.values()) {
            sx += b.x + 0.5;
            sz += b.z + 0.5;
        }
        int n = record.blocks.size();
        return new double[] {sx / n, sz / n};
    }

    // ------------------------------------------------------------------ start

    /** Starts the clock and the rumble (or the fallback). Call on the world thread. */
    void start(Store<EntityStore> store, String forcedReason) {
        WormsOfArrakisConfig cfg = cfg();
        info("Sand disturbance %d,%d started in %s: %d placed block(s), ends in %.1f s (capture %.1f, boom %.1f, kill %.1f)",
                cx, cz, world.getName(), record.blocks.size(), total, captureAt, boomAt, killAt);
        List<PlayerRef> viewers = playersWithin(store, cfg.getDisturbanceViewDistance());
        viewerCount = viewers.size();
        String reason = forcedReason;
        if (reason == null) {
            reason = manager.lagReason(world, 1024, viewers.size());
        }
        if (reason == null) {
            RumbleSchedule schedule = new RumbleSchedule(SinkSchedule.Params.fromConfig(cfg), cfg.getDisturbancePulseSeconds(),
                    total, cfg.getDisturbanceIncrementStart(), cfg.getDisturbanceIncrementEnd(), seed);
            rumble = ChunkRumbleJob.create(world, cx, cz, sandId, schedule, (int) cfg.getDisturbanceMaxEditsPerTick());
            if (rumble == null) {
                info("Sand disturbance %d,%d: the rumble could not start (fake blocks or chunk not loaded); the event goes on without it", cx, cz);
            } else {
                String cost = cfg.isDisturbanceLagFallback() ? manager.costReason(rumble.columns(), viewers.size()) : null;
                if (cost != null) {
                    rumble.end();
                    rumble = null;
                    reason = cost;
                } else {
                    double[] c = centreOfBlocks();
                    rumble.setCentre(c[0], c[1]);
                    rumble.setViewers(viewers);
                    info("Sand disturbance %d,%d: rumbling %d columns for %d viewer(s)", cx, cz, rumble.columns(), viewers.size());
                }
            }
        }
        if (reason != null) {
            useFallback(reason);
        }
    }

    private void useFallback(String reason) {
        if (fallback) {
            return;
        }
        fallback = true;
        fallbackReason = reason;
        if (rumble != null) {
            info("Sand disturbance %d,%d: %s", cx, cz, rumble.summary());
            rumble.end();
            rumble = null;
        }
        WormsOfArrakisConfig cfg = cfg();
        double lead = cfg.getDisturbanceSwallowLead();
        double end = Math.max(0.5, cfg.getDisturbanceFallbackSeconds());
        if (!captured) {
            captureAt = Math.max(elapsed, end - lead);
            boomAt = captureAt + Math.max(0, cfg.getDisturbanceBoomLead());
            killAt = Math.max(captureAt + lead, boomAt + Math.max(0.05, cfg.getBreachSinkSeconds()));
        }
        info("Sand disturbance %d,%d: LAG FALLBACK (%s): no chunk rumble, no sounds, no shake; capture at %.1f s, dust and removal at %.1f s, kill at %.1f s",
                cx, cz, reason, captureAt, boomAt, killAt);
    }

    // ------------------------------------------------------------------ listeners from the tracker

    void placed(PlacedBlockTracker.Placed block) {
        info("Sand disturbance %d,%d: a block placed by %s joined at %d %d %d (%d tracked, %.1f s left)", cx, cz,
                block.by == null ? "someone" : block.by.toString(), block.x, block.y, block.z, record.blocks.size(), Math.max(0, killAt - elapsed));
        if (rumble != null) {
            rumble.dropColumn(block.x, block.z);
            double[] c = centreOfBlocks();
            rumble.setCentre(c[0], c[1]);
        }
    }

    void forgotten(PlacedBlockTracker.Placed block) {
        if (rumble != null) {
            double[] c = centreOfBlocks();
            rumble.setCentre(c[0], c[1]);
        }
    }

    // ------------------------------------------------------------------ tick

    /** Returns false when it is over. */
    boolean tick(float dt, Store<EntityStore> store) {
        if (ended) {
            return false;
        }
        WormsOfArrakisConfig cfg = cfg();
        elapsed += dt;

        if (!fallback && cfg.isDisturbanceLagFallback()) {
            sinceLag += dt;
            if (sinceLag >= 1.0) {
                sinceLag = 0;
                String reason = manager.lagReason(world, rumble == null ? 0 : rumble.columns(), viewerCount);
                if (reason != null) {
                    useFallback(reason);
                }
            }
        } else if (!fallback && manager.isForced()) {
            useFallback("forced with /worm disturb fallback on");
        }

        if (!boomed) {
            sinceSweep += dt;
            if (sinceSweep >= 0.5) {
                sinceSweep = 0;
                tracker.sweep(world, record);
            }
        }
        if (rumble != null) {
            rumbleStep(dt, store, cfg);
        }
        if (!captured && elapsed >= captureAt) {
            captured = true;
            capture(store);
        }
        if (captured) {
            hold.tick(world, store);
        }
        if (!boomed && elapsed >= boomAt) {
            boomed = true;
            boom(store, cfg);
        }
        if (boomed && returnStart < 0 && elapsed < killAt) {
            double sink = Math.max(0.05, cfg.getBreachSinkSeconds());
            double q = Math.min(1, (elapsed - boomAt) / sink);
            hold.sinkTo(world, store, cfg.getBreachSinkDepth(), q);
            if (!bottomBurst && q >= 1) {
                bottomBurst = true;
                hold.bursts(store, cfg, WormEffects.refs(playersWithin(store, cfg.getGroupRadius())), false);
            }
        }
        if (boomed && returnStart < 0 && elapsed >= killAt) {
            kill(store, cfg);
        } else if (returnStart >= 0) {
            double sink = Math.max(0.05, cfg.getBreachSinkSeconds());
            double q = 1 - (elapsed - returnStart) / sink;
            if (q <= 0) {
                hold.releaseAndHurt(world, store, (s, ref, id) -> WormDevour.apply(s, cfg, ref));
                finish(store, "ended (DevourKills is off: released and hurt)");
                return false;
            }
            hold.sinkTo(world, store, cfg.getBreachSinkDepth(), q);
        }
        return !ended;
    }

    private void rumbleStep(float dt, Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        if (!new WorldAccess(world).isTickingChunk(cx, cz)) {
            info("Sand disturbance %d,%d: the chunk was unloaded; the rumble stops here", cx, cz);
            rumble.end();
            rumble = null;
            return;
        }
        sinceViewers += dt;
        if (sinceViewers >= 1.0) {
            sinceViewers = 0;
            List<PlayerRef> viewers = playersWithin(store, cfg.getDisturbanceViewDistance());
            viewerCount = viewers.size();
            rumble.setViewers(viewers);
        }
        rumble.tick(dt);
        double q = Math.min(1, elapsed / total);
        sinceSound += dt;
        if (sinceSound >= cfg.getRumbleIntervalSeconds()) {
            sinceSound = 0;
            for (PlayerRef p : playersWithin(store, cfg.getGroupRadius())) {
                Vector3d at = WormEffects.position(store, p);
                boolean inside = at != null && inChunk(at.x, at.z);
                float volume = (float) (inside ? cfg.getRumbleBystanderVolume() + (1.0 - cfg.getRumbleBystanderVolume()) * q
                        : cfg.getRumbleBystanderVolume());
                WormEffects.playRumble(p, volume, 1.0f);
            }
        }
        sinceShake += dt;
        if (cfg.isCameraShake() && sinceShake >= 0.5) {
            sinceShake = 0;
            for (PlayerRef p : world.getPlayerRefs()) {
                Vector3d at = WormEffects.position(store, p);
                if (at != null && inChunk(at.x, at.z)) {
                    WormEffects.shake(p, SinkJob.SHAKE_ID, cfg.getRippleShakeStrength() * (0.25 + 0.75 * q));
                }
            }
        }
    }

    // ------------------------------------------------------------------ capture

    /** Everyone in the chunk standing on Arrakis_Sand or a placed block is held. Decided once, now. */
    private void capture(Store<EntityStore> store) {
        WormsOfArrakisConfig cfg = cfg();
        WorldAccess access = new WorldAccess(world);
        int depthBelow = (int) Math.ceil(cfg.getSandTolerance()) + 1;
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> ref = p.getReference();
            Vector3d at = WormEffects.position(store, p);
            if (ref == null || !ref.isValid() || at == null || !inChunk(at.x, at.z) || BreachHold.isDead(store, ref)
                    || BreachHold.isCreative(store, ref)) {
                continue;
            }
            int x = (int) Math.floor(at.x);
            int z = (int) Math.floor(at.z);
            if (!access.isTicking(x, z)) {
                continue;
            }
            int top = (int) Math.floor(at.y - 0.1);
            boolean caught = false;
            for (int y = top; y >= top - depthBelow && y >= ChunkUtil.MIN_Y; y--) {
                int id = access.block(x, y, z);
                if (id != 0) {
                    caught = id == sandId || tracker.isTracked(world.getName(), x, y, z);
                    break;
                }
            }
            if (caught && hold.capturePlayer(store, p)) {
                capturedCount++;
            }
        }
        info("Sand disturbance %d,%d: %d player(s) held at %.2f s", cx, cz, capturedCount, elapsed);
    }

    /** Blocks from the player's feet down to three blocks under the Arrakis_Sand surface below them. */
    private double sinkDepthFor(Store<EntityStore> store, PlayerRef p, double fallbackDepth) {
        Vector3d at = WormEffects.position(store, p);
        if (at == null) {
            return fallbackDepth;
        }
        int x = (int) Math.floor(at.x);
        int z = (int) Math.floor(at.z);
        WorldAccess access = new WorldAccess(world);
        if (!access.isTicking(x, z)) {
            return fallbackDepth;
        }
        for (int y = (int) Math.floor(at.y - 0.1); y >= Math.max(ChunkUtil.MIN_Y, (int) at.y - 256); y--) {
            if (access.block(x, y, z) == sandId) {
                return Math.max(0, at.y - (y + 1)) + fallbackDepth;
            }
        }
        return fallbackDepth;
    }

    // ------------------------------------------------------------------ the boom

    private void boom(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        // The sink starts from where everyone stands now, before the blocks go.
        hold.startSink(store);
        for (PlayerRef p : hold.sunkPlayersNow()) {
            hold.setDepth(p.getUuid(), sinkDepthFor(store, p, cfg.getBreachSinkDepth()));
            Vector3d surface = hold.surfaceOf(p);
            if (surface != null) {
                camera.pin(world, store, cfg, p, surface, new Vector3d(centreX(), surface.y, centreZ()), new Vector3d(0, 0, 1),
                        0, cfg.getZoomFromDistance());
            }
            if (cfg.isCameraShake()) {
                WormEffects.shake(p, "Arrakis_Worm_Lock", cfg.getShakeStrength());
            }
        }
        dustAndBoom(store, cfg);
        hold.bursts(store, cfg, WormEffects.refs(playersWithin(store, cfg.getGroupRadius())), true);
        removeBlocks();
        if (rumble != null) {
            info("Sand disturbance %d,%d: %s", cx, cz, rumble.summary());
            rumble.end();
            rumble = null;
        }
    }

    /** A ring of dust over the chunk, a burst at each placed block, taller dust over the cluster, the boom and the shake. */
    private void dustAndBoom(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        List<PlayerRef> hearers = playersWithin(store, cfg.getGroupRadius());
        if (hearers.isEmpty()) {
            return;
        }
        List<Ref<EntityStore>> refs = WormEffects.refs(hearers);
        float far = (float) cfg.getGroupRadius() + 60f;
        float dust = (float) cfg.getDisturbanceDustScale();
        WorldAccess access = new WorldAccess(world);
        boolean loaded = access.isTickingChunk(cx, cz);
        // The ring on the surface round the middle of the chunk.
        for (int k = 0; k < 24; k++) {
            double a = k * Math.PI * 2 / 24;
            double x = centreX() + Math.cos(a) * 15;
            double z = centreZ() + Math.sin(a) * 15;
            int ground = loaded ? access.height((int) Math.floor(x), (int) Math.floor(z)) : WorldAccess.NO_HEIGHT;
            double y = ground == WorldAccess.NO_HEIGHT ? 64 : ground + 1.0;
            WormEffects.particle(store, "Block_Break_Dust", x, y + 0.4, z, dust, far, refs);
            WormEffects.particle(store, "Block_Land_Hard_Dust", x, y + 0.3, z, dust * 0.8f, far, refs);
        }
        // A burst where each placed block is, sampled if there are many.
        List<PlacedBlockTracker.Placed> blocks = new ArrayList<>(record.blocks.values());
        int cap = Math.max(1, (int) cfg.getDisturbanceMaxBursts());
        if (blocks.size() > cap) {
            Collections.shuffle(blocks, new Random(seed));
            blocks = blocks.subList(0, cap);
        }
        double minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (PlacedBlockTracker.Placed b : record.blocks.values()) {
            minY = Math.min(minY, b.y);
            maxY = Math.max(maxY, b.y);
        }
        for (PlacedBlockTracker.Placed b : blocks) {
            for (int i = 0; i < DUST_BURST.length; i++) {
                WormEffects.particle(store, DUST_BURST[i], b.x + 0.5, b.y + 0.5, b.z + 0.5, dust * (i < 2 ? 0.6f : 0.5f), far, refs);
            }
        }
        // Taller dust over the cluster.
        if (!record.blocks.isEmpty()) {
            double[] c = centreOfBlocks();
            for (double y = minY; y <= maxY + 8; y += 2) {
                WormEffects.particle(store, "Block_Break_Dust", c[0], y + 0.5, c[1], dust, far, refs);
            }
        }
        for (PlayerRef p : hearers) {
            Vector3d at = WormEffects.position(store, p);
            boolean near = at != null && Math.hypot(at.x - centreX(), at.z - centreZ()) <= cfg.getBreachShakeRange();
            BreachJob.boomSounds(p, (float) (near ? cfg.getBreachBoomVolume() : cfg.getBreachFarVolume()));
            if (cfg.isCameraShake() && at != null && at.distance(new Vector3d(centreX(), at.y, centreZ())) <= 60) {
                WormEffects.shake(p, "Arrakis_Worm_Lock", cfg.getShakeStrength() * cfg.getBreachShakeStrength());
            }
        }
    }

    // ------------------------------------------------------------------ removal

    /**
     * Sets every tracked position that still holds the block that was placed to air, top of each stack first. Nothing
     * outside the list is touched, Arrakis_Sand never is, and a chunk that is not loaded is skipped, not loaded.
     */
    private void removeBlocks() {
        long t0 = System.nanoTime();
        List<PlacedBlockTracker.Placed> list = new ArrayList<>(record.blocks.values());
        list.sort((a, b) -> Integer.compare(b.y, a.y));
        int removed = 0, gone = 0, changed = 0, unloaded = 0, failed = 0;
        WorldAccess access = new WorldAccess(world);
        for (PlacedBlockTracker.Placed b : list) {
            if (!access.isTicking(b.x, b.z)) {
                unloaded++;
                continue;
            }
            int id = access.block(b.x, b.y, b.z);
            if (id == 0) {
                gone++;
                continue;
            }
            if (id == sandId || (id != b.blockId && !(b.key != null && b.key.equals(PlacedBlockTracker.keyOf(id))))) {
                changed++;
                continue;
            }
            try {
                if (access.remove(b.x, b.y, b.z, REMOVE_FLAGS)) {
                    removed++;
                } else {
                    failed++;
                }
            } catch (Throwable t) {
                failed++;
                WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not remove the block at %s", b.x + " " + b.y + " " + b.z);
            }
        }
        removedCount = removed;
        info("Sand disturbance %d,%d: removed %d of %d placed block(s) in %.2f ms (%d already gone, %d changed to something else and left alone, %d in unloaded chunks skipped, %d failed)",
                cx, cz, removed, list.size(), (System.nanoTime() - t0) / 1e6, gone, changed, unloaded, failed);
    }

    // ------------------------------------------------------------------ the end

    private void kill(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        killedCount = hold.size();
        if (cfg.isDevourKills()) {
            hold.kill(world, store, (s, ref, id) -> WormDevour.apply(s, cfg, ref));
            finish(store, "ended: " + killedCount + " killed");
        } else {
            returnStart = elapsed;
        }
    }

    private void finish(Store<EntityStore> store, String how) {
        if (ended) {
            return;
        }
        ended = true;
        endedAs = how;
        camera.resetAll();
        if (rumble != null) {
            rumble.end();
            rumble = null;
        }
        tracker.clear(world.getName(), record.chunkIndex());
        info("Sand disturbance %d,%d %s (%d held, %d removed, %.1f s%s)", cx, cz, how, capturedCount, removedCount, elapsed,
                fallback ? ", lag fallback: " + fallbackReason : "");
    }

    /** Ends it early: the rumble is put back, nothing is removed, nobody is killed, the held are released on the surface. */
    void abort(Store<EntityStore> store, String why) {
        if (ended) {
            return;
        }
        try {
            hold.releaseAll(world, store);
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not release the held players");
        }
        finish(store, "stopped early (" + why + "): nothing removed, nobody killed");
    }

    boolean isEnded() {
        return ended;
    }

    boolean isFallback() {
        return fallback;
    }

    ChunkRumbleJob rumble() {
        return rumble;
    }

    String status() {
        return String.format(Locale.ROOT, "%d,%d: %.1f s left, %d tracked block(s), %d viewer(s), %s", cx, cz,
                Math.max(0, killAt - elapsed), record.blocks.size(), viewerCount,
                fallback ? "lag fallback (" + fallbackReason + ")" : rumble == null ? "no rumble" : "rumbling " + rumble.columns() + " columns");
    }
}
