package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Reads the world into the aggro rules. Each world tick it reads every being of every kind (only players are read so
 * far; a later reader for mobs and totems feeds the same code path), hands the readings to {@link AggroCore}, starts the
 * events whose trigger has reached the threshold, and then runs the live events.
 */
public final class AggroManager {

    /** Faster than this between two ticks is a teleport, not walking. */
    static final double TELEPORT_SPEED = 40.0;

    private final Supplier<WormsOfArrakisConfig> config;
    private final Supplier<Set<Integer>> sand;
    private final WormEvents events = WormEvents.get();
    private final AggroCore core;
    private final Set<UUID> ignored = ConcurrentHashMap.newKeySet();
    private final TotemRegistry totemRegistry = new TotemRegistry();
    private final MobTracker mobs;
    private final TotemTracker totems;
    private final EffectMultipliers effects;

    AggroManager(Supplier<WormsOfArrakisConfig> config, Supplier<Set<Integer>> sand) {
        this.config = config;
        this.sand = sand;
        this.core = new AggroCore(this::rules, events);
        this.mobs = new MobTracker(core, config, this::effectFactor);
        this.totems = new TotemTracker(core, totemRegistry, config);
        this.effects = new EffectMultipliers(() -> config.get().getAggroEffectMultipliers(),
                () -> config.get().getAggroMultiplierMax(), id -> EntityEffect.getAssetMap().getIndex(id));
        // The one built-in source, through the same registry other mods use: flat per block for walking, running,
        // sprinting and jumping. Stamina spent is added by the core as the source "stamina".
        core.registerSource(new AggroSource("move", () -> config.get().getMovementAggroPerBlock(), c -> true));
    }

    AggroCore core() {
        return core;
    }

    MobTracker mobs() {
        return mobs;
    }

    /** The system that finds totems; the plugin registers it with the entity store. */
    TotemTracker totems() {
        return totems;
    }

    TotemRegistry totemRegistry() {
        return totemRegistry;
    }

    private AggroCore.Rules rules() {
        WormsOfArrakisConfig c = config.get();
        return new AggroCore.Rules(c.getAggroThreshold(), c.getGroupRadius(), c.getStillDecayPerSecond(),
                c.getOffSandDecayMultiplier(), c.getMinGainSpeed(), c.getStaminaAggroFactor(), c.getStalkBaseSeconds(),
                c.getStalkStepSeconds(), c.getStalkMinSeconds(), c.getEntityStalkSeconds(), c.getLockedSeconds(),
                c.getBreachSeconds(), c.getMobAggroMultiplier(), c.getTotemStartAggro(), c.getTotemAggroPerSecond());
    }

    // ------------------------------------------------------------------ public surface (see WormAggro)

    void registerSource(AggroSource source) {
        core.registerSource(source);
    }

    boolean unregisterSource(String id) {
        return core.unregisterSource(id);
    }

    List<AggroSource> sources() {
        return core.sources();
    }

    BeingAggro state(UUID id) {
        return core.being(id);
    }

    BeingAggro stateOrCreate(PlayerRef ref) {
        BeingAggro a = core.beingOrCreate(ref.getUuid(), BeingKind.PLAYER);
        a.ref = ref;
        return a;
    }

    void addAggro(PlayerRef ref, double amount, String sourceId) {
        core.add(stateOrCreate(ref), amount, sourceId);
    }

    boolean addAggro(UUID id, double amount, String sourceId) {
        return core.addAggro(id, amount, sourceId);
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

    /** A player logs off (see {@link AggroCore#logOff}). */
    void logOff(UUID id) {
        core.logOff(id);
    }

    /** A being that is gone for good. */
    void forget(UUID id) {
        core.forget(id);
    }

    // ------------------------------------------------------------------ tick

    /** One world tick. Must run on the world thread. */
    void tick(World world, Store<EntityStore> store, float dt) {
        if (dt <= 0) {
            return;
        }
        Set<Integer> sandIds = sand.get();
        String name = world.getName();
        // Anyone of this world we do not see this tick is not valid until we do.
        for (BeingAggro a : core.all()) {
            if (name.equals(a.world)) {
                a.eligible = false;
            }
        }
        for (PlayerRef pr : world.getPlayerRefs()) {
            updatePlayer(world, store, pr, dt, sandIds);
        }
        // Hostile mobs near a player, and the totems that have landed, are beings like the players.
        mobs.tick(world, store, dt, sandIds);
        totems.tick(world, store, dt, sandIds);
        // A mob not seen this tick (gone, dead, out of the loaded chunks or out of range) is dropped with its score,
        // unless an event has it in its list: the event then deals with it.
        core.dropUnseenMobs(name);
        core.checkTriggers(name);
        events.tick(name, dt, core.env(name));
    }

    /** The kind-specific reader for a player: position, movement flags and the stamina stat. */
    private void updatePlayer(World world, Store<EntityStore> store, PlayerRef pr, float dt, Set<Integer> sandIds) {
        Ref<EntityStore> ref = pr.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        BeingAggro a = stateOrCreate(pr);
        a.entity = ref;
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

        MovementStates s = statesComponent.getMovementStates();
        if (s.jumping) {
            a.airborneAfterJump = true;
        }
        if (s.onGround || s.inFluid || s.flying) {
            a.airborneAfterJump = false;
        }
        WormsOfArrakisConfig cfg = config.get();
        double tolerance = cfg.getSandTolerance() * (a.airborneAfterJump ? 3 : 1);
        a.onSand = a.eligible && onSand(world, p, tolerance, sandIds);

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
        AggroContext context = new AggroContext(pr, s, distance, speed, a.airborneAfterJump);
        EffectMultipliers.Result factor = effectFactor(store, ref);
        a.effectFactor = factor;
        core.apply(a, new AggroCore.Reading(a.eligible, a.onSand, distance, speed, stamina(store, ref), context, factor.factor()), dt);
    }

    /** The effect multipliers (see {@link EffectMultipliers}) of the effects this entity has right now. */
    EffectMultipliers.Result effectFactor(Store<EntityStore> store, Ref<EntityStore> ref) {
        if (effects.idle()) {
            return EffectMultipliers.Result.NONE;
        }
        EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
        return controller == null ? EffectMultipliers.Result.NONE : effects.evaluate(controller::hasEffect);
    }

    EffectMultipliers effects() {
        return effects;
    }

    /** The stamina stat, or NaN for a being that has none. */
    static double stamina(Store<EntityStore> store, Ref<EntityStore> ref) {
        EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
        EntityStatValue value = stats == null ? null : stats.get(DefaultEntityStatTypes.getStamina());
        return value == null ? AggroCore.Reading.NO_STAMINA : value.get();
    }

    /**
     * True if the first non-empty block at or below the one under the feet (searching down by the tolerance, so
     * a player mid-step still counts) is a sand block.
     */
    static boolean onSand(World world, Vector3d p, double tolerance, Set<Integer> sandIds) {
        int x = (int) Math.floor(p.x);
        int z = (int) Math.floor(p.z);
        WorldAccess access = new WorldAccess(world);
        if (!access.isTicking(x, z)) {
            return false;
        }
        int top = (int) Math.floor(p.y - 0.1);
        int bottom = top - (int) Math.ceil(tolerance);
        for (int y = top; y >= bottom && y >= ChunkUtil.MIN_Y; y--) {
            int id = access.block(x, y, z);
            if (id != 0) {
                return sandIds.contains(id);
            }
        }
        return false;
    }

    /** True if the top block of the column is sand, false if not, null if the chunk is not loaded. */
    static Boolean surfaceIsSand(World world, double x, double z, Set<Integer> sandIds) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        WorldAccess access = new WorldAccess(world);
        if (!access.isTicking(bx, bz)) {
            return null;
        }
        int top = access.height(bx, bz);
        return top != WorldAccess.NO_HEIGHT && sandIds.contains(access.block(bx, top, bz));
    }

    // ------------------------------------------------------------------ events by command

    /** The list a being would start now, for display only. */
    List<ListScan.Candidate> listOf(UUID id) {
        return core.previewList(id);
    }

    /** Starts a forced event with {@code being} as the trigger (the list is scanned as normal). */
    WormEvent trigger(BeingAggro being, boolean solo, double stalkOverride, double lockOverride) {
        return core.startEvent(being, true, solo, stalkOverride, lockOverride);
    }
}
