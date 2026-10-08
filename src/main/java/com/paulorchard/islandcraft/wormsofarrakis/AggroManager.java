package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Tracks every player's aggro and decides when a worm event starts. Runs from the world tick: each tick it reads
 * every player's position and movement flags, adds or decays their aggro, every quarter second looks for groups
 * whose total has reached the threshold, and then runs the live events.
 */
public final class AggroManager {

    /** How often groups are looked for, in seconds. */
    static final double GROUP_CHECK_INTERVAL = 0.25;
    /** Faster than this between two ticks is a teleport, not walking. */
    static final double TELEPORT_SPEED = 40.0;

    private final Supplier<WormsOfArrakisConfig> config;
    private final Supplier<Set<Integer>> sand;
    private final WormEvents events = WormEvents.get();
    private final Map<UUID, PlayerAggro> players = new ConcurrentHashMap<>();
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    private final List<AggroSource> sources = new CopyOnWriteArrayList<>();
    private final Map<String, Double> sinceCheck = new ConcurrentHashMap<>();
    private final AtomicLong gainCounter = new AtomicLong();
    private double checkClock;

    AggroManager(Supplier<WormsOfArrakisConfig> config, Supplier<Set<Integer>> sand) {
        this.config = config;
        this.sand = sand;
        // Built-in sources, through the same registry other mods use. The first match wins, so jumping outranks running.
        registerSource(new AggroSource("jump", () -> config.get().getJumpAggroPerBlock(), AggroContext::isAirborneAfterJump));
        registerSource(new AggroSource("run", () -> config.get().getRunAggroPerBlock(),
                c -> c.getStates().running || c.getStates().sprinting));
        registerSource(new AggroSource("walk", () -> config.get().getWalkAggroPerBlock(), c -> c.getStates().walking));
    }

    // ------------------------------------------------------------------ public surface (see WormAggro)

    void registerSource(AggroSource source) {
        unregisterSource(source.getId());
        sources.add(source);
    }

    boolean unregisterSource(String id) {
        return sources.removeIf(s -> s.getId().equals(id));
    }

    List<AggroSource> sources() {
        return new ArrayList<>(sources);
    }

    PlayerAggro state(UUID id) {
        return players.get(id);
    }

    PlayerAggro stateOrCreate(PlayerRef ref) {
        return players.computeIfAbsent(ref.getUuid(), k -> {
            PlayerAggro a = new PlayerAggro();
            a.ref = ref;
            return a;
        });
    }

    void addAggro(PlayerRef ref, double amount, String sourceId) {
        PlayerAggro a = stateOrCreate(ref);
        a.add(amount, sourceId);
        if (amount > 0) {
            a.lastGain = gainCounter.incrementAndGet();
        }
    }

    boolean toggleIgnore(UUID id) {
        if (!ignored.remove(id)) {
            ignored.add(id);
            return true;
        }
        return false;
    }

    boolean isIgnored(UUID id) {
        return ignored.contains(id);
    }

    void forget(UUID id) {
        players.remove(id);
    }

    // ------------------------------------------------------------------ tick

    /** One world tick. Must run on the world thread. */
    void tick(World world, Store<EntityStore> store, float dt) {
        if (dt <= 0) {
            return;
        }
        WormsOfArrakisConfig cfg = config.get();
        Set<Integer> sandIds = sand.get();
        String name = world.getName();
        // Anyone of this world we do not see this tick is not valid until we do.
        for (PlayerAggro a : players.values()) {
            if (name.equals(a.world)) {
                a.eligible = false;
            }
        }
        for (PlayerRef pr : world.getPlayerRefs()) {
            updatePlayer(world, store, pr, dt, cfg, sandIds);
        }
        checkClock += dt;
        if (checkClock >= GROUP_CHECK_INTERVAL) {
            checkClock = 0;
            checkGroups(world, store, cfg);
        }
        events.tick(name, dt, env(name));
    }

    private void updatePlayer(World world, Store<EntityStore> store, PlayerRef pr, float dt,
                              WormsOfArrakisConfig cfg, Set<Integer> sandIds) {
        Ref<EntityStore> ref = pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        PlayerAggro a = stateOrCreate(pr);
        a.ref = pr;
        a.world = world.getName();
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        MovementStatesComponent statesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (transform == null || statesComponent == null) {
            return;
        }
        Player player = store.getComponent(ref, Player.getComponentType());
        boolean dead = store.getComponent(ref, DeathComponent.getComponentType()) != null;
        boolean creative = player != null && player.getGameMode() == GameMode.Creative;
        a.eligible = !dead && !creative && !ignored.contains(pr.getUuid());
        Vector3d p = new Vector3d(transform.getPosition());
        a.position = p;

        double distance = a.hasLast ? Math.hypot(p.x - a.lastX, p.z - a.lastZ) : 0;
        a.hasLast = true;
        a.lastX = p.x;
        a.lastZ = p.z;
        double speed = distance / dt;
        a.speed = speed;
        if (speed > TELEPORT_SPEED) {
            distance = 0;
            speed = 0;
        }

        MovementStates s = statesComponent.getMovementStates();
        if (s.jumping) {
            a.airborneAfterJump = true;
        }
        if (s.onGround || s.inFluid || s.flying) {
            a.airborneAfterJump = false;
        }
        double tolerance = cfg.getSandTolerance() * (a.airborneAfterJump ? 3 : 1);
        a.onSand = a.eligible && onSand(world, p, tolerance, sandIds);

        boolean gained = false;
        if (a.eligible && a.onSand && speed >= cfg.getMinGainSpeed() && distance > 0) {
            AggroContext context = new AggroContext(pr, s, distance, speed, a.airborneAfterJump);
            for (AggroSource source : sources) {
                if (source.matches(context)) {
                    double amount = source.getRate() * distance;
                    if (amount > 0) {
                        a.add(amount, source.getId());
                        a.lastGain = gainCounter.incrementAndGet();
                        gained = true;
                    }
                    break;
                }
            }
        }
        if (!gained) {
            // Off sand the aggro drains much faster than when standing still on it.
            double rate = cfg.getStillDecayPerSecond() * (a.onSand ? 1.0 : cfg.getOffSandDecayMultiplier());
            a.setScore(a.score() - rate * dt);
        }
    }

    /**
     * True if the first non-empty block at or below the one under the feet (searching down by the tolerance, so
     * a player mid-step still counts) is a sand block.
     */
    static boolean onSand(World world, Vector3d p, double tolerance, Set<Integer> sandIds) {
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return false;
        }
        int top = (int) Math.floor(p.y - 0.1);
        int bottom = top - (int) Math.ceil(tolerance);
        for (int y = top; y >= bottom && y >= ChunkUtil.MIN_Y; y--) {
            int id = chunk.getBlock(x, y, z);
            if (id != 0) {
                return sandIds.contains(id);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ groups and triggering

    /** True if the top block of the column is sand, false if not, null if the chunk is not loaded. */
    static Boolean surfaceIsSand(World world, double x, double z, Set<Integer> sandIds) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int top = chunk.getHeight(bx & ChunkUtil.SIZE_MASK, bz & ChunkUtil.SIZE_MASK);
        return sandIds.contains(chunk.getBlock(bx, top, bz));
    }

    /** Players of a world who can be in a group right now and are not already in an event. */
    private List<GroupFinder.Member> candidates(String worldName) {
        List<GroupFinder.Member> list = new ArrayList<>();
        for (Map.Entry<UUID, PlayerAggro> e : players.entrySet()) {
            PlayerAggro a = e.getValue();
            if (a.eligible && worldName.equals(a.world) && events.eventOf(e.getKey()) == null) {
                Vector3d p = a.position;
                list.add(new GroupFinder.Member(e.getKey(), p.x, p.z, a.score(), a.lastGain));
            }
        }
        return list;
    }

    /** The group a player would be counted in now, for display; null if the player is not a candidate. */
    List<GroupFinder.Member> groupOf(UUID player) {
        PlayerAggro a = players.get(player);
        if (a == null || a.world == null) {
            return null;
        }
        for (List<GroupFinder.Member> group : GroupFinder.find(candidates(a.world), config.get().getGroupRadius())) {
            for (GroupFinder.Member m : group) {
                if (m.id().equals(player)) {
                    return group;
                }
            }
        }
        return null;
    }

    private void checkGroups(World world, Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        for (List<GroupFinder.Member> group : GroupFinder.find(candidates(world.getName()), cfg.getGroupRadius())) {
            if (GroupFinder.total(group) >= cfg.getAggroThreshold()) {
                trigger(world.getName(), group, null, null, false);
            }
        }
    }

    /**
     * Starts an event for a group. {@code forcedTarget} (from /worm trigger) is used as the target even if the
     * threshold has not been reached; otherwise the target is the best-ranked player on sand.
     */
    WormEvent trigger(String worldName, List<GroupFinder.Member> group, UUID forcedTarget, WormEvent.Timings timings,
                      boolean forced) {
        WormsOfArrakisConfig cfg = config.get();
        List<GroupFinder.Member> ranked = new ArrayList<>(group);
        ranked.sort(GroupFinder.RANKING);
        List<UUID> ranking = new ArrayList<>();
        for (GroupFinder.Member m : ranked) {
            ranking.add(m.id());
        }
        UUID target = forcedTarget;
        if (target == null) {
            for (UUID id : ranking) {
                PlayerAggro a = players.get(id);
                if (a != null && a.onSand) {
                    target = id;
                    break;
                }
            }
        } else {
            ranking.remove(target);
            ranking.add(0, target);
        }
        Vector3d start = new Vector3d();
        if (target != null) {
            Vector3d tp = players.get(target).position;
            ThreadLocalRandom random = ThreadLocalRandom.current();
            double min = cfg.getWormStartMinDistance();
            World world = Universe.get().getWorld(worldName);
            // A random direction whose start is on sand; if that column is not loaded to check, the first try is used.
            for (int attempt = 0; attempt < 16; attempt++) {
                double angle = random.nextDouble(0, Math.PI * 2);
                double distance = min + random.nextDouble() * Math.max(0, cfg.getWormStartMaxDistance() - min);
                start.set(tp.x + Math.cos(angle) * distance, tp.y, tp.z + Math.sin(angle) * distance);
                Boolean sandy = world == null ? null : surfaceIsSand(world, start.x, start.z, sand.get());
                if (sandy == null || sandy) {
                    break; // sand, or not loaded so it cannot be checked
                }
            }
            if (!forced) {
                for (UUID id : ranking) {
                    PlayerAggro a = players.get(id);
                    if (a != null) {
                        a.setScore(a.score() * (1.0 - cfg.getResetFractionOnTrigger()));
                    }
                }
            }
        }
        WormEvent.Timings t = timings != null ? timings : defaultTimings(cfg);
        return events.start(worldName, ranking, target, start, t, forced);
    }

    WormEvent.Timings defaultTimings(WormsOfArrakisConfig cfg) {
        return new WormEvent.Timings(cfg.getStalkSeconds(), cfg.getLockSeconds(), cfg.getBreachSeconds(),
                cfg.getCooldownSeconds(), cfg.isRetargetKeepsClock(), cfg.getPathEaseExponent());
    }

    /** After a fizzle the group is still agitated: scale their scores so the total is a share of the threshold. */
    void agitate(WormEvent event) {
        WormsOfArrakisConfig cfg = config.get();
        double goal = cfg.getAggroThreshold() * cfg.getFizzleScoreFraction();
        double total = 0;
        for (UUID id : event.getGroup()) {
            PlayerAggro a = players.get(id);
            total += a == null ? 0 : a.score();
        }
        if (total <= 0) {
            return;
        }
        for (UUID id : event.getGroup()) {
            PlayerAggro a = players.get(id);
            if (a != null) {
                a.setScore(a.score() * goal / total);
            }
        }
    }

    /** The event's view of the players of one world. */
    WormEvent.Env env(String worldName) {
        return new WormEvent.Env() {
            @Override
            public boolean valid(UUID player) {
                PlayerAggro a = players.get(player);
                return a != null && a.eligible && worldName.equals(a.world);
            }

            @Override
            public boolean onSand(UUID player) {
                PlayerAggro a = players.get(player);
                return a != null && a.eligible && a.onSand && worldName.equals(a.world);
            }

            @Override
            public Vector3d position(UUID player) {
                PlayerAggro a = players.get(player);
                return a != null && a.eligible && worldName.equals(a.world) ? new Vector3d(a.position) : null;
            }

            @Override
            public double groundY(double x, double z) {
                World world = Universe.get().getWorld(worldName);
                WorldChunk chunk = world == null ? null : world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                if (chunk == null) {
                    return Double.NaN;
                }
                return chunk.getHeight((int) Math.floor(x) & ChunkUtil.SIZE_MASK, (int) Math.floor(z) & ChunkUtil.SIZE_MASK) + 1.0;
            }
        };
    }
}
