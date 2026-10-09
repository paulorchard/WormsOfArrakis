package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * What happens when an event begins: a boom and a burst of dust where the worm starts, and a moment later a
 * sink-and-rebound ripple under every player near who is standing on sand, as the warning that a worm is about.
 * The sounds and particles are the breach's own.
 */
final class WormStartFx {

    /** Players this close to the start point feel the camera shake. */
    static final double SHAKE_RANGE = 60;

    private WormStartFx() {
    }

    static void play(World world, Store<EntityStore> store, WormEvent event, WormsOfArrakisConfig cfg,
                     WormTestSystem jobs, Set<Integer> sand) {
        Vector3d start = event.getWormPosition();
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(start.x, start.z));
        double surface = chunk == null ? start.y : chunk.getHeight((int) Math.floor(start.x) & ChunkUtil.SIZE_MASK,
                (int) Math.floor(start.z) & ChunkUtil.SIZE_MASK) + 1.0;
        Vector3d at = new Vector3d(start.x, surface, start.z);
        List<PlayerRef> hearers = hearers(world, store, event, at, cfg);
        if (hearers.isEmpty()) {
            return;
        }

        for (PlayerRef p : hearers) {
            Vector3d pos = WormEffects.position(store, p);
            boolean near = pos != null && pos.distance(at) <= SHAKE_RANGE;
            float volume = (float) (near ? cfg.getBreachBoomVolume() : cfg.getBreachFarVolume());
            BreachJob.boomSounds(p, volume);
            if (near && cfg.isCameraShake()) {
                WormEffects.shake(p, "Arrakis_Worm_Pass", cfg.getShakeStrength() * 0.6);
            }
        }
        dust(store, at, hearers, cfg);

        // A short beat after the boom, everyone near who stands on sand feels the ripple under their own feet.
        UUID owner = event.getTarget() != null ? event.getTarget() : event.getGroup().get(0);
        TimedEffectsJob job = new TimedEffectsJob(world, owner, "startfx");
        job.at((float) cfg.getStartRippleDelay(), () -> warn(world, world.getEntityStore().getStore(), event, at, cfg, sand));
        jobs.add(job);
    }

    /** Everyone within the group radius of the start point or of the target. */
    private static List<PlayerRef> hearers(World world, Store<EntityStore> store, WormEvent event, Vector3d at,
                                           WormsOfArrakisConfig cfg) {
        List<PlayerRef> list = new ArrayList<>();
        PlayerRef target = event.getTarget() == null ? null : com.hypixel.hytale.server.core.universe.Universe.get().getPlayer(event.getTarget());
        Vector3d targetPos = target == null ? null : WormEffects.position(store, target);
        for (PlayerRef p : world.getPlayerRefs()) {
            Vector3d pos = WormEffects.position(store, p);
            if (pos != null && (pos.distance(at) <= cfg.getGroupRadius()
                    || (targetPos != null && pos.distance(targetPos) <= cfg.getGroupRadius()))) {
                list.add(p);
            }
        }
        return list;
    }

    /** The breach's ring of dust and its dirt burst, at the start point, scaled by StartBoomDustScale. */
    private static void dust(Store<EntityStore> store, Vector3d at, List<PlayerRef> viewers, WormsOfArrakisConfig cfg) {
        List<Ref<EntityStore>> refs = WormEffects.refs(viewers);
        float far = (float) cfg.getGroupRadius() + 60f;
        float scale = (float) cfg.getStartBoomDustScale();
        double radius = 3.0 * cfg.getBreachWormSize() * 1.3;
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI * 2 / 12;
            double x = at.x + Math.cos(a) * radius;
            double z = at.z + Math.sin(a) * radius;
            WormEffects.particle(store, "Block_Break_Dust", x, at.y + 0.5, z, scale, far, refs);
            WormEffects.particle(store, "Block_Land_Hard_Dust", x, at.y + 0.4, z, scale * 0.8f, far, refs);
        }
        WormEffects.particle(store, "Block_Break_Dirt", at.x, at.y + 0.5, at.z, scale, far, refs);
        WormEffects.particle(store, "Block_Break_Sand", at.x, at.y + 1.5, at.z, scale * 1.5f, far, refs);
    }

    /** One ripple under each player on sand; they see their own and anyone within the view distance sees it too. */
    private static void warn(World world, Store<EntityStore> store, WormEvent event, Vector3d at, WormsOfArrakisConfig cfg,
                             Set<Integer> sand) {
        for (PlayerRef p : hearers(world, store, event, at, cfg)) {
            Vector3d pos = WormEffects.position(store, p);
            if (pos == null) {
                continue;
            }
            Boolean sandy = AggroManager.surfaceIsSand(world, pos.x, pos.z, sand);
            if (sandy == null || !sandy) {
                continue;
            }
            List<PlayerRef> viewers = new ArrayList<>(WormEffects.near(world, store, pos, cfg.getRippleViewDistance(), p));
            SinkJob.Options options = new SinkJob.Options();
            options.owner = p.getUuid();
            SinkRipple.play(world, pos, 2, options, viewers);
        }
    }
}
