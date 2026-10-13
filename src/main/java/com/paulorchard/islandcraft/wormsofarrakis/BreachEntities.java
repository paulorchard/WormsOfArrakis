package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.protocol.ChangeVelocityType;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.physics.component.Velocity;
import com.hypixel.hytale.server.core.modules.splitvelocity.VelocityConfig;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * The mobs and totems the worm catches in the swallow zone, beside the players that {@link BreachHold} catches. The zone
 * is decided once, when the worm appears: every hostile mob the aggro rules track and every totem within it, in the
 * event's list or not. A mob is held with the same effect as a player, pulled down the same way and killed (or removed, so
 * it drops nothing) at the bottom; a totem is not held or sunk and is removed at the bottom. Everything done is undone in
 * {@link #releaseAll}. Plain unlisted NPCs and animals are not beings and are never touched.
 */
final class BreachEntities {

    private static final float SECONDS = 12f;
    /**
     * The hold effect (movement and abilities disabled) does nothing to an NPC, whose motion is driven by its role, so a mob
     * is also given this effect: a horizontal speed multiplier of 0, which the NPC does honour.
     */
    static final String FREEZE = "Arrakis_Worm_Mob_Freeze";

    private static final class Held {
        final UUID id;
        final BeingKind kind;
        final Ref<EntityStore> ref;
        Vector3d surface;
        Rotation3f facing;
        boolean sunk;

        Held(UUID id, BeingKind kind, Ref<EntityStore> ref) {
            this.id = id;
            this.kind = kind;
            this.ref = ref;
        }
    }

    private final AggroCore core;
    private final Map<UUID, Held> held = new LinkedHashMap<>();

    BreachEntities(AggroCore core) {
        this.core = core;
    }

    int size() {
        return held.size();
    }

    boolean isHeld(UUID id) {
        return held.containsKey(id);
    }

    /** Holds every mob and totem within {@code radius} (horizontally) of the centre, and {@code always} wherever it is. */
    void capture(World world, Store<EntityStore> store, Vector3d centre, double radius, UUID always) {
        if (core == null) {
            return;
        }
        for (BeingAggro b : core.all()) {
            Ref<EntityStore> ref = b.entity;
            if (b.kind == BeingKind.PLAYER || ref == null || !ref.isValid() || !world.getName().equals(b.world)) {
                continue;
            }
            if (b.kind == BeingKind.MOB && BreachHold.isDead(store, ref)) {
                continue;
            }
            TransformComponent tc = store.getComponent(ref, TransformComponent.getComponentType());
            if (tc == null) {
                continue;
            }
            Vector3d at = tc.getPosition();
            boolean inZone = Math.hypot(at.x - centre.x, at.z - centre.z) <= radius;
            if (!inZone && !b.id.equals(always)) {
                continue;
            }
            held.put(b.id, new Held(b.id, b.kind, ref));
            if (b.kind == BeingKind.MOB) {
                hold(store, ref, SECONDS);
            }
        }
        int mobs = 0;
        for (Held h : held.values()) {
            if (h.kind == BeingKind.MOB) {
                mobs++;
            }
        }
        if (!held.isEmpty()) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.INFO).log("%s", "[Worm] breach zone caught " + mobs + " mob(s) and "
                    + (held.size() - mobs) + " totem(s)");
        }
    }

    /** Drops any mob or totem that has gone (despawned, died, left the loaded chunks) since the zone was decided. */
    void tick(Store<EntityStore> store) {
        for (Held h : new ArrayList<>(held.values())) {
            if (!h.ref.isValid() || (h.kind == BeingKind.MOB && BreachHold.isDead(store, h.ref))) {
                held.remove(h.id);
            }
        }
    }

    /** The sink begins: remember where each mob stands on the surface. */
    void startSink(Store<EntityStore> store) {
        for (Held h : held.values()) {
            if (h.kind != BeingKind.MOB || !h.ref.isValid()) {
                continue;
            }
            TransformComponent tc = store.getComponent(h.ref, TransformComponent.getComponentType());
            if (tc != null) {
                h.surface = new Vector3d(tc.getPosition());
                h.facing = new Rotation3f(tc.getRotation());
                h.sunk = true;
            }
        }
    }

    /** Puts every sunk mob at {@code fraction} of the way down, on the same curve as the players. */
    void sinkTo(World world, Store<EntityStore> store, double depth, double fraction) {
        double q = Math.max(0, Math.min(1, fraction));
        for (Held h : held.values()) {
            if (!h.sunk || !h.ref.isValid()) {
                continue;
            }
            moveTo(store, h, new Vector3d(h.surface.x, h.surface.y - depth * q * q, h.surface.z));
        }
    }

    private static void moveTo(Store<EntityStore> store, Held h, Vector3d to) {
        TransformComponent tc = store.getComponent(h.ref, TransformComponent.getComponentType());
        if (tc != null) {
            tc.setPosition(new Vector3d(to));
        }
        store.putComponent(h.ref, Teleport.getComponentType(), Teleport.createExact(to, new Rotation3f(h.facing)));
        Velocity velocity = store.getComponent(h.ref, Velocity.getComponentType());
        if (velocity != null) {
            velocity.addInstruction(new Vector3d(0, 0, 0), new VelocityConfig(), ChangeVelocityType.Set);
        }
    }

    /** The bottom: every mob is killed or removed (see {@link WormDevour}), every totem removed. */
    void kill(World world, Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        if (!held.isEmpty()) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.INFO).log("%s", "[Worm] the worm takes " + held.size() + " mob(s)/totem(s)");
        }
        for (Held h : new ArrayList<>(held.values())) {
            devour(store, cfg, h);
        }
    }

    /** DevourKills off: the mobs are back on the surface, released and hurt; totems are still removed. */
    void releaseAndHurt(World world, Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        for (Held h : new ArrayList<>(held.values())) {
            if (h.kind == BeingKind.MOB) {
                release(store, h, true);
            }
            devour(store, cfg, h);
        }
    }

    /** One caught being now, whatever the clock (the swallow with BreachHold off). */
    void killOne(Store<EntityStore> store, WormsOfArrakisConfig cfg, UUID id) {
        BeingAggro b = core == null ? null : core.being(id);
        if (b != null && b.entity != null && b.entity.isValid()) {
            WormDevour.apply(store, cfg, b.entity, b.kind);
        }
    }

    private void devour(Store<EntityStore> store, WormsOfArrakisConfig cfg, Held h) {
        held.remove(h.id);
        if (h.ref.isValid() && !(h.kind == BeingKind.MOB && BreachHold.isDead(store, h.ref))) {
            WormDevour.apply(store, cfg, h.ref, h.kind);
        }
        if (h.kind == BeingKind.MOB && h.ref.isValid() && cfg.isDevourKills()) {
            // A mob that is not dead after this (DevourMobDrops: the damage did not kill it) is not left held.
            release(store, h, false);
        }
        core.forget(h.id);
    }

    /** Puts everything back as it was: mobs on the surface, free to move. Totems are untouched. */
    void releaseAll(World world, Store<EntityStore> store) {
        for (Held h : new ArrayList<>(held.values())) {
            held.remove(h.id);
            if (h.kind == BeingKind.MOB) {
                release(store, h, true);
            }
        }
    }

    private void release(Store<EntityStore> store, Held h, boolean surface) {
        try {
            if (!h.ref.isValid()) {
                return;
            }
            if (surface && h.sunk && h.surface != null) {
                moveTo(store, h, h.surface);
            }
            h.sunk = false;
            unhold(store, h.ref);
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not release a held mob");
        }
    }

    /** The hold effect on an entity for this long (movement and abilities disabled). */
    static void hold(Store<EntityStore> store, Ref<EntityStore> ref, float seconds) {
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(BreachHold.HOLD);
        if (effects != null && effect != null) {
            effects.addEffect(ref, effect, seconds, OverlapBehavior.OVERWRITE, store);
        }
        EntityEffect freeze = EntityEffect.getAssetMap().getAsset(FREEZE);
        if (effects != null && freeze != null) {
            effects.addEffect(ref, freeze, seconds, OverlapBehavior.OVERWRITE, store);
        }
    }

    static void unhold(Store<EntityStore> store, Ref<EntityStore> ref) {
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(BreachHold.HOLD);
        if (effects != null && index != Integer.MIN_VALUE) {
            effects.removeEffect(ref, index, store);
        }
        int freeze = EntityEffect.getAssetMap().getIndex(FREEZE);
        if (effects != null && freeze != Integer.MIN_VALUE) {
            effects.removeEffect(ref, freeze, store);
        }
    }
}
