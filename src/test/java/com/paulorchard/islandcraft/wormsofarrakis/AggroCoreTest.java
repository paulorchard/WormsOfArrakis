package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The aggro rules for beings: gains, decay, triggering, the list, the end of an event and logging off. */
class AggroCoreTest {

    private static final double NO = AggroCore.Reading.NO_STAMINA;
    private static final AggroCore.Rules RULES = new AggroCore.Rules(100, 200, 1, 10, 0.5, 1.0, 45, 5, 15, 10, 5, 8, 0.25, 100, 5);

    private WormEvents events;
    private AggroCore core;

    @BeforeEach
    void setUp() {
        events = new WormEvents();
        core = new AggroCore(() -> RULES, events);
        events.addListener(core);
        core.registerSource(new AggroSource("move", 1.0, c -> true));
    }

    private BeingAggro being(BeingKind kind, double x, double z) {
        BeingAggro b = core.beingOrCreate(UUID.randomUUID(), kind);
        b.world = "w";
        b.eligible = true;
        b.onSand = true;
        b.position = new Vector3d(x, 80, z);
        return b;
    }

    private BeingAggro player(double x, double z) {
        return being(BeingKind.PLAYER, x, z);
    }

    /** One tick of walking {@code blocks} blocks (fast enough to count). */
    private void walk(BeingAggro b, double blocks) {
        core.apply(b, new AggroCore.Reading(b.eligible, b.onSand, blocks, blocks / 0.05, NO, null), 0.05);
    }

    private void idle(BeingAggro b, double seconds) {
        core.apply(b, new AggroCore.Reading(b.eligible, b.onSand, 0, 0, NO, null), seconds);
    }

    private void stamina(BeingAggro b, double value) {
        core.apply(b, new AggroCore.Reading(b.eligible, b.onSand, 0, 0, value, null), 0);
    }

    // ------------------------------------------------------------------ what adds aggro

    @Test
    void movementIsOnePerBlockAndAHundredBlocksTriggersAnEvent() {
        BeingAggro a = player(0, 0);
        for (int i = 0; i < 99; i++) {
            walk(a, 1);
        }
        assertEquals(99, a.score(), 1e-9);
        assertTrue(core.checkTriggers("w").isEmpty());
        walk(a, 1);
        assertEquals(100, a.score(), 1e-9);
        assertEquals("move", a.feeding().keySet().iterator().next());
        List<WormEvent> started = core.checkTriggers("w");
        assertEquals(1, started.size());
        assertEquals(List.of(a.id), started.get(0).getList());
        assertEquals(0, a.score(), 1e-9, "the list is locked and the score goes to 0");
        assertSame(started.get(0), a.event);
    }

    @Test
    void shufflingBelowTheMinimumSpeedGainsNothing() {
        BeingAggro a = player(0, 0);
        core.apply(a, new AggroCore.Reading(true, true, 0.01, 0.2, NO, null), 0.05);
        assertEquals(0, a.score(), 1e-9);
    }

    @Test
    void nothingIsGainedOffSandOrWhenNotEligible() {
        BeingAggro a = player(0, 0);
        a.onSand = false;
        walk(a, 1);
        assertEquals(0, a.score(), 1e-9);
        a.onSand = true;
        a.eligible = false;
        walk(a, 1);
        assertEquals(0, a.score(), 1e-9);
    }

    @Test
    void staminaSpentIsTheNetFallAndRegenerationIsIgnored() {
        BeingAggro a = player(0, 0);
        stamina(a, 10);
        assertEquals(0, a.score(), 1e-9, "the first reading has nothing to compare with");
        stamina(a, 5); // a jump costing 5
        assertEquals(5, a.score(), 1e-9);
        stamina(a, 5.3); // regenerating
        assertEquals(5, a.score(), 1e-9);
        stamina(a, 4.3); // spent 1 net, whether or not some regenerated in the same tick
        assertEquals(6, a.score(), 1e-9);
        stamina(a, 10); // reset to full by something else
        assertEquals(6, a.score(), 1e-9);
        assertEquals(6, a.feeding().get("stamina"), 1e-9);
    }

    @Test
    void aBeingWithNoStaminaStatGainsNothingFromIt() {
        BeingAggro a = player(0, 0);
        core.apply(a, new AggroCore.Reading(true, true, 0, 0, NO, null), 0.05);
        core.apply(a, new AggroCore.Reading(true, true, 0, 0, NO, null), 0.05);
        assertEquals(0, a.score(), 1e-9);
        assertTrue(a.feeding().isEmpty());
    }

    @Test
    void staminaAfterRespawnStartsFresh() {
        BeingAggro a = player(0, 0);
        stamina(a, 10);
        a.eligible = false; // dead
        stamina(a, 0);
        a.eligible = true; // respawned at full
        stamina(a, 10);
        stamina(a, 9);
        assertEquals(1, a.score(), 1e-9, "only the real drop after respawn counts");
    }

    @Test
    void staminaSpentOffSandDoesNotCountLater() {
        BeingAggro a = player(0, 0);
        a.onSand = false;
        stamina(a, 10);
        stamina(a, 4);
        a.onSand = true;
        stamina(a, 4);
        assertEquals(0, a.score(), 1e-9);
    }

    @Test
    void aJumpAndASprintAddTheirStaminaOnTopOfMovement() {
        BeingAggro a = player(0, 0);
        core.apply(a, new AggroCore.Reading(true, true, 0, 0, 10, null), 0.05);
        core.apply(a, new AggroCore.Reading(true, true, 0.3, 6, 9.9, null), 0.05);
        assertEquals(0.3 + 0.1, a.score(), 1e-9);
    }

    @Test
    void oneOffAddsAndNegativeAddsFloorAtZero() {
        BeingAggro a = player(0, 0);
        assertTrue(core.addAggro(a.id, 25, "mod"));
        assertEquals(25, a.score(), 1e-9);
        core.addAggro(a.id, -100, "mod");
        assertEquals(0, a.score(), 1e-9);
        assertFalse(core.addAggro(UUID.randomUUID(), 5, "mod"));
    }

    // ------------------------------------------------------------------ decay

    @Test
    void decaysOneAPerSecondOnSandAndTenTimesFasterOffIt() {
        BeingAggro a = player(0, 0);
        core.add(a, 50, "x");
        idle(a, 5);
        assertEquals(45, a.score(), 1e-9);
        a.onSand = false;
        idle(a, 2);
        assertEquals(25, a.score(), 1e-9);
        idle(a, 10);
        assertEquals(0, a.score(), 1e-9);
    }

    @Test
    void insideAnEventNothingDecaysAndTheScoreMayPassTheThreshold() {
        BeingAggro a = player(0, 0);
        BeingAggro b = player(10, 0);
        a.lastGain = 0;
        core.add(a, 100, "x");
        core.checkTriggers("w");
        assertNotNull(a.event);
        idle(b, 30);
        core.add(b, 40, "x");
        idle(b, 30);
        b.onSand = false;
        idle(b, 30);
        assertEquals(40, b.score(), 1e-9, "no decay on sand or off it while listed");
        b.onSand = true;
        for (int i = 0; i < 300; i++) {
            walk(b, 1);
        }
        assertEquals(340, b.score(), 1e-9, "no cap");
        assertEquals(1, events.all().size(), "reaching the threshold inside an event starts nothing else");
    }

    // ------------------------------------------------------------------ the list

    @Test
    void theListHoldsEveryoneOnSandWithinTheRadiusAndZeroesTheirScores() {
        BeingAggro t = player(0, 0);
        BeingAggro near = player(150, 0);
        BeingAggro onRock = player(20, 0);
        onRock.onSand = false;
        BeingAggro far = player(250, 0);
        BeingAggro busy = player(30, 0);
        BeingAggro earlier = player(31, 0);
        core.add(t, 100, "x");
        core.add(near, 30, "x");
        core.add(onRock, 60, "x");
        core.add(far, 70, "x");
        core.add(busy, 20, "x");
        WormEvent first = core.startEvent(earlier, true, true, Double.NaN, Double.NaN);
        busy.event = first; // pretend busy is in the first one too
        WormEvent e = core.checkTriggers("w").get(0);
        assertEquals(List.of(t.id, near.id), e.getList());
        assertEquals(0, t.score(), 1e-9);
        assertEquals(0, near.score(), 1e-9);
        assertEquals(60, onRock.score(), 1e-9, "on rock when the list is locked: not in it, aggro unchanged");
        assertEquals(70, far.score(), 1e-9);
        assertEquals(20, busy.score(), 1e-9);
        assertSame(e, near.event);
        assertNull(onRock.event);
    }

    @Test
    void nobodyJoinsLater() {
        BeingAggro t = player(0, 0);
        core.add(t, 100, "x");
        WormEvent e = core.checkTriggers("w").get(0);
        BeingAggro late = player(5, 0);
        events.tick("w", 0.05, core.env("w"));
        assertFalse(e.hasMember(late.id));
        assertEquals(List.of(t.id), e.getList());
    }

    @Test
    void stalkLengthFollowsThePlayersInTheList() {
        BeingAggro t = player(0, 0);
        assertEquals(40, core.startEvent(t, true, false, Double.NaN, Double.NaN).getStalkSeconds(), 1e-9);
        events.stop(null);
        BeingAggro b = player(1, 0);
        t.eligible = true;
        assertEquals(35, core.startEvent(t, true, false, Double.NaN, Double.NaN).getStalkSeconds(), 1e-9);
        assertTrue(events.eventOf(b.id) != null);
    }

    @Test
    void anEntityTriggerGetsTheShortStalkAndIsTheGuaranteedTarget() {
        BeingAggro mob = being(BeingKind.MOB, 0, 0);
        player(5, 0);
        player(6, 0);
        player(7, 0);
        WormEvent e = core.startEvent(mob, false, false, Double.NaN, Double.NaN);
        assertEquals(10, e.getStalkSeconds(), 1e-9);
        assertEquals(mob.id, e.getTarget());
        assertTrue(e.isGuaranteed());
        assertEquals(4, e.getList().size());
    }

    @Test
    void anOverrideChangesTheStalkAndTheLock() {
        BeingAggro t = player(0, 0);
        WormEvent e = core.startEvent(t, true, false, 2, 1);
        assertEquals(2, e.getStalkSeconds(), 1e-9);
        assertEquals(1, e.getLockSeconds(), 1e-9);
    }

    // ------------------------------------------------------------------ several events

    @Test
    void aBeingIsInOneEventAndAFreeBeingFarAwayStartsItsOwn() {
        BeingAggro a = player(0, 0);
        BeingAggro b = player(10, 0);
        BeingAggro far = player(600, 0);
        core.add(a, 100, "x");
        WormEvent first = core.checkTriggers("w").get(0);
        assertEquals(List.of(a.id, b.id), first.getList());
        core.add(a, 200, "x"); // in an event: gains continue, no second event
        assertTrue(core.checkTriggers("w").isEmpty());
        core.add(far, 100, "x");
        List<WormEvent> started = core.checkTriggers("w");
        assertEquals(1, started.size());
        WormEvent second = started.get(0);
        assertEquals(List.of(far.id), second.getList());
        assertNotSame(first, second);
        assertEquals(2, events.all().size());
        assertSame(second, far.event);
        assertSame(first, a.event);
    }

    @Test
    void severalReachingTheThresholdTogetherTakeTheHighestFirstAndTheOthersJoinIt() {
        BeingAggro low = player(0, 0);
        BeingAggro high = player(10, 0);
        core.add(low, 100, "x");
        core.add(high, 120, "x");
        List<WormEvent> started = core.checkTriggers("w");
        assertEquals(1, started.size(), "the second is already in the first's list, so not a trigger");
        assertEquals(high.id, started.get(0).getTrigger());
        assertTrue(started.get(0).hasMember(low.id));
    }

    // ------------------------------------------------------------------ the end of an event

    @Test
    void whenItEndsTheListIsZeroedAndFreeAndOthersAreUntouched() {
        BeingAggro a = player(0, 0);
        BeingAggro b = player(10, 0);
        BeingAggro rock = player(20, 0);
        rock.onSand = false;
        core.add(a, 100, "x");
        core.add(rock, 70, "x");
        WormEvent e = core.checkTriggers("w").get(0);
        core.add(a, 45, "x");
        core.add(b, 15, "x");
        events.stop(null);
        assertFalse(e.isLive());
        assertEquals(0, a.score(), 1e-9);
        assertEquals(0, b.score(), 1e-9);
        assertNull(a.event);
        assertNull(b.event);
        assertEquals(70, rock.score(), 1e-9);
        // no cooldown: free again at once
        core.add(a, 100, "x");
        assertEquals(1, core.checkTriggers("w").size());
    }

    @Test
    void aFizzledEventAlsoZeroesTheList() {
        BeingAggro a = player(0, 0);
        core.add(a, 100, "x");
        core.checkTriggers("w");
        a.onSand = false;
        core.add(a, 50, "x");
        for (int i = 0; i < 900; i++) {
            events.tick("w", 0.05, core.env("w"));
        }
        assertTrue(events.all().isEmpty());
        assertEquals(0, a.score(), 1e-9);
        assertNull(a.event);
    }

    @Test
    void aMemberWhoLeavesTheWorldIsRemovedAndZeroed() {
        BeingAggro a = player(0, 0);
        BeingAggro b = player(10, 0);
        core.add(a, 100, "x");
        WormEvent e = core.checkTriggers("w").get(0);
        core.add(b, 30, "x");
        b.eligible = false; // died, left, went Creative
        events.tick("w", 0.05, core.env("w"));
        assertEquals(List.of(a.id), e.getList());
        assertNull(b.event);
        assertEquals(0, b.score(), 1e-9);
    }

    // ------------------------------------------------------------------ logging off

    @Test
    void loggingOffInAnEventKeepsNinetyNineForTheReturnAndOneBlockTriggers() {
        BeingAggro a = player(0, 0);
        BeingAggro b = player(10, 0);
        core.add(a, 100, "x");
        WormEvent e = core.checkTriggers("w").get(0);
        core.add(b, 12, "x");
        core.logOff(b.id);
        assertEquals(List.of(a.id), e.getList());
        assertNull(core.being(b.id));
        assertEquals(99, core.keptFor(b.id), 1e-9);

        BeingAggro back = core.beingOrCreate(b.id, BeingKind.PLAYER);
        back.world = "w";
        back.eligible = true;
        back.onSand = true;
        back.position = new Vector3d(10, 80, 0);
        assertEquals(99, back.score(), 1e-9);
        assertNull(core.keptFor(b.id));
        assertTrue(core.checkTriggers("w").isEmpty() || events.eventOf(b.id) == null);
        walk(back, 1);
        assertEquals(100, back.score(), 1e-9);
        List<WormEvent> started = core.checkTriggers("w");
        assertEquals(1, started.size());
        assertEquals(b.id, started.get(0).getTrigger());
    }

    @Test
    void theKeptScoreDecaysLikeAnyOtherOnceBackOutsideAnEvent() {
        BeingAggro a = player(0, 0);
        core.add(a, 100, "x");
        core.checkTriggers("w");
        core.logOff(a.id);
        BeingAggro back = core.beingOrCreate(a.id, BeingKind.PLAYER);
        back.onSand = true;
        idle(back, 10);
        assertEquals(89, back.score(), 1e-9);
    }

    @Test
    void loggingOffOutsideAnEventLosesTheScore() {
        BeingAggro a = player(0, 0);
        core.add(a, 60, "x");
        core.logOff(a.id);
        assertNull(core.keptFor(a.id));
        assertEquals(0, core.beingOrCreate(a.id, BeingKind.PLAYER).score(), 1e-9);
    }

    @Test
    void theLockedTargetLoggingOffFizzlesTheEvent() {
        BeingAggro a = player(0, 0);
        core.add(a, 100, "x");
        WormEvent e = core.checkTriggers("w").get(0);
        for (int i = 0; i < 40 * 20 + 20; i++) {
            events.tick("w", 0.05, core.env("w"));
        }
        assertEquals(WormPhase.LOCKED, e.getPhase());
        core.logOff(a.id);
        events.tick("w", 0.05, core.env("w"));
        assertEquals(WormEndReason.FIZZLED, e.getEndReason());
    }
}
