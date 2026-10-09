package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Everyone the worm has caught in the swallow zone: held in place from the moment the worm appears, pulled down into
 * the sand as the worm goes back under, and killed (or, with DevourKills off, brought back up and released) at the
 * bottom. The zone is decided once, when the worm appears, so someone who runs in later is not caught. Everything
 * applied is undone in {@link #release}, which runs when the breach ends for any reason, and a sunk player is put
 * back on the surface first.
 *
 * <p>Hold: the entity effect Arrakis_Worm_Hold (movement and abilities disabled). Sink: a Teleport to a position a
 * little lower every tick, with the velocity zeroed.
 */
final class BreachHold {

    static final String HOLD = "Arrakis_Worm_Hold";
    /** The effect lasts longer than any breach; it is removed explicitly. */
    private static final float SECONDS = 12f;

    /** Where each sunk player stands on the surface, by player, so that leaving the world mid-sink saves them there. */
    private static final Map<UUID, Transform> SUNK = new ConcurrentHashMap<>();

    /** What the worm does to someone it has caught. */
    interface Devour {
        void devour(Store<EntityStore> store, Ref<EntityStore> ref, UUID id);
    }

    private static final class Held {
        final PlayerRef player;
        /** Surface position and facing where the sink began; null until then. */
        Vector3d surface;
        Rotation3f facing;
        boolean sunk;

        Held(PlayerRef player) {
            this.player = player;
        }
    }

    private final Map<UUID, Held> held = new LinkedHashMap<>();

    /** The transform to save for a player who leaves the world while sunk, or null if they are not sunk. */
    static Transform surfaceTransform(UUID player) {
        return SUNK.get(player);
    }

    static Set<UUID> sunkPlayers() {
        return Collections.unmodifiableSet(SUNK.keySet());
    }

    int size() {
        return held.size();
    }

    /** Holds every player within {@code radius} (horizontally) of the centre, and the victim wherever they are. */
    void capture(World world, Store<EntityStore> store, Vector3d centre, double radius, PlayerRef victim) {
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> ref = p.getReference();
            if (ref == null || !ref.isValid() || isDead(store, ref)) {
                continue;
            }
            boolean isVictim = victim != null && p.getUuid().equals(victim.getUuid());
            if (!isVictim) {
                if (isCreative(store, ref)) {
                    continue;
                }
                Vector3d at = WormEffects.position(store, p);
                if (at == null || Math.hypot(at.x - centre.x, at.z - centre.z) > radius) {
                    continue;
                }
            }
            held.put(p.getUuid(), new Held(p));
            addEffect(store, ref, HOLD);
        }
    }

    /** Drops anyone who has left, died or changed world, undoing what was done to them. */
    void tick(World world, Store<EntityStore> store) {
        for (Held h : new ArrayList<>(held.values())) {
            Ref<EntityStore> ref = h.player.getReference();
            boolean gone = ref == null || !ref.isValid() || isDead(store, ref)
                    || Universe.get().getWorld(h.player.getWorldUuid()) != world;
            if (gone) {
                release(world, store, h, false);
            }
        }
    }

    /** The sink begins: remember where everyone stands on the surface. Returns their positions for the effects. */
    java.util.List<Vector3d> startSink(Store<EntityStore> store) {
        java.util.List<Vector3d> where = new ArrayList<>();
        for (Held h : held.values()) {
            Ref<EntityStore> ref = h.player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
            if (tc == null) {
                continue;
            }
            h.surface = new Vector3d(tc.getPosition());
            h.facing = head == null ? new Rotation3f(tc.getRotation()) : new Rotation3f(head.getRotation());
            h.sunk = true;
            SUNK.put(h.player.getUuid(), new Transform(h.surface, h.facing));
            where.add(h.surface);
        }
        return where;
    }

    /** A ring of dust where each sunk player goes under; at the bottom, sand falls in after them. */
    void bursts(Store<EntityStore> store, WormsOfArrakisConfig cfg, java.util.List<Ref<EntityStore>> refs, boolean start) {
        float far = (float) cfg.getGroupRadius() + 60f;
        float scale = (float) cfg.getBreachDustScale() * 0.5f;
        for (PlayerRef p : sunkPlayersNow()) {
            Vector3d at = surfaceOf(p);
            if (at == null) {
                continue;
            }
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI * 2 / 6;
                double x = at.x + Math.cos(a) * 1.5;
                double z = at.z + Math.sin(a) * 1.5;
                WormEffects.particle(store, "Block_Break_Dust", x, at.y + 0.4, z, scale, far, refs);
                WormEffects.particle(store, "Block_Land_Hard_Dust", x, at.y + 0.3, z, scale * 0.8f, far, refs);
            }
            if (!start) {
                WormEffects.particle(store, "Block_Break_Sand", at.x, at.y + 1.0, at.z, scale * 1.5f, far, refs);
            }
        }
    }

    /** Where everyone sunk stands on the surface (for the camera and the dust). */
    java.util.List<PlayerRef> sunkPlayersNow() {
        java.util.List<PlayerRef> list = new ArrayList<>();
        for (Held h : held.values()) {
            if (h.sunk) {
                list.add(h.player);
            }
        }
        return list;
    }

    Vector3d surfaceOf(PlayerRef player) {
        Held h = held.get(player.getUuid());
        return h == null || h.surface == null ? null : new Vector3d(h.surface);
    }

    /**
     * Puts everyone sunk at {@code fraction} (0 on the surface, 1 at the bottom) of the way down: straight down from
     * where they stood, facing unchanged, with their velocity zeroed. The pull speeds up as it goes.
     */
    void sinkTo(World world, Store<EntityStore> store, double depth, double fraction) {
        double q = Math.max(0, Math.min(1, fraction));
        double drop = depth * q * q;
        for (Held h : held.values()) {
            if (!h.sunk) {
                continue;
            }
            Ref<EntityStore> ref = h.player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            Vector3d to = new Vector3d(h.surface.x, h.surface.y - drop, h.surface.z);
            moveTo(world, store, ref, to, h.facing);
        }
    }

    private static void moveTo(World world, Store<EntityStore> store, Ref<EntityStore> ref, Vector3d to, Rotation3f facing) {
        store.putComponent(ref, Teleport.getComponentType(), Teleport.createForPlayer(world, to, new Rotation3f(facing)));
        Velocity velocity = store.getComponent(ref, Velocity.getComponentType());
        if (velocity != null) {
            velocity.addInstruction(new Vector3d(0, 0, 0), new VelocityConfig(), ChangeVelocityType.Set);
        }
    }

    /** The bottom: kill everyone held (clearing what they carry first, in the shared devour method). */
    void kill(World world, Store<EntityStore> store, Devour devour) {
        for (Held h : new ArrayList<>(held.values())) {
            Ref<EntityStore> ref = h.player.getReference();
            if (ref != null && ref.isValid() && !isDead(store, ref)) {
                devour.devour(store, ref, h.player.getUuid());
            }
            release(world, store, h, false);
        }
    }

    /** DevourKills off: everyone is back on the surface, released, and hurt. */
    void releaseAndHurt(World world, Store<EntityStore> store, Devour devour) {
        for (Held h : new ArrayList<>(held.values())) {
            Ref<EntityStore> ref = h.player.getReference();
            release(world, store, h, true);
            if (ref != null && ref.isValid() && !isDead(store, ref)) {
                devour.devour(store, ref, h.player.getUuid());
            }
        }
    }

    /** Puts everyone back as they were: on the surface, free to move, effects gone. */
    void releaseAll(World world, Store<EntityStore> store) {
        for (Held h : new ArrayList<>(held.values())) {
            release(world, store, h, true);
        }
    }

    /** @param surface put a sunk player back on the surface first */
    private void release(World world, Store<EntityStore> store, Held h, boolean surface) {
        held.remove(h.player.getUuid());
        SUNK.remove(h.player.getUuid());
        try {
            // The player may be in another world by now; use the store of the world they are in.
            Store<EntityStore> theirs = store;
            World w = Universe.get().getWorld(h.player.getWorldUuid());
            if (w != null) {
                theirs = w.getEntityStore().getStore();
            }
            Ref<EntityStore> ref = h.player.getReference();
            if (ref != null && ref.isValid()) {
                if (surface && h.sunk && h.surface != null && w == world) {
                    TransformComponent tc = theirs.getComponent(ref, TransformComponent.getComponentType());
                    if (tc != null) {
                        tc.setPosition(new Vector3d(h.surface));
                    }
                    moveTo(world, theirs, ref, h.surface, h.facing);
                }
                EffectControllerComponent effects = theirs.getComponent(ref, EffectControllerComponent.getComponentType());
                if (effects != null) {
                    removeEffect(theirs, ref, effects, HOLD);
                }
            }
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not release a held player");
        }
        h.sunk = false;
    }

    private static void addEffect(Store<EntityStore> store, Ref<EntityStore> ref, String id) {
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
        if (effects != null && effect != null) {
            effects.addEffect(ref, effect, SECONDS, OverlapBehavior.OVERWRITE, store);
        }
    }

    private static void removeEffect(Store<EntityStore> store, Ref<EntityStore> ref, EffectControllerComponent effects, String id) {
        int index = EntityEffect.getAssetMap().getIndex(id);
        if (index != Integer.MIN_VALUE) {
            effects.removeEffect(ref, index, store);
        }
    }

    static boolean isDead(Store<EntityStore> store, Ref<EntityStore> ref) {
        return store.getComponent(ref, DeathComponent.getComponentType()) != null;
    }

    static boolean isCreative(Store<EntityStore> store, Ref<EntityStore> ref) {
        Player player = store.getComponent(ref, Player.getComponentType());
        return player != null && player.getGameMode() == GameMode.Creative;
    }
}
