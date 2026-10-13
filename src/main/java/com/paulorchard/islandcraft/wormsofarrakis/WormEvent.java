package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * One worm event for one list of beings: STALKING, LOCKED, BREACH, then ENDED. There is no worm travelling over the sand
 * and no cooldown. The list is fixed when the event starts and only shrinks. It knows nothing of the engine; the world
 * is reached through {@link Env}, so the rules can be tested without a server.
 */
public final class WormEvent {

    /** What the event needs to know about beings, answered by the aggro manager. Any kind of being, by UUID. */
    public interface Env {
        /** The being is in this event's world, alive and eligible. */
        boolean valid(UUID being);

        /** The being is valid and standing on sand. */
        boolean onSand(UUID being);

        /** The being's position, or null if not valid. */
        Vector3d position(UUID being);

        /** The being's aggro. */
        double score(UUID being);

        /** Higher means the being gained aggro more recently. */
        long recency(UUID being);

        /** The being is not a player (a mob or a totem): if it is the target and is gone, the breach still happens. */
        default boolean entity(UUID being) {
            return false;
        }
    }

    /** Why a being left the list early. */
    public enum Leave {
        /** Died, left the world, went Creative or was ignored. */
        GONE,
        /** Logged off. */
        LOGOFF
    }

    /** Lengths in seconds. */
    public record Timings(double stalk, double lock, double breach) {
    }

    private final int id;
    private final String world;
    private final UUID trigger;
    private final List<UUID> list;
    private final Timings timings;
    private final boolean forced;
    private final Vector3d origin;
    private final WormEventListener hooks;
    /** The target is fixed from the start (an entity trigger) instead of chosen at the end of the stalk. */
    private final boolean guaranteed;
    private final Vector3d lastTargetPosition = new Vector3d();
    /** Totems that expired during the stalk: still in the list as candidates, with the score and place they had. */
    private final java.util.Map<UUID, Ghost> ghosts = new java.util.concurrent.ConcurrentHashMap<>();

    /** What an expired totem keeps: its score when it vanished, where it stood and when it last gained. */
    record Ghost(double score, Vector3d position, long recency) {
    }

    private UUID target;
    /** The target is a mob or a totem: if it is gone at the lock or later, its last position still gets the breach. */
    private boolean entityTarget;
    private WormPhase phase;
    private double elapsed;
    private double length;
    private WormEndReason endReason;

    /**
     * Creates the event and enters STALKING. {@code guaranteedTarget} is null for a player trigger (the target is
     * chosen at the end of the stalk) and the trigger itself for an entity trigger.
     */
    WormEvent(int id, String world, UUID trigger, List<UUID> list, UUID guaranteedTarget, Vector3d origin, Timings timings,
              boolean forced, WormEventListener hooks) {
        this.id = id;
        this.world = world;
        this.trigger = trigger;
        this.list = new CopyOnWriteArrayList<>(list);
        this.target = guaranteedTarget;
        this.guaranteed = guaranteedTarget != null;
        this.entityTarget = guaranteedTarget != null;
        this.timings = timings;
        this.forced = forced;
        this.origin = new Vector3d(origin);
        this.hooks = hooks;
        enter(WormPhase.STALKING, timings.stalk());
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

    /** The being that reached the threshold (or was forced). */
    public UUID getTrigger() {
        return trigger;
    }

    /** The being the worm takes: null until the end of the stalk, unless the trigger was an entity (then always set). */
    public UUID getTarget() {
        return target;
    }

    /** True if the target was fixed at the start because an entity triggered the event. */
    public boolean isGuaranteed() {
        return guaranteed;
    }

    /** Everyone still in the list. */
    public List<UUID> getList() {
        return Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** Where the event began: the trigger being's position when the list was locked. */
    public Vector3d getOrigin() {
        return new Vector3d(origin);
    }

    /** The target's last known position, for a breach when the target has gone. Zero until a target was seen. */
    public Vector3d getLastTargetPosition() {
        return new Vector3d(lastTargetPosition);
    }

    /** True if started by /worm trigger, which skips the threshold. */
    public boolean isForced() {
        return forced;
    }

    public WormEndReason getEndReason() {
        return endReason;
    }

    /** STALKING, LOCKED or BREACH. */
    public boolean isActive() {
        return phase != WormPhase.ENDED;
    }

    /** False once the event can be dropped. */
    public boolean isLive() {
        return phase != WormPhase.ENDED;
    }

    public boolean hasMember(UUID being) {
        return list.contains(being);
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
        enter(WormPhase.ENDED, 0);
        hooks.onEnd(this, reason);
    }

    /**
     * Adds a being to the list while the event is still stalking (a totem thrown by someone in the list). The stalk is
     * not restarted. Returns false if the event is past STALKING or the being is already listed.
     */
    boolean addMember(UUID being) {
        if (phase != WormPhase.STALKING || list.contains(being)) {
            return false;
        }
        list.add(being);
        hooks.onMemberAdded(this, being);
        return true;
    }

    /**
     * A totem of a player-triggered event has expired during the stalk. It stays in the list as a candidate with the
     * score it had and the place it stood, so it can still be chosen as the target: the decoy worked while it stood.
     * Returns false (and does nothing) if the event is past STALKING, is an entity trigger, or does not list the being.
     */
    boolean retire(UUID being, double score, Vector3d position, long recency) {
        if (phase != WormPhase.STALKING || guaranteed || !list.contains(being)) {
            return false;
        }
        ghosts.put(being, new Ghost(score, new Vector3d(position), recency));
        return true;
    }

    /** Takes a being out of the list now. The manager looks after its aggro. */
    void removeMember(UUID being, Leave why) {
        if (list.remove(being)) {
            hooks.onMemberRemoved(this, being, why);
        }
    }

    /** Ends the event now (command or server stop). */
    void stop() {
        if (phase != WormPhase.ENDED) {
            finish(WormEndReason.STOPPED);
        }
    }

    /** Runs the phase logic for one world tick. */
    void advance(double dt, Env env) {
        if (phase == WormPhase.ENDED) {
            return;
        }
        elapsed += dt;
        for (UUID member : list) {
            if (!ghosts.containsKey(member) && !env.valid(member)) {
                removeMember(member, Leave.GONE);
            }
        }
        if (target != null) {
            Vector3d p = env.position(target);
            if (p != null) {
                lastTargetPosition.set(p);
            }
        }
        switch (phase) {
            case STALKING -> {
                if (list.isEmpty() && !guaranteed) {
                    finish(WormEndReason.FIZZLED);
                } else if (elapsed >= length) {
                    chooseAndLock(env);
                }
            }
            case LOCKED -> {
                // The target cannot change here, whatever they stand on. If a player target is gone entirely there is
                // no one to take; an entity target that is gone still gets its breach, with no victim.
                if (!entityTarget && !env.valid(target)) {
                    finish(WormEndReason.FIZZLED);
                } else if (elapsed >= length) {
                    enter(WormPhase.BREACH, timings.breach());
                }
            }
            case BREACH -> {
                if (elapsed >= length) {
                    finish(WormEndReason.DEVOURED);
                }
            }
            default -> {
            }
        }
        if (phase != WormPhase.ENDED) {
            hooks.onTick(this);
        }
    }

    /** The end of the stalk: the member with the highest aggro who is on sand now is the target. */
    private void chooseAndLock(Env env) {
        if (!guaranteed) {
            List<ListScan.Ranked> ranked = new ArrayList<>();
            for (UUID member : list) {
                Ghost g = ghosts.get(member);
                ranked.add(g != null ? new ListScan.Ranked(member, g.score(), g.recency(), true)
                        : new ListScan.Ranked(member, env.score(member), env.recency(member), env.onSand(member)));
            }
            target = ListScan.chooseTarget(ranked);
            if (target == null) {
                finish(WormEndReason.FIZZLED);
                return;
            }
            Ghost chosen = ghosts.get(target);
            entityTarget = chosen != null || env.entity(target);
            Vector3d p = chosen != null ? chosen.position() : env.position(target);
            if (p != null) {
                lastTargetPosition.set(p);
            }
        }
        enter(WormPhase.LOCKED, timings.lock());
    }
}
