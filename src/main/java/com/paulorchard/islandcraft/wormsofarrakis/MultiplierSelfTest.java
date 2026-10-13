package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.npc.NPCPlugin;
import org.joml.Vector3d;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * The effect multipliers against the real engine: an NPC is given an effect, and the factor read through the call the tick
 * uses goes from 1 to the registered factor and back to 1 once unregistered. The effect used is this mod's own stumble
 * slow, which always exists; the Holtzman field is reported as present or not (present when Equipment of Arrakis is
 * installed).
 */
final class MultiplierSelfTest {

    private MultiplierSelfTest() {
    }

    static void run(Consumer<String> say, Store<EntityStore> store, Vector3d spot) {
        AggroManager manager = WormsOfArrakisPlugin.get().aggro();
        String id = StumbleFx.effectId(WormsOfArrakisPlugin.get().config().getStumbleSlowPercent());
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
        var pair = NPCPlugin.get().spawnNPC(store, "Zombie", null, new Vector3d(spot.x, spot.y + 1.2, spot.z), new Rotation3f(0, 0, 0));
        Ref<EntityStore> ref = pair == null ? null : pair.left();
        EffectControllerComponent controller = ref == null ? null : store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effect == null || controller == null) {
            say.accept("effect multiplier: no NPC with an effect controller, or effect " + id + " missing -> FAILED");
            return;
        }
        boolean registered = WormAggro.registerEffectMultiplier(id, 3.0);
        boolean missingAccepted = WormAggro.registerEffectMultiplier("No_Such_Effect_Anywhere", 5.0);
        double without = manager.effectFactor(store, ref).factor();
        controller.addEffect(ref, effect, store);
        EffectMultipliers.Result with = manager.effectFactor(store, ref);
        boolean removed = WormAggro.unregisterEffectMultiplier(id);
        WormAggro.unregisterEffectMultiplier("No_Such_Effect_Anywhere");
        double after = manager.effectFactor(store, ref).factor();
        // The cost of the per-tick check for one being that has the effect, measured over many calls.
        WormAggro.registerEffectMultiplier(id, 3.0);
        int calls = 10_000;
        long t = System.nanoTime();
        for (int i = 0; i < calls; i++) {
            manager.effectFactor(store, ref);
        }
        double micros = (System.nanoTime() - t) / 1e3 / calls;
        WormAggro.unregisterEffectMultiplier(id);
        store.removeEntity(ref, RemoveReason.REMOVE);
        boolean holtzman = EntityEffect.getAssetMap().getIndex("Arrakis_Holtzman_Field") != EffectMultipliers.MISSING;
        boolean ok = registered && missingAccepted && without == 1.0 && with.factor() == 3.0 && removed && after == 1.0;
        say.accept(String.format(Locale.ROOT,
                "effect multiplier: %s without the effect, %s with it, x%.0f once unregistered; %.2f us per being per tick "
                        + "(100 beings: %.3f ms); Holtzman field effect in this game: %s -> %s",
                "x" + (long) without, with.text(), after, micros, micros * 100 / 1000,
                holtzman ? "present" : "absent (Equipment not installed)", ok ? "OK" : "FAILED"));
    }
}
