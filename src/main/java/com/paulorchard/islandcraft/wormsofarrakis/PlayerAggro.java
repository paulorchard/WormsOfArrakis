package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.joml.Vector3d;

import java.util.Map;
import java.util.TreeMap;

/** One player's aggro and what the brain last saw of them. Touched by the world thread and by commands. */
final class PlayerAggro {

    PlayerRef ref;
    String world;
    /** Online, in a world, alive, not in creative and not ignored: may gain aggro and be in a group or a target. */
    volatile boolean eligible;
    volatile boolean onSand;
    boolean airborneAfterJump;
    boolean hasLast;
    double lastX;
    double lastZ;
    volatile Vector3d position = new Vector3d();
    volatile double speed;
    /** Orders ties: higher means gained more recently. */
    volatile long lastGain;
    private double score;
    private final Map<String, Double> feeding = new TreeMap<>();

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
