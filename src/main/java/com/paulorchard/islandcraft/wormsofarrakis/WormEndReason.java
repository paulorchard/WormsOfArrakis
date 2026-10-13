package com.paulorchard.islandcraft.wormsofarrakis;

/** Why an event ended. Whichever it is, everyone in the list has their aggro set to 0 and is free again. */
public enum WormEndReason {
    /** The breach sequence ran to its end. */
    DEVOURED,
    /** Nobody in the list was on sand at the end of the stalk, everyone left, or the locked target vanished. */
    FIZZLED,
    /** Stopped by a command or by the server stopping. */
    STOPPED
}
