package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.function.DoubleSupplier;
import java.util.function.Predicate;

/**
 * A way of gaining aggro by moving on sand: an id, a rate in aggro per block, and a test. Each tick, for a player
 * who moved on sand fast enough, the first registered source whose test passes is used, and the player gains
 * rate times the horizontal distance. Register with {@link WormAggro#registerSource}.
 */
public final class AggroSource {

    private final String id;
    private final DoubleSupplier rate;
    private final Predicate<AggroContext> test;

    public AggroSource(String id, DoubleSupplier rate, Predicate<AggroContext> test) {
        this.id = id;
        this.rate = rate;
        this.test = test;
    }

    public AggroSource(String id, double rate, Predicate<AggroContext> test) {
        this(id, () -> rate, test);
    }

    public String getId() {
        return id;
    }

    /** Aggro per block. A supplier, so a built-in source follows the config. */
    public double getRate() {
        return rate.getAsDouble();
    }

    public boolean matches(AggroContext context) {
        return test.test(context);
    }
}
