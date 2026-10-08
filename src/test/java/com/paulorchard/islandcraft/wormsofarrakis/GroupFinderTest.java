package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroupFinderTest {

    private static GroupFinder.Member m(double x, double z, double score, long recency) {
        return new GroupFinder.Member(UUID.randomUUID(), x, z, score, recency);
    }

    @Test
    void farApartPlayersAreSeparateGroups() {
        List<List<GroupFinder.Member>> groups = GroupFinder.find(List.of(m(0, 0, 10, 1), m(500, 0, 10, 2)), 200);
        assertEquals(2, groups.size());
    }

    @Test
    void nearbyPlayersShareOneTotal() {
        List<List<GroupFinder.Member>> groups =
                GroupFinder.find(List.of(m(0, 0, 40, 1), m(100, 100, 35, 2), m(50, 0, 30, 3)), 200);
        assertEquals(1, groups.size());
        assertEquals(105, GroupFinder.total(groups.get(0)), 1e-9);
    }

    @Test
    void highestScoreFirstAndTiesGoToTheMoreRecentContributor() {
        GroupFinder.Member low = m(0, 0, 10, 5);
        GroupFinder.Member high = m(1, 0, 50, 1);
        GroupFinder.Member tieOld = m(2, 0, 30, 2);
        GroupFinder.Member tieNew = m(3, 0, 30, 9);
        List<GroupFinder.Member> group = GroupFinder.find(List.of(low, tieOld, high, tieNew), 200).get(0);
        assertEquals(List.of(high, tieNew, tieOld, low), group);
    }

    /** Not an assertion on speed, just a printed cost for the log: 20 players, and 200 for scale. */
    @Test
    void cost() {
        Random random = new Random(1);
        for (int n : new int[] {20, 200}) {
            List<GroupFinder.Member> players = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                players.add(m(random.nextDouble() * 1000, random.nextDouble() * 1000, random.nextDouble() * 60, i));
            }
            for (int i = 0; i < 2000; i++) {
                GroupFinder.find(players, 200); // warm up
            }
            int runs = 20000;
            long t0 = System.nanoTime();
            int groups = 0;
            for (int i = 0; i < runs; i++) {
                groups += GroupFinder.find(players, 200).size();
            }
            double micros = (System.nanoTime() - t0) / 1000.0 / runs;
            System.out.printf("GroupFinder %d players: %.1f microseconds per call (%d groups on average)%n", n, micros,
                    groups / runs);
            assertTrue(micros < 5000);
        }
    }
}
