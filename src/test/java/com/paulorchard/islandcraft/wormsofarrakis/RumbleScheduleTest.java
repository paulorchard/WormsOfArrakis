package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RumbleScheduleTest {

    private static RumbleSchedule schedule(long seed) {
        return new RumbleSchedule(new SinkSchedule.Params(), 1.2, 10, 1, 3, seed);
    }

    @Test
    void theIncrementRisesAcrossTheDisturbance() {
        RumbleSchedule s = schedule(1);
        assertEquals(1.0, s.incrementAt(0), 1e-9);
        assertEquals(2.0, s.incrementAt(5), 1e-9);
        assertEquals(3.0, s.incrementAt(10), 1e-9);
        assertEquals(3.0, s.incrementAt(99), 1e-9);
    }

    @Test
    void weightIsOneAtTheBlocksAndTheFloorAtTheFarCorner() {
        assertEquals(1.0, RumbleSchedule.weight(0, 40, 0.35), 1e-9);
        assertEquals(0.35, RumbleSchedule.weight(40, 40, 0.35), 1e-9);
        assertEquals(0.35, RumbleSchedule.weight(400, 40, 0.35), 1e-9);
        assertTrue(RumbleSchedule.weight(10, 40, 0.35) > RumbleSchedule.weight(30, 40, 0.35));
    }

    @Test
    void aPulseIsTheSameEveryTimeAndStaysInsideItsPeriod() {
        RumbleSchedule s = schedule(7);
        for (int cell = 0; cell < 1024; cell += 37) {
            for (int period = 0; period < 8; period++) {
                RumbleSchedule.Pulse a = s.pulse(cell, period, 0.7);
                RumbleSchedule.Pulse b = s.pulse(cell, period, 0.7);
                assertEquals(a.start, b.start, 0);
                assertTrue(a.start >= period * 1.2 - 1e-9, "starts after its period begins");
                assertTrue(a.start + a.length <= (period + 1) * 1.2 + 1e-9 || a.length > 1.2,
                        "ends before the next period (cell " + cell + " period " + period + ")");
            }
        }
    }

    @Test
    void columnsStartAtDifferentMomentsNotTogether() {
        RumbleSchedule s = schedule(3);
        java.util.Set<Long> starts = new java.util.HashSet<>();
        for (int cell = 0; cell < 200; cell++) {
            starts.add(Math.round(s.pulse(cell, 2, 1.0).start * 100));
        }
        assertTrue(starts.size() > 20, "only " + starts.size() + " distinct start times in 200 columns");
    }

    @Test
    void everyColumnComesBackToTheSurfaceAndNeverGoesPastTheModels() {
        RumbleSchedule s = schedule(11);
        for (int cell = 0; cell < 64; cell++) {
            for (double t = 0; t < 10.5; t += 1.0 / 30) {
                int o = s.offsetAt(cell, 1.0, t);
                assertTrue(Math.abs(o) <= SinkSchedule.MAX_PX, "offset " + o);
            }
            for (int period = 0; period < 8; period++) {
                RumbleSchedule.Pulse p = s.pulse(cell, period, 1.0);
                assertEquals(0, s.offsetAt(p, p.start + p.length + 0.001), "back at the surface when the pulse is over");
                assertEquals(0, s.offsetAt(p, p.start - 0.001), "at the surface before the pulse");
            }
        }
    }

    @Test
    void everyColumnMovesAtLeastAPixelInEveryPulseEvenAtTheWeakestPoint() {
        RumbleSchedule s = schedule(5);
        for (int cell = 0; cell < 100; cell++) {
            RumbleSchedule.Pulse p = s.pulse(cell, 0, RumbleSchedule.weight(1000, 40, 0.35));
            assertTrue(p.depthAfterStep[0] >= 1, "first drop at least a pixel");
            int deepest = 0;
            for (double t = p.start; t < p.start + p.length; t += 0.01) {
                deepest = Math.max(deepest, Math.abs(s.offsetAt(p, t)));
            }
            assertTrue(deepest >= 1);
        }
    }

    @Test
    void laterPulsesAreDeeperThanEarlierOnes() {
        RumbleSchedule s = schedule(9);
        double early = 0, late = 0;
        for (int cell = 0; cell < 500; cell++) {
            early += s.pulse(cell, 0, 1.0).depthAfterStep[1];
            late += s.pulse(cell, 7, 1.0).depthAfterStep[1];
        }
        assertTrue(late > early * 1.5, "late " + late + " early " + early);
    }
}
