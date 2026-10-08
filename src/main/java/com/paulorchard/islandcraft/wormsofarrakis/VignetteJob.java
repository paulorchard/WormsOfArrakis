package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.UUID;

/**
 * A screen overlay for one player, by one of two routes: a per-player weather override whose weather carries a
 * ScreenEffect texture, or an entity effect whose ApplicationEffects carry one. Holds until stopped, with a
 * safety limit, and always puts things back in abort.
 */
final class VignetteJob implements WormTestSystem.Job {

    static final float MAX_SECONDS = 60.0f;
    static final float FADE_SECONDS = 1.5f;

    private final World world;
    private final PlayerRef player;
    private final Ref<EntityStore> ref;
    private final boolean weatherRoute;
    private final String assetId;
    private final String kind;
    private float elapsed;
    private int previousWeatherIndex = Integer.MIN_VALUE;
    private int appliedEffectIndex = Integer.MIN_VALUE;

    VignetteJob(World world, PlayerRef player, Ref<EntityStore> ref, boolean weatherRoute, String assetId, String kind) {
        this.kind = kind;
        this.world = world;
        this.player = player;
        this.ref = ref;
        this.weatherRoute = weatherRoute;
        this.assetId = assetId;
    }

    /** Applies the effect. Returns an error text, or null if it worked. */
    String start(Store<EntityStore> store) {
        if (weatherRoute) {
            WeatherTracker tracker = store.getComponent(ref, WeatherTracker.getComponentType());
            int index = Weather.getAssetMap().getIndex(assetId);
            if (tracker == null || index == Integer.MIN_VALUE) {
                return "weather " + assetId + " is not loaded";
            }
            previousWeatherIndex = tracker.getWeatherIndex();
            tracker.setOverrideWeatherIndex(index);
            tracker.sendWeatherIndex(player, index, FADE_SECONDS);
            return null;
        }
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(assetId);
        EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
        if (effect == null || controller == null) {
            return "entity effect " + assetId + " is not loaded";
        }
        appliedEffectIndex = EntityEffect.getAssetMap().getIndex(assetId);
        controller.addEffect(ref, effect, MAX_SECONDS, OverlapBehavior.OVERWRITE, store);
        return null;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public UUID owner() {
        return player.getUuid();
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        elapsed += dt;
        return ref.isValid() && elapsed < MAX_SECONDS;
    }

    @Override
    public void abort(Store<EntityStore> store) {
        if (!ref.isValid()) {
            return;
        }
        if (weatherRoute) {
            WeatherTracker tracker = store.getComponent(ref, WeatherTracker.getComponentType());
            if (tracker != null) {
                tracker.clearOverrideWeatherIndex();
                if (previousWeatherIndex != Integer.MIN_VALUE) {
                    // Blend back at our own speed; the game's own once-a-second send then finds it already there.
                    tracker.sendWeatherIndex(player, previousWeatherIndex, FADE_SECONDS);
                }
            }
        } else if (appliedEffectIndex != Integer.MIN_VALUE) {
            EffectControllerComponent controller = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (controller != null) {
                controller.removeEffect(ref, appliedEffectIndex, store);
            }
        }
    }
}
