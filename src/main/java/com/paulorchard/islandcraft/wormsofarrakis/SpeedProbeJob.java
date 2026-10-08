package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.entity.movement.MovementStatesComponent;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Measures how fast the server sees a player move over the ground, per movement state, from their position each tick.
 * The player walks, runs and sprints during the sample; the report gives blocks per second for each.
 */
final class SpeedProbeJob implements WormTestSystem.Job {

    private static final class Bucket {
        double distance, seconds, peak;
    }

    private final World world;
    private final PlayerRef player;
    private final Ref<EntityStore> ref;
    private final float duration;
    private final Map<String, Bucket> buckets = new LinkedHashMap<>();
    private float elapsed;
    private Vector3d last;

    SpeedProbeJob(World world, PlayerRef player, Ref<EntityStore> ref, float duration) {
        this.world = world;
        this.player = player;
        this.ref = ref;
        this.duration = duration;
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
        return "speed";
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return false;
        }
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        MovementStatesComponent statesComponent = store.getComponent(ref, MovementStatesComponent.getComponentType());
        if (transform == null || statesComponent == null || dt <= 0) {
            return true;
        }
        Vector3d now = new Vector3d(transform.getPosition());
        if (last != null) {
            MovementStates s = statesComponent.getMovementStates();
            if (s.onGround && !s.horizontalIdle && !s.jumping && !s.falling && !s.flying && !s.inFluid) {
                double speed = Math.hypot(now.x - last.x, now.z - last.z) / dt;
                String state = s.sprinting ? "sprinting" : s.running ? "running" : s.walking ? "walking"
                        : s.crouching ? "crouching" : "other";
                Bucket b = buckets.computeIfAbsent(state, k -> new Bucket());
                b.distance += speed * dt;
                b.seconds += dt;
                b.peak = Math.max(b.peak, speed);
            }
        }
        last = now;
        elapsed += dt;
        if (elapsed >= duration) {
            report(store);
            return false;
        }
        return true;
    }

    private void report(Store<EntityStore> store) {
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (manager != null) {
            MovementSettings s = manager.getSettings();
            player.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "settings: baseSpeed %.2f, forward walk x%.3f, run x%.3f, sprint x%.3f, crouch x%.3f, jumpForce %.2f, acceleration %.3f",
                    s.baseSpeed, s.forwardWalkSpeedMultiplier, s.forwardRunSpeedMultiplier,
                    s.forwardSprintSpeedMultiplier, s.forwardCrouchSpeedMultiplier, s.jumpForce, s.acceleration)));
        }
        if (buckets.isEmpty()) {
            player.sendMessage(Message.raw("speed: no ground movement seen"));
        }
        for (Map.Entry<String, Bucket> e : buckets.entrySet()) {
            Bucket b = e.getValue();
            player.sendMessage(Message.raw(String.format(Locale.ROOT,
                    "speed %s: mean %.2f blocks/s over %.1f s, peak %.2f", e.getKey(), b.distance / b.seconds,
                    b.seconds, b.peak)));
        }
    }

    @Override
    public void abort(Store<EntityStore> store) {
    }
}
