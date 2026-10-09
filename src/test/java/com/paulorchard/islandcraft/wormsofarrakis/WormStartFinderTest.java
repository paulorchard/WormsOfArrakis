package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WormStartFinderTest {

    private static WormStartFinder.Candidate c(int height, boolean crest) {
        return new WormStartFinder.Candidate(0, 0, height, true, crest);
    }

    @Test
    void nothingToChooseFrom() {
        assertNull(WormStartFinder.choose(List.of(), 2, new Random(1)));
    }

    @Test
    void picksOnlyWithinToleranceOfTheHighest() {
        List<WormStartFinder.Candidate> list = List.of(c(100, false), c(99, false), c(98, false), c(90, false), c(60, false));
        for (int seed = 0; seed < 50; seed++) {
            assertTrue(WormStartFinder.choose(list, 2, new Random(seed)).height() >= 98);
        }
    }

    @Test
    void prefersACrestAmongTheHighest() {
        List<WormStartFinder.Candidate> list = List.of(c(100, false), c(99, true), c(98, false));
        for (int seed = 0; seed < 20; seed++) {
            assertEquals(99, WormStartFinder.choose(list, 2, new Random(seed)).height());
        }
    }

    @Test
    void aLowCrestDoesNotBeatAHighPoint() {
        List<WormStartFinder.Candidate> list = List.of(c(100, false), c(80, true));
        assertEquals(100, WormStartFinder.choose(list, 2, new Random(3)).height());
    }
}
