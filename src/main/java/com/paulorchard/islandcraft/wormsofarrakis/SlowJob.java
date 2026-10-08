package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.MovementSettings;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.player.movement.MovementManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Ramps a player's movement settings down to a fraction of the defaults, holds, then restores them.
 * Every value is computed from the manager's default settings, never from the current ones, so the ramp cannot
 * compound and the restore is exact.
 */
final class SlowJob implements WormTestSystem.Job {

    static final float HOLD_SECONDS = 5.0f;
    static final float RAMP_BACK_SECONDS = 1.0f;

    private final World world;
    private final PlayerRef player;
    private final Ref<EntityStore> ref;
    private final float target;
    private final float rampSeconds;
    private final Set<String> fields;
    private final float updateInterval;

    private float elapsed;
    private float sinceUpdate = 1e9f;
    private int updatesSent;
    private float lastFactor = 1.0f;

    SlowJob(World world, PlayerRef player, Ref<EntityStore> ref, float target, float rampSeconds,
            Set<String> fields, float updatesPerSecond) {
        this.world = world;
        this.player = player;
        this.ref = ref;
        this.target = target;
        this.rampSeconds = Math.max(rampSeconds, 0.0f);
        this.fields = fields;
        this.updateInterval = updatesPerSecond <= 0 ? 0 : 1.0f / updatesPerSecond;
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
        return "slow";
    }

    private float factorAt(float t) {
        if (t < rampSeconds) {
            return 1.0f - (1.0f - target) * (rampSeconds <= 0 ? 1.0f : t / rampSeconds);
        }
        if (t < rampSeconds + HOLD_SECONDS) {
            return target;
        }
        float back = (t - rampSeconds - HOLD_SECONDS) / RAMP_BACK_SECONDS;
        return target + (1.0f - target) * Math.min(1.0f, back);
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        if (!ref.isValid()) {
            return false;
        }
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (manager == null) {
            return false;
        }
        elapsed += dt;
        float end = rampSeconds + HOLD_SECONDS + RAMP_BACK_SECONDS;
        float factor = factorAt(Math.min(elapsed, end));
        sinceUpdate += dt;
        boolean last = elapsed >= end;
        if (last) {
            manager.resetDefaultsAndUpdate(ref, store);
            report(manager, "ramped and restored");
            return false;
        }
        if (sinceUpdate >= updateInterval && Math.abs(factor - lastFactor) > 1e-4f) {
            apply(manager.getSettings(), manager.getDefaultSettings(), factor);
            manager.update(player.getPacketHandler());
            lastFactor = factor;
            updatesSent++;
            sinceUpdate = 0;
        }
        return true;
    }

    private void apply(MovementSettings s, MovementSettings d, float f) {
        if (fields.contains("base")) {
            s.baseSpeed = d.baseSpeed * f;
        }
        if (fields.contains("mult")) {
            s.forwardWalkSpeedMultiplier = d.forwardWalkSpeedMultiplier * f;
            s.backwardWalkSpeedMultiplier = d.backwardWalkSpeedMultiplier * f;
            s.strafeWalkSpeedMultiplier = d.strafeWalkSpeedMultiplier * f;
            s.forwardRunSpeedMultiplier = d.forwardRunSpeedMultiplier * f;
            s.backwardRunSpeedMultiplier = d.backwardRunSpeedMultiplier * f;
            s.strafeRunSpeedMultiplier = d.strafeRunSpeedMultiplier * f;
            s.forwardSprintSpeedMultiplier = d.forwardSprintSpeedMultiplier * f;
        }
        if (fields.contains("jump")) {
            s.jumpForce = d.jumpForce * f;
        }
        if (fields.contains("air")) {
            s.airSpeedMultiplier = d.airSpeedMultiplier * f;
            s.airControlMaxMultiplier = d.airControlMaxMultiplier * f;
        }
        if (fields.contains("accel")) {
            s.acceleration = d.acceleration * f;
        }
    }

    @Override
    public void abort(Store<EntityStore> store) {
        if (!ref.isValid()) {
            return;
        }
        MovementManager manager = store.getComponent(ref, MovementManager.getComponentType());
        if (manager != null) {
            manager.resetDefaultsAndUpdate(ref, store);
        }
    }

    private void report(MovementManager manager, String how) {
        MovementSettings s = manager.getSettings();
        player.sendMessage(Message.raw(String.format(Locale.ROOT,
                "slow %s: target %.0f%% of %s, %d settings packets sent; now baseSpeed %.2f jumpForce %.2f",
                how, target * 100, fields, updatesSent, s.baseSpeed, s.jumpForce)));
    }
}
