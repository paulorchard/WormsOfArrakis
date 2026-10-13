package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Mobs and totems as beings: triggering, being the target, the totem's landing cases, the registry and the track radius. */
class EntityEventTest {

    private static final double NO = AggroCore.Reading.NO_STAMINA;
    private static final AggroCore.Rules RULES = new AggroCore.Rules(100, 200, 1, 10, 0.5, 1.0, 45, 5, 15, 10, 5, 8, 0.25, 100, 5);
    private static final double DT = 0.1;

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

    /** One tick: the totems climb, then the events run (the order of the real tick). */
    private void tick(List<BeingAggro> totems) {
        for (BeingAggro t : totems) {
            if (t.eligible) {
                core.tickTotem(t, DT);
            }
        }
        events.tick("w", DT, core.env("w"));
    }

    private void run(WormEvent e, List<BeingAggro> totems, double seconds) {
        for (double t = 0; t < seconds && e.isLive(); t += DT) {
            tick(totems);
        }
    }

    private void walk(BeingAggro b, double blocks) {
        core.apply(b, new AggroCore.Reading(b.eligible, b.onSand, blocks, blocks / DT, NO, null), DT);
    }

    // ------------------------------------------------------------------ mobs

    @Test
    void aMobGainsAQuarterOfAPlayersRateAndNeedsAboutFourHundredBlocks() {
        BeingAggro player = being(BeingKind.PLAYER, 0, 0);
        BeingAggro mob = being(BeingKind.MOB, 5, 0);
        for (int i = 0; i < 100; i++) {
            walk(player, 1);
            walk(mob, 1);
        }
        assertEquals(100, player.score(), 1e-9);
        assertEquals(25, mob.score(), 1e-9);
        assertEquals("move", mob.feeding().keySet().iterator().next());
    }

    @Test
    void aMobUsesOnlyTheBuiltInMovementSourceAndHasNoStamina() {
        core.registerSource(new AggroSource("drum", 50.0, c -> c.getStates().crouching));
        BeingAggro mob = being(BeingKind.MOB, 0, 0);
        walk(mob, 1); // a mod source would read a player's movement states; the mob has none
        assertEquals(0.25, mob.score(), 1e-9);
        core.apply(mob, new AggroCore.Reading(true, true, 0, 0, 5, null), DT);
        core.apply(mob, new AggroCore.Reading(true, true, 0, 0, 1, null), DT);
        assertEquals(0.25, mob.score(), 0.2, "only decay changes it, no stamina cost is charged");
    }

    @Test
    void aMobOnRockGainsNothingAndDecaysLikeAPlayer() {
        BeingAggro mob = being(BeingKind.MOB, 0, 0);
        core.add(mob, 50, "t");
        mob.onSand = false;
        walk(mob, 1);
        assertTrue(mob.score() < 50);
        double before = mob.score();
        core.apply(mob, new AggroCore.Reading(true, false, 0, 0, NO, null), 1);
        assertEquals(before - 10, mob.score(), 1e-9, "off sand: ten times the still decay");
    }

    @Test
    void aMobAtTheThresholdTriggersAnEntityEventWithAGuaranteedTargetAndTheTenSecondStalk() {
        BeingAggro mob = being(BeingKind.MOB, 0, 0);
        BeingAggro near = being(BeingKind.PLAYER, 30, 0);
        BeingAggro farRock = being(BeingKind.PLAYER, 40, 0);
        farRock.onSand = false;
        BeingAggro bystander = being(BeingKind.PLAYER, 500, 0);
        core.add(near, 90, "t"); // far higher than the mob will ever be, and still never the target
        core.add(mob, 100, "t");
        List<WormEvent> started = core.checkTriggers("w");
        assertEquals(1, started.size());
        WormEvent e = started.get(0);
        assertEquals(10, e.getStalkSeconds(), 1e-9, "one player in the list does not make it 40");
        assertTrue(e.isGuaranteed());
        assertEquals(mob.id, e.getTarget());
        assertEquals(mob.id, e.getTrigger());
        assertEquals(List.of(mob.id, near.id), e.getList());
        assertFalse(e.hasMember(farRock.id));
        assertFalse(e.hasMember(bystander.id));
        assertEquals(0, near.score(), 1e-9);
        run(e, List.of(), 10.3);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(mob.id, e.getTarget(), "the player's score never matters");
        assertEquals(mob.id, e.getTarget());
    }

    @Test
    void aMobTargetThatDiesDuringTheStalkStillGetsItsBreachAtTheLastPosition() {
        BeingAggro mob = being(BeingKind.MOB, 12, 34);
        BeingAggro player = being(BeingKind.PLAYER, 20, 34);
        core.add(mob, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        run(e, List.of(), 4);
        core.forget(mob.id); // dies or despawns
        assertFalse(e.hasMember(mob.id));
        run(e, List.of(), 20);
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
        assertEquals(12, e.getLastTargetPosition().x, 1e-9);
        assertEquals(34, e.getLastTargetPosition().z, 1e-9);
        assertNull(player.event, "the player in the list is freed when the event ends");
    }

    @Test
    void aMobCanBeTheTargetOfAPlayerEventWhenItHasTheHighestAggro() {
        BeingAggro player = being(BeingKind.PLAYER, 0, 0);
        BeingAggro mob = being(BeingKind.MOB, 10, 0);
        BeingAggro other = being(BeingKind.PLAYER, 20, 0);
        core.add(player, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        assertEquals(45 - 5 * 2, e.getStalkSeconds(), 1e-9, "the stalk counts players only: two, not three");
        assertEquals(3, e.getList().size());
        core.add(mob, 60, "t");
        core.add(other, 30, "t");
        core.add(player, 20, "t");
        run(e, List.of(), 35.3);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(mob.id, e.getTarget());
        assertFalse(e.isGuaranteed());
    }

    @Test
    void aMobTargetThatIsGoneAtTheEndOfTheStalkOrDuringTheLockIsABreachNotAFizzle() {
        BeingAggro player = being(BeingKind.PLAYER, 0, 0);
        BeingAggro mob = being(BeingKind.MOB, 10, 7);
        core.add(player, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        core.add(mob, 80, "t");
        run(e, List.of(), 40.2);
        assertEquals(mob.id, e.getTarget());
        assertEquals(WormPhase.LOCKED, e.getPhase());
        core.forget(mob.id);
        run(e, List.of(), 20);
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
        assertEquals(10, e.getLastTargetPosition().x, 1e-9);
    }

    @Test
    void aPlayerTargetThatVanishesWhileLockedStillFizzles() {
        BeingAggro player = being(BeingKind.PLAYER, 0, 0);
        core.add(player, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        core.add(player, 5, "t");
        run(e, List.of(), 40.2);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        core.forget(player.id);
        run(e, List.of(), 1);
        assertEquals(WormEndReason.FIZZLED, e.getEndReason());
    }

    @Test
    void aMobFarFromEveryPlayerIsNotTrackedUnlessItIsInAnEvent() {
        List<Vector3d> players = List.of(new Vector3d(0, 80, 0), new Vector3d(500, 80, 0));
        assertTrue(MobTracker.trackable(new Vector3d(150, 80, 0), players, 200, false));
        assertTrue(MobTracker.trackable(new Vector3d(200, 80, 0), players, 200, false), "inclusive");
        assertTrue(MobTracker.trackable(new Vector3d(400, 80, 100), players, 200, false), "near the second player");
        assertFalse(MobTracker.trackable(new Vector3d(250, 80, 0), players, 200, false));
        assertTrue(MobTracker.trackable(new Vector3d(250, 80, 0), players, 200, true), "in an event: kept");
        assertFalse(MobTracker.trackable(new Vector3d(0, 80, 0), List.of(), 200, false), "nobody is online");
    }

    @Test
    void aMobNotSeenThisTickIsDroppedWithItsScoreUnlessItIsInAnEvent() {
        BeingAggro gone = being(BeingKind.MOB, 300, 0); // too far to be listed
        BeingAggro busy = being(BeingKind.MOB, 5, 0);
        BeingAggro player = being(BeingKind.PLAYER, 10, 0);
        core.add(gone, 40, "t");
        core.add(busy, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        gone.eligible = false;
        busy.eligible = false;
        player.eligible = false;
        assertEquals(1, core.dropUnseenMobs("w"));
        assertNull(core.being(gone.id));
        assertNotNull(core.being(busy.id));
        assertNotNull(core.being(player.id), "players are never dropped this way");
        assertTrue(e.isLive());
        // The event removes the unseen one itself on its next tick.
        events.tick("w", DT, core.env("w"));
        assertFalse(e.hasMember(busy.id));
        assertEquals(0, busy.score(), 1e-9);
        assertEquals(1, core.dropUnseenMobs("w"));
    }

    // ------------------------------------------------------------------ totems

    @Test
    void aTotemNeverTriggersByItsScore() {
        BeingAggro totem = being(BeingKind.TOTEM, 0, 0);
        core.add(totem, 500, "t");
        assertTrue(core.checkTriggers("w").isEmpty());
    }

    @Test
    void aTotemOfAFreeOwnerStartsAnEntityEventTargetingItselfAndTheOwnerGetsThePulses() {
        BeingAggro owner = being(BeingKind.PLAYER, 0, 0);
        BeingAggro totem = being(BeingKind.TOTEM, 4, 0);
        core.add(owner, 70, "t");
        assertEquals(AggroCore.TotemOutcome.TRIGGERED, core.landTotem(totem, owner.id));
        WormEvent e = totem.event;
        assertNotNull(e);
        assertEquals(10, e.getStalkSeconds(), 1e-9);
        assertTrue(e.isGuaranteed());
        assertEquals(totem.id, e.getTarget());
        assertEquals(List.of(totem.id, owner.id), e.getList());
        assertSame(e, owner.event);
        assertEquals(0, owner.score(), 1e-9);
    }

    @Test
    void aTotemWithNoKnownOwnerIsTreatedAsFree() {
        BeingAggro totem = new BeingAggro(UUID.randomUUID(), BeingKind.TOTEM);
        totem = core.beingOrCreate(totem.id, BeingKind.TOTEM);
        totem.world = "w";
        totem.eligible = true;
        totem.onSand = true;
        totem.position = new Vector3d(0, 80, 0);
        assertEquals(AggroCore.TotemOutcome.TRIGGERED, core.landTotem(totem, null));
        assertEquals(AggroCore.TotemOutcome.TRIGGERED, core.landTotem(being(BeingKind.TOTEM, 300, 0), UUID.randomUUID()));
    }

    @Test
    void aTotemThrownInAStalkJoinsTheListAtOneHundredAndWinsTheTargetAgainstARunningPlayer() {
        BeingAggro runner = being(BeingKind.PLAYER, 0, 0);
        core.add(runner, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        assertEquals(40, e.getStalkSeconds(), 1e-9);
        BeingAggro totem = being(BeingKind.TOTEM, 3, 0);
        List<BeingAggro> totems = List.of(totem);
        // The runner runs at about 5.5 a second from zero.
        for (int i = 0; i < 50; i++) {
            walk(runner, 5.5 * DT);
            tick(totems);
        }
        double left = e.getSecondsLeft();
        assertEquals(AggroCore.TotemOutcome.JOINED, core.landTotem(totem, runner.id));
        assertTrue(e.hasMember(totem.id));
        assertSame(e, totem.event);
        assertEquals(100, totem.score(), 1e-9);
        assertEquals(40, e.getStalkSeconds(), 1e-9, "the stalk is not restarted or changed");
        assertEquals(left, e.getSecondsLeft(), 0.2);
        assertEquals(WormPhase.STALKING, e.getPhase());
        for (int i = 0; i < 360 && e.getPhase() == WormPhase.STALKING; i++) {
            walk(runner, 5.5 * DT);
            tick(totems);
        }
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(totem.id, e.getTarget(), "100 plus five a second beats 5.5 a second from zero over the stalk");
        assertFalse(e.isGuaranteed());
    }

    @Test
    void aTotemThatClimbsToTheEndOfAStalkWinsOverAPlayerGainingLessThanItsHeadStart() {
        BeingAggro p = being(BeingKind.PLAYER, 0, 0);
        core.add(p, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        BeingAggro totem = being(BeingKind.TOTEM, 3, 0);
        core.landTotem(totem, p.id);
        core.add(p, 99, "t");
        run(e, List.of(totem), 5);
        assertTrue(totem.score() > 100 + 20);
        run(e, List.of(totem), 36);
        assertEquals(totem.id, e.getTarget(), "99 against 100 + 5 a second");
    }

    @Test
    void aTotemThrownWhileTheOwnersEventIsLockedOrBreachingDoesNothing() {
        BeingAggro p = being(BeingKind.PLAYER, 0, 0);
        core.add(p, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        run(e, List.of(), 40.2);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        BeingAggro totem = being(BeingKind.TOTEM, 3, 0);
        assertEquals(AggroCore.TotemOutcome.IGNORED, core.landTotem(totem, p.id));
        assertFalse(e.hasMember(totem.id));
        assertNull(totem.event);
        assertEquals(p.id, e.getTarget());
        run(e, List.of(), 5);
        assertEquals(WormPhase.BREACH, e.getPhase());
        BeingAggro late = being(BeingKind.TOTEM, 3, 0);
        assertEquals(AggroCore.TotemOutcome.IGNORED, core.landTotem(late, p.id));
        assertFalse(e.hasMember(late.id));
    }

    @Test
    void aSecondTotemFromSomeoneInATotemTriggeredEventJoinsIt() {
        BeingAggro owner = being(BeingKind.PLAYER, 0, 0);
        BeingAggro first = being(BeingKind.TOTEM, 3, 0);
        core.landTotem(first, owner.id);
        WormEvent e = first.event;
        BeingAggro second = being(BeingKind.TOTEM, 6, 0);
        assertEquals(AggroCore.TotemOutcome.JOINED, core.landTotem(second, owner.id));
        assertTrue(e.hasMember(second.id));
        assertEquals(10, e.getStalkSeconds(), 1e-9);
        assertEquals(first.id, e.getTarget(), "the guaranteed target does not change");
    }

    @Test
    void aTotemRemovedBeforeTheEndOfTheStalkStillGetsTheBreachAtItsLastPosition() {
        BeingAggro owner = being(BeingKind.PLAYER, 0, 0);
        BeingAggro totem = being(BeingKind.TOTEM, 8, 9);
        core.landTotem(totem, owner.id);
        WormEvent e = totem.event;
        List<BeingAggro> totems = List.of(totem);
        run(e, totems, 6);
        core.forget(totem.id); // the vanilla Slowness Totem has lived its 10 seconds
        run(e, List.of(), 5);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(totem.id, e.getTarget());
        assertEquals(8, e.getLastTargetPosition().x, 1e-9);
        assertEquals(9, e.getLastTargetPosition().z, 1e-9);
        run(e, List.of(), 12);
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
        assertNull(owner.event);
        assertEquals(0, owner.score(), 1e-9);
    }

    @Test
    void aTotemThatExpiresDuringAPlayersStalkStaysACandidateWithItsFrozenScoreAndGetsTheBreachWhereItStood() {
        BeingAggro p = being(BeingKind.PLAYER, 0, 0);
        core.add(p, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        BeingAggro totem = being(BeingKind.TOTEM, 8, 9);
        core.landTotem(totem, p.id);
        List<BeingAggro> totems = List.of(totem);
        run(e, totems, 10); // the vanilla Slowness Totem lives ten seconds
        core.forget(totem.id);
        assertTrue(e.hasMember(totem.id), "still in the list");
        core.add(p, 60, "t"); // the player gains less than the totem's frozen 150
        run(e, List.of(), 31);
        assertEquals(WormPhase.LOCKED, e.getPhase());
        assertEquals(totem.id, e.getTarget());
        assertEquals(8, e.getLastTargetPosition().x, 1e-9);
        assertEquals(9, e.getLastTargetPosition().z, 1e-9);
        run(e, List.of(), 14);
        assertEquals(WormEndReason.DEVOURED, e.getEndReason());
        assertNull(p.event);
    }

    @Test
    void anExpiredTotemLosesToAPlayerWhoHasGainedMore() {
        BeingAggro p = being(BeingKind.PLAYER, 0, 0);
        core.add(p, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        BeingAggro totem = being(BeingKind.TOTEM, 8, 9);
        core.landTotem(totem, p.id);
        run(e, List.of(totem), 10);
        core.forget(totem.id);
        core.add(p, 400, "t");
        run(e, List.of(), 31);
        assertEquals(p.id, e.getTarget());
    }

    @Test
    void aTotemKeepsClimbingInsideAnEventAndIsFreedAndZeroedAtItsEnd() {
        BeingAggro p = being(BeingKind.PLAYER, 0, 0);
        core.add(p, 100, "t");
        WormEvent e = core.checkTriggers("w").get(0);
        BeingAggro totem = being(BeingKind.TOTEM, 3, 0);
        core.landTotem(totem, p.id);
        run(e, List.of(totem), 10);
        assertEquals(150, totem.score(), 2.0);
        e.stop();
        assertNull(totem.event);
        assertEquals(0, totem.score(), 1e-9);
    }

    // ------------------------------------------------------------------ the registry

    @Test
    void theRegistryAddsAndRemovesExactIdsAndKeepsTheDefaults() {
        TotemRegistry registry = new TotemRegistry();
        registry.addAll(List.of("Slowness_Totem", "Healing_Totem"));
        assertTrue(registry.contains("Slowness_Totem"));
        assertFalse(registry.contains("Thumper"));
        assertTrue(registry.register("Thumper"));
        assertFalse(registry.register("Thumper"), "already there");
        assertTrue(registry.contains("Thumper"));
        assertFalse(registry.contains("Thumper_2"), "exact ids only, no wildcard");
        assertEquals(List.of("Healing_Totem", "Slowness_Totem", "Thumper"), registry.ids());
        assertTrue(registry.unregister("Thumper"));
        assertFalse(registry.unregister("Thumper"));
        assertFalse(registry.contains("Thumper"));
        assertFalse(registry.register(""));
        assertFalse(registry.register(null));
    }
}
