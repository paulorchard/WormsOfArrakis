package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The stumble of the stalk pulse: a brief near-stop that makes the movement feel violent. It is the existing slow effect
 * {@code Arrakis_Worm_Slow_<percent>} added for StumbleSeconds and taken off again, on its own small clock, so it does not
 * share anything with {@link TargetFx}, whose slow ramp belongs to the target in LOCKED and removes only its own level.
 */
final class StumbleFx {

    private static final class Active {
        final String world;
        final Ref<EntityStore> ref;
        final String effect;
        double left;

        Active(String world, Ref<EntityStore> ref, String effect, double left) {
            this.world = world;
            this.ref = ref;
            this.effect = effect;
            this.left = left;
        }
    }

    private final Map<UUID, Active> active = new ConcurrentHashMap<>();

    /** The nearest Arrakis_Worm_Slow asset: 10 to 95 percent in steps of 5. */
    static String effectId(double percent) {
        int p = (int) Math.max(10, Math.min(95, Math.round(percent / 5.0) * 5));
        return "Arrakis_Worm_Slow_" + p;
    }

    /** Starts (or renews) a stumble. A being with no entity or no effect controller is skipped. */
    void stumble(World world, Store<EntityStore> store, UUID id, Ref<EntityStore> ref, double seconds, double percent) {
        if (ref == null || !ref.isValid() || seconds <= 0) {
            return;
        }
        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(effectId(percent));
        if (effects == null || effect == null) {
            return;
        }
        effects.addEffect(ref, effect, (float) seconds, OverlapBehavior.OVERWRITE, store);
        active.put(id, new Active(world.getName(), ref, effectId(percent), seconds));
    }

    /** Counts the stumbles of one world down and takes the effect off when its time is up. */
    void tick(String world, Store<EntityStore> store, double dt) {
        for (Iterator<Map.Entry<UUID, Active>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Active a = it.next().getValue();
            if (!a.world.equals(world)) {
                continue;
            }
            a.left -= dt;
            if (a.left <= 0 || !a.ref.isValid()) {
                remove(store, a);
                it.remove();
            }
        }
    }

    private static void remove(Store<EntityStore> store, Active a) {
        if (!a.ref.isValid()) {
            return;
        }
        EffectControllerComponent effects = store.getComponent(a.ref, EffectControllerComponent.getComponentType());
        int index = EntityEffect.getAssetMap().getIndex(a.effect);
        if (effects != null && index != Integer.MIN_VALUE) {
            effects.removeEffect(a.ref, index, store);
        }
    }

    /** Takes one being's stumble off at once. */
    void restore(UUID id) {
        Active a = active.remove(id);
        if (a != null && a.ref.isValid()) {
            World w = com.hypixel.hytale.server.core.universe.Universe.get().getWorld(a.world);
            if (w != null) {
                remove(w.getEntityStore().getStore(), a);
            }
        }
    }

    void forget(UUID id) {
        active.remove(id);
    }

    void restoreAll() {
        for (UUID id : active.keySet()) {
            restore(id);
        }
    }
}
