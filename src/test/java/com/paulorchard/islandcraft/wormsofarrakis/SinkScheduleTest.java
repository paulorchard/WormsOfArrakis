package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinkScheduleTest {

    @Test
    void centreSinksByTheIncrementOnEveryStep() {
        SinkSchedule s = new SinkSchedule(new SinkSchedule.Params(), 1);
        assertArrayEquals(new int[] {2, 4, 6, 8}, s.centre().depthAfterStep);
    }

    @Test
    void ringsJoinOneAfterAnotherAndDropByScaledAmounts() {
        for (int increment = 1; increment <= 4; increment++) {
            SinkSchedule.Params p = new SinkSchedule.Params();
            p.increment = increment;
            SinkSchedule s = new SinkSchedule(p, 42 + increment);
            int lo = Math.max(1, (int) Math.round(increment * 0.5));
            int hi = (int) Math.round(increment * 1.5);
            for (SinkSchedule.Cell c : s.cells) {
                int previous = 0;
                for (int step = 1; step <= s.steps; step++) {
                    int d = c.depthAfterStep[step - 1] - previous;
                    previous = c.depthAfterStep[step - 1];
                    if (c.ring == 0) {
                        assertEquals(increment, d);
                    } else if (step <= c.ring) {
                        assertEquals(0, d, "ring " + c.ring + " must not move before step " + (c.ring + 1));
                    } else {
                        assertTrue(d >= lo && d <= hi, "drop " + d + " outside " + lo + ".." + hi);
                    }
                }
            }
        }
    }

    @Test
    void everyBlockRestsAtZeroAndSwingsAboveFirst() {
        SinkSchedule s = new SinkSchedule(new SinkSchedule.Params(), 7);
        for (SinkSchedule.Cell c : s.cells) {
            assertEquals(0, s.offsetAt(c, s.totalSeconds + 0.01));
            assertEquals(c.ring == 0 ? -2 : 0, s.offsetAt(c, 0));
            if (c.depth() > 0) {
                assertTrue(c.extremes[0] > 0 && c.extremes[0] <= Math.max(1, c.depth() - 1));
                assertEquals(0, c.extremes[c.extremes.length - 1]);
            }
        }
        // 8 px: the first rebound is round(8 * 0.75..0.88) = 6 or 7.
        int first = s.centre().extremes[0];
        assertTrue(first == 6 || first == 7);
    }

    @Test
    void sameSeedSameRipple() {
        SinkSchedule a = new SinkSchedule(new SinkSchedule.Params(), 99);
        SinkSchedule b = new SinkSchedule(new SinkSchedule.Params(), 99);
        assertEquals(a.describe(), b.describe());
        assertEquals(a.totalSeconds, b.totalSeconds);
    }

    @Test
    void depthIsCutToTheLongestModel() {
        SinkSchedule.Params p = new SinkSchedule.Params();
        p.increment = 4;
        p.rings = 5;
        SinkSchedule s = new SinkSchedule(p, 3);
        assertTrue(s.clamped);
        for (SinkSchedule.Cell c : s.cells) {
            assertTrue(c.depth() <= SinkSchedule.MAX_PX);
        }
    }
}
