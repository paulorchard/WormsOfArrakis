package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AccumulationMode;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.packets.camera.CameraShakeEffect;
import com.hypixel.hytale.builtin.adventure.camera.asset.camerashake.CameraShake;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Everything the players see and hear before the worm arrives, driven by the prompt 18 hooks: wormsign for anyone
 * near the worm, the rumble for everyone near the target, and for the target alone the ripple, the vignette, the
 * camera shake and the slowdown. Prompt 20 adds the breach on top.
 */
final class WormEffects implements WormEventListener {

    static final String[] RUMBLE = {"Arrakis_SFX_Worm_Rumble_Statue_Low", "Arrakis_SFX_Worm_Rumble_Ice_Low",
            "Arrakis_SFX_Worm_Rumble_Storm_Low"};
    private static final Color TAN = new Color((byte) 0xc8, (byte) 0xa4, (byte) 0x6e);

    /** What one event has done so far: timers for the repeating effects. */
    private static final class State {
        Vector3d lastTrail;
        double trail;
        double puff;
        double rumble = 1e9;
        double ripple;
        /** Sink ripples of LOCKED that may still be running (at most two at once). */
        final List<SinkRipple.Handle> locked = new ArrayList<>();
        /** How long the last STALKING ripple takes: the next one starts no sooner than a quarter of it. */
        double lastRippleSeconds;
        double shake;
        final Map<UUID, Double> passed = new HashMap<>();
        double clock;
    }

    private final Supplier<WormsOfArrakisConfig> config;
    private final Supplier<Set<Integer>> sand;
    private final WormTestSystem jobs;
    private final Map<Integer, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, TargetFx> fx = new ConcurrentHashMap<>();
    /** Targets whose pulled-out camera the breach has let go (BreachCameraReturnAt): TargetFx eases them back. */
    private final Set<UUID> zoomReleased = ConcurrentHashMap.newKeySet();
    private volatile double dt = 1.0 / 30;

    WormEffects(Supplier<WormsOfArrakisConfig> config, Supplier<Set<Integer>> sand, WormTestSystem jobs) {
        this.config = config;
        this.sand = sand;
        this.jobs = jobs;
    }

    void setDt(double dt) {
        this.dt = dt;
    }

    void releaseZoom(UUID target) {
        zoomReleased.add(target);
    }

    void clearZoomRelease(UUID target) {
        zoomReleased.remove(target);
    }

    TargetFx fxOf(PlayerRef player) {
        return fx.computeIfAbsent(player.getUuid(), k -> new TargetFx(player, config));
    }

    /** The per-player fades, once per world tick after the events have run. */
    void tick(World world, Store<EntityStore> store, double dt) {
        for (Iterator<TargetFx> it = fx.values().iterator(); it.hasNext(); ) {
            TargetFx f = it.next();
            Ref<EntityStore> ref = f.player.getReference();
            if (ref == null || !ref.isValid() || !world.getName().equals(worldNameOf(f.player))) {
                continue; // another world's tick looks after it
            }
            if (!f.tick(store, dt) || f.finished()) {
                f.restore(store);
                it.remove();
            }
        }
    }

    private static String worldNameOf(PlayerRef p) {
        World w = Universe.get().getWorld(p.getWorldUuid());
        return w == null ? "" : w.getName();
    }

    /** A client that is gone needs nothing restored. */
    void forget(UUID player) {
        fx.remove(player);
    }

    /** Takes back every vignette and slowdown at once (server stop, plugin shutdown, /worm stop). */
    void restoreAll() {
        for (TargetFx f : fx.values()) {
            Ref<EntityStore> ref = f.player.getReference();
            if (ref != null && ref.isValid()) {
                World w = Universe.get().getWorld(f.player.getWorldUuid());
                if (w != null) {
                    f.restore(w.getEntityStore().getStore());
                }
            }
        }
        fx.clear();
        states.clear();
    }

    // ------------------------------------------------------------------ hooks

    @Override
    public void onPhaseChange(WormEvent event, WormPhase old, WormPhase now) {
        if (old == null) {
            World startWorld = Universe.get().getWorld(event.getWorldName());
            if (startWorld != null) {
                WormStartFx.play(startWorld, startWorld.getEntityStore().getStore(), event, config.get(), jobs, sand.get());
            }
        }
        if (now != WormPhase.BREACH || event.getTarget() == null) {
            return;
        }
        World world = Universe.get().getWorld(event.getWorldName());
        PlayerRef target = Universe.get().getPlayer(event.getTarget());
        if (world != null && target != null) {
            startBreach(world, world.getEntityStore().getStore(), target, target, event.getSecondsLeft());
        }
    }

    /**
     * Starts the breach under a player. {@code victim} is the one swallowed, or null for a breach with no victim
     * (/worm breach here), in which case {@code caller} gives the spot and the direction.
     */
    void startBreach(World world, Store<EntityStore> store, PlayerRef victim, PlayerRef caller, double seconds) {
        Ref<EntityStore> ref = caller.getReference();
        Vector3d p = position(store, caller);
        HeadRotation head = ref == null ? null : store.getComponent(ref, HeadRotation.getComponentType());
        if (p == null) {
            return;
        }
        Vector3d dir = head == null ? new Vector3d(0, 0, 1) : head.getDirection();
        double len = Math.hypot(dir.x, dir.z);
        Vector3d heading = len < 1e-6 ? new Vector3d(0, 0, 1) : new Vector3d(dir.x / len, 0, dir.z / len);
        Vector3d centre = new Vector3d(p.x, surfaceY(world, p.x, p.z, p.y), p.z);
        jobs.stop(caller.getUuid(), "breach");
        zoomReleased.remove(caller.getUuid());
        jobs.add(new BreachJob(this, world, caller.getUuid(), victim, victim == null ? null : ref, centre, heading,
                seconds, config));
    }

    @Override
    public void onRetarget(WormEvent event, UUID oldTarget, UUID newTarget) {
        PlayerRef old = Universe.get().getPlayer(oldTarget);
        if (old != null) {
            fxOf(old).fadeOut();
        }
        State s = states.get(event.getId());
        if (s != null) {
            s.ripple = 0;
            s.shake = 0;
            s.rumble = 1e9; // the new target hears it at once
        }
    }

    @Override
    public void onEnd(WormEvent event, WormEndReason reason) {
        states.remove(event.getId());
        for (UUID id : event.getGroup()) {
            TargetFx f = fx.get(id);
            if (f != null) {
                f.fadeOut();
            }
        }
        if (reason == WormEndReason.STOPPED) {
            if (event.getTarget() != null) {
                jobs.stop(event.getTarget(), "breach");
                jobs.stop(event.getTarget(), "sink");
            }
            for (UUID id : event.getGroup()) {
                TargetFx f = fx.remove(id);
                if (f != null) {
                    World w = Universe.get().getWorld(f.player.getWorldUuid());
                    if (w != null) {
                        f.restore(w.getEntityStore().getStore());
                    }
                }
            }
        }
    }

    @Override
    public void onTick(WormEvent event) {
        WormPhase phase = event.getPhase();
        if (phase != WormPhase.STALKING && phase != WormPhase.LOCKED && phase != WormPhase.BREACH) {
            return;
        }
        World world = Universe.get().getWorld(event.getWorldName());
        PlayerRef target = event.getTarget() == null ? null : Universe.get().getPlayer(event.getTarget());
        if (world == null || target == null) {
            return;
        }
        WormsOfArrakisConfig cfg = config.get();
        Store<EntityStore> store = world.getEntityStore().getStore();
        State s = states.computeIfAbsent(event.getId(), k -> new State());
        s.clock += dt;
        Vector3d worm = event.getWormPosition();
        Vector3d targetPos = position(store, target);
        if (targetPos == null) {
            return;
        }
        double stalk = event.getStalkSeconds();
        double lock = event.getLockSeconds();
        double left = event.getSecondsLeft();
        // Progress through the whole approach, 0 at the start of STALKING and 1 at the start of LOCKED, and then
        // seconds into LOCKED.
        double stalkProgress = phase == WormPhase.STALKING ? clamp01(1 - left / Math.max(stalk, 0.001)) : 1;
        double lockedFor = phase == WormPhase.LOCKED ? lock - left : phase == WormPhase.BREACH ? lock : 0;

        if (phase != WormPhase.BREACH) {
            wormsign(world, store, s, worm, cfg);
            rumble(world, store, s, target, targetPos, stalkProgress, cfg);
            ripples(world, store, s, target, targetPos, phase, left, cfg);
            passPulse(store, s, target, worm, cfg);
        }
        targetEffects(store, s, target, phase, stalk, left, lock, lockedFor, cfg);
    }

    // ------------------------------------------------------------------ wormsign

    private void wormsign(World world, Store<EntityStore> store, State s, Vector3d worm, WormsOfArrakisConfig cfg) {
        s.trail += dt;
        s.puff += dt;
        if (s.trail < cfg.getWormsignTrailInterval()) {
            return;
        }
        s.trail = 0;
        List<PlayerRef> viewers = near(world, store, worm, cfg.getWormsignViewDistance(), null);
        Vector3d previous = s.lastTrail;
        s.lastTrail = new Vector3d(worm);
        if (viewers.isEmpty() || !emitSurface(world, worm)) {
            return;
        }
        boolean puff = s.puff >= cfg.getWormsignPuffInterval();
        if (puff) {
            s.puff = 0;
        }
        trail(world, store, previous, worm, viewers, puff, cfg);
    }

    /** True if the worm is over loaded sand; over rock or buildings the worm passes under without showing. */
    private boolean emitSurface(World world, Vector3d p) {
        Boolean sandy = AggroManager.surfaceIsSand(world, p.x, p.z, sand.get());
        return sandy != null && sandy;
    }

    /** A low wide trail of dust along the way the worm came, and now and then a puff of sand thrown up. */
    void trail(World world, Store<EntityStore> store, Vector3d from, Vector3d to, List<PlayerRef> viewers,
               boolean puff, WormsOfArrakisConfig cfg) {
        float scale = (float) cfg.getWormsignScale();
        float far = (float) cfg.getWormsignViewDistance() + 40f;
        List<Ref<EntityStore>> refs = refs(viewers);
        int points = from == null ? 1 : (int) Math.max(1, Math.min(4, from.distance(to) / 4));
        for (int i = 1; i <= points; i++) {
            double f = (double) i / points;
            double x = from == null ? to.x : from.x + (to.x - from.x) * f;
            double z = from == null ? to.z : from.z + (to.z - from.z) * f;
            double y = surfaceY(world, x, z, to.y);
            particle(store, "Block_Break_Dust", x, y + 0.4, z, scale, far, refs);
            particle(store, "Block_Land_Hard_Dust", x, y + 0.3, z, scale * 1.2f, far, refs);
        }
        if (puff) {
            double y = surfaceY(world, to.x, to.z, to.y);
            particle(store, "Block_Break_Sand", to.x, y + 1.5, to.z, scale * 1.8f, far, refs);
            particle(store, "Block_Break_Dust", to.x, y + 3.5, to.z, scale * 1.4f, far, refs);
        }
    }

    private static double surfaceY(World world, double x, double z, double fallback) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        return chunk == null ? fallback : chunk.getHeight((int) Math.floor(x) & ChunkUtil.SIZE_MASK,
                (int) Math.floor(z) & ChunkUtil.SIZE_MASK) + 1.0;
    }

    static void particle(Store<EntityStore> store, String system, double x, double y, double z, float scale,
                                 float far, List<Ref<EntityStore>> refs) {
        com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect(system, x, y, z, 0f, 0f, 0f,
                scale, TAN, null, refs, store, far);
    }

    // ------------------------------------------------------------------ rumble

    /**
     * Everyone near the target hears the rumble at a flat volume; the target hears it rise as the worm comes.
     * One-shot sounds played again every few seconds, per player, so there is no distance falloff.
     */
    private void rumble(World world, Store<EntityStore> store, State s, PlayerRef target, Vector3d targetPos,
                        double progress, WormsOfArrakisConfig cfg) {
        s.rumble += dt;
        if (s.rumble < cfg.getRumbleIntervalSeconds()) {
            return;
        }
        s.rumble = 0;
        double groupRadius = cfg.getGroupRadius();
        for (PlayerRef p : near(world, store, targetPos, groupRadius, null)) {
            if (p.getUuid().equals(target.getUuid())) {
                float volume = (float) lerp(cfg.getRumbleTargetStartVolume(), cfg.getRumbleTargetVolume(), progress);
                float pitch = (float) lerp(1.0, cfg.getRumbleTargetEndPitch(), progress);
                playRumble(p, volume, pitch);
            } else {
                playRumble(p, (float) cfg.getRumbleBystanderVolume(), 1.0f);
            }
        }
    }

    static void playRumble(PlayerRef player, float volume, float pitch) {
        for (String id : RUMBLE) {
            TimedEffectsJob.sound(player, id, volume, pitch);
        }
    }

    // ------------------------------------------------------------------ ripple

    /**
     * The sink-and-rebound ripple under the target: STALKING in the last RippleSeconds at the eased interval (but never
     * sooner than a quarter of the last one's length), and in LOCKED back to back with at most two running.
     */
    private void ripples(World world, Store<EntityStore> store, State s, PlayerRef target, Vector3d targetPos,
                         WormPhase phase, double left, WormsOfArrakisConfig cfg) {
        s.ripple += dt;
        if (phase == WormPhase.LOCKED) {
            s.locked.removeIf(h -> !h.running());
            if (s.ripple < cfg.getLockedRippleInterval() || s.locked.size() >= 2) {
                return;
            }
            s.ripple = 0;
            SinkRipple.Handle h = sink(world, store, target.getUuid(), targetPos, 3, 2, false, cfg.getRippleViewDistance(), target);
            if (h != null) {
                s.locked.add(h);
            }
            return;
        }
        if (left > cfg.getRippleSeconds()) {
            return;
        }
        double q = clamp01(1 - left / Math.max(cfg.getRippleSeconds(), 0.001));
        double interval = Math.max(lerp(cfg.getRippleStartInterval(), cfg.getRippleEndInterval(), q), 0.25 * s.lastRippleSeconds);
        if (s.ripple < interval) {
            return;
        }
        s.ripple = 0;
        SinkRipple.Handle h = sink(world, store, target.getUuid(), targetPos, 2, null, false, cfg.getRippleViewDistance(), target);
        if (h != null) {
            s.lastRippleSeconds = h.seconds();
        }
    }

    /**
     * One sink-and-rebound ripple under {@code at}, shown to {@code owner} (may be null) and to players within
     * {@code viewRange}. {@code ownerId} owns the job so it is undone with them. Null if there was no sand.
     */
    SinkRipple.Handle sink(World world, Store<EntityStore> store, UUID ownerId, Vector3d at, int increment, Integer rings,
                           boolean shake, double viewRange, PlayerRef owner) {
        List<PlayerRef> viewers = near(world, store, at, viewRange, owner);
        SinkJob.Options options = new SinkJob.Options();
        options.owner = ownerId;
        options.rings = rings;
        options.shake = shake;
        return SinkRipple.play(world, at, increment, options, viewers);
    }

    // ------------------------------------------------------------------ the target only

    private void targetEffects(Store<EntityStore> store, State s, PlayerRef target, WormPhase phase, double stalk,
                               double left, double lock, double lockedFor, WormsOfArrakisConfig cfg) {
        double vignette = 0;
        double slow = 1.0;
        double window = Math.min(cfg.getVignetteSeconds(), Math.max(stalk, 0.001));
        if (phase == WormPhase.STALKING) {
            if (left <= window) {
                vignette = cfg.getVignetteStalkLevel() * clamp01(1 - left / window);
            }
        } else {
            double q = phase == WormPhase.BREACH ? 1 : clamp01(lockedFor / Math.max(lock, 0.001));
            vignette = lerp(cfg.getVignetteStalkLevel(), 1.0, q);
            slow = lerp(1.0, cfg.getSlowFloor(), q);
        }
        TargetFx targetFx = fxOf(target);
        targetFx.want(vignette, slow);
        double zoom = 0;
        if (phase == WormPhase.LOCKED) {
            double zs = Math.min(cfg.getZoomSeconds(), Math.max(lock, 0.001));
            zoom = left <= zs ? clamp01(1 - left / zs) : 0;
        } else if (phase == WormPhase.BREACH) {
            zoom = zoomReleased.contains(target.getUuid()) ? 0 : 1;
        }
        targetFx.wantZoom(zoom);

        if (cfg.isCameraShake()) {
            shake(s, target, phase, left, cfg);
        }
    }

    /** A faint tremble from ShakeSeconds out that grows in size and frequency, then strong shakes while LOCKED. */
    private void shake(State s, PlayerRef target, WormPhase phase, double left, WormsOfArrakisConfig cfg) {
        s.shake += dt;
        if (phase == WormPhase.STALKING) {
            if (left > cfg.getShakeSeconds()) {
                return;
            }
            double q = clamp01(1 - left / Math.max(cfg.getShakeSeconds(), 0.001));
            if (s.shake >= lerp(2.0, 0.5, q)) {
                s.shake = 0;
                shake(target, "Arrakis_Worm_Tremble", cfg.getShakeStrength() * lerp(0.2, 0.6, q));
            }
        } else if (phase == WormPhase.LOCKED && s.shake >= 0.4) {
            s.shake = 0;
            shake(target, "Arrakis_Worm_Lock", cfg.getShakeStrength());
        }
    }

    static void shake(PlayerRef player, String shakeId, double intensity) {
        int index = CameraShake.getAssetMap().getIndex(shakeId);
        intensity *= WormsOfArrakisPlugin.get().config().getCameraShakeScale();
        if (index != Integer.MIN_VALUE && intensity > 0) {
            player.getPacketHandler().writeNoCache(new CameraShakeEffect(index, (float) intensity, AccumulationMode.Set));
        }
    }

    /** One shake pulse for a bystander the worm passes close to. */
    private void passPulse(Store<EntityStore> store, State s, PlayerRef target, Vector3d worm, WormsOfArrakisConfig cfg) {
        if (!cfg.isCameraShake()) {
            return;
        }
        for (PlayerRef p : Universe.get().getPlayers()) {
            if (p.getUuid().equals(target.getUuid()) || p.getReference() == null || !p.getReference().isValid()) {
                continue;
            }
            Vector3d at = position(store, p);
            Double last = s.passed.get(p.getUuid());
            if (at != null && at.distance(worm) <= cfg.getBystanderShakeRange() && (last == null || s.clock - last > 10)) {
                s.passed.put(p.getUuid(), s.clock);
                shake(p, "Arrakis_Worm_Pass", cfg.getShakeStrength() * 0.6);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Players of the world within a distance of a point, plus {@code always} even if further. */
    static List<PlayerRef> near(World world, Store<EntityStore> store, Vector3d point, double distance, PlayerRef always) {
        List<PlayerRef> list = new ArrayList<>();
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Vector3d at = position(store, p);
            if (at != null && (at.distance(point) <= distance || (always != null && always.getUuid().equals(p.getUuid())))) {
                list.add(p);
            }
        }
        return list;
    }

    static Vector3d position(Store<EntityStore> store, PlayerRef p) {
        Ref<EntityStore> ref = p.getReference();
        if (ref == null || !ref.isValid()) {
            return null;
        }
        TransformComponent t = store.getComponent(ref, TransformComponent.getComponentType());
        return t == null ? null : new Vector3d(t.getPosition());
    }

    static List<Ref<EntityStore>> refs(List<PlayerRef> players) {
        List<Ref<EntityStore>> list = new ArrayList<>();
        for (PlayerRef p : players) {
            Ref<EntityStore> ref = p.getReference();
            if (ref != null && ref.isValid()) {
                list.add(ref);
            }
        }
        return list;
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }
}
