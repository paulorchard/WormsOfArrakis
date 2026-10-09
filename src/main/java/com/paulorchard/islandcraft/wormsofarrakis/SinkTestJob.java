package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * /worm sink: the swallow on its own, for watching. The caller is held and the camera pulls out; after a wait they
 * are pulled down into the sand with the same code the breach uses (BreachHold and BreachCamera), and killed at the
 * bottom (or, with DevourKills off, brought back up and hurt). Reports each step to the caller in chat.
 */
final class SinkTestJob implements WormTestSystem.Job {

    private final World world;
    private final PlayerRef player;
    private final Supplier<WormsOfArrakisConfig> config;
    private final double depth;
    private final double wait;
    private final double delay;
    private final BreachHold hold = new BreachHold();
    private final BreachCamera camera = new BreachCamera();
    private Vector3d centre;
    private Vector3d heading = new Vector3d(0, 0, 1);
    private double elapsed;
    private boolean started;
    private boolean sinking;
    private boolean bottom;
    private boolean reported;
    private double endAt = -1;
    private double killAt;
    private double returnStart = -1;

    SinkTestJob(World world, PlayerRef player, Supplier<WormsOfArrakisConfig> config, double depth,
                double wait, double delay) {
        this.world = world;
        this.delay = delay;
        this.player = player;
        this.config = config;
        this.depth = depth;
        this.wait = wait;
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
        return "sinktest";
    }

    private void say(String text) {
        player.sendMessage(Message.raw("sink: " + text));
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        elapsed += dt;
        WormsOfArrakisConfig cfg = config.get();
        Ref<EntityStore> ref = player.getReference();
        if (ref == null || !ref.isValid()) {
            return false;
        }
        double sinkSeconds = Math.max(0.05, cfg.getBreachSinkSeconds());
        double wormHeight = WormJob.HEIGHT * cfg.getBreachWormSize();
        if (!started) {
            started = true;
            Vector3d at = WormEffects.position(store, player);
            if (at == null) {
                return false;
            }
            centre = at;
            HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
            if (head != null) {
                Vector3d d = head.getDirection();
                double len = Math.hypot(d.x, d.z);
                if (len > 1e-6) {
                    heading = new Vector3d(d.x / len, 0, d.z / len);
                }
            }
            hold.capture(world, store, centre, 0, player);
            killAt = wait + sinkSeconds + delay;
            say(String.format(Locale.ROOT, "held, camera pulling out to %.0f blocks; the sink starts in %.1f s (depth %.1f, death %.1f s after the bottom)",
                    BreachCamera.distance(cfg), wait, depth, delay));
        }
        double returnAt = killAt + 1.0;
        camera.tick(world, store, cfg, dt, centre, heading, wormHeight, 10, null, elapsed < returnAt);
        if (endAt > 0) {
            return elapsed < endAt;
        }
        hold.tick(world, store);
        BreachHold.Devour devour = (s, r, id) -> WormDevour.apply(s, cfg, r);
        if (!sinking && elapsed >= wait) {
            sinking = true;
            hold.startSink(store);
            hold.bursts(store, cfg, WormEffects.refs(List.of(player)), true);
            for (PlayerRef p : hold.sunkPlayersNow()) {
                Vector3d surface = hold.surfaceOf(p);
                if (surface != null) {
                    camera.pin(world, store, cfg, p, surface, centre, heading, wormHeight);
                }
            }
            if (cfg.isCameraShake()) {
                WormEffects.shake(player, "Arrakis_Worm_Lock", cfg.getShakeStrength());
            }
            say("sinking now");
        }
        if (sinking && returnStart < 0) {
            double q = Math.min(1, (elapsed - wait) / sinkSeconds);
            hold.sinkTo(world, store, depth, q);
            if (!bottom && q >= 1) {
                bottom = true;
                hold.bursts(store, cfg, WormEffects.refs(List.of(player)), false);
            }
            if (elapsed >= wait + sinkSeconds + Math.min(0.25, delay) && !reported) {
                reported = true;
                Vector3d now = WormEffects.position(store, player);
                Vector3d top = hold.surfaceOf(player);
                if (now != null && top != null) {
                    say(String.format(Locale.ROOT, "server position at the bottom: y %.2f (surface %.2f, wanted %.2f)",
                            now.y, top.y, top.y - depth));
                }
            }
            if (elapsed >= killAt) {
                if (cfg.isDevourKills()) {
                    hold.kill(world, store, devour);
                    say("at the bottom: killed");
                    endAt = returnAt + Math.max(cfg.getZoomSeconds(), 0.05) + 0.5;
                } else {
                    returnStart = elapsed;
                    say("at the bottom: coming back up (DevourKills is off)");
                }
            }
        } else if (sinking) {
            double q = 1 - (elapsed - returnStart) / sinkSeconds;
            if (q <= 0) {
                hold.releaseAndHurt(world, store, devour);
                say("released and hurt");
                endAt = Math.max(returnAt, elapsed) + Math.max(cfg.getZoomSeconds(), 0.05) + 0.5;
            } else {
                hold.sinkTo(world, store, depth, q);
            }
        }
        return true;
    }

    @Override
    public void abort(Store<EntityStore> store) {
        hold.releaseAll(world, store);
        camera.resetAll();
    }
}
