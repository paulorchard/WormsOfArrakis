package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.system.tick.TickingSystem;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Runs the throwaway test effects once per world tick. Every effect is a Job that knows how to undo itself,
 * so one place can restore everything: when a job ends, when a player leaves, and when the plugin shuts down.
 */
public class WormTestSystem extends TickingSystem<EntityStore> {

    interface Job {
        World world();

        UUID owner();

        String kind();

        /** Returns false when the job is finished. Runs on the world thread. */
        boolean tick(float dt, Store<EntityStore> store);

        /** Undoes the job's effect. Called once, on the world thread where possible. */
        void abort(Store<EntityStore> store);
    }

    private final List<Job> jobs = new CopyOnWriteArrayList<>();

    void add(Job job) {
        jobs.add(job);
    }

    /** Ends the owner's jobs of this kind, undoing them. Returns how many there were. */
    int stop(UUID owner, String kind) {
        int n = 0;
        for (Job job : jobs) {
            if (job.owner().equals(owner) && job.kind().equals(kind)) {
                finish(job);
                n++;
            }
        }
        return n;
    }

    /** The player has left: nothing to undo on a client that is gone, but the job must not run on. */
    void dropFor(UUID owner) {
        for (Job job : jobs) {
            if (job.owner().equals(owner)) {
                finish(job);
            }
        }
    }

    void abortAll() {
        for (Job job : jobs) {
            finish(job);
        }
    }

    private void finish(Job job) {
        if (!jobs.remove(job)) {
            return;
        }
        try {
            job.abort(job.world().getEntityStore().getStore());
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("Could not undo %s", job.kind());
        }
    }

    @Override
    public void tick(float dt, int index, Store<EntityStore> store) {
        World world = store.getExternalData().getWorld();
        for (Job job : jobs) {
            if (job.world() != world) {
                continue;
            }
            boolean keep;
            try {
                keep = job.tick(dt, store);
            } catch (Throwable t) {
                WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("%s failed", job.kind());
                keep = false;
            }
            if (!keep) {
                finish(job);
            }
        }
    }
}
