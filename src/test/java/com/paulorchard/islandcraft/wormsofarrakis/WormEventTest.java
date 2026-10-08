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
    };

    private final WormEventListener hooks = new WormEventListener() {
        public void onPhaseChange(WormEvent e, WormPhase old, WormPhase now) {
            log.add(old + ">" + now);
        }

        public void onRetarget(WormEvent e, UUID o, UUID n) {
            log.add("retarget");
        }

        public void onEnd(WormEvent e, WormEndReason r) {
            log.add("end:" + r);
        }
    };

    @BeforeEach
    void setUp() {
        for (UUID p : List.of(a, b, c)) {
            online.add(p);
            onSand.add(p);
            where.put(p, new Vector3d(0, 80, 0));
        }
    }

    private WormEvent event(UUID target, WormEvent.Timings t) {
        return new WormEvent(1, "default", List.of(a, b, c), target, new Vector3d(150, 80, 0), t, false, hooks);
    }

    private static final WormEvent.Timings T = new WormEvent.Timings(10, 4, 3, 120, false, 1.0);

    private void run(WormEvent e, double seconds) {
        for (double t = 0; t < seconds; t += 0.1) {
            e.advance(0.1, env);
        }
    }

    @Test
    void runsAllPhasesWithTheRightTimings() {
        WormEvent e = event(a, T);
        assertEquals(WormPhase.STALKING, e.getPhase());
        run(e, 9.5);
        assertEquals(WormPhase.STALKING, e.getPhase());
        run(e, 1.0); // 10.5 s
        assertEquals(WormPhase.LOCKED, e.getPhase());
        run(e, 4.0); // 14.5 s
        assertEquals(WormPhase.BREACH, e.getPhase());
        run(e, 3.0); // 17.5 s
        assertEquals(WormPhase.COOLDOWN, e.getPhase());
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
        run(e, 121);
        assertEquals(WormPhase.ENDED, e.getPhase());
        assertEquals(List.of("null>STALKING", "STALKING>LOCKED", "LOCKED>BREACH", "BREACH>COOLDOWN", "end:DEVOURED",
                "COOLDOWN>ENDED"), log);
    }

    @Test
    void wormArrivesAtTheTargetAsTheClockRunsOut() {
        WormEvent e = event(a, T);
        run(e, 9.9);
        assertTrue(e.getWormPosition().distance(where.get(a)) < 3, "worm at " + e.getWormPosition());
    }

    @Test
    void targetOnRockDuringStalkingPicksTheNextSandPlayerAndRestartsTheClock() {
        WormEvent e = event(a, T);
        run(e, 6);
        onSand.remove(a);
        e.advance(0.1, env);
        assertEquals(b, e.getTarget());
        assertEquals(WormPhase.STALKING, e.getPhase());
        assertEquals(10.0, e.getSecondsLeft(), 0.01);
        assertTrue(log.contains("retarget"));
    }

    @Test
    void retargetKeepsClockWhenConfigured() {
        WormEvent e = event(a, new WormEvent.Timings(10, 4, 3, 120, true, 1.0));
        run(e, 6);
        onSand.remove(a);
        e.advance(0.1, env);
        assertEquals(b, e.getTarget());
        assertEquals(3.9, e.getSecondsLeft(), 0.2);
    }

    @Test
    void everyoneOnRockFizzles() {
        WormEvent e = event(a, T);
        run(e, 2);
        onSand.clear();
        e.advance(0.1, env);
        assertEquals(WormPhase.ENDED, e.getPhase());
        assertEquals(WormEndReason.FIZZLED, e.getEndReason());
    }

    @Test
    void lockedTargetCannotEscape() {
        WormEvent e = event(a, T);
        run(e, 10.5);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        onSand.remove(a);
        run(e, 3);
        assertEquals(a, e.getTarget());
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertFalse(log.contains("retarget"));
    }

    @Test
    void targetDisconnectingWhileStalkingMovesOn() {
        WormEvent e = event(a, T);
        run(e, 2);
        online.remove(a);
        e.advance(0.1, env);
        assertEquals(b, e.getTarget());
    }

    @Test
    void targetDisconnectingWhileLockedFizzles() {
        WormEvent e = event(a, T);
        run(e, 10.5);
        online.remove(a);
        e.advance(0.1, env);
        assertEquals(WormEndReason.FIZZLED, e.getEndReason());
    }

    @Test
    void noTargetFizzlesAtOnce() {
        WormEvent e = event(null, T);
        assertEquals(WormPhase.ENDED, e.getPhase());
        assertNull(e.getTarget());
        assertEquals(List.of("end:FIZZLED"), log);
    }

    @Test
    void stopEndsAnEventAndACooldown() {
        WormEvent e = event(a, T);
        e.stop();
        assertEquals(WormEndReason.STOPPED, e.getEndReason());
        assertEquals(WormPhase.ENDED, e.getPhase());

        log.clear();
        WormEvent d = event(a, new WormEvent.Timings(1, 1, 1, 120, false, 1.0));
        run(d, 3.5);
        assertEquals(WormPhase.COOLDOWN, d.getPhase());
        d.stop();
        assertEquals(WormPhase.ENDED, d.getPhase());
        assertEquals(WormEndReason.DEVOURED, d.getEndReason());
    }

    @Test
    void breachEndsDevouredEvenThoughTheTargetIsDeadByThen() {
        WormEvent e = event(a, T);
        run(e, 14.6); // into BREACH
        assertEquals(WormPhase.BREACH, e.getPhase());
        online.remove(a); // swallowed: no longer a valid player
        run(e, 3.2);
        assertEquals(WormPhase.COOLDOWN, e.getPhase());
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
    }

    @Test
    void easeInMakesTheWormSlowAtFirst() {
        WormEvent e = new WormEvent(1, "default", List.of(a), a, new Vector3d(100, 80, 0), new WormEvent.Timings(10, 4, 3, 120, false, 2.0), false, hooks);
        run(e, 5.0); // half the time
        double covered = 100 - e.getWormPosition().x;
        assertTrue(covered > 20 && covered < 30, "covered " + covered); // a quarter, not a half
    }
}
