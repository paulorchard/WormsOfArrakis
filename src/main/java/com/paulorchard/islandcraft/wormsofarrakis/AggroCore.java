package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * The rules of aggro for every kind of being, with nothing from the engine in them: what adds aggro, when it decays, who
 * triggers an event, who is in its list, and what happens to a score when an event ends or a player logs off. The
 * {@link AggroManager} reads the world into {@link Reading}s and calls this; the tests call it directly.
 */
final class AggroCore implements WormEventListener {

    /** The numbers the rules use, read from the config each tick. */
    record Rules(double threshold, double radius, double stillDecay, double offSandDecayMultiplier, double minGainSpeed,
                 double staminaFactor, double stalkBase, double stalkStep, double stalkMin, double entityStalk, double lock,
                 double breach, double mobMultiplier, double totemStart, double totemPerSecond) {
    }

    /** What happened to a totem that landed (see {@link #landTotem}). */
    enum TotemOutcome {
        /** Its owner was in a stalking event: it joined that list with its head start. */
        JOINED,
        /** It started an event of its own, as an entity trigger. */
        TRIGGERED,
        /** Its owner is in an event that is already locked or breaching: it does nothing. */
        IGNORED
    }

    /** What one being did over one tick, read by whatever knows how to read that kind of being. */
    record Reading(boolean eligible, boolean onSand, double distance, double speed, double stamina, AggroContext context,
                   double factor) {
        /** A reading with no effect multiplier. */
        Reading(boolean eligible, boolean onSand, double distance, double speed, double stamina, AggroContext context) {
            this(eligible, onSand, distance, speed, stamina, context, 1.0);
        }

        /** {@code stamina} is NaN for a being with no stamina stat. */
        static final double NO_STAMINA = Double.NaN;
    }

    private final Supplier<Rules> rules;
    private final WormEvents events;
    private final Map<UUID, BeingAggro> beings = new ConcurrentHashMap<>();
    /** Aggro kept for players who logged off in an event, given back on login. In memory only. */
    private final Map<UUID, Double> loggedOff = new ConcurrentHashMap<>();
    private final List<AggroSource> sources = new CopyOnWriteArrayList<>();
    private final AtomicLong gainCounter = new AtomicLong();

    AggroCore(Supplier<Rules> rules, WormEvents events) {
        this.rules = rules;
        this.events = events;
    }

    // ------------------------------------------------------------------ registry of sources and beings

    void registerSource(AggroSource source) {
        unregisterSource(source.getId());
        sources.add(source);
    }

    boolean unregisterSource(String id) {
        return sources.removeIf(s -> s.getId().equals(id));
    }

    List<AggroSource> sources() {
        return new ArrayList<>(sources);
    }

    BeingAggro being(UUID id) {
        return beings.get(id);
    }

    List<BeingAggro> all() {
        return new ArrayList<>(beings.values());
    }

    /** The being, created if new. A player who logged off inside an event gets their kept score back here. */
    BeingAggro beingOrCreate(UUID id, BeingKind kind) {
        return beings.computeIfAbsent(id, k -> {
            BeingAggro b = new BeingAggro(k, kind);
            Double kept = loggedOff.remove(k);
            if (kept != null) {
                b.add(kept, "logoff");
            }
            return b;
        });
    }

    boolean addAggro(UUID id, double amount, String sourceId) {
        BeingAggro b = beings.get(id);
        if (b == null) {
            return false;
        }
        add(b, amount, sourceId);
        return true;
    }

    void add(BeingAggro b, double amount, String sourceId) {
        b.add(amount, sourceId);
        if (amount > 0) {
            b.lastGain = gainCounter.incrementAndGet();
        }
    }

    /**
     * Drops every mob of the world that was not seen this tick (gone, dead, out of the loaded chunks or out of range of
     * every player), score and all, unless an event has it in its list: the event deals with it. Returns how many.
     */
    int dropUnseenMobs(String world) {
        int n = 0;
        for (BeingAggro a : all()) {
            if (a.kind == BeingKind.MOB && world.equals(a.world) && !a.eligible && a.event == null) {
                forget(a.id);
                n++;
            }
        }
        return n;
    }

    /** A being that is gone (despawned, or a player who logged off outside an event): its score is lost. */
    void forget(UUID id) {
        BeingAggro b = beings.remove(id);
        if (b != null && b.event != null) {
            if (b.kind == BeingKind.TOTEM && b.event.retire(id, b.score(), b.position, b.lastGain)) {
                return; // an expired totem stays a candidate in a player's stalk
            }
            b.event.removeMember(id, WormEvent.Leave.GONE);
        }
    }

    /**
     * A player logs off. In an event's list: leave the list and keep one point under the threshold in memory for when
     * they come back, so that moving a little on sand starts an event for them. Otherwise the score is lost.
     */
    void logOff(UUID id) {
        BeingAggro b = beings.get(id);
        if (b != null && b.event != null) {
            loggedOff.put(id, Math.max(0, rules.get().threshold() - 1));
            b.event.removeMember(id, WormEvent.Leave.LOGOFF);
            beings.remove(id);
        } else {
            beings.remove(id);
            loggedOff.remove(id);
        }
    }

    /** The score kept for a player who is logged off, or null. */
    Double keptFor(UUID id) {
        return loggedOff.get(id);
    }

    // ------------------------------------------------------------------ one being, one tick

    /**
     * Applies one tick of a being's behaviour. Movement and stamina spent add aggro (only while eligible and on sand),
     * times the reading's effect factor (one-off adds, totem growth and decay are not multiplied);
     * a being that gained nothing decays, except inside a worm event, where the score only rises and may pass the
     * threshold.
     */
    void apply(BeingAggro a, Reading r, double dt) {
        Rules rules = this.rules.get();
        boolean gained = false;
        boolean counts = r.eligible() && r.onSand();
        if (counts && r.speed() >= rules.minGainSpeed() && r.distance() > 0) {
            for (AggroSource source : sources) {
                // Only the built-in movement source has a meaning for a mob: the others read a player's movement states.
                if (a.kind != BeingKind.PLAYER && !"move".equals(source.getId())) {
                    continue;
                }
                if (source.matches(r.context())) {
                    double amount = source.getRate() * r.distance() * r.factor() * (a.kind == BeingKind.MOB ? rules.mobMultiplier() : 1.0);
                    if (amount > 0) {
                        add(a, amount, source.getId());
                        gained = true;
                    }
                    break;
                }
            }
        }
        // Stamina spent: the net fall since the last tick. A rise (regeneration, or a reset to full) is ignored, and the
        // first reading after joining, respawning or gaining a stamina stat has nothing to compare with.
        // Only players spend stamina into aggro: a mob has none to speak of.
        if (a.kind == BeingKind.PLAYER && r.eligible() && !Double.isNaN(r.stamina())) {
            if (a.hasStamina && counts && rules.staminaFactor() > 0) {
                double spent = a.lastStamina - r.stamina();
                if (spent > 1e-9) {
                    add(a, spent * rules.staminaFactor() * r.factor(), "stamina");
                    gained = true;
                }
            }
            a.lastStamina = r.stamina();
            a.hasStamina = true;
        } else {
            a.hasStamina = false;
        }
        if (!gained && a.event == null) {
            // Off sand the aggro drains much faster than when standing still on it.
            double rate = rules.stillDecay() * (a.onSand ? 1.0 : rules.offSandDecayMultiplier());
            a.setScore(a.score() - rate * dt);
        }
    }

    /** A totem's whole behaviour for one tick: it gains TotemAggroPerSecond for as long as it stands, with no decay. */
    void tickTotem(BeingAggro totem, double dt) {
        add(totem, rules.get().totemPerSecond() * dt, "totem");
    }

    /**
     * A totem has landed on sand. If its owner is in an event that is still stalking, the totem joins that list with the
     * start score and keeps climbing (the hail mary). If the owner is in an event that is locked or breaching, nothing
     * happens. Otherwise (no owner, or the owner is free) the totem starts an event of its own, as an entity trigger.
     */
    TotemOutcome landTotem(BeingAggro totem, UUID owner) {
        Rules rules = this.rules.get();
        BeingAggro o = owner == null ? null : beings.get(owner);
        WormEvent ev = o == null ? null : o.event;
        totem.setScore(0);
        add(totem, rules.totemStart(), "totem");
        if (ev != null && ev.isLive()) {
            if (ev.getPhase() == WormPhase.STALKING && ev.addMember(totem.id)) {
                totem.event = ev;
                return TotemOutcome.JOINED;
            }
            return TotemOutcome.IGNORED;
        }
        startEvent(totem, false, false, Double.NaN, Double.NaN);
        // A totem that could not start (it is already listed) is simply left as it is.
        return TotemOutcome.TRIGGERED;
    }

    // ------------------------------------------------------------------ triggering

    private ListScan.Candidate candidate(BeingAggro b) {
        Vector3d p = b.position;
        return new ListScan.Candidate(b.id, b.kind, p.x, p.z, b.onSand, b.event != null, b.eligible, b.score(), b.lastGain);
    }

    private List<ListScan.Candidate> candidates(String world) {
        List<ListScan.Candidate> list = new ArrayList<>();
        for (BeingAggro b : beings.values()) {
            if (world.equals(b.world)) {
                list.add(candidate(b));
            }
        }
        return list;
    }

    /** Starts an event for every being of the world that has reached the threshold, highest score first. */
    List<WormEvent> checkTriggers(String world) {
        List<WormEvent> started = new ArrayList<>();
        Rules rules = this.rules.get();
        for (ListScan.Candidate t : ListScan.triggers(candidates(world), rules.threshold())) {
            BeingAggro b = beings.get(t.id());
            // One already placed in an earlier list this tick is not a trigger.
            if (b != null && b.event == null) {
                started.add(startEvent(b, false, false, Double.NaN, Double.NaN));
            }
        }
        return started;
    }

    /**
     * Locks the list for an event triggered by {@code trigger}, sets everyone's aggro in it to 0 and starts the stalk.
     * {@code stalkOverride} and {@code lockOverride} (NaN for none) are for /worm trigger. {@code solo} lists only the
     * trigger (for /worm breach, which wants one named victim).
     */
    WormEvent startEvent(BeingAggro trigger, boolean forced, boolean solo, double stalkOverride, double lockOverride) {
        Rules rules = this.rules.get();
        List<ListScan.Candidate> scanned = ListScan.scan(candidate(trigger), solo ? List.of() : candidates(trigger.world), rules.radius());
        List<UUID> ids = new ArrayList<>();
        int players = 0;
        for (ListScan.Candidate c : scanned) {
            ids.add(c.id());
            if (c.kind() == BeingKind.PLAYER) {
                players++;
            }
        }
        double stalk = ListScan.stalkSeconds(trigger.kind, players, rules.stalkBase(), rules.stalkStep(), rules.stalkMin(),
                rules.entityStalk());
        if (!Double.isNaN(stalkOverride)) {
            stalk = stalkOverride;
        }
        double lock = Double.isNaN(lockOverride) ? rules.lock() : lockOverride;
        for (UUID id : ids) {
            beings.get(id).setScore(0);
        }
        UUID guaranteed = trigger.kind == BeingKind.PLAYER ? null : trigger.id;
        WormEvent event = events.start(trigger.world, trigger.id, ids, guaranteed, trigger.position,
                new WormEvent.Timings(stalk, lock, rules.breach()), forced);
        if (event.isLive()) {
            for (UUID id : ids) {
                beings.get(id).event = event;
            }
        }
        return event;
    }

    /** The list a being would start now, for display only. Null if the being is unknown. */
    List<ListScan.Candidate> previewList(UUID id) {
        BeingAggro b = beings.get(id);
        if (b == null || b.world == null) {
            return null;
        }
        return ListScan.scan(candidate(b), candidates(b.world), rules.get().radius());
    }

    // ------------------------------------------------------------------ the event's view of beings

    WormEvent.Env env(String world) {
        return new WormEvent.Env() {
            @Override
            public boolean valid(UUID id) {
                BeingAggro b = beings.get(id);
                return b != null && b.eligible && world.equals(b.world);
            }

            @Override
            public boolean onSand(UUID id) {
                BeingAggro b = beings.get(id);
                return b != null && b.eligible && b.onSand && world.equals(b.world);
            }

            @Override
            public Vector3d position(UUID id) {
                BeingAggro b = beings.get(id);
                return b != null && b.eligible && world.equals(b.world) ? new Vector3d(b.position) : null;
            }

            @Override
            public double score(UUID id) {
                BeingAggro b = beings.get(id);
                return b == null ? 0 : b.score();
            }

            @Override
            public long recency(UUID id) {
                BeingAggro b = beings.get(id);
                return b == null ? 0 : b.lastGain;
            }

            @Override
            public boolean entity(UUID id) {
                BeingAggro b = beings.get(id);
                return b != null && b.kind != BeingKind.PLAYER;
            }
        };
    }

    // ------------------------------------------------------------------ the end of an event

    @Override
    public void onMemberRemoved(WormEvent event, UUID id, WormEvent.Leave why) {
        BeingAggro b = beings.get(id);
        if (b != null && b.event == event) {
            b.event = null;
            if (why == WormEvent.Leave.GONE) {
                b.setScore(0);
            }
        }
    }

    /** Everyone left in the list goes back to 0 and is free; beings that were never in the list are untouched. */
    @Override
    public void onEnd(WormEvent event, WormEndReason reason) {
        for (UUID id : event.getList()) {
            BeingAggro b = beings.get(id);
            if (b != null) {
                b.setScore(0);
                if (b.event == event) {
                    b.event = null;
                }
            }
        }
    }
}
