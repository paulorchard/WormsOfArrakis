package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.OverlapBehavior;
import com.hypixel.hytale.server.core.entity.effect.EffectControllerComponent;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.modules.entity.damage.DeathComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * What the worm does to its target: a screen vignette and a slowdown, each with a wanted strength that the event
 * sets every tick and a current strength that follows it. Rising is immediate; falling is limited so an old target
 * fades out over {@code FadeOutSeconds} instead of snapping. Everything it applies is undone in {@link #restore}.
 *
 * <p>Both are entity effects, one asset per level ({@code Arrakis_Worm_Vignette_Level_1..6},
 * {@code Arrakis_Worm_Slow_95..10}), swapped as the level changes. The effect route was used because the weather
 * route would replace the Coriolis storm look and sound and the movement-settings route did not slow the player in
 * the prompt 17 test. Jump force is the one thing only the movement settings can change.
 */
final class TargetFx {

    private static final int VIGNETTE_LEVELS = 6;

    final PlayerRef player;
    private final Supplier<WormsOfArrakisConfig> config;

    private double vigWant;
    private double slowWant = 1.0;
    private double vigCur;
    private double slowCur = 1.0;
    private boolean dying;
    private double zoomWant;
    private double zoomCur;
    private boolean zoomOn;
    private double zoomSent = -1;
    private double zoomClock;

    private int vigLevel;
    private int slowPercent = 100;
    private double renew;
    private boolean settingsTouched;
    private double lastJumpScale = 1.0;
    private double settingsClock;

    TargetFx(PlayerRef player, Supplier<WormsOfArrakisConfig> config) {
        this.player = player;
        this.config = config;
    }

    /** Sets what the worm wants right now: vignette 0 to 1, slow as a share of normal speed (1 = none). */
    void want(double vignette, double slow) {
        dying = false;
        vigWant = vignette;
        slowWant = slow;
    }

    /** The camera zoom wanted, 0 (the player normal view) to 1 (fully pulled out). */
    void wantZoom(double zoom) {
        dying = false;
        zoomWant = zoom;
    }

    /** Fades everything out, then the entry can be dropped. */
    void fadeOut() {
        dying = true;
        vigWant = 0;
        slowWant = 1.0;
        zoomWant = 0;
    }

    boolean isDying() {
        return dying;
    }

    /** True once a dying entry has nothing left applied. */
    boolean finished() {
        return dying && vigLevel == 0 && slowPercent == 100 && !settingsTouched && !zoomOn;
    }

    /** Runs on the world thread. Returns false when the entry should be dropped. */
    boolean tick(Store<EntityStore> store, double dt) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return false; // left the world or disconnected: the game removes effects and resets movement with the entity
        }
        if (store.getComponent(ref, DeathComponent.getComponentType()) != null) {
            restore(store);
            return false;
        }
        WormsOfArrakisConfig cfg = config.get();
        double fade = Math.max(cfg.getFadeOutSeconds(), 0.05);
        vigCur = vigWant >= vigCur ? vigWant : Math.max(vigWant, vigCur - dt / fade);
        slowCur = slowWant <= slowCur ? slowWant : Math.min(slowWant, slowCur + dt * (1.0 - cfg.getSlowFloor()) / fade);

        EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
        renew += dt;
        boolean refresh = renew >= 1.0;
        if (refresh) {
            renew = 0;
        }
        if (effects != null) {
            applyVignette(store, ref, effects, cfg, refresh);
            if (!cfg.getSlowMethod().equals("settings")) {
                applySlowEffect(store, ref, effects, refresh);
            }
        }
        applyZoom(ref, cfg, dt);
        settingsClock += dt;
        if (settingsClock >= 0.1) {
            settingsClock = 0;
            applySettings(store, ref, cfg);
        }
        return true;
    }

    private void applyVignette(Store<EntityStore> store, Ref<EntityStore> ref, EffectControllerComponent effects,
                               WormsOfArrakisConfig cfg, boolean refresh) {
        int level = (int) Math.round(Math.max(0, Math.min(1, vigCur * cfg.getVignetteStrength())) * VIGNETTE_LEVELS);
        if (level == vigLevel && !(refresh && level > 0)) {
            return;
        }
        if (vigLevel > 0 && level != vigLevel) {
            remove(store, ref, effects, "Arrakis_Worm_Vignette_Level_" + vigLevel);
        }
        if (level > 0) {
            add(store, ref, effects, "Arrakis_Worm_Vignette_Level_" + level);
        }
        vigLevel = level;
    }

    private void applySlowEffect(Store<EntityStore> store, Ref<EntityStore> ref, EffectControllerComponent effects,
                                 boolean refresh) {
        // Nearest asset: 95% down to 10% in steps of 5; above 97% there is no effect.
        int percent = slowCur >= 0.975 ? 100 : (int) Math.max(10, Math.min(95, Math.round(slowCur * 20.0) * 5));
        if (percent == slowPercent && !(refresh && percent < 100)) {
            return;
        }
        if (slowPercent < 100 && percent != slowPercent) {
            remove(store, ref, effects, "Arrakis_Worm_Slow_" + slowPercent);
        }
        if (percent < 100) {
            add(store, ref, effects, "Arrakis_Worm_Slow_" + percent);
        }
        slowPercent = percent;
    }

    /**
     * Pulls the camera out into a custom third person view (a ServerCameraSettings with a distance) as the zoom
     * rises, and gives the player their own view back (the same packet the game /camera reset sends) when it ends.
     */
    private void applyZoom(Ref<EntityStore> ref, WormsOfArrakisConfig cfg, double dt) {
        double fade = Math.max(cfg.getFadeOutSeconds(), 0.05);
        zoomCur = zoomWant >= zoomCur ? zoomWant : Math.max(zoomWant, zoomCur - dt / fade);
        zoomClock += dt;
        if (!cfg.isCameraZoom() || zoomCur < 0.001) {
            if (zoomOn) {
                player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
                zoomOn = false;
                zoomSent = -1;
            }
            return;
        }
        double q = zoomCur * zoomCur * (3 - 2 * zoomCur);
        double distance = cfg.getZoomFromDistance() + (cfg.getZoomDistance() - cfg.getZoomFromDistance()) * q;
        if (Math.abs(distance - zoomSent) < 0.05 || zoomClock < 0.05) {
            return;
        }
        zoomClock = 0;
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.isFirstPerson = false;
        settings.distance = (float) distance;
        settings.eyeOffset = true;
        settings.positionLerpSpeed = 0.3f;
        settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
        player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, settings));
        zoomOn = true;
        zoomSent = distance;
    }

    /** Jump force always, and the whole speed when SlowMethod is settings. Sent at most ten times a second. */
    private void applySettings(Store<EntityStore> store, Ref<EntityStore> ref, WormsOfArrakisConfig cfg) {
        boolean viaSettings = cfg.getSlowMethod().equals("settings");
        double progress = (1.0 - slowCur) / Math.max(1e-6, 1.0 - cfg.getSlowFloor());
        double jumpScale = 1.0 - (1.0 - cfg.getSlowJumpFloor()) * Math.max(0, Math.min(1, progress));
        double speedScale = viaSettings ? slowCur : 1.0;
        boolean want = jumpScale < 0.999 || speedScale < 0.999;
        if (!want && !settingsTouched) {
            return;
        }
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (manager == null) {
            return;
        }
        if (!want) {
            manager.resetDefaultsAndUpdate(ref, store);
            settingsTouched = false;
            lastJumpScale = 1.0;
            return;
        }
        if (Math.abs(jumpScale - lastJumpScale) < 0.01 && !viaSettings) {
            return;
        }
        MovementSettings s = manager.getSettings();
        MovementSettings d = manager.getDefaultSettings();
        s.jumpForce = (float) (d.jumpForce * jumpScale);
        if (viaSettings) {
            s.baseSpeed = (float) (d.baseSpeed * speedScale);
            s.forwardWalkSpeedMultiplier = (float) (d.forwardWalkSpeedMultiplier * speedScale);
            s.forwardRunSpeedMultiplier = (float) (d.forwardRunSpeedMultiplier * speedScale);
            s.forwardSprintSpeedMultiplier = (float) (d.forwardSprintSpeedMultiplier * speedScale);
        }
        manager.update(player.getPacketHandler());
        settingsTouched = true;
        lastJumpScale = jumpScale;
    }

    private void add(Store<EntityStore> store, Ref<EntityStore> ref, EffectControllerComponent effects, String id) {
        EntityEffect effect = EntityEffect.getAssetMap().getAsset(id);
        if (effect != null) {
            effects.addEffect(ref, effect, 2.0f, OverlapBehavior.OVERWRITE, store);
        }
    }

    private void remove(Store<EntityStore> store, Ref<EntityStore> ref, EffectControllerComponent effects, String id) {
        int index = EntityEffect.getAssetMap().getIndex(id);
        if (index != Integer.MIN_VALUE) {
            effects.removeEffect(ref, index, store);
        }
    }

    /** Takes back everything applied, at once. Safe to call more than once. */
    void restore(Store<EntityStore> store) {
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return;
        }
        try {
            EffectControllerComponent effects = store.getComponent(ref, EffectControllerComponent.getComponentType());
            if (effects != null) {
                if (vigLevel > 0) {
                    remove(store, ref, effects, "Arrakis_Worm_Vignette_Level_" + vigLevel);
                }
                if (slowPercent < 100) {
                    remove(store, ref, effects, "Arrakis_Worm_Slow_" + slowPercent);
                }
            }
            if (settingsTouched) {
                MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
                if (manager != null) {
                    manager.resetDefaultsAndUpdate(ref, store);
                }
            }
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not restore worm effects");
        }
        if (zoomOn) {
            player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
            zoomOn = false;
            zoomSent = -1;
        }
        zoomCur = 0;
        zoomWant = 0;
        vigLevel = 0;
        slowPercent = 100;
        settingsTouched = false;
        vigCur = 0;
        slowCur = 1.0;
        vigWant = 0;
        slowWant = 1.0;
    }
}
