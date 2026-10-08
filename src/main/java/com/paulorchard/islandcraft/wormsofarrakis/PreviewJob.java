package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * /worm preview: one effect, for the caller only, ramped from nothing to full over the given seconds, using the
 * same code the real event uses. For tuning each effect in isolation.
 */
final class PreviewJob implements WormTestSystem.Job {

    static final List<String> EFFECTS = List.of("wormsign", "rumble", "ripple", "vignette", "slow", "shake");

    private final WormEffects fx;
    private final World world;
    private final PlayerRef player;
    private final com.hypixel.hytale.component.Ref<EntityStore> ref;
    private final String effect;
    private final double seconds;
    private final Supplier<WormsOfArrakisConfig> config;
    private double elapsed;
    private double timer = 1e9;
    private Vector3d wormStart;
    private Vector3d lastTrail;

    PreviewJob(WormEffects fx, World world, PlayerRef player, com.hypixel.hytale.component.Ref<EntityStore> ref,
               String effect, double seconds, Supplier<WormsOfArrakisConfig> config) {
        this.fx = fx;
        this.world = world;
        this.player = player;
        this.ref = ref;
        this.effect = effect;
        this.seconds = Math.max(seconds, 1);
        this.config = config;
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
        return "preview";
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return false;
        }
        elapsed += dt;
        timer += dt;
        double u = Math.min(1, elapsed / seconds);
        WormsOfArrakisConfig cfg = config.get();
        Vector3d pos = WormEffects.position(store, player);
        if (pos == null) {
            return false;
        }
        switch (effect) {
            case "wormsign" -> {
                if (wormStart == null) {
                    HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
                    Vector3d d = head == null ? new Vector3d(0, 0, 1) : head.getDirection();
                    double len = Math.max(1e-6, Math.hypot(d.x, d.z));
                    wormStart = new Vector3d(pos.x + d.x / len * 120, pos.y, pos.z + d.z / len * 120);
                }
                double p = Math.pow(u, cfg.getPathEaseExponent());
                Vector3d worm = new Vector3d(wormStart.x + (pos.x - wormStart.x) * p, pos.y,
                        wormStart.z + (pos.z - wormStart.z) * p);
                if (timer >= cfg.getWormsignTrailInterval()) {
                    timer = 0;
                    boolean puff = ((int) (elapsed / cfg.getWormsignPuffInterval()))
                            != ((int) ((elapsed - cfg.getWormsignTrailInterval()) / cfg.getWormsignPuffInterval()));
                    fx.trail(world, store, lastTrail, worm, List.of(player), puff, cfg);
                    lastTrail = worm;
                }
            }
            case "rumble" -> {
                if (timer >= cfg.getRumbleIntervalSeconds()) {
                    timer = 0;
                    WormEffects.playRumble(player,
                            (float) (cfg.getRumbleTargetStartVolume() + (cfg.getRumbleTargetVolume() - cfg.getRumbleTargetStartVolume()) * u),
                            (float) (1.0 + (cfg.getRumbleTargetEndPitch() - 1.0) * u));
                }
            }
            case "ripple" -> {
                double interval = cfg.getRippleStartInterval() + (cfg.getRippleEndInterval() - cfg.getRippleStartInterval()) * u;
                if (timer >= interval) {
                    timer = 0;
                    fx.ripple(world, store, player, pos, (int) Math.round(cfg.getRippleRadius()), 1.2f, cfg);
                }
            }
            case "vignette" -> fx.fxOf(player).want(u, 1.0);
            case "slow" -> fx.fxOf(player).want(0, 1.0 - (1.0 - cfg.getSlowFloor()) * u);
            case "shake" -> {
                double interval = u < 0.7 ? 2.0 - 1.5 * (u / 0.7) : 0.4;
                if (timer >= interval) {
                    timer = 0;
                    if (u < 0.7) {
                        WormEffects.shake(player, "Arrakis_Worm_Tremble", cfg.getShakeStrength() * (0.2 + 0.4 * u / 0.7));
                    } else {
                        WormEffects.shake(player, "Arrakis_Worm_Lock", cfg.getShakeStrength());
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return elapsed < seconds;
    }

    @Override
    public void abort(Store<EntityStore> store) {
        fx.fxOf(player).fadeOut();
    }
}
