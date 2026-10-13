package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * One being's aggro and what the brain last saw of it. A being is anything that has aggro: a player, and later a hostile
 * mob or a totem. Keyed by the entity's UUID. Touched by the world thread and by commands.
 */
final class BeingAggro {

    final UUID id;
    final BeingKind kind;
    /** The player, for a PLAYER. */
    volatile PlayerRef ref;
    /** The entity, for a being that has one (a player's too). */
    volatile Ref<EntityStore> entity;
    volatile String world;
    /** A name for display: the NPC role of a mob, the deployable id of a totem. Null for a player. */
    volatile String label;
    /** Online or alive, in a world, not in creative and not ignored: may gain aggro, be listed and be a target. */
    volatile boolean eligible;
    volatile boolean onSand;
    volatile Vector3d position = new Vector3d();
    volatile double speed;
    /** Orders ties: higher means gained more recently. */
    volatile long lastGain;
    /** The worm event this being is in the list of, or null. */
    volatile WormEvent event;
    /** The aggro multiplier from effects the being has, as of the last tick, and where it comes from (for display). */
    volatile EffectMultipliers.Result effectFactor = EffectMultipliers.Result.NONE;

    // Read by the same code for every kind; only the world thread touches these.
    boolean airborneAfterJump;
    boolean hasLast;
    double lastX;
    double lastZ;
    boolean hasStamina;
    double lastStamina;

    private double score;
    private final Map<String, Double> feeding = new TreeMap<>();

    BeingAggro(UUID id, BeingKind kind) {
        this.id = id;
        this.kind = kind;
    }

    synchronized double score() {
        return score;
    }

    synchronized void setScore(double value) {
        score = Math.max(0, value);
        if (score == 0) {
            feeding.clear();
        }
    }

    synchronized void add(double amount, String sourceId) {
        score = Math.max(0, score + amount);
        if (amount > 0) {
            feeding.merge(sourceId, amount, Double::sum);
        }
    }

    synchronized Map<String, Double> feeding() {
        return new TreeMap<>(feeding);
    }
}
