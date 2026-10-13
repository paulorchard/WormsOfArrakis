package com.paulorchard.islandcraft.wormsofarrakis;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The effect factor of a being: none, one, several, the cap, a missing id, run-time registration, and what it multiplies. */
class EffectMultipliersTest {

    private static final Map<String, Integer> INDEX = Map.of("Shield", 1, "Noise", 2, "Loud", 3);

    private final Map<String, Double> config = new HashMap<>(Map.of("Shield", 10.0));
    private double max = 20;
    private int lookups;

    private EffectMultipliers make() {
        return new EffectMultipliers(() -> config, () -> max, id -> {
            lookups++;
            return INDEX.getOrDefault(id, EffectMultipliers.MISSING);
        });
    }

    private static EffectMultipliers.Result eval(EffectMultipliers m, Integer... has) {
        Set<Integer> set = Set.of(has);
        return m.evaluate(set::contains);
    }

    @Test
    void noEffectIsOne() {
        EffectMultipliers.Result r = eval(make());
        assertEquals(1.0, r.factor());
        assertTrue(r.effects().isEmpty());
        assertEquals("x1", r.text());
    }

    @Test
    void oneEffectGivesItsFactor() {
        EffectMultipliers.Result r = eval(make(), 1);
        assertEquals(10.0, r.factor());
        assertEquals("x10 (Shield)", r.text());
    }

    @Test
    void severalEffectsMultiply() {
        config.put("Noise", 2.0);
        assertEquals(20.0, eval(make(), 1, 2).factor());
        assertEquals(2.0, eval(make(), 2).factor());
    }

    @Test
    void theProductIsCapped() {
        config.put("Noise", 5.0);
        config.put("Loud", 5.0);
        assertEquals(20.0, eval(make(), 1, 2, 3).factor());
        max = 100;
        assertEquals(100.0, eval(make(), 1, 2, 3).factor());
    }

    @Test
    void aMissingIdIsSkippedAndCostsNothingPerTick() {
        config.put("Not_Installed", 7.0);
        EffectMultipliers m = make();
        assertEquals(10.0, eval(m, 1).factor());
        assertEquals(java.util.List.of("Not_Installed"), m.missing());
        int after = lookups;
        for (int i = 0; i < 100; i++) {
            eval(m, 1);
        }
        assertEquals(after, lookups, "ids are looked up once, not per tick");
    }

    @Test
    void whenNothingExistsTheCheckIsSkipped() {
        config.clear();
        config.put("Not_Installed", 7.0);
        EffectMultipliers m = make();
        assertTrue(m.idle());
        assertEquals(1.0, eval(m, 1).factor());
    }

    @Test
    void runtimeRegistrationAddsAndRemoves() {
        EffectMultipliers m = make();
        assertEquals(1.0, eval(m, 2).factor());
        assertTrue(m.register("Noise", 3.0));
        assertEquals(3.0, eval(m, 2).factor());
        assertEquals(20.0, eval(m, 1, 2).factor(), "10 x 3 = 30, capped at 20");
        assertEquals(3.0, m.all().get("Noise"));
        assertTrue(m.unregister("Noise"));
        assertFalse(m.unregister("Noise"));
        assertEquals(1.0, eval(m, 2).factor());
        assertTrue(m.register("Nowhere", 4.0), "an id that does not exist is accepted and ignored");
        assertEquals(java.util.List.of("Nowhere"), m.missing());
        assertFalse(m.register("Noise", 0));
    }

    @Test
    void aConfigChangeIsPickedUp() {
        EffectMultipliers m = make();
        assertEquals(10.0, eval(m, 1).factor());
        config.put("Shield", 4.0);
        // The same map object edited in place is not seen: a reload gives a new map.
        Map<String, Double> reloaded = new HashMap<>(config);
        EffectMultipliers m2 = new EffectMultipliers(() -> reloaded, () -> max, id -> INDEX.getOrDefault(id, EffectMultipliers.MISSING));
        assertEquals(4.0, eval(m2, 1).factor());
    }

    // ------------------------------------------------------------------ what the factor multiplies

    private static final AggroCore.Rules RULES = new AggroCore.Rules(1000, 200, 1, 10, 0.5, 1.0, 45, 5, 15, 10, 5, 8, 0.25, 100, 5);

    private AggroCore core() {
        WormEvents events = new WormEvents();
        AggroCore core = new AggroCore(() -> RULES, events);
        events.addListener(core);
        core.registerSource(new AggroSource("move", 1.0, c -> true));
        core.registerSource(new AggroSource("other", 2.0, c -> false));
        return core;
    }

    private static BeingAggro player(AggroCore core) {
        BeingAggro b = core.beingOrCreate(UUID.randomUUID(), BeingKind.PLAYER);
        b.world = "w";
        b.eligible = true;
        b.onSand = true;
        return b;
    }

    @Test
    void movementAndStaminaAreMultipliedButAOneOffAddIsNot() {
        AggroCore core = core();
        BeingAggro a = player(core);
        core.apply(a, new AggroCore.Reading(true, true, 0, 0, 10, null, 10.0), 0.05);
        core.apply(a, new AggroCore.Reading(true, true, 10, 200, 9.5, null, 10.0), 0.05);
        assertEquals(100 + 5, a.score(), 1e-9, "10 blocks x10, 0.5 stamina x10");
        double before = a.score();
        core.addAggro(a.id, 25, "mod");
        assertEquals(before + 25, a.score(), 1e-9, "a one-off add is exactly what the caller gave");
    }

    @Test
    void withFactorOneNothingChangesAndDecayIsNotMultiplied() {
        AggroCore core = core();
        BeingAggro a = player(core);
        core.apply(a, new AggroCore.Reading(true, true, 10, 200, AggroCore.Reading.NO_STAMINA, null, 1.0), 0.05);
        assertEquals(10, a.score(), 1e-9);
        core.apply(a, new AggroCore.Reading(true, true, 0, 0, AggroCore.Reading.NO_STAMINA, null, 10.0), 2);
        assertEquals(8, a.score(), 1e-9, "still decay of 1 a second whatever the factor");
    }

    @Test
    void noGainOffSandOrWhenNotEligibleWhateverTheFactor() {
        AggroCore core = core();
        BeingAggro a = player(core);
        core.apply(a, new AggroCore.Reading(true, false, 10, 200, AggroCore.Reading.NO_STAMINA, null, 10.0), 0.05);
        core.apply(a, new AggroCore.Reading(false, true, 10, 200, AggroCore.Reading.NO_STAMINA, null, 10.0), 0.05);
        assertEquals(0, a.score(), 1e-9);
    }
}
