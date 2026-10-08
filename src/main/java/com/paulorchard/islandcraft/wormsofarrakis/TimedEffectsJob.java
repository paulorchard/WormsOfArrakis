package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.ParticleUtil;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plays scripted particle waves (burst) or re-triggered one-shot sounds (rumble), spread over time.
 * Nothing here needs undoing; being a job just lets it run across ticks and stop with its player.
 */
final class TimedEffectsJob implements WormTestSystem.Job {

    /** One thing to do at a time offset: either a particle wave or a sound. */
    static final class Step {
        final float at;
        final Runnable action;

        Step(float at, Runnable action) {
            this.at = at;
            this.action = action;
        }
    }

    private final World world;
    private final UUID owner;
    private final String kind;
    private final List<Step> steps = new ArrayList<>();
    private float elapsed;
    private int next;

    TimedEffectsJob(World world, UUID owner, String kind) {
        this.world = world;
        this.owner = owner;
        this.kind = kind;
    }

    TimedEffectsJob at(float seconds, Runnable action) {
        steps.add(new Step(seconds, action));
        steps.sort((a, b) -> Float.compare(a.at, b.at));
        return this;
    }

    @Override
    public World world() {
        return world;
    }

    @Override
    public UUID owner() {
        return owner;
    }

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        elapsed += dt;
        while (next < steps.size() && steps.get(next).at <= elapsed) {
            steps.get(next++).action.run();
        }
        return next < steps.size();
    }

    @Override
    public void abort(Store<EntityStore> store) {
        // Particles and one-shot sounds already sent cannot be recalled; the rest is simply not played.
    }

    /** All the players of the world, as entity references, for the particle calls. */
    static List<Ref<EntityStore>> everyone(World world) {
        List<Ref<EntityStore>> refs = new ArrayList<>();
        for (PlayerRef p : world.getPlayerRefs()) {
            Ref<EntityStore> ref = p.getReference();
            if (ref != null && ref.isValid()) {
                refs.add(ref);
            }
        }
        return refs;
    }

    static void particle(World world, Store<EntityStore> store, String system, double x, double y, double z,
                         float scale, Color color, float visibleDistance) {
        ParticleUtil.spawnParticleEffect(system, x, y, z, 0f, 0f, 0f, scale, color, null, everyone(world), store,
                visibleDistance);
    }

    static void sound(PlayerRef player, String soundEventId, float volume, float pitch) {
        int index = SoundEvent.getAssetMap().getIndex(soundEventId);
        if (index != Integer.MIN_VALUE) {
            SoundUtil.playSoundEvent2dToPlayer(player, index, SoundCategory.SFX, volume, pitch);
        }
    }
}
