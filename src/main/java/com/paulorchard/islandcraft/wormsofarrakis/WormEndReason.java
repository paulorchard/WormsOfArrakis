package com.paulorchard.islandcraft.wormsofarrakis;

/** Why an event ended. */
public enum WormEndReason {
    /** The breach finished with the target still there. The group then goes into cooldown. */
    DEVOURED,
    /** Nobody was left on sand (or the target vanished once locked). The group stays agitated. */
    FIZZLED,
    /** Stopped by a command or by the server stopping. */
    STOPPED
}
