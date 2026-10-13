package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The worm event through the real listeners (start boom and dust, rumble, stalk pulses, the end), as far as it can be run
 * with no player: two mob-kind beings stand on the sand, one of them triggers an entity event, and the event is ticked by
 * hand for its whole length. The breach is the real thing only with an entity to take; here the stalk, the list and the end
 * are the real code.
 */
final class EventSelfTest {

    private EventSelfTest() {
    }

    static void run(Consumer<String> say, World world, Store<EntityStore> store, Vector3d spot) {
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        AggroCore core = plugin.aggro().core();
        WormEvents events = WormEvents.get();
        WormEffects effects = plugin.effects();
        String name = world.getName();
        List<BeingAggro> beings = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            BeingAggro b = core.beingOrCreate(UUID.randomUUID(), BeingKind.MOB);
            b.world = name;
            b.eligible = true;
            b.onSand = true;
            b.position = new Vector3d(spot.x + i * 2, spot.y, spot.z);
            beings.add(b);
        }
        BeingAggro trigger = beings.get(0);
        BeingAggro other = beings.get(1);
        core.add(other, 40, "selftest");
        int pulsesBefore = effects.pulseCount;
        List<String> phases = new ArrayList<>();
        WormEventListener probe = new WormEventListener() {
            double clock;

            @Override
            public void onPhaseChange(WormEvent e, WormPhase old, WormPhase now) {
                phases.add(String.format(Locale.ROOT, "%s@%.1f", now, clock));
            }

            @Override
            public void onTick(WormEvent e) {
                clock += 1 / 30.0;
            }
        };
        events.addListener(probe);
        WormEvent event = core.startEvent(trigger, false, false, Double.NaN, Double.NaN);
        boolean zeroed = trigger.score() == 0 && other.score() == 0;
        int listSize = event.getList().size();
        double stalk = event.getStalkSeconds();
        int ticks = 0;
        try {
            while (event.isLive() && ticks < 30 * 60) {
                effects.setDt(1 / 30.0);
                events.tick(name, 1 / 30.0, core.env(name));
                effects.tick(world, store, 1 / 30.0);
                if (event.isLive()) {
                    core.add(other, 0.01, "selftest"); // gains inside the event: no decay
                }
                ticks++;
            }
        } finally {
            events.removeListener(probe);
        }
        int pulses = effects.pulseCount - pulsesBefore;
        boolean free = trigger.event == null && other.event == null && other.score() == 0 && trigger.score() == 0;
        boolean ok = zeroed && listSize == 2 && stalk == plugin.config().getEntityStalkSeconds() && event.getTarget() != null
                && event.getEndReason() == WormEndReason.DEVOURED && free && pulses >= 2;
        core.forget(trigger.id);
        core.forget(other.id);
        say.accept(String.format(Locale.ROOT,
                "event (entity trigger, list of %d): stalk %.0f s, %d stalk pulses, phases %s, ended %s after %.1f s, scores back to 0 and free: %s -> %s",
                listSize, stalk, pulses, phases, event.getEndReason(), ticks / 30.0, free, ok ? "OK" : "FAILED"));
    }
}
