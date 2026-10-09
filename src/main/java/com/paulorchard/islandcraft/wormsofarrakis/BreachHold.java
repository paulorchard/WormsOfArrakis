package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Everyone the worm has caught in the swallow zone: held in place from the moment the worm appears, made invisible
 * as it swallows them, and killed (or released) only when the worm is back under the sand. The zone is decided once,
 * when the worm appears, so someone who runs in later is not caught. Everything applied is undone in
 * {@link #release}, which runs when the breach ends for any reason.
 *
 * <p>Hold: the entity effect Arrakis_Worm_Hold (movement and abilities disabled). Hide: the entity effect
 * Arrakis_Worm_Hide, a model override to an invisible model, plus HiddenPlayersManager on every other player.
 */
final class BreachHold {

    static final String HOLD = "Arrakis_Worm_Hold";
    static final String HIDE = "Arrakis_Worm_Hide";
    /** The effects last longer than any breach; they are removed explicitly. */
    private static final float SECONDS = 12f;

    /** What the worm does to someone it has caught. */
    interface Devour {
        void devour(Store<EntityStore> store, Ref<EntityStore> ref, UUID id);
    }

    private static final class Held {
        final PlayerRef player;
        boolean hidden;

        Held(PlayerRef player) {
            this.player = player;
        }
    }

    private final Map<UUID, Held> held = new LinkedHashMap<>();

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
                release(world, store, h);
            }
        }
    }

    /** Everyone held turns invisible, to others and (if the model swap reaches it) to themselves. */
    void hide(World world, Store<EntityStore> store) {
        for (Held h : held.values()) {
            Ref<EntityStore> ref = h.player.getReference();
            if (ref == null || !ref.isValid()) {
                continue;
            }
            addEffect(store, ref, HIDE);
            for (PlayerRef other : Universe.get().getPlayers()) {
                if (!other.getUuid().equals(h.player.getUuid())) {
                    other.getHiddenPlayersManager().hidePlayer(h.player.getUuid());
                }
            }
            h.hidden = true;
        }
    }

    /** The worm is under the sand: kill everyone held (or, with DevourKills off, let them go and hurt them). */
    void finish(World world, Store<EntityStore> store, boolean kills, Devour devour) {
        for (Held h : new ArrayList<>(held.values())) {
            Ref<EntityStore> ref = h.player.getReference();
            if (ref != null && ref.isValid() && !isDead(store, ref)) {
                if (kills) {
                    devour.devour(store, ref, h.player.getUuid());
                    release(world, store, h);
                } else {
                    release(world, store, h);
                    devour.devour(store, ref, h.player.getUuid());
                }
            } else {
                release(world, store, h);
            }
        }
    }

    /** Puts everyone back as they were: free to move, visible, effects gone. */
    void releaseAll(World world, Store<EntityStore> store) {
        for (Held h : new ArrayList<>(held.values())) {
            release(world, store, h);
        }
    }

    private void release(World world, Store<EntityStore> store, Held h) {
        held.remove(h.player.getUuid());
        try {
            // The player may be in another world by now; use the store of the world they are in.
            Store<EntityStore> theirs = store;
            World w = Universe.get().getWorld(h.player.getWorldUuid());
            if (w != null) {
                theirs = w.getEntityStore().getStore();
            }
            Ref<EntityStore> ref = h.player.getReference();
            if (ref != null && ref.isValid()) {
                EffectControllerComponent effects = theirs.getComponent(ref, EffectControllerComponent.getComponentType());
                if (effects != null) {
                    removeEffect(theirs, ref, effects, HOLD);
                    removeEffect(theirs, ref, effects, HIDE);
                }
            }
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not release a held player");
        }
        if (h.hidden) {
            for (PlayerRef other : Universe.get().getPlayers()) {
                other.getHiddenPlayersManager().showPlayer(h.player.getUuid());
            }
            h.hidden = false;
        }
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
