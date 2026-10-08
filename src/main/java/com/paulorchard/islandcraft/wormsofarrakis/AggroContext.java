package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.protocol.MovementStates;
import com.hypixel.hytale.server.core.universe.PlayerRef;

/** What an {@link AggroSource} predicate gets to look at: one player's movement over one world tick. */
public final class AggroContext {

    private final PlayerRef player;
    private final MovementStates states;
    private final double distance;
    private final double speed;
    private final boolean airborneAfterJump;

    AggroContext(PlayerRef player, MovementStates states, double distance, double speed, boolean airborneAfterJump) {
        this.player = player;
        this.states = states;
        this.distance = distance;
        this.speed = speed;
        this.airborneAfterJump = airborneAfterJump;
    }

    public PlayerRef getPlayer() {
        return player;
    }

    /** The player's movement flags (walking, running, sprinting, crouching, jumping, onGround ...). */
    public MovementStates getStates() {
        return states;
    }

    /** Horizontal blocks travelled since the last tick. */
    public double getDistance() {
        return distance;
    }

    /** Horizontal blocks per second over the last tick. */
    public double getSpeed() {
        return speed;
    }

    /** True from a jump until the player lands. */
    public boolean isAirborneAfterJump() {
        return airborneAfterJump;
    }
}
