package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.List;
import java.util.UUID;

/**
 * The aggro API for other mods. Sources are the ways of gaining aggro by moving on sand; the built-in one is
 * registered through this same registry with the id "move" (the rate is MovementAggroPerBlock). Stamina spent adds
 * aggro as the source "stamina", without a source object. A being is anything with aggro: a player, and later a mob or
 * a totem; the UUID methods work for any of them.
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

    /** The being's current score, 0 if it is not tracked. */
    public static double getAggro(UUID being) {
        BeingAggro a = manager().state(being);
        return a == null ? 0 : a.score();
    }

    /**
     * Registers a deployable config id (for example the Thumper of Equipment of Arrakis) as a totem: a decoy that,
     * landed on sand, starts with TotemStartAggro and gains TotemAggroPerSecond, and draws the worm. Call it only if
     * this mod is present. Returns false if it was already registered.
     *
     * <pre>
     * if (PluginManager.get().getPlugin(new PluginIdentifier("IslandCraft", "WormsOfArrakis")) != null) {
     *     WormAggro.registerTotem("Thumper");
     * }
     * </pre>
     */
    public static boolean registerTotem(String deployableId) {
        return manager().totemRegistry().register(deployableId);
    }

    /** Removes a deployable id from the totems. Returns false if it was not registered. */
    public static boolean unregisterTotem(String deployableId) {
        return manager().totemRegistry().unregister(deployableId);
    }

    /** The registered totem ids, sorted. */
    public static List<String> totemIds() {
        return manager().totemRegistry().ids();
    }

    /** The same for any being by UUID. Returns false if the being is not tracked. */
    public static boolean addAggro(UUID being, double amount, String sourceId) {
        return manager().addAggro(being, amount, sourceId);
    }

    /**
     * Makes an entity effect multiply the aggro a being gains from movement, stamina spent and any AggroSource while it has
     * the effect (the factors of several effects multiply, up to AggroMultiplierMax). One-off addAggro calls, a totem's own
     * growth and decay are not multiplied. Lasts until unregistered or the server stops; the config keeps the saved list.
     * An id that does not exist in this game is accepted and does nothing. Returns false for a factor that is not above 0.
     */
    public static boolean registerEffectMultiplier(String effectId, double factor) {
        return manager().effects().register(effectId, factor);
    }

    /** Removes a run-time multiplier (one in the config stays). Returns false if there was none. */
    public static boolean unregisterEffectMultiplier(String effectId) {
        return manager().effects().unregister(effectId);
    }

    /** Every effect multiplier in force, configured and registered, by effect id. */
    public static java.util.Map<String, Double> effectMultipliers() {
        return manager().effects().all();
    }
}
