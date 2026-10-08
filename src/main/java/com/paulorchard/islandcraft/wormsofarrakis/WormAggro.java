package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.UUID;

/**
 * The aggro API for other mods. Sources are the ways of gaining aggro by moving on sand; the built-in ones are
 * registered through this same registry with the ids "jump", "run" and "walk" and rates from the config.
 *
 * <pre>
 * WormAggro.registerSource(new AggroSource("mymod_drum", 3.0, ctx -> ctx.getStates().crouching));
 * WormAggro.addAggro(playerRef, 25, "mymod_explosion");   // a one-off, e.g. a blast
 * </pre>
 */
public final class WormAggro {

    private WormAggro() {
    }

    private static AggroManager manager() {
        return WormsOfArrakisPlugin.get().aggro();
    }

    /** Adds a source, replacing any with the same id. Sources are tried in registration order, first match wins. */
    public static void registerSource(AggroSource source) {
        manager().registerSource(source);
    }

    /** Removes a source by id. Returns false if there was none. */
    public static boolean unregisterSource(String id) {
        return manager().unregisterSource(id);
    }

    public static List<AggroSource> sources() {
        return manager().sources();
    }

    /**
     * Adds (or with a negative amount removes) aggro. The score never goes below 0. {@code sourceId} is recorded
     * so that /worm aggro can show what is feeding the score.
     */
    public static void addAggro(PlayerRef player, double amount, String sourceId) {
        manager().addAggro(player, amount, sourceId);
    }

    /** The player's current score, 0 if they are not tracked. */
    public static double getAggro(UUID player) {
        PlayerAggro a = manager().state(player);
        return a == null ? 0 : a.score();
    }
}
