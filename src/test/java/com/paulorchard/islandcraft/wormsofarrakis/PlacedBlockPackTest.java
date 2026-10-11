package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PlacedBlockPackTest {

    @Test
    void positionsPackToDistinctKeysIncludingNegativeCoordinates() {
        Set<Long> keys = new HashSet<>();
        int n = 0;
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y < 6; y++) {
                for (int z = -3; z <= 3; z++) {
                    keys.add(PlacedBlockTracker.pack(x * 1000, y * 50, z * 1000));
                    n++;
                }
            }
        }
        assertEquals(n, keys.size());
        assertNotEquals(PlacedBlockTracker.pack(1, 2, 3), PlacedBlockTracker.pack(3, 2, 1));
        assertEquals(PlacedBlockTracker.pack(-5, 70, -9), PlacedBlockTracker.pack(-5, 70, -9));
    }
}
