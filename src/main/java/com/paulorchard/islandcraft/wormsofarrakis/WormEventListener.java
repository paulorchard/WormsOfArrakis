package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.UUID;

/**
 * Hooks for the effects. All run on the world thread. Register with {@link WormEvents#addListener}. Read the event for
 * the phase, the seconds left, the target, the list and the origin.
 */
public interface WormEventListener {

    /** The phase changed. {@code old} is null for the first phase of an event. */
    default void onPhaseChange(WormEvent event, WormPhase old, WormPhase now) {
    }

    /** Once per world tick for every phase except ENDED, after the phase logic has run. */
    default void onTick(WormEvent event) {
    }

    /** A being joined the list after the event began (a totem thrown by someone in it). */
    default void onMemberAdded(WormEvent event, UUID being) {
    }

    /** A being left the list before the event ended (it died, left the world, went Creative or logged off). */
    default void onMemberRemoved(WormEvent event, UUID being, WormEvent.Leave why) {
    }

    /** The event is over and the phase is ENDED. The list is still readable. */
    default void onEnd(WormEvent event, WormEndReason reason) {
    }
}
