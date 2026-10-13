package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The live worm events and the hooks. Register a {@link WormEventListener} to see them. Ticked by the brain once per
 * world tick. Several events can run at once; a being is in at most one.
 */
public final class WormEvents {

    private static final WormEvents INSTANCE = new WormEvents();

    public static WormEvents get() {
        return INSTANCE;
    }

    private final List<WormEvent> events = new CopyOnWriteArrayList<>();
    private final List<WormEventListener> listeners = new CopyOnWriteArrayList<>();
    private int nextId = 1;

    /** Dispatches to every listener; one failing listener does not stop the others. */
    private final WormEventListener dispatcher = new WormEventListener() {
        @Override
        public void onPhaseChange(WormEvent event, WormPhase old, WormPhase now) {
            each(l -> l.onPhaseChange(event, old, now));
        }

        @Override
        public void onTick(WormEvent event) {
            each(l -> l.onTick(event));
        }

        @Override
        public void onMemberAdded(WormEvent event, UUID being) {
            each(l -> l.onMemberAdded(event, being));
        }

        @Override
        public void onMemberRemoved(WormEvent event, UUID being, WormEvent.Leave why) {
            each(l -> l.onMemberRemoved(event, being, why));
        }

        @Override
        public void onEnd(WormEvent event, WormEndReason reason) {
            each(l -> l.onEnd(event, reason));
        }
    };

    /** The one the plugin uses; tests make their own. */
    WormEvents() {
    }

    private void each(Consumer<WormEventListener> call) {
        for (WormEventListener listener : listeners) {
            try {
                call.accept(listener);
            } catch (Throwable t) {
                WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
                if (plugin != null) {
                    plugin.getLogger().at(Level.WARNING).withCause(t).log("A worm event listener failed");
                }
            }
        }
    }

    /** Registers a hook. The aggro manager and the built-in logger are registered first. */
    public void addListener(WormEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(WormEventListener listener) {
        listeners.remove(listener);
    }

    /** Every event not yet ENDED. */
    public List<WormEvent> all() {
        return new ArrayList<>(events);
    }

    /** The event this being is in the list of; null if none. */
    public WormEvent eventOf(UUID being) {
        for (WormEvent e : events) {
            if (e.isLive() && e.hasMember(being)) {
                return e;
            }
        }
        return null;
    }

    /** Creates an event and enters STALKING. {@code guaranteedTarget} is set for an entity trigger. */
    WormEvent start(String world, UUID trigger, List<UUID> list, UUID guaranteedTarget, Vector3d origin,
                    WormEvent.Timings timings, boolean forced) {
        WormEvent event = new WormEvent(nextId++, world, trigger, list, guaranteedTarget, origin, timings, forced, dispatcher);
        if (event.isLive()) {
            events.add(event);
        }
        return event;
    }

    /** Runs every event of a world for one tick and drops the ended ones. */
    void tick(String world, double dt, WormEvent.Env env) {
        for (WormEvent e : events) {
            if (e.getWorldName().equals(world)) {
                e.advance(dt, env);
                if (!e.isLive()) {
                    events.remove(e);
                }
            }
        }
    }

    /** Ends the being's event (STOPPED), or all events if null. Returns how many were ended. */
    int stop(UUID being) {
        int n = 0;
        for (WormEvent e : events) {
            if (being == null || e.hasMember(being)) {
                e.stop();
                events.remove(e);
                n++;
            }
        }
        return n;
    }
}
