package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LagMonitorTest {

    private static final long MS = 1_000_000L;

    @Test
    void steadyThirtyTicksPerSecondIsNotLagging() {
        LagMonitor m = new LagMonitor();
        long t = 0;
        for (int i = 0; i < 200; i++) {
            m.sample(t);
            t += 33 * MS + 333_333;
        }
        assertEquals(33.33, m.averageMillis(), 0.05);
        assertFalse(m.lagging(50));
    }

    @Test
    void slowTicksOverTheWindowAreLagging() {
        LagMonitor m = new LagMonitor();
        long t = 0;
        for (int i = 0; i < 100; i++) {
            m.sample(t);
            t += 80 * MS;
        }
        assertEquals(80.0, m.averageMillis(), 0.01);
        assertTrue(m.lagging(50));
    }

    @Test
    void onlyTheLastTwoSecondsCount() {
        LagMonitor m = new LagMonitor();
        long t = 0;
        for (int i = 0; i < 100; i++) {
            m.sample(t);
            t += 200 * MS; // 20 s of very slow ticks
        }
        for (int i = 0; i < 100; i++) {
            m.sample(t);
            t += 33 * MS; // then healthy again
        }
        assertEquals(33.0, m.averageMillis(), 1.0);
        assertFalse(m.lagging(50));
    }

    @Test
    void oneLongTickInAHealthyRunDoesNotTripIt() {
        LagMonitor m = new LagMonitor();
        long t = 0;
        for (int i = 0; i < 60; i++) {
            m.sample(t);
            t += (i == 30 ? 120 : 33) * MS;
        }
        assertFalse(m.lagging(50));
    }

    @Test
    void notEnoughSamplesIsNotLag() {
        LagMonitor m = new LagMonitor();
        m.sample(0);
        m.sample(500 * MS);
        assertFalse(m.lagging(50));
        assertEquals(0, new LagMonitor().averageMillis(), 0);
    }
}
