package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.DoubleSupplier;
import java.util.function.IntPredicate;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Entity effects that multiply the aggro a being gains from movement, stamina and other mods' sources: the configured
 * AggroEffectMultipliers plus the ones other mods register at run time. Nothing from the engine in here; the effect index
 * lookup and the "does this being have it" test are passed in. An id the game does not know (the mod that adds it is not
 * installed) resolves to no index and is skipped for good, at no cost per tick.
 */
final class EffectMultipliers {

    /** What {@link ToIntFunction} returns for an effect id that does not exist (the asset map's own "not found"). */
    static final int MISSING = Integer.MIN_VALUE;

    /** The factor a being has now and the ids of the effects it comes from. */
    record Result(double factor, List<String> effects) {
        static final Result NONE = new Result(1.0, List.of());

        String text() {
            if (effects.isEmpty()) {
                return "x1";
            }
            return "x" + (factor == Math.rint(factor) ? String.valueOf((long) factor) : String.valueOf(factor))
                    + " (" + String.join(", ", effects) + ")";
        }
    }

    private record Entry(String id, double factor, int index) {
    }

    private final Supplier<Map<String, Double>> configured;
    private final DoubleSupplier max;
    private final ToIntFunction<String> indexOf;
    private final Map<String, Double> runtime = new ConcurrentHashMap<>();
    private volatile int runtimeVersion;

    private volatile Map<String, Double> resolvedFrom;
    private volatile int resolvedVersion = -1;
    private volatile Entry[] entries = new Entry[0];
    private volatile List<String> missing = List.of();

    EffectMultipliers(Supplier<Map<String, Double>> configured, DoubleSupplier max, ToIntFunction<String> indexOf) {
        this.configured = configured;
        this.max = max;
        this.indexOf = indexOf;
    }

    // ------------------------------------------------------------------ registry

    /** Adds or replaces a run-time multiplier. Not saved: the config is the saved list. A factor that is not above 0 is refused. */
    boolean register(String effectId, double factor) {
        if (effectId == null || effectId.isEmpty() || !(factor > 0)) {
            return false;
        }
        runtime.put(effectId, factor);
        runtimeVersion++;
        return true;
    }

    boolean unregister(String effectId) {
        boolean removed = runtime.remove(effectId) != null;
        if (removed) {
            runtimeVersion++;
        }
        return removed;
    }

    /** Every multiplier in force, configured and run-time (a run-time one wins over the same id in the config), sorted. */
    Map<String, Double> all() {
        Map<String, Double> out = new TreeMap<>();
        Map<String, Double> c = configured.get();
        if (c != null) {
            out.putAll(c);
        }
        out.putAll(runtime);
        return out;
    }

    /** The ids that are listed but do not exist in this game, as of the last resolve. */
    List<String> missing() {
        resolve();
        return missing;
    }

    /** True if no id that exists is listed, so a tick need not look at any being. */
    boolean idle() {
        resolve();
        return entries.length == 0;
    }

    /** Looks each id's index up again when the config's map or the run-time registry has changed, and only then. */
    private synchronized void resolveNow(Map<String, Double> from) {
        Map<String, Double> merged = new LinkedHashMap<>();
        if (from != null) {
            merged.putAll(from);
        }
        merged.putAll(runtime);
        List<Entry> found = new ArrayList<>();
        List<String> absent = new ArrayList<>();
        merged.forEach((id, factor) -> {
            if (id == null || factor == null || !(factor > 0)) {
                return;
            }
            int index = indexOf.applyAsInt(id);
            if (index == MISSING || index < 0) {
                absent.add(id);
            } else {
                found.add(new Entry(id, factor, index));
            }
        });
        absent.sort(null);
        missing = absent;
        entries = found.toArray(new Entry[0]);
        resolvedFrom = from;
        resolvedVersion = runtimeVersion;
    }

    private void resolve() {
        Map<String, Double> from = configured.get();
        if (from != resolvedFrom || runtimeVersion != resolvedVersion) {
            resolveNow(from);
        }
    }

    // ------------------------------------------------------------------ the factor of one being

    /**
     * The product of the factors of the listed effects the being has, limited to the maximum; 1 if none. {@code has} is
     * asked once per existing id and not at all when none exists.
     */
    Result evaluate(IntPredicate has) {
        resolve();
        Entry[] list = entries;
        if (list.length == 0) {
            return Result.NONE;
        }
        List<Double> factors = new ArrayList<>(2);
        List<String> ids = new ArrayList<>(2);
        for (Entry e : list) {
            if (has.test(e.index)) {
                factors.add(e.factor);
                ids.add(e.id);
            }
        }
        if (ids.isEmpty()) {
            return Result.NONE;
        }
        return new Result(combine(factors, max.getAsDouble()), ids);
    }

    /** The product of the factors, limited to {@code max}. */
    static double combine(List<Double> factors, double max) {
        double product = 1.0;
        for (double f : factors) {
            product *= f;
        }
        // A cap below 1 would turn a bonus into a penalty; the cap only limits, it never goes under 1.
        return Math.min(product, Math.max(1.0, max));
    }
}
