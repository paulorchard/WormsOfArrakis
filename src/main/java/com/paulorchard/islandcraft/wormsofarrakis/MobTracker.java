package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.attitude.Attitude;
import com.hypixel.hytale.server.core.entity.UUIDComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.entities.NPCEntity;
import com.hypixel.hytale.server.npc.role.support.WorldSupport;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * Reads the hostile NPCs of a world into the aggro rules. Once per world tick one query over {@link NPCEntity} (only the
 * chunks that are loaded hold any) picks the NPCs whose role regards a player as HOSTILE, and of those only the ones
 * within MobTrackRadius of a player (or already in an event) are given a being and fed to {@link AggroCore#apply} with
 * the movement rule and no stamina. An NPC that is not seen this tick is dropped by {@link AggroManager}, score and all.
 */
final class MobTracker {

    private final AggroCore core;
    private final Supplier<WormsOfArrakisConfig> config;
    /** The role's attitude to a player never changes, so it is looked up once per role name. */
    private final Map<String, Boolean> hostileByRole = new ConcurrentHashMap<>();

    /** The last tick's cost, for /worm status: NPCs in the world, hostile and near a player, and milliseconds. */
    /** Places that count as a player for the track radius: only the headless entity test sets any. */
    volatile List<Vector3d> anchors = List.of();
    volatile int scanned;
    volatile int hostile;
    volatile int tracked;
    volatile double millis;
    volatile double maxMillis;

    private final BiFunction<Store<EntityStore>, Ref<EntityStore>, EffectMultipliers.Result> effectReader;

    MobTracker(AggroCore core, Supplier<WormsOfArrakisConfig> config,
               BiFunction<Store<EntityStore>, Ref<EntityStore>, EffectMultipliers.Result> effectReader) {
        this.effectReader = effectReader;
        this.core = core;
        this.config = config;
    }

    /** A mob is tracked while it is within the radius (horizontally) of at least one player, or while it is in an event. */
    static boolean trackable(Vector3d p, List<Vector3d> players, double radius, boolean inEvent) {
        if (inEvent) {
            return true;
        }
        double r2 = radius * radius;
        for (Vector3d q : players) {
            double dx = q.x - p.x;
            double dz = q.z - p.z;
            if (dx * dx + dz * dz <= r2) {
                return true;
            }
        }
        return false;
    }

    /** Does this role count as a mob? The deny list first, then the allow list, then the engine's own attitude. */
    boolean isHostile(String role, WorldSupport support, Set<String> deny, Set<String> allow) {
        if (role == null) {
            return false;
        }
        if (deny.contains(role)) {
            return false;
        }
        if (allow.contains(role)) {
            return true;
        }
        return hostileByRole.computeIfAbsent(role, r -> support != null && support.getDefaultPlayerAttitude() == Attitude.HOSTILE);
    }

    void tick(World world, Store<EntityStore> store, double dt, Set<Integer> sandIds) {
        long start = System.nanoTime();
        WormsOfArrakisConfig cfg = config.get();
        double radius = cfg.getMobTrackRadius() > 0 ? cfg.getMobTrackRadius() : cfg.getGroupRadius();
        String name = world.getName();
        Set<String> deny = set(cfg.getMobRoleDeny());
        Set<String> allow = set(cfg.getMobRoleAllow());
        List<Vector3d> players = new ArrayList<>(anchors);
        for (BeingAggro b : core.all()) {
            if (b.kind == BeingKind.PLAYER && name.equals(b.world) && b.position != null) {
                players.add(b.position);
            }
        }
        int[] counts = new int[3];
        store.forEachChunk(NPCEntity.getComponentType(), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                counts[0]++;
                TransformComponent transform = chunk.getComponent(i, TransformComponent.getComponentType());
                UUIDComponent uuid = chunk.getComponent(i, UUIDComponent.getComponentType());
                NPCEntity npc = chunk.getComponent(i, NPCEntity.getComponentType());
                if (transform == null || uuid == null || npc == null) {
                    continue;
                }
                Vector3d p = transform.getPosition();
                BeingAggro known = core.being(uuid.getUuid());
                boolean near = trackable(p, players, radius, known != null && known.event != null);
                if (!near || !isHostile(npc.getRoleName(), chunk.getComponent(i, WorldSupport.getComponentType()), deny, allow)) {
                    continue;
                }
                counts[1]++;
                Ref<EntityStore> ref = chunk.getReferenceTo(i);
                boolean dead = chunk.getComponent(i, DeathComponent.getComponentType()) != null;
                read(world, store, ref, uuid.getUuid(), npc.getRoleName(), p, dead, dt, sandIds);
                counts[2]++;
            }
        });
        scanned = counts[0];
        hostile = counts[1];
        tracked = counts[2];
        millis = (System.nanoTime() - start) / 1e6;
        maxMillis = Math.max(maxMillis, millis);
    }

    private void read(World world, Store<EntityStore> store, Ref<EntityStore> ref, UUID id, String role, Vector3d p, boolean dead, double dt,
                      Set<Integer> sandIds) {
        BeingAggro a = core.beingOrCreate(id, BeingKind.MOB);
        a.entity = ref;
        a.label = role;
        a.world = world.getName();
        a.eligible = !dead;
        a.position = new Vector3d(p);
        a.onSand = a.eligible && AggroManager.onSand(world, p, config.get().getSandTolerance(), sandIds);
        double distance = a.hasLast ? Math.hypot(p.x - a.lastX, p.z - a.lastZ) : 0;
        a.hasLast = true;
        a.lastX = p.x;
        a.lastZ = p.z;
        double speed = distance / dt;
        a.speed = speed;
        if (speed > AggroManager.TELEPORT_SPEED) {
            distance = 0;
            speed = 0;
        }
        EffectMultipliers.Result factor = effectReader.apply(store, ref);
        a.effectFactor = factor;
        core.apply(a, new AggroCore.Reading(a.eligible, a.onSand, distance, speed, AggroCore.Reading.NO_STAMINA, null, factor.factor()), dt);
    }

    private static Set<String> set(String[] names) {
        Set<String> out = new HashSet<>();
        if (names != null) {
            for (String n : names) {
                out.add(n);
            }
        }
        return out;
    }
}
