package com.paulorchard.islandcraft.wormsofarrakis;

import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The live worm events and the hooks. Later prompts read events from here and register a
 * {@link WormEventListener}. Ticked by the brain once per world tick.
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
        public void onRetarget(WormEvent event, UUID oldTarget, UUID newTarget) {
            each(l -> l.onRetarget(event, oldTarget, newTarget));
        }

        @Override
        public void onEnd(WormEvent event, WormEndReason reason) {
            each(l -> l.onEnd(event, reason));
        }
    };

    private WormEvents() {
    }

    private void each(Consumer<WormEventListener> call) {
        for (WormEventListener listener : listeners) {
            try {
                call.accept(listener);
            } catch (Throwable t) {
                WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("A worm event listener failed");
            }
        }
    }

    /** Registers a hook. The built-in logger and score handling are registered first. */
    public void addListener(WormEventListener listener) {
        listeners.add(listener);
    }

    public void removeListener(WormEventListener listener) {
        listeners.remove(listener);
    }

    /** Every event not yet ENDED, including groups in COOLDOWN. */
    public List<WormEvent> all() {
        return new ArrayList<>(events);
    }

    /** The event, in any phase but ENDED, that this player is a member of; null if none. */
    public WormEvent eventOf(UUID player) {
        for (WormEvent e : events) {
            if (e.isLive() && e.hasMember(player)) {
                return e;
            }
        }
        return null;
    }

    /** Creates an event. If {@code target} is null it ends FIZZLED at once. */
    WormEvent start(String world, List<UUID> ranking, UUID target, Vector3d wormStart, WormEvent.Timings timings,
                    boolean forced) {
        WormEvent event = new WormEvent(nextId++, world, ranking, target, wormStart, timings, forced, dispatcher);
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

    /** Ends the player's event (STOPPED), or all events if null. Returns how many were ended. */
    int stop(UUID player) {
        int n = 0;
        for (WormEvent e : events) {
            if (player == null || e.hasMember(player)) {
                e.stop();
                events.remove(e);
                n++;
            }
        }
        return n;
    }
}
