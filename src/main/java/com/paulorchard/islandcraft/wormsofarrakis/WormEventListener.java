package com.paulorchard.islandcraft.wormsofarrakis;

import java.util.UUID;

/**
 * Hooks for the effects of prompts 19 and 20. All run on the world thread. Register with
 * {@link WormEvents#addListener}. Read the event for the phase, the seconds left, the target, the group and the
 * worm's position.
 */
public interface WormEventListener {

    /** The phase changed. {@code old} is null for the first phase of an event. */
    default void onPhaseChange(WormEvent event, WormPhase old, WormPhase now) {
    }

    /** Once per world tick for every phase except ENDED, after the phase logic has run. */
    default void onTick(WormEvent event) {
    }

    /** The worm picked a new target while STALKING. */
    default void onRetarget(WormEvent event, UUID oldTarget, UUID newTarget) {
    }

    /** The event is over. For DEVOURED the group is still in COOLDOWN; for the others the phase is ENDED. */
    default void onEnd(WormEvent event, WormEndReason reason) {
    }
}
