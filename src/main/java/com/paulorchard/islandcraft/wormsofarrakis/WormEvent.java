package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * One worm event for one group: STALKING, LOCKED, BREACH, then COOLDOWN. It knows nothing of the engine; the world
 * is reached through {@link Env}, so the rules can be tested without a server.
 */
public final class WormEvent {

    /** What the event needs to know about the players, answered by the aggro manager. */
    public interface Env {
        /** The player is online, in this event's world and alive. */
        boolean valid(UUID player);

        /** The player is valid and standing on sand. */
        boolean onSand(UUID player);

        /** The player's position, or null if not valid. */
        Vector3d position(UUID player);

        /** Surface height at a column, or NaN if unknown (not loaded). The worm follows it. */
        default double groundY(double x, double z) {
            return Double.NaN;
        }
    }

    /** Lengths in seconds. */
    public record Timings(double stalk, double lock, double breach, double cooldown, boolean retargetKeepsClock,
                         double ease) {
    }

    private final int id;
    private final String world;
    private final List<UUID> group;
    /** The group, best target first: highest aggro, ties to the most recent contributor. */
    private final List<UUID> ranking;
    private final Timings timings;
    private final boolean forced;
    private final Vector3d worm;
    /** Where the current leg of the worm path began, and how far through it the worm is. */
    private final Vector3d legStart;
    private double legElapsed;
    private double legLength;
    private final WormEventListener hooks;

    private UUID target;
    private WormPhase phase;
    private double elapsed;
    private double length;
    private WormEndReason endReason;
    /** The target was there when BREACH began, so the breach counts as a kill however it goes. */
    private boolean breachHadTarget;

    /** Creates the event and enters STALKING, or ends it FIZZLED at once if {@code target} is null. */
    WormEvent(int id, String world, List<UUID> ranking, UUID target, Vector3d wormStart, Timings timings,
              boolean forced, WormEventListener hooks) {
        this.id = id;
        this.world = world;
        this.ranking = new ArrayList<>(ranking);
        this.group = Collections.unmodifiableList(new ArrayList<>(ranking));
        this.target = target;
        this.timings = timings;
        this.forced = forced;
        this.worm = new Vector3d(wormStart);
        this.legStart = new Vector3d(wormStart);
        this.legLength = Math.max(timings.stalk(), 0.001);
        this.hooks = hooks;
        if (target == null) {
            this.phase = WormPhase.ENDED;
            this.endReason = WormEndReason.FIZZLED;
            hooks.onEnd(this, WormEndReason.FIZZLED);
        } else {
            enter(WormPhase.STALKING, timings.stalk());
        }
    }

    public int getId() {
        return id;
    }

    public String getWorldName() {
        return world;
    }

    /** Length of the stalk and lock phases this event runs with, in seconds. */
    public double getStalkSeconds() {
        return timings.stalk();
    }

    public double getLockSeconds() {
        return timings.lock();
    }

    public WormPhase getPhase() {
        return phase;
    }

    public double getSecondsLeft() {
        return Math.max(0, length - elapsed);
    }

    /** The player being hunted, or null if the event fizzled at the start. */
    public UUID getTarget() {
        return target;
    }

    /** Everyone in the group when the event started. */
    public List<UUID> getGroup() {
        return group;
    }

    /** The stand-in worm position: the point under the target, approaching from far away while STALKING. */
    public Vector3d getWormPosition() {
        return new Vector3d(worm);
    }

    /** True if started by /worm trigger, which skips the threshold and the cooldown. */
    public boolean isForced() {
        return forced;
    }

    public WormEndReason getEndReason() {
        return endReason;
    }

    /** STALKING, LOCKED or BREACH. */
    public boolean isActive() {
        return phase == WormPhase.STALKING || phase == WormPhase.LOCKED || phase == WormPhase.BREACH;
    }

    /** False once the event can be dropped. */
    public boolean isLive() {
        return phase != WormPhase.ENDED;
    }

    public boolean hasMember(UUID player) {
        return group.contains(player);
    }

    private void enter(WormPhase next, double seconds) {
        WormPhase old = phase;
        phase = next;
        elapsed = 0;
        length = seconds;
        hooks.onPhaseChange(this, old, next);
    }

    private void finish(WormEndReason reason) {
        endReason = reason;
        if (reason != WormEndReason.DEVOURED) {
            enter(WormPhase.ENDED, 0);
        }
        hooks.onEnd(this, reason);
    }

    /** Ends the event now (command or server stop). */
    void stop() {
        if (phase != WormPhase.ENDED && endReason == null) {
            finish(WormEndReason.STOPPED);
        } else if (phase != WormPhase.ENDED) {
            enter(WormPhase.ENDED, 0); // already devoured: just cut the cooldown short
        }
    }

    /** Runs the phase logic for one world tick. */
    void advance(double dt, Env env) {
        if (phase == WormPhase.ENDED) {
            return;
        }
        elapsed += dt;
        switch (phase) {
            case STALKING -> {
                if (!env.onSand(target)) {
                    retarget(env);
                }
                if (phase == WormPhase.STALKING) {
                    approach(dt, env);
                    if (elapsed >= length) {
                        enter(WormPhase.LOCKED, timings.lock());
                    }
                }
            }
            case LOCKED -> {
                // The target cannot change here, whatever they stand on. Only if they are gone is there no one.
                if (!env.valid(target)) {
                    finish(WormEndReason.FIZZLED);
                } else {
                    follow(env);
                    if (elapsed >= length) {
                        breachHadTarget = true;
                        enter(WormPhase.BREACH, timings.breach());
                    }
                }
            }
            case BREACH -> {
                follow(env);
                if (elapsed >= length) {
                    boolean present = breachHadTarget;
                    if (present) {
                        enter(WormPhase.COOLDOWN, timings.cooldown());
                        finish(WormEndReason.DEVOURED);
                    } else {
                        finish(WormEndReason.FIZZLED);
                    }
                }
            }
            case COOLDOWN -> {
                if (elapsed >= length) {
                    enter(WormPhase.ENDED, 0);
                }
            }
            default -> {
            }
        }
        if (phase != WormPhase.ENDED) {
            hooks.onTick(this);
        }
    }

    /** The target left the sand (or the game): the first player in the ranking now on sand takes over. */
    private void retarget(WormEvent.Env env) {
        UUID next = null;
        for (UUID candidate : ranking) {
            if (env.onSand(candidate)) {
                next = candidate;
                break;
            }
        }
        if (next == null) {
            finish(WormEndReason.FIZZLED);
            return;
        }
        UUID old = target;
        target = next;
        if (!timings.retargetKeepsClock()) {
            elapsed = 0;
        }
        // The path bends: a new leg from where the worm is, timed to arrive when the clock runs out.
        legStart.set(worm);
        legElapsed = 0;
        legLength = Math.max(length - elapsed, 0.001);
        hooks.onRetarget(this, old, next);
    }

    /**
     * Moves the stand-in worm along a straight leg from where it started to the target, which may be moving, with
     * an ease-in: slow at first, faster near the end, arriving as the stalk clock runs out. It follows the surface.
     */
    private void approach(double dt, Env env) {
        Vector3d p = env.position(target);
        if (p == null) {
            return;
        }
        legElapsed += dt;
        double u = Math.min(1.0, legElapsed / legLength);
        double progress = Math.pow(u, timings.ease());
        worm.x = legStart.x + (p.x - legStart.x) * progress;
        worm.z = legStart.z + (p.z - legStart.z) * progress;
        double ground = env.groundY(worm.x, worm.z);
        worm.y = Double.isNaN(ground) ? p.y : ground;
    }

    private void follow(Env env) {
        Vector3d p = env.position(target);
        if (p != null) {
            worm.set(p);
        }
    }
}
