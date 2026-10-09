package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WormVolumeTest {

    @Test
    void uprightWormScalesWithSize() {
        for (double scale : new double[] {1, 3, 6, 9}) {
            WormVolume v = new WormVolume();
            v.set(new Vector3d(0, 0, 0), 0, 1, 0, 10 * scale, 3 * scale);
            assertTrue(v.contains(3 * scale - 0.01, 0, 0), "inside the edge at scale " + scale);
            assertFalse(v.contains(3 * scale + 0.01, 0, 0), "outside the edge at scale " + scale);
            assertTrue(v.contains(0, 10 * scale - 0.01, 0));
            assertFalse(v.contains(0, 10 * scale + 0.01, 0));
        }
    }

    @Test
    void tiltedWormLeansTowardsTheHeading() {
        WormVolume v = new WormVolume();
        v.set(new Vector3d(0, 0, 0), 0, 1, Math.PI / 2, 10, 3); // lying along +z
        assertTrue(v.contains(0, 0, 9));
        assertFalse(v.contains(0, 0, -11));
        assertFalse(v.contains(0, 9, 0));
        assertTrue(v.contains(0, 2.9, 5));
        v.set(new Vector3d(0, 0, 0), 0, 1, Math.PI / 4, 10, 3);
        assertTrue(v.contains(0, 7, 7));
        assertFalse(v.contains(0, 7, -7));
    }

    @Test
    void bodyIsTestedFeetToHead() {
        WormVolume v = new WormVolume();
        v.set(new Vector3d(0, 0, 0), 0, 1, 0, 10, 3);
        assertTrue(v.touches(0, 9, 0, 1.8)); // feet 1 block under the top, head above it
        assertFalse(v.touches(0, 10.5, 0, 1.8)); // standing on the top (feet above)
    }
}
