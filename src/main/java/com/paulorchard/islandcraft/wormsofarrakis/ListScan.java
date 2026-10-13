package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Engine-free rules for who is in a worm event's list and how long the stalk is. The list is decided once, when the
 * event starts: every being within the radius of the trigger that is eligible, on sand right now and not already in an
 * event, plus the trigger itself.
 */
final class ListScan {

    /** One being as the scan sees it. {@code recency} is higher the more recently the being gained aggro. */
    record Candidate(UUID id, BeingKind kind, double x, double z, boolean onSand, boolean inEvent, boolean eligible,
                     double score, long recency) {
    }

    private ListScan() {
    }

    /** Highest score first; a tie goes to whoever gained aggro most recently. */
    static final Comparator<Candidate> RANKING =
            Comparator.comparingDouble(Candidate::score).reversed().thenComparing(Comparator.comparingLong(Candidate::recency).reversed());

    /**
     * The list for an event started by {@code trigger}: the trigger first, then everyone else who qualifies, best
     * ranked first. Horizontal distance only. Beings that are on rock, in an event, or not eligible are left out.
     */
    static List<Candidate> scan(Candidate trigger, Collection<Candidate> candidates, double radius) {
        double r2 = radius * radius;
        List<Candidate> others = new ArrayList<>();
        for (Candidate c : candidates) {
            if (c.id().equals(trigger.id()) || !c.eligible() || !c.onSand() || c.inEvent()) {
                continue;
            }
            double dx = c.x() - trigger.x();
            double dz = c.z() - trigger.z();
            if (dx * dx + dz * dz <= r2) {
                others.add(c);
            }
        }
        others.sort(RANKING);
        List<Candidate> list = new ArrayList<>();
        list.add(trigger);
        list.addAll(others);
        return list;
    }

    /** The beings to start an event for this tick, highest score first (each is a trigger if it has reached the threshold). */
    static List<Candidate> triggers(Collection<Candidate> candidates, double threshold) {
        List<Candidate> list = new ArrayList<>();
        for (Candidate c : candidates) {
            // A totem never triggers by its score: it starts (or joins) an event when it lands.
            if (c.kind() != BeingKind.TOTEM && c.eligible() && c.onSand() && !c.inEvent() && c.score() >= threshold) {
                list.add(c);
            }
        }
        list.sort(RANKING);
        return list;
    }

    /**
     * Stalk length: {@code max(min, base - step * players)}. An entity trigger (anything but a player) gets
     * {@code entitySeconds} whatever the list size, so a mob cannot accidentally call a worm that kills a player.
     */
    static double stalkSeconds(BeingKind trigger, int players, double base, double step, double min, double entitySeconds) {
        if (trigger != BeingKind.PLAYER) {
            return entitySeconds;
        }
        return Math.max(min, base - step * players);
    }

    /** One list member as the end of the stalk sees it. */
    record Ranked(UUID id, double score, long recency, boolean onSand) {
    }

    /**
     * The target: the member with the highest score that is on sand right now (ties: the most recent gainer). A
     * member off the sand cannot be chosen however high the score. Null if nobody is on sand.
     */
    static UUID chooseTarget(Collection<Ranked> members) {
        Ranked best = null;
        for (Ranked m : members) {
            if (!m.onSand()) {
                continue;
            }
            if (best == null || m.score() > best.score() || (m.score() == best.score() && m.recency() > best.recency())) {
                best = m;
            }
        }
        return best == null ? null : best.id();
    }
}
