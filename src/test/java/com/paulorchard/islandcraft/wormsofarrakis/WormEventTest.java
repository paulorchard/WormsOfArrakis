package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The phase rules, driven with a fake world. */
class WormEventTest {

    private final UUID a = UUID.randomUUID();
    private final UUID b = UUID.randomUUID();
    private final UUID c = UUID.randomUUID();

    private final Set<UUID> online = new HashSet<>();
    private final Set<UUID> onSand = new HashSet<>();
    private final Map<UUID, Double> score = new HashMap<>();
    private final Map<UUID, Long> recency = new HashMap<>();
    private final Map<UUID, Vector3d> where = new HashMap<>();
    private final List<String> log = new ArrayList<>();

    private final WormEvent.Env env = new WormEvent.Env() {
        public boolean valid(UUID p) {
            return online.contains(p);
        }

        public boolean onSand(UUID p) {
            return online.contains(p) && onSand.contains(p);
        }

        public Vector3d position(UUID p) {
            return online.contains(p) ? where.get(p) : null;
        }

        public double score(UUID p) {
            return score.getOrDefault(p, 0.0);
        }

        public long recency(UUID p) {
            return recency.getOrDefault(p, 0L);
        }
    };

    private final WormEventListener hooks = new WormEventListener() {
        public void onPhaseChange(WormEvent e, WormPhase old, WormPhase now) {
            log.add(old + ">" + now);
        }

        public void onMemberRemoved(WormEvent e, UUID who, WormEvent.Leave why) {
            log.add("left:" + why);
        }

        public void onEnd(WormEvent e, WormEndReason r) {
            log.add("end:" + r);
        }
    };

    private static final WormEvent.Timings T = new WormEvent.Timings(10, 4, 3);

    @BeforeEach
    void setUp() {
        for (UUID p : List.of(a, b, c)) {
            online.add(p);
            onSand.add(p);
            where.put(p, new Vector3d(0, 80, 0));
        }
    }

    private WormEvent event(UUID trigger, UUID guaranteed) {
        return new WormEvent(1, "default", trigger, List.of(a, b, c), guaranteed, new Vector3d(5, 80, 5), T, false, hooks);
    }

    private void run(WormEvent e, double seconds) {
        for (double t = 0; t < seconds; t += 0.1) {
            e.advance(0.1, env);
        }
    }

    @Test
    void runsAllPhasesWithTheRightTimingsAndEndsWithNoCooldown() {
        score.put(a, 10.0);
        WormEvent e = event(a, null);
        assertEquals(WormPhase.STALKING, e.getPhase());
        assertNull(e.getTarget());
        run(e, 9.7);
        assertEquals(WormPhase.STALKING, e.getPhase());
        run(e, 0.6);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(a, e.getTarget());
        run(e, 4.1);
        assertEquals(WormPhase.BREACH, e.getPhase());
        run(e, 3.1);
        assertEquals(WormPhase.ENDED, e.getPhase());
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
        assertEquals(List.of("null>STALKING", "STALKING>LOCKED", "LOCKED>BREACH", "BREACH>ENDED", "end:DEVOURED"), log);
        assertEquals(5, e.getOrigin().x, 1e-9);
    }

    @Test
    void theTargetIsTheHighestOnSandAtTheEndNotAtTheStart() {
        score.put(a, 10.0);
        score.put(b, 20.0);
        WormEvent e = event(a, null);
        run(e, 5);
        score.put(a, 160.0); // gains inside the event, past the threshold: no cap
        run(e, 5.5);
        assertEquals(a, e.getTarget());
    }

    @Test
    void aHigherScoringMemberOffSandIsNotChosenButIsChosenIfTheyReturn() {
        score.put(a, 10.0);
        score.put(b, 300.0);
        WormEvent e = event(a, null);
        onSand.remove(b);
        run(e, 9.0);
        assertTrue(e.hasMember(b), "leaving the sand does not leave the list");
        onSand.add(b);
        run(e, 1.5);
        assertEquals(b, e.getTarget());
    }

    @Test
    void offSandAtTheEndIsSkippedAndNobodyOnSandFizzles() {
        score.put(a, 10.0);
        score.put(b, 300.0);
        WormEvent e = event(a, null);
        onSand.remove(b);
        run(e, 10.5);
        assertEquals(a, e.getTarget());

        log.clear();
        WormEvent f = event(a, null);
        onSand.clear();
        run(f, 10.5);
        assertEquals(WormPhase.ENDED, f.getPhase());
        assertEquals(WormEndReason.FIZZLED, f.getEndReason());
        assertNull(f.getTarget());
        assertFalse(log.contains("STALKING>LOCKED"));
    }

    @Test
    void tieAtTheEndGoesToTheMostRecentGainer() {
        score.put(a, 50.0);
        score.put(b, 50.0);
        recency.put(a, 3L);
        recency.put(b, 9L);
        WormEvent e = event(a, null);
        run(e, 10.5);
        assertEquals(b, e.getTarget());
    }

    @Test
    void membersWhoLeaveAreRemovedAndEveryoneLeavingFizzles() {
        WormEvent e = event(a, null);
        online.remove(b);
        run(e, 0.2);
        assertEquals(List.of(a, c), e.getList());
        assertTrue(log.contains("left:GONE"));
        online.clear();
        run(e, 0.2);
        assertEquals(WormEndReason.FIZZLED, e.getEndReason());
        assertTrue(e.getList().isEmpty());
    }

    @Test
    void aTargetWhoVanishesWhileLockedFizzlesTheEvent() {
        score.put(a, 80.0);
        WormEvent e = event(a, null);
        run(e, 10.5);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        online.remove(a);
        run(e, 0.2);
        assertEquals(WormEndReason.FIZZLED, e.getEndReason());
    }

    @Test
    void aGuaranteedEntityTargetSkipsTheSelectionAndStillBreachesWhenItIsGone() {
        UUID mob = UUID.randomUUID();
        online.add(mob);
        onSand.add(mob);
        where.put(mob, new Vector3d(7, 80, 7));
        score.put(a, 500.0); // a player with a much higher score is not chosen
        WormEvent e = new WormEvent(2, "default", mob, List.of(mob, a), mob, new Vector3d(7, 80, 7), T, false, hooks);
        assertEquals(mob, e.getTarget());
        assertTrue(e.isGuaranteed());
        run(e, 5);
        online.remove(mob); // despawned or died before the end
        run(e, 5.5);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(mob, e.getTarget());
        assertEquals(7, e.getLastTargetPosition().x, 1e-9);
        run(e, 4.1);
        assertEquals(WormPhase.BREACH, e.getPhase());
        run(e, 3.1);
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
    }

    @Test
    void stopEndsItNow() {
        WormEvent e = event(a, null);
        run(e, 1);
        e.stop();
        assertEquals(WormPhase.ENDED, e.getPhase());
        assertEquals(WormEndReason.STOPPED, e.getEndReason());
        assertFalse(e.isLive());
    }

    @Test
    void secondsLeftCountsDownInThePhase() {
        WormEvent e = event(a, null);
        run(e, 3);
        assertEquals(7, e.getSecondsLeft(), 0.15);
    }
}
