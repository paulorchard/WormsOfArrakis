package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Entry point for the sink-and-rebound ripple. No worm logic in it and no dependency on the /wormtest commands.
 * Call on the world thread.
 */
final class SinkRipple {

    private SinkRipple() {
    }

    /** Lets the caller end the ripple early. Stopping restores every column at once. */
    interface Handle {
        void stop();

        boolean running();

        /** Seconds the whole ripple takes. */
        double seconds();
    }

    /**
     * The block under {@code centre} sinks ring by ring, then every block springs back. Two ripples at once are
     * allowed; where they overlap the newer one draws the column.
     *
     * @param increment pixels the centre drops per step; cut to 1 to 4
     * @param viewers   the players who see it and feel the shake
     * @return a handle, or null if the fake blocks are missing or there was no sand under the centre
     */
    static Handle play(World world, Vector3d centre, int increment, SinkJob.Options options, List<PlayerRef> viewers) {
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        WormsOfArrakisConfig cfg = plugin.config();
        SinkSchedule.Params params = SinkSchedule.Params.fromConfig(cfg);
        params.increment = SinkSchedule.clampIncrement(increment);
        if (options.rings != null) {
            params.rings = Math.max(1, Math.min(8, options.rings));
        }
        if (options.stepSeconds != null) {
            params.stepSeconds = Math.max(0.01, options.stepSeconds);
        }
        if (options.swingSeconds != null) {
            params.swingSeconds = Math.max(0.02, options.swingSeconds);
        }
        boolean shake = options.shake != null ? options.shake : cfg.isRippleShake();
        SinkJob job = SinkJob.create(world, new ArrayList<>(viewers), centre, params, plugin.sandBlockIds(), shake, options);
        if (job == null || job.cellCount() == 0) {
            return null;
        }
        WormTestSystem system = plugin.jobSystem();
        system.add(job);
        return new Handle() {
            @Override
            public void stop() {
                system.finish(job);
            }

            @Override
            public boolean running() {
                return system.isRunning(job);
            }

            @Override
            public double seconds() {
                return job.schedule().totalSeconds;
            }
        };
    }
}
