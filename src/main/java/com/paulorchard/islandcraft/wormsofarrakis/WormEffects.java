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
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * Everything the listed beings see and hear before the worm arrives, driven by the event hooks. In STALKING: the rumble
 * for everyone near, and every few seconds a ripple, a stumble and a small shake for each listed being on sand. In
 * LOCKED, for the target alone: the ripple, the vignette, the shake build-up, the slowdown and the camera zoom. The breach
 * itself is {@link BreachJob}.
 */
final class WormEffects implements WormEventListener {

    static final String[] RUMBLE = {"Arrakis_SFX_Worm_Rumble_Statue_Low", "Arrakis_SFX_Worm_Rumble_Ice_Low",
            "Arrakis_SFX_Worm_Rumble_Storm_Low"};
    private static final Color TAN = new Color((byte) 0xc8, (byte) 0xa4, (byte) 0x6e);

    /** What one event has done so far: timers for the repeating effects. */
    private static final class State {
        double rumble = 1e9;
        double ripple;
        /** Sink ripples of LOCKED that may still be running (at most two at once). */
        final List<SinkRipple.Handle> locked = new ArrayList<>();
        double shake;
        /** Seconds until each listed being's next pulse; the first is a random offset within one pulse period. */
        final Map<UUID, Double> pulse = new HashMap<>();
        double clock;
        /** A mob target of LOCKED has been slowed / held. */
        boolean mobSlow;
        boolean mobHold;
    }

    private final Supplier<WormsOfArrakisConfig> config;
    private final Supplier<Set<Integer>> sand;
    private final WormTestSystem jobs;
    private final StumbleFx stumbles = new StumbleFx();
    private final Map<Integer, State> states = new ConcurrentHashMap<>();
    private final Map<UUID, TargetFx> fx = new ConcurrentHashMap<>();
    /** Targets whose pulled-out camera the breach has let go (BreachCameraReturnAt): TargetFx eases them back. */
    private final Set<UUID> zoomReleased = ConcurrentHashMap.newKeySet();
    private volatile AggroCore core;
    private volatile double dt = 1.0 / 30;
    /** Stalk pulses played since the plugin started (read by the self-test). */
    volatile int pulseCount;

    WormEffects(Supplier<WormsOfArrakisConfig> config, Supplier<Set<Integer>> sand, WormTestSystem jobs) {
        this.config = config;
        this.sand = sand;
        this.jobs = jobs;
    }

    /** Where the effects find a being's position and whether it stands on sand. */
    void setCore(AggroCore core) {
        this.core = core;
    }

    void setDt(double dt) {
        this.dt = dt;
    }

    AggroCore core() {
        return core;
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
        stumbles.tick(world.getName(), store, dt);
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
        stumbles.forget(player);
    }

    /** Takes back every vignette, slowdown and stumble at once (server stop, plugin shutdown, /worm stop). */
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
        stumbles.restoreAll();
        states.clear();
    }

    // ------------------------------------------------------------------ hooks

    @Override
    public void onPhaseChange(WormEvent event, WormPhase old, WormPhase now) {
        if (old == null) {
            World startWorld = Universe.get().getWorld(event.getWorldName());
            if (startWorld != null) {
                WormStartFx.play(startWorld, startWorld.getEntityStore().getStore(), event, config.get(), jobs, sand.get(), core);
            }
        }
        if (now != WormPhase.BREACH || event.getTarget() == null) {
            return;
        }
        World world = Universe.get().getWorld(event.getWorldName());
        PlayerRef target = Universe.get().getPlayer(event.getTarget());
        if (world != null && target != null) {
            startBreach(world, world.getEntityStore().getStore(), target, target, event.getSecondsLeft());
        } else if (world != null) {
            startEntityBreach(world, world.getEntityStore().getStore(), event);
        }
    }

    /**
     * The breach for a mob or a totem target: at where it stands, or, if it has died, despawned or been removed, at the
     * last place it was seen. There is no victim; the swallow zone catches whoever is in it, the entity included.
     */
    private void startEntityBreach(World world, Store<EntityStore> store, WormEvent event) {
        UUID id = event.getTarget();
        BeingAggro b = being(id);
        Vector3d p = b != null && b.entity != null && b.entity.isValid() ? b.position : event.getLastTargetPosition();
        if (p == null || (p.x == 0 && p.y == 0 && p.z == 0)) {
            p = event.getOrigin();
        }
        double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
        Vector3d heading = new Vector3d(Math.cos(a), 0, Math.sin(a));
        Vector3d centre = new Vector3d(p.x, surfaceY(world, p.x, p.z, p.y), p.z);
        jobs.stop(id, "breach");
        jobs.add(new BreachJob(this, world, id, null, null, centre, heading, event.getSecondsLeft(), config).forEntity(id));
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
    public void onEnd(WormEvent event, WormEndReason reason) {
        states.remove(event.getId());
        Set<UUID> everyone = new HashSet<>(event.getList());
        if (event.getTarget() != null) {
            everyone.add(event.getTarget());
        }
        for (UUID id : everyone) {
            TargetFx f = fx.get(id);
            if (f != null) {
                f.fadeOut();
            }
        }
        for (UUID id : everyone) {
            BeingAggro b = being(id);
            if (b != null && b.kind == BeingKind.MOB && b.entity != null && b.entity.isValid()) {
                // A mob slowed or held for the lock is let go when the event ends (the breach releases its own).
                World w = Universe.get().getWorld(event.getWorldName());
                if (w != null && reason == WormEndReason.STOPPED) {
                    BreachEntities.unhold(w.getEntityStore().getStore(), b.entity);
                }
            }
        }
        if (reason == WormEndReason.STOPPED) {
            if (event.getTarget() != null) {
                jobs.stop(event.getTarget(), "breach");
                jobs.stop(event.getTarget(), "sink");
            }
            for (UUID id : everyone) {
                stumbles.restore(id);
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
        World world = Universe.get().getWorld(event.getWorldName());
        if (world == null) {
            return;
        }
        WormsOfArrakisConfig cfg = config.get();
        Store<EntityStore> store = world.getEntityStore().getStore();
        State s = states.computeIfAbsent(event.getId(), k -> new State());
        s.clock += dt;
        double left = event.getSecondsLeft();
        if (phase == WormPhase.STALKING) {
            double stalkProgress = clamp01(1 - left / Math.max(event.getStalkSeconds(), 0.001));
            rumble(world, store, s, event, stalkProgress, cfg);
            pulses(world, store, s, event, cfg);
            return;
        }
        if (phase == WormPhase.LOCKED) {
            rumble(world, store, s, event, 1.0, cfg);
        }
        PlayerRef target = event.getTarget() == null ? null : Universe.get().getPlayer(event.getTarget());
        Vector3d targetPos = target == null ? null : position(store, target);
        if (target == null && phase == WormPhase.LOCKED) {
            mobTarget(world, store, s, event, cfg);
        }
        if (target == null || targetPos == null) {
            return;
        }
        double lock = event.getLockSeconds();
        double lockedFor = phase == WormPhase.LOCKED ? lock - left : lock;
        if (phase == WormPhase.LOCKED) {
            lockedRipples(world, store, s, target, targetPos, cfg);
        }
        targetEffects(s, target, phase, left, lock, lockedFor, cfg);
    }

    /**
     * A mob that is the target of LOCKED is slowed to SlowFloor for the whole lock and held for the second half: the
     * effects that make sense for it (no vignette, zoom or camera). A totem is an object and gets nothing.
     */
    private void mobTarget(World world, Store<EntityStore> store, State s, WormEvent event, WormsOfArrakisConfig cfg) {
        BeingAggro b = being(event.getTarget());
        if (b == null || b.kind != BeingKind.MOB || b.entity == null || !b.entity.isValid()) {
            return;
        }
        double lock = event.getLockSeconds();
        if (!s.mobSlow) {
            s.mobSlow = true;
            stumbles.stumble(world, store, b.id, b.entity, lock, cfg.getSlowFloor() * 100);
        }
        if (!s.mobHold && lock - event.getSecondsLeft() >= lock * 0.5) {
            s.mobHold = true;
            BreachEntities.hold(store, b.entity, (float) (lock * 0.5 + 1));
        }
    }

    // ------------------------------------------------------------------ position and sand of a being

    private BeingAggro being(UUID id) {
        return core == null ? null : core.being(id);
    }

    // ------------------------------------------------------------------ trail (the breach's departure)

    /** A low wide trail of dust along the way the worm went, and now and then a puff of sand thrown up. Used by the breach. */
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
        int ground = WorldAccess.groundHeight(world, x, z);
        return ground == WorldAccess.NO_HEIGHT ? fallback : ground + 1.0;
    }

    static void particle(Store<EntityStore> store, String system, double x, double y, double z, float scale,
                                 float far, List<Ref<EntityStore>> refs) {
        com.hypixel.hytale.server.core.universe.world.ParticleUtil.spawnParticleEffect(system, x, y, z, 0f, 0f, 0f,
                scale, TAN, null, refs, store, far);
    }

    // ------------------------------------------------------------------ rumble

    /**
     * Everyone in the list hears the rumble rise (louder and lower) as the stalk goes on; other players within the group
     * radius of the trigger hear it at a flat volume. One-shot sounds played again every few seconds, per player, so
     * there is no distance falloff.
     */
    private void rumble(World world, Store<EntityStore> store, State s, WormEvent event, double progress,
                        WormsOfArrakisConfig cfg) {
        s.rumble += dt;
        if (s.rumble < cfg.getRumbleIntervalSeconds()) {
            return;
        }
        s.rumble = 0;
        BeingAggro trigger = being(event.getTrigger());
        Vector3d centre = trigger != null && trigger.eligible ? trigger.position : event.getOrigin();
        Set<UUID> listed = new HashSet<>(event.getList());
        float volume = (float) lerp(cfg.getRumbleTargetStartVolume(), cfg.getRumbleTargetVolume(), progress);
        float pitch = (float) lerp(1.0, cfg.getRumbleTargetEndPitch(), progress);
        for (PlayerRef p : near(world, store, centre, cfg.getGroupRadius(), null)) {
            if (!listed.contains(p.getUuid())) {
                playRumble(p, (float) cfg.getRumbleBystanderVolume(), 1.0f);
            }
        }
        for (UUID id : listed) {
            BeingAggro b = being(id);
            if (b != null && b.ref != null) {
                playRumble(b.ref, volume, pitch);
            }
        }
    }

    static void playRumble(PlayerRef player, float volume, float pitch) {
        for (String id : RUMBLE) {
            TimedEffectsJob.sound(player, id, volume, pitch);
        }
    }

    // ------------------------------------------------------------------ the stalk pulse

    /**
     * Every StalkPulseSeconds each listed being standing on sand gets a sink-and-rebound ripple under its feet, a small
     * camera shake (players) and a stumble. Each being has its own random start offset so they do not all stumble together.
     */
    private void pulses(World world, Store<EntityStore> store, State s, WormEvent event, WormsOfArrakisConfig cfg) {
        double period = Math.max(cfg.getStalkPulseSeconds(), 0.1);
        for (UUID id : event.getList()) {
            double left = s.pulse.computeIfAbsent(id, k -> ThreadLocalRandom.current().nextDouble() * period) - dt;
            if (left > 0) {
                s.pulse.put(id, left);
                continue;
            }
            s.pulse.put(id, left + period);
            BeingAggro b = being(id);
            if (b == null || !b.eligible || !b.onSand || b.position == null) {
                continue; // a being off the sand gets no pulse
            }
            Vector3d at = new Vector3d(b.position);
            pulseCount++;
            sink(world, store, id, at, 2, null, false, cfg.getRippleViewDistance(), b.ref);
            if (b.ref != null && cfg.isCameraShake()) {
                shake(b.ref, "Arrakis_Worm_Tremble", cfg.getShakeStrength() * 0.4);
            }
            if (b.kind != BeingKind.TOTEM) { // a totem is an object: the ripple, but no stumble
                stumbles.stumble(world, store, id, b.entity, cfg.getStumbleSeconds(), cfg.getStumbleSlowPercent());
            }
        }
        s.pulse.keySet().retainAll(event.getList());
    }

    /** One stumble, with the configured length and strength (for /worm preview stumble). */
    void stumble(World world, Store<EntityStore> store, UUID id, Ref<EntityStore> ref) {
        WormsOfArrakisConfig cfg = config.get();
        stumbles.stumble(world, store, id, ref, cfg.getStumbleSeconds(), cfg.getStumbleSlowPercent());
    }

    // ------------------------------------------------------------------ ripple (LOCKED)

    /** In LOCKED the sink-and-rebound ripple under the target, back to back with at most two running. */
    private void lockedRipples(World world, Store<EntityStore> store, State s, PlayerRef target, Vector3d targetPos,
                               WormsOfArrakisConfig cfg) {
        s.ripple += dt;
        s.locked.removeIf(h -> !h.running());
        if (s.ripple < cfg.getLockedRippleInterval() || s.locked.size() >= 2) {
            return;
        }
        s.ripple = 0;
        SinkRipple.Handle h = sink(world, store, target.getUuid(), targetPos, 3, 2, false, cfg.getRippleViewDistance(), target);
        if (h != null) {
            s.locked.add(h);
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

    // ------------------------------------------------------------------ the target only (LOCKED and BREACH)

    /**
     * From the start of LOCKED the target is slowed progressively to SlowFloor, gets the vignette rising to full, the
     * shake and, in the last ZoomSeconds, the camera pulled out. In BREACH it is all at full.
     */
    private void targetEffects(State s, PlayerRef target, WormPhase phase, double left, double lock, double lockedFor,
                               WormsOfArrakisConfig cfg) {
        double q = phase == WormPhase.BREACH ? 1 : clamp01(lockedFor / Math.max(lock, 0.001));
        double vignette = q;
        double slow = lerp(1.0, cfg.getSlowFloor(), q);
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

        if (cfg.isCameraShake() && phase == WormPhase.LOCKED) {
            shake(s, target, q, cfg);
        }
    }

    /** A tremble that grows in size and frequency over the first half of LOCKED, then strong shakes. */
    private void shake(State s, PlayerRef target, double q, WormsOfArrakisConfig cfg) {
        s.shake += dt;
        if (q < 0.5) {
            double r = q / 0.5;
            if (s.shake >= lerp(1.0, 0.5, r)) {
                s.shake = 0;
                shake(target, "Arrakis_Worm_Tremble", cfg.getShakeStrength() * lerp(0.2, 0.6, r));
            }
        } else if (s.shake >= 0.4) {
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
