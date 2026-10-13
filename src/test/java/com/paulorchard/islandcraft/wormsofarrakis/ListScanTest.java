package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Who is in the list, who triggers, how long the stalk is and who is the target. */
class ListScanTest {

    private static ListScan.Candidate c(double x, double z, boolean onSand, boolean inEvent, boolean eligible, double score, long recency) {
        return new ListScan.Candidate(UUID.randomUUID(), BeingKind.PLAYER, x, z, onSand, inEvent, eligible, score, recency);
    }

    private static ListScan.Candidate ok(double x, double z) {
        return c(x, z, true, false, true, 0, 0);
    }

    private static List<UUID> ids(List<ListScan.Candidate> list) {
        List<UUID> out = new ArrayList<>();
        list.forEach(m -> out.add(m.id()));
        return out;
    }

    @Test
    void theTriggerIsAlwaysFirstAndInTheList() {
        ListScan.Candidate t = c(0, 0, true, false, true, 100, 1);
        List<ListScan.Candidate> list = ListScan.scan(t, List.of(t), 200);
        assertEquals(List.of(t.id()), ids(list));
    }

    @Test
    void radiusIsHorizontalAndInclusive() {
        ListScan.Candidate t = ok(0, 0);
        ListScan.Candidate near = ok(199, 0);
        ListScan.Candidate edge = ok(200, 0);
        ListScan.Candidate far = ok(250, 0);
        ListScan.Candidate diagonal = ok(150, 150); // 212 away
        List<UUID> list = ids(ListScan.scan(t, List.of(t, near, edge, far, diagonal), 200));
        assertTrue(list.contains(near.id()) && list.contains(edge.id()));
        assertEquals(3, list.size());
    }

    @Test
    void onlyBeingsOnSandNotInAnEventAndEligible() {
        ListScan.Candidate t = ok(0, 0);
        ListScan.Candidate rock = c(10, 0, false, false, true, 50, 1);
        ListScan.Candidate busy = c(10, 0, true, true, true, 50, 1);
        ListScan.Candidate dead = c(10, 0, true, false, false, 50, 1);
        ListScan.Candidate fine = ok(10, 0);
        assertEquals(List.of(t.id(), fine.id()), ids(ListScan.scan(t, List.of(t, rock, busy, dead, fine), 200)));
    }

    @Test
    void othersAreRankedBestFirst() {
        ListScan.Candidate t = ok(0, 0);
        ListScan.Candidate low = c(1, 0, true, false, true, 10, 5);
        ListScan.Candidate high = c(2, 0, true, false, true, 90, 1);
        assertEquals(List.of(t.id(), high.id(), low.id()), ids(ListScan.scan(t, List.of(low, t, high), 200)));
    }

    @Test
    void triggersAreTheOnesAtTheThresholdHighestFirstAndNotInAnEvent() {
        ListScan.Candidate a = c(0, 0, true, false, true, 100, 1);
        ListScan.Candidate b = c(0, 0, true, false, true, 130, 2);
        ListScan.Candidate below = c(0, 0, true, false, true, 99.9, 3);
        ListScan.Candidate inEvent = c(0, 0, true, true, true, 500, 4);
        ListScan.Candidate rock = c(0, 0, false, false, true, 500, 5);
        ListScan.Candidate gone = c(0, 0, true, false, false, 500, 6);
        assertEquals(List.of(b.id(), a.id()), ids(ListScan.triggers(List.of(a, b, below, inEvent, rock, gone), 100)));
    }

    @Test
    void stalkLengthForOneToEightPlayers() {
        double[] expected = {40, 35, 30, 25, 20, 15, 15, 15};
        for (int n = 1; n <= 8; n++) {
            assertEquals(expected[n - 1], ListScan.stalkSeconds(BeingKind.PLAYER, n, 45, 5, 15, 10), 1e-9, "players " + n);
        }
    }

    @Test
    void anEntityTriggerAlwaysGetsTheEntityStalk() {
        for (BeingKind kind : List.of(BeingKind.MOB, BeingKind.TOTEM)) {
            for (int n = 0; n <= 8; n++) {
                assertEquals(10, ListScan.stalkSeconds(kind, n, 45, 5, 15, 10), 1e-9);
            }
        }
    }

    private static ListScan.Ranked r(double score, long recency, boolean onSand) {
        return new ListScan.Ranked(UUID.randomUUID(), score, recency, onSand);
    }

    @Test
    void targetIsTheHighestOnSand() {
        ListScan.Ranked low = r(40, 9, true);
        ListScan.Ranked high = r(70, 1, true);
        assertEquals(high.id(), ListScan.chooseTarget(List.of(low, high)));
    }

    @Test
    void tieGoesToTheMostRecentGainer() {
        ListScan.Ranked old = r(50, 3, true);
        ListScan.Ranked recent = r(50, 8, true);
        assertEquals(recent.id(), ListScan.chooseTarget(List.of(old, recent)));
        assertEquals(recent.id(), ListScan.chooseTarget(List.of(recent, old)));
    }

    @Test
    void aHigherScoreOffSandIsSkipped() {
        ListScan.Ranked off = r(500, 9, false);
        ListScan.Ranked on = r(1, 1, true);
        assertEquals(on.id(), ListScan.chooseTarget(List.of(off, on)));
    }

    @Test
    void nobodyOnSandMeansNoTarget() {
        assertNull(ListScan.chooseTarget(List.of(r(500, 9, false), r(10, 1, false))));
        assertNull(ListScan.chooseTarget(List.of()));
    }
}
