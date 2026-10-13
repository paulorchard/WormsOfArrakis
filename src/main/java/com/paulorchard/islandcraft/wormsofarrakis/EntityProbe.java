package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.builtin.deployables.DeployablesUtils;
import com.hypixel.hytale.builtin.deployables.config.DeployableConfig;
import com.hypixel.hytale.builtin.deployables.interaction.SpawnDeployableAtHitLocationInteraction;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.InteractionType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.Interaction;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.RootInteraction;
import com.hypixel.hytale.server.core.modules.projectile.config.ProjectileConfig;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import org.joml.Vector3d;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * /wormtest entitytest: what the game does with hostile NPCs and totems, run on game ticks on a headless server with no
 * player. A short script spawns a handful of roles and reports how each is classified, how fast a tracked mob gains, whether
 * the stumble and the hold change an NPC's speed, whether a position set moves it, what dies and what drops, what the
 * tracking costs with 300 NPCs, and runs a whole totem-triggered event with mobs standing in the swallow zone. A virtual
 * player (an anchor in {@link MobTracker}) stands in for the player that mob tracking needs.
 */
final class EntityProbe extends AbstractWorldCommand {

        private final Supplier<Set<Integer>> sand;

    EntityProbe(Supplier<Set<Integer>> sand) {
        super("entitytest", "server.commands.wormtest.entitytest.desc");
        this.sand = sand;
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        List<java.util.concurrent.CompletableFuture<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>>> loads = new ArrayList<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                loads.add(WorldAccess.loadColumn(world, cx, cz, true));
            }
        }
        java.util.concurrent.CompletableFuture.allOf(loads.toArray(new java.util.concurrent.CompletableFuture[0]))
                .thenAccept(v -> world.execute(() -> start(context, world)));
    }

    private void start(CommandContext context, World world) {
        WorldAccess access = new WorldAccess(world);
        Vector3d spot = null;
        for (int r = 0; r < 90 && spot == null; r++) {
            for (int dx = -r; dx <= r && spot == null; dx++) {
                for (int dz = -r; dz <= r && spot == null; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    if (!access.isTicking(dx, dz)) {
                        continue;
                    }
                    int top = access.height(dx, dz);
                    if (top != WorldAccess.NO_HEIGHT && sand.get().contains(access.block(dx, top, dz))) {
                        spot = new Vector3d(dx + 0.5, top + 1, dz + 0.5);
                    }
                }
            }
        }
        if (spot == null) {
            context.sendMessage(Message.raw("entitytest: no sand near the origin"));
            return;
        }
        WormsOfArrakisPlugin.get().jobSystem().add(new Job(world, spot));
        context.sendMessage(Message.raw("entitytest started at " + spot + "; read the log for [probe] lines"));
    }

    private static void say(String text) {
        WormsOfArrakisPlugin.get().getLogger().at(Level.INFO).log("%s", "[probe] " + text);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    // ------------------------------------------------------------------ the script

    /**
     * NPCs are followed by UUID, not by reference: with no player near, the headless world unloads and reloads its chunks
     * every few seconds, which gives every entity a new reference. Each tick the live references are looked up again.
     */
    private static final class Job implements WormTestSystem.Job {
        final World world;
        final Vector3d spot;
        final UUID owner = UUID.randomUUID();
        final AggroManager manager = WormsOfArrakisPlugin.get().aggro();
        final AggroCore core = manager.core();
        final java.util.Map<UUID, Ref<EntityStore>> live = new java.util.HashMap<>();
        final List<UUID> all = new ArrayList<>();
        final List<WorldChunk> pinned = new ArrayList<>();
        final List<String> roles = new ArrayList<>();
        final List<UUID> samples = new ArrayList<>();
        double t;
        int step;
        int enteredStep = -1;
        UUID a;
        UUID b;
        final List<UUID> held = new ArrayList<>();
        final List<UUID> free = new ArrayList<>();
        final List<UUID> damaged = new ArrayList<>();
        final List<UUID> crowd = new ArrayList<>();
        final List<UUID> zone = new ArrayList<>();
        final java.util.Map<UUID, Vector3d> startPos = new java.util.HashMap<>();
        Vector3d aLast;
        double aMoved;
        double maxSpeed;
        double gained;
        boolean stumbled;
        boolean crowdNear;
        int itemsBefore = -1;
        double costSum;
        int costTicks;
        boolean totemDone;
        boolean triggered;
        boolean thrown;
        boolean unloadWas = true;
        boolean spawningWas = true;
        int spawnFailures;
        java.util.concurrent.CompletableFuture<Void> pending;
        BreachEntities entities;
        int sinkTick;
        double sinkSurfaceY;

        Job(World world, Vector3d spot) {
            this.world = world;
            this.spot = spot;
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
            return "entitytest";
        }

        @Override
        public void abort(Store<EntityStore> store) {
            WormsOfArrakisPlugin.get().aggro().mobs().anchors = List.of();
            world.getWorldConfig().setCanUnloadChunks(unloadWas);
            world.getWorldConfig().setSpawningNPC(spawningWas);
            for (WorldChunk c : pinned) {
                c.removeKeepLoaded();
            }
            refresh(store);
            for (UUID id : all) {
                Ref<EntityStore> r = live.get(id);
                if (r != null && r.isValid()) {
                    store.removeEntity(r, com.hypixel.hytale.component.RemoveReason.REMOVE);
                }
            }
        }

        void refresh(Store<EntityStore> store) {
            live.clear();
            store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
                for (int i = 0; i < chunk.size(); i++) {
                    UUIDComponent u = chunk.getComponent(i, UUIDComponent.getComponentType());
                    if (u != null) {
                        live.put(u.getUuid(), chunk.getReferenceTo(i));
                    }
                }
            });
        }

        Ref<EntityStore> r(UUID id) {
            Ref<EntityStore> ref = id == null ? null : live.get(id);
            return ref != null && ref.isValid() ? ref : null;
        }

        boolean here(UUID id) {
            return r(id) != null;
        }

        long count(List<UUID> ids) {
            return ids.stream().filter(this::here).count();
        }

        /** A standing place on the top of a loaded column near (spot + dx, spot + dz), sand if there is any within 10 blocks. */
        Vector3d place(double dx, double dz) {
            Vector3d best = null;
            WorldAccess access = new WorldAccess(world);
            for (int radius = 0; radius <= 10; radius++) {
                for (int ox = -radius; ox <= radius; ox++) {
                    for (int oz = -radius; oz <= radius; oz++) {
                        if (Math.max(Math.abs(ox), Math.abs(oz)) != radius) {
                            continue;
                        }
                        int x = (int) Math.floor(spot.x + dx) + ox;
                        int z = (int) Math.floor(spot.z + dz) + oz;
                        if (!access.isTicking(x, z)) {
                            continue;
                        }
                        int top = access.height(x, z);
                        if (top == WorldAccess.NO_HEIGHT) {
                            continue;
                        }
                        Vector3d p = new Vector3d(x + 0.5, top + 1.2, z + 0.5);
                        if (WormsOfArrakisPlugin.get().sandBlockIds().contains(access.block(x, top, z))) {
                            return p;
                        }
                        if (best == null) {
                            best = p;
                        }
                    }
                }
            }
            return best != null ? best : new Vector3d(spot.x + dx, spot.y + 1.2, spot.z + dz);
        }

        /** Spawns an NPC and returns its UUID (null if it could not be). */
        UUID spawn(Store<EntityStore> store, String role, double dx, double dz) {
            try {
                var pair = NPCPlugin.get().spawnNPC(store, role, null, place(dx, dz), new Rotation3f(0, 0, 0));
                Ref<EntityStore> ref = pair == null ? null : pair.left();
                if (ref == null) {
                    if (spawnFailures++ < 3) {
                        say("spawnNPC(" + role + ") gave " + pair + " at " + place(dx, dz));
                    }
                    return null;
                }
                UUIDComponent u = store.getComponent(ref, UUIDComponent.getComponentType());
                if (u == null) {
                    return null;
                }
                live.put(u.getUuid(), ref);
                all.add(u.getUuid());
                return u.getUuid();
            } catch (Throwable e) {
                say("spawn " + role + " failed: " + e);
                return null;
            }
        }

        Vector3d pos(Store<EntityStore> store, UUID id) {
            Ref<EntityStore> ref = r(id);
            TransformComponent tc = ref == null ? null : store.getComponent(ref, TransformComponent.getComponentType());
            return tc == null ? null : new Vector3d(tc.getPosition());
        }

        double multiplier(Store<EntityStore> store, UUID id) {
            Ref<EntityStore> ref = r(id);
            NPCEntity npc = ref == null ? null : store.getComponent(ref, NPCEntity.getComponentType());
            return npc == null ? Double.NaN : npc.getCurrentHorizontalSpeedMultiplier(ref, store);
        }

        double walked(Store<EntityStore> store, List<UUID> group) {
            double sum = 0;
            int n = 0;
            for (UUID id : group) {
                Vector3d start = startPos.get(id);
                Vector3d now = pos(store, id);
                if (start != null && now != null) {
                    sum += now.distance(start);
                    n++;
                }
            }
            return n == 0 ? Double.NaN : sum / n;
        }

        /** True once per step, on its first tick. */
        boolean begin() {
            if (enteredStep == step) {
                return false;
            }
            enteredStep = step;
            t = 0;
            return true;
        }

        int items(Store<EntityStore> store) {
            int[] n = {0};
            store.forEachChunk(ItemComponent.getComponentType(), (chunk, buffer) -> {
                n[0] += chunk.size();
            });
            return n[0];
        }

        boolean dead(Store<EntityStore> store, UUID id) {
            Ref<EntityStore> ref = r(id);
            return ref != null && store.getComponent(ref, DeathComponent.getComponentType()) != null;
        }

        @Override
        public boolean tick(float dt, Store<EntityStore> store) {
            t += dt;
            refresh(store);
            for (WorldChunk c : pinned) {
                c.resetKeepAlive();
            }
            WormsOfArrakisConfig cfg = WormsOfArrakisPlugin.get().config();
            WormEffects fx = WormsOfArrakisPlugin.get().effects();
            MobTracker tracker = manager.mobs();
            // Every step starts by asking for its chunks again (they are unloaded a few seconds after the last request).
            if (step >= 3 && step != 9 && enteredStep != step) {
                if (pending == null) {
                    List<java.util.concurrent.CompletableFuture<com.hypixel.hytale.component.Ref<com.hypixel.hytale.server.core.universe.world.storage.ChunkStore>>> loads = new ArrayList<>();
                    for (int cx = -3; cx <= 3; cx++) {
                        for (int cz = -3; cz <= 3; cz++) {
                            loads.add(WorldAccess.loadColumn(world, cx, cz, true));
                        }
                    }
                    pending = java.util.concurrent.CompletableFuture.allOf(loads.toArray(new java.util.concurrent.CompletableFuture[0]));
                }
                if (!pending.isDone()) {
                    return true;
                }
                pending = null;
            }
            switch (step) {
                case 0 -> { // spawn the roles
                    // With no player near, the world unloads its chunks and gives every entity a new reference: stop that for the test.
                    var wc = world.getWorldConfig();
                    unloadWas = wc.canUnloadChunks();
                    spawningWas = wc.isSpawningNPC();
                    wc.setCanUnloadChunks(false);
                    wc.setSpawningNPC(false);
                    for (int cx = -3; cx <= 3; cx++) {
                        for (int cz = -3; cz <= 3; cz++) {
                            WorldChunk c = new WorldAccess(world).chunkComponent(cx, cz);
                            if (c != null) {
                                c.addKeepLoaded();
                                pinned.add(c);
                            }
                        }
                    }
                    tracker.anchors = List.of(new Vector3d(spot));
                    String[] names = {"Zombie", "Skeleton", "Trork_Warrior", "Wolf_Black", "Cow", "Chicken", "Sheep",
                            "Kweebec_Sapling", "Bramblekin", "Goblin_Scrapper", "Scarak_Fighter", "Crawler_Void"};
                    int i = 0;
                    for (String role : names) {
                        UUID id = spawn(store, role, 14 + (i % 4) * 4, -12 + (i / 4) * 4);
                        if (id != null) {
                            roles.add(role);
                            samples.add(id);
                        }
                        i++;
                    }
                    a = spawn(store, "Zombie", 30, 0);
                    say("spawned " + roles.size() + " role samples " + roles + " and zombie A");
                    step = 1;
                    t = 0;
                }
                case 1 -> { // classification
                    if (t < 1.5) {
                        return true;
                    }
                    for (int i = 0; i < samples.size(); i++) {
                        Ref<EntityStore> ref = r(samples.get(i));
                        NPCEntity npc = ref == null ? null : store.getComponent(ref, NPCEntity.getComponentType());
                        WorldSupport ws = ref == null ? null : store.getComponent(ref, WorldSupport.getComponentType());
                        BeingAggro being = core.being(samples.get(i));
                        say(String.format(Locale.ROOT, "role %-18s npc=%s worldSupport=%s defaultPlayerAttitude=%s counted=%s tracked as being=%s",
                                roles.get(i), npc != null, ws != null, ws == null ? "?" : ws.getDefaultPlayerAttitude(),
                                npc != null && tracker.isHostile(npc.getRoleName(), ws, Set.of(), Set.of()), being != null));
                    }
                    aLast = pos(store, a);
                    say("baseline: zombie A at " + aLast + " multiplier " + f(multiplier(store, a)));
                    step = 2;
                    t = 0;
                }
                case 2 -> { // a mob gains aggro as it moves on sand
                    Vector3d p = pos(store, a);
                    if (p != null && aLast != null) {
                        aMoved += Math.hypot(p.x - aLast.x, p.z - aLast.z);
                    }
                    aLast = p;
                    BeingAggro watched = core.being(a);
                    if (watched != null) {
                        maxSpeed = Math.max(maxSpeed, watched.speed);
                        gained = Math.max(gained, watched.score());
                    }
                    if (t < 4) {
                        return true;
                    }
                    say(String.format(Locale.ROOT, "zombie A walked %.1f blocks in 4 s (onSand=%s); score %.2f (highest seen %.2f), feeding %s, "
                                    + "fastest tick %.2f blocks/s (MinGainSpeed %.2f); a quarter of the distance moved would be %.2f",
                            aMoved, watched != null && watched.onSand, watched == null ? -1 : watched.score(), gained,
                            watched == null ? "-" : watched.feeding(), maxSpeed, cfg.getMinGainSpeed(), aMoved * 0.25));
                    step = 3;
                }
                case 3 -> { // stumble
                    if (begin()) {
                        a = spawn(store, "Zombie", 30, 0);
                        stumbled = false;
                        return true;
                    }
                    if (!here(a)) {
                        say("stumble: A is not in the world");
                        step = 4;
                        return true;
                    }
                    if (!stumbled && t >= dt) {
                        stumbled = true;
                        say("stumble applied to A, multiplier before " + f(multiplier(store, a)));
                        fx.stumble(world, store, a, r(a));
                    }
                    if (stumbled && ((int) (t * 10)) != (int) ((t - dt) * 10)) {
                        say(String.format(Locale.ROOT, "  stumble t=%.1f multiplier %s", t, f(multiplier(store, a))));
                    }
                    if (t < 1.0) {
                        return true;
                    }
                    step = 4;
                }
                case 4 -> { // hold: six held zombies against six free ones, how far each group walks in 0.9 s
                    if (begin()) {
                        held.clear();
                        free.clear();
                        for (int k = 0; k < 10; k++) {
                            UUID h = spawn(store, "Zombie", 20 + k * 2, -6);
                            UUID f2 = spawn(store, "Zombie", 20 + k * 2, 6);
                            if (h != null) {
                                held.add(h);
                            }
                            if (f2 != null) {
                                free.add(f2);
                            }
                        }
                        return true;
                    }
                    if (t < dt * 2) {
                        startPos.clear();
                        for (UUID h : held) {
                            BreachEntities.hold(store, r(h), 3f);
                            startPos.put(h, pos(store, h));
                        }
                        for (UUID f2 : free) {
                            startPos.put(f2, pos(store, f2));
                        }
                        say("hold applied to " + held.size() + " zombies; speed multiplier of one: " + (held.isEmpty() ? "-" : f(multiplier(store, held.get(0)))));
                        return true;
                    }
                    if (t < 3.0) {
                        return true;
                    }
                    say(String.format(Locale.ROOT, "hold: in 3 s the held group walked %.2f blocks on average, the free group %.2f (%d and %d in the world)",
                            walked(store, held), walked(store, free), count(held), count(free)));
                    for (UUID h : held) {
                        if (here(h)) {
                            BreachEntities.unhold(store, r(h));
                        }
                    }
                    step = 5;
                }
                case 5 -> { // the sink on B: hold, set the position a little lower each tick, then remove
                    if (begin()) {
                        b = spawn(store, "Zombie", 34, 0);
                        entities = null;
                        return true;
                    }
                    if (!here(b)) {
                        say("sink: B is not in the world");
                        step = 6;
                        return true;
                    }
                    if (entities == null && t < 0.4) {
                        return true;
                    }
                    if (entities == null) {
                        entities = new BreachEntities(core);
                        Vector3d p = pos(store, b);
                        say("B tracked as a being: " + (core.being(b) != null));
                        entities.capture(world, store, p, 2, b);
                        say("captured " + entities.size() + " (B)");
                        entities.startSink(store);
                        sinkSurfaceY = p.y;
                        sinkTick = 0;
                        return true;
                    }
                    sinkTick++;
                    double q = Math.min(1, sinkTick / 15.0);
                    entities.sinkTo(world, store, 3, q);
                    Vector3d p = pos(store, b);
                    if (sinkTick == 1 || sinkTick == 8 || sinkTick == 15 || sinkTick == 25 || sinkTick == 40) {
                        say(String.format(Locale.ROOT, "sink tick %d: q=%.2f B y=%.2f (surface %.2f, target %.2f)", sinkTick, q,
                                p == null ? Double.NaN : p.y, sinkSurfaceY, sinkSurfaceY - 3 * q * q));
                    }
                    if (sinkTick < 40) {
                        return true;
                    }
                    entities.kill(world, store, cfg);
                    say("B after kill (DevourMobDrops=" + cfg.isDevourMobDrops() + "): still in the world=" + here(b));
                    step = 6;
                }
                case 6 -> { // damage path on six zombies: does the death drop anything?
                    if (begin()) {
                        damaged.clear();
                        for (int k = 0; k < 6; k++) {
                            UUID id = spawn(store, "Zombie", 30 + k * 2, 8);
                            if (id != null) {
                                damaged.add(id);
                            }
                        }
                        itemsBefore = -1;
                        return true;
                    }
                    if (t < dt * 3) {
                        return true;
                    }
                    if (itemsBefore < 0) {
                        itemsBefore = items(store);
                        for (UUID id : damaged) {
                            if (here(id)) {
                                WormDevour.damageMob(store, cfg, r(id));
                            }
                        }
                        say("six zombies hit with the lethal damage; items in the world before: " + itemsBefore);
                        return true;
                    }
                    if (t < 4.0) {
                        return true;
                    }
                    long dead = damaged.stream().filter(id -> dead(store, id)).count();
                    say("3.9 s later: " + count(damaged) + " of 6 still in the world, " + dead + " with a DeathComponent; items in the world now: "
                            + items(store));
                    step = 7;
                }
                case 7 -> { // cost with 300 NPCs, first near a player, then with none
                    if (begin()) {
                        crowd.clear();
                        for (int i = 0; i < 300; i++) {
                            double angle = i * 0.37;
                            double radius = 12 + (i % 10) * 3;
                            UUID id = spawn(store, "Zombie", Math.cos(angle) * radius, Math.sin(angle) * radius);
                            if (id != null) {
                                crowd.add(id);
                            }
                        }
                        say("spawned a crowd of " + crowd.size() + " zombies");
                        costSum = 0;
                        costTicks = 0;
                        tracker.maxMillis = 0;
                        crowdNear = false;
                        return true;
                    }
                    if (!crowdNear && t > 0.1 && t < 0.6) {
                        costSum += tracker.millis;
                        costTicks++;
                    }
                    if (t >= 0.6 && !crowdNear) {
                        crowdNear = true;
                        say(String.format(Locale.ROOT, "tracking cost with a crowd near a player: scanned %d NPCs, %d hostile and near, %d tracked, "
                                + "%.3f ms per tick on average over %d ticks (worst %.3f)", tracker.scanned, tracker.hostile, tracker.tracked,
                                costSum / Math.max(costTicks, 1), costTicks, tracker.maxMillis));
                        tracker.anchors = List.of();
                        costSum = 0;
                        costTicks = 0;
                    }
                    if (crowdNear && t >= 0.8 && t < 1.3) {
                        costSum += tracker.millis;
                        costTicks++;
                    }
                    if (t < 1.3) {
                        return true;
                    }
                    say(String.format(Locale.ROOT, "same crowd, no player near: scanned %d, tracked %d, %.3f ms per tick on average over %d ticks",
                            tracker.scanned, tracker.tracked, costSum / Math.max(costTicks, 1), costTicks));
                    for (UUID id : crowd) {
                        if (here(id)) {
                            store.removeEntity(r(id), com.hypixel.hytale.component.RemoveReason.REMOVE);
                        }
                    }
                    crowd.clear();
                    tracker.anchors = List.of(new Vector3d(spot));
                    step = 9;
                }
                case 9 -> { // a vanilla Slowness Totem lands: a whole totem-triggered event with mobs in the zone
                    if (!totemDone) {
                        DeployableConfig dc = totemConfig("Projectile_Config_Slowness_Totem_Deploy");
                        if (dc == null) {
                            say("could not find the Slowness Totem deployable config");
                            return false;
                        }
                        UUID totemOwner = spawn(store, "Zombie", 40, 40);
                        Ref<EntityStore> ownerRef = r(totemOwner);
                        Ref<EntityStore>[] made = new Ref[1];
                        store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
                            if (made[0] == null) {
                                made[0] = DeployablesUtils.spawnDeployable(buffer, store, dc, ownerRef,
                                        new Vector3d(spot.x, spot.y + 0.2, spot.z), new Rotation3f(0, 0, 0), null);
                            }
                        });
                        totemDone = true;
                        say("totem spawned: config id " + dc.getId() + ", live " + dc.getLiveDuration() + " s");
                        t = 0;
                        return true;
                    }
                    if (t >= 13 && t < 21 && ((int) (t * 2)) != (int) ((t - dt) * 2)) {
                        for (int i = 0; i < 2; i++) {
                            UUID z = spawn(store, "Zombie", 3 + i * 2, 3);
                            if (z != null) {
                                zone.add(z);
                            }
                        }
                    }
                    if (((int) (t / 2)) != (int) ((t - dt) / 2)) {
                        say(String.format(Locale.ROOT, "t=%.0f events=%d totems landed=%d totem beings=%d zombies in the world=%d of %d spawned for the zone", t,
                                WormEvents.get().all().size(), manager.totems().landed(),
                                core.all().stream().filter(x -> x.kind == BeingKind.TOTEM).count(), count(zone), zone.size()));
                    }
                    if (t < 30 || (!WormEvents.get().all().isEmpty() && t < 40)) {
                        return true;
                    }
                    say("end of the totem run: events live " + WormEvents.get().all().size());
                    step = 10;
                    return true;
                }
                case 10 -> { // a mob triggers an event; a totem thrown by a listed mob joins it; the breach takes mob and totem
                    if (begin()) {
                        zone.clear();
                        for (int i = 0; i < 3; i++) {
                            UUID z = spawn(store, "Zombie", 4 + i * 3, -4);
                            if (z != null) {
                                zone.add(z);
                            }
                        }
                        return true;
                    }
                    if (t < 0.5) {
                        return true;
                    }
                    if (!triggered) {
                        triggered = true;
                        BeingAggro z1 = core.being(zone.get(0));
                        say("mob z1 tracked as a being: " + (z1 != null) + " on sand: " + (z1 != null && z1.onSand));
                        WormEvent e = core.startEvent(z1, true, false, Double.NaN, Double.NaN);
                        say("mob-triggered event #" + e.getId() + ": stalk " + e.getStalkSeconds() + " s, guaranteed " + e.isGuaranteed()
                                + ", list size " + e.getList().size() + ", target is the mob: " + z1.id.equals(e.getTarget()));
                        return true;
                    }
                    if (!thrown && t >= 3.5) {
                        thrown = true;
                        DeployableConfig dc = totemConfig("Projectile_Config_Healing_Totem_Deploy");
                        Ref<EntityStore> ownerRef = r(zone.get(1));
                        Ref<EntityStore>[] made = new Ref[1];
                        store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
                            if (made[0] == null) {
                                Vector3d p = pos(store, zone.get(0));
                                made[0] = DeployablesUtils.spawnDeployable(buffer, store, dc, ownerRef,
                                        new Vector3d(p.x + 2, p.y + 0.2, p.z), new Rotation3f(0, 0, 0), null);
                            }
                        });
                        say("Healing_Totem thrown by a listed mob during the stalk, live " + dc.getLiveDuration() + " s");
                    }
                    if (((int) (t / 2)) != (int) ((t - dt) / 2)) {
                        say(String.format(Locale.ROOT, "t=%.0f events=%d totem beings=%d zombies of the three still in the world=%d", t,
                                WormEvents.get().all().size(), core.all().stream().filter(x -> x.kind == BeingKind.TOTEM).count(), count(zone)));
                    }
                    if (t < 24 || (!WormEvents.get().all().isEmpty() && t < 40)) {
                        return true;
                    }
                    say("end of the mob run: events live " + WormEvents.get().all().size() + ", zombies left " + count(zone) + " of 3");
                    return false;
                }
                default -> {
                    return false;
                }
            }
            return true;
        }

        static DeployableConfig totemConfig(String projectileId) {
            try {
                ProjectileConfig pc = ProjectileConfig.getAssetMap().getAsset(projectileId);
                String rootId = pc.getInteractions().get(InteractionType.ProjectileMiss);
                RootInteraction root = RootInteraction.getRootInteractionOrUnknown(rootId);
                for (String id : root.getInteractionIds()) {
                    Interaction i = Interaction.getInteractionOrUnknown(id);
                    if (i instanceof SpawnDeployableAtHitLocationInteraction s) {
                        Field field = SpawnDeployableAtHitLocationInteraction.class.getDeclaredField("config");
                        field.setAccessible(true);
                        return (DeployableConfig) field.get(s);
                    }
                }
            } catch (Throwable e) {
                say("totemConfig failed: " + e);
            }
            return null;
        }
    }
}
