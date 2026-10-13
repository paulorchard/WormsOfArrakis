package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.builtin.deployables.component.DeployableComponent;
import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.component.AddReason;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.RefSystem;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Finds the totems. A totem is a deployable entity (a {@link DeployableComponent}) whose config id is in the
 * {@link TotemRegistry}. A {@link RefSystem} is told of every deployable the moment it is added, so a spawn cannot be
 * missed (scanning each tick could miss one that lives and dies between scans); the tick then looks at each new one
 * once it has come to rest, and either gives it a being and lands it (see {@link AggroCore#landTotem}) or ignores it for
 * good if the block under it is not sand. A landed totem gains aggro each tick for as long as its entity is valid.
 */
final class TotemTracker extends RefSystem<EntityStore> {

    private enum State { WAITING, ACTIVE, DORMANT }

    private static final class Tot {
        final Ref<EntityStore> ref;
        final String world;
        State state = State.WAITING;
        UUID id;
        String deployableId;
        UUID owner;
        Vector3d last;
        /** Seconds the totem has been waiting to come to rest. */
        double waited;
        volatile boolean removed;

        Tot(Ref<EntityStore> ref, String world) {
            this.ref = ref;
            this.world = world;
        }
    }

    /** A thrown totem that is still moving is checked again for this long before it is judged where it is. */
    private static final double SETTLE_SECONDS = 1.5;
    /** Moved less than this since the last tick: at rest. */
    private static final double REST = 0.02;

    private final AggroCore core;
    private final TotemRegistry registry;
    private final Supplier<WormsOfArrakisConfig> config;
    private final List<Tot> totems = new CopyOnWriteArrayList<>();
    private volatile int landedCount;

    TotemTracker(AggroCore core, TotemRegistry registry, Supplier<WormsOfArrakisConfig> config) {
        this.core = core;
        this.registry = registry;
        this.config = config;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.and(DeployableComponent.getComponentType());
    }

    @Override
    public void onEntityAdded(Ref<EntityStore> ref, AddReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        // A deployable loaded from disk with its chunk is not a new throw.
        if (reason != AddReason.SPAWN) {
            return;
        }
        World world = store.getExternalData().getWorld();
        totems.add(new Tot(ref, world.getName()));
    }

    @Override
    public void onEntityRemove(Ref<EntityStore> ref, RemoveReason reason, Store<EntityStore> store, CommandBuffer<EntityStore> buffer) {
        for (Tot t : totems) {
            if (t.ref.equals(ref)) {
                t.removed = true;
            }
        }
    }

    /** Totems landed on sand since the plugin started (for /worm status and the self-test). */
    int landed() {
        return landedCount;
    }

    /** Totems being tracked as beings right now. */
    int active() {
        int n = 0;
        for (Tot t : totems) {
            if (t.state == State.ACTIVE) {
                n++;
            }
        }
        return n;
    }

    /** One world tick: judge the new totems, feed the landed ones, drop the gone ones. */
    void tick(World world, Store<EntityStore> store, double dt, Set<Integer> sandIds) {
        String name = world.getName();
        for (Tot t : totems) {
            if (!t.world.equals(name)) {
                continue;
            }
            if (t.removed || !t.ref.isValid()) {
                if (t.id != null) {
                    core.forget(t.id);
                }
                totems.remove(t);
                continue;
            }
            switch (t.state) {
                case WAITING -> wait(world, store, t, dt, sandIds);
                case ACTIVE -> feed(store, t, dt);
                default -> {
                }
            }
        }
    }

    private void wait(World world, Store<EntityStore> store, Tot t, double dt, Set<Integer> sandIds) {
        DeployableComponent comp = store.getComponent(t.ref, DeployableComponent.getComponentType());
        DeployableConfig cfg = comp == null ? null : comp.getConfig();
        TransformComponent transform = store.getComponent(t.ref, TransformComponent.getComponentType());
        UUIDComponent uuid = store.getComponent(t.ref, UUIDComponent.getComponentType());
        t.waited += dt;
        if (cfg == null || transform == null || uuid == null) {
            if (t.waited > SETTLE_SECONDS) {
                t.state = State.DORMANT;
            }
            return;
        }
        if (!registry.contains(cfg.getId())) {
            t.state = State.DORMANT; // some other deployable (a turret, a trap)
            return;
        }
        Vector3d p = new Vector3d(transform.getPosition());
        boolean moving = t.last == null || t.last.distance(p) > REST;
        t.last = p;
        if (moving && t.waited < SETTLE_SECONDS) {
            return; // new, or still in flight or falling: look again next tick
        }
        t.id = uuid.getUuid();
        t.deployableId = cfg.getId();
        t.owner = comp.getOwnerUUID();
        if (!AggroManager.onSand(world, p, config.get().getSandTolerance(), sandIds)) {
            log("totem %s (owner %s) landed at %s and is not on sand: ignored", t.deployableId, t.owner, at(p));
            t.state = State.DORMANT;
            return;
        }
        BeingAggro b = core.beingOrCreate(t.id, BeingKind.TOTEM);
        b.entity = t.ref;
        b.label = t.deployableId;
        b.world = world.getName();
        b.eligible = true;
        b.onSand = true;
        b.position = p;
        AggroCore.TotemOutcome outcome = core.landTotem(b, t.owner);
        landedCount++;
        switch (outcome) {
            case JOINED -> {
                log("totem %s (owner %s) landed at %s and joined event #%d with %.0f aggro", t.deployableId, t.owner, at(p),
                        b.event.getId(), b.score());
                t.state = State.ACTIVE;
            }
            case TRIGGERED -> {
                log("totem %s (owner %s) landed at %s and started event #%d (guaranteed target)", t.deployableId, t.owner,
                        at(p), b.event == null ? -1 : b.event.getId());
                t.state = State.ACTIVE;
            }
            default -> {
                log("totem %s (owner %s) landed at %s but the owner's event is already past its stalk: it does nothing",
                        t.deployableId, t.owner, at(p));
                core.forget(t.id);
                t.state = State.DORMANT;
            }
        }
    }

    private void feed(Store<EntityStore> store, Tot t, double dt) {
        BeingAggro b = core.being(t.id);
        if (b == null) {
            t.state = State.DORMANT;
            return;
        }
        TransformComponent transform = store.getComponent(t.ref, TransformComponent.getComponentType());
        if (transform != null) {
            b.position = new Vector3d(transform.getPosition());
        }
        b.entity = t.ref;
        b.eligible = true;
        b.onSand = true;
        core.tickTotem(b, dt);
    }

    private static String at(Vector3d p) {
        return String.format(Locale.ROOT, "%.0f %.0f %.0f", p.x, p.y, p.z);
    }

    private static void log(String format, Object... args) {
        WormsOfArrakisPlugin.get().getLogger().at(Level.INFO).log("%s", "[Worm] " + String.format(Locale.ROOT, format, args));
    }
}
