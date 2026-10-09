package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.AttachedToType;
import com.hypixel.hytale.protocol.ClientCameraView;
import com.hypixel.hytale.protocol.Direction;
import com.hypixel.hytale.protocol.Position;
import com.hypixel.hytale.protocol.PositionDistanceOffsetType;
import com.hypixel.hytale.protocol.PositionType;
import com.hypixel.hytale.protocol.RotationType;
import com.hypixel.hytale.protocol.ServerCameraSettings;
import com.hypixel.hytale.protocol.packets.camera.SetServerCamera;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The pulled-out camera of everyone near the breach except the target (whose camera TargetFx already looks after).
 * Mode "distance" eases the camera back to a distance that scales with the worm; mode "fixed" cuts to a camera placed
 * on the player's side of the worm and aimed at it. Everyone gets the normal view back, with the same packet the game's
 * /camera reset sends, when the camera returns, on death, and when the breach ends for any reason.
 */
final class BreachCamera {

    private static final class Member {
        final PlayerRef player;
        double cur;
        double clock = 1;
        double sent = -1;
        boolean on;

        Member(PlayerRef player) {
            this.player = player;
        }
    }

    private final Map<UUID, Member> members = new HashMap<>();
    /** Players who were reset (died, or the camera has returned): not put back in. */
    private final Set<UUID> done = new HashSet<>();
    private double scan = 1e9;

    /** How far back the camera goes for this worm: it scales with the worm and stops at ZoomMaxDistance. */
    static double distance(WormsOfArrakisConfig cfg) {
        return Math.min(cfg.getZoomMaxDistance(), cfg.getZoomWormHeightFactor() * WormJob.HEIGHT * cfg.getBreachWormSize());
    }

    /**
     * @param skip    the target, whose zoom is TargetFx's
     * @param active  false once the camera is on its way back
     */
    void tick(World world, Store<EntityStore> store, WormsOfArrakisConfig cfg, double dt, Vector3d centre, Vector3d heading,
              double wormHeight, double cameraRadius, UUID skip, boolean active) {
        if (!cfg.isCameraZoom()) {
            return;
        }
        boolean fixed = cfg.getBreachCameraMode().equals("fixed");
        scan += dt;
        if (active && scan >= 0.5) {
            scan = 0;
            for (PlayerRef p : world.getPlayerRefs()) {
                Ref<EntityStore> ref = p.getReference();
                if (ref == null || !ref.isValid() || p.getUuid().equals(skip) || members.containsKey(p.getUuid())
                        || done.contains(p.getUuid()) || BreachHold.isDead(store, ref)) {
                    continue;
                }
                Vector3d at = WormEffects.position(store, p);
                if (at != null && Math.hypot(at.x - centre.x, at.z - centre.z) <= cameraRadius) {
                    members.put(p.getUuid(), new Member(p));
                }
            }
        }
        double seconds = Math.max(cfg.getZoomSeconds(), 0.05);
        double far = distance(cfg);
        for (Member m : new ArrayList<>(members.values())) {
            Ref<EntityStore> ref = m.player.getReference();
            if (ref == null || !ref.isValid()) {
                members.remove(m.player.getUuid());
                continue;
            }
            if (BreachHold.isDead(store, ref)) {
                reset(m);
                members.remove(m.player.getUuid());
                done.add(m.player.getUuid());
                continue;
            }
            m.clock += dt;
            if (fixed) {
                if (active && !m.on) {
                    sendFixed(world, store, m, centre, heading, wormHeight, far);
                } else if (!active) {
                    reset(m);
                    members.remove(m.player.getUuid());
                    done.add(m.player.getUuid());
                }
                continue;
            }
            m.cur = active ? Math.min(1, m.cur + dt / seconds) : Math.max(0, m.cur - dt / seconds);
            if (!active && m.cur <= 0) {
                reset(m);
                members.remove(m.player.getUuid());
                done.add(m.player.getUuid());
                continue;
            }
            double q = m.cur * m.cur * (3 - 2 * m.cur);
            double d = cfg.getZoomFromDistance() + (far - cfg.getZoomFromDistance()) * q;
            if (Math.abs(d - m.sent) >= 0.05 && m.clock >= 0.05) {
                m.clock = 0;
                m.sent = d;
                m.on = true;
                ServerCameraSettings settings = new ServerCameraSettings();
                settings.isFirstPerson = false;
                settings.distance = (float) d;
                settings.eyeOffset = true;
                settings.positionLerpSpeed = 0.3f;
                settings.positionDistanceOffsetType = PositionDistanceOffsetType.DistanceOffsetRaycast;
                m.player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, settings));
            }
        }
    }

    /** A camera on the player's side of the worm at about its middle height, above the terrain, aimed at the worm. */
    private void sendFixed(World world, Store<EntityStore> store, Member m, Vector3d centre, Vector3d heading,
                           double wormHeight, double distance) {
        Vector3d at = WormEffects.position(store, m.player);
        double dx = at == null ? -heading.x : at.x - centre.x;
        double dz = at == null ? -heading.z : at.z - centre.z;
        double len = Math.hypot(dx, dz);
        if (len < 1e-3) {
            dx = -heading.x;
            dz = -heading.z;
            len = Math.max(1e-3, Math.hypot(dx, dz));
        }
        double cx = centre.x + dx / len * distance;
        double cz = centre.z + dz / len * distance;
        double cy = centre.y + wormHeight * 0.5;
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(cx, cz));
        if (chunk != null) {
            int ground = chunk.getHeight((int) Math.floor(cx) & ChunkUtil.SIZE_MASK, (int) Math.floor(cz) & ChunkUtil.SIZE_MASK);
            cy = Math.max(cy, ground + 3.0);
        }
        double tx = centre.x;
        double ty = centre.y + wormHeight * 0.5;
        double tz = centre.z;
        double horizontal = Math.hypot(tx - cx, tz - cz);
        ServerCameraSettings settings = new ServerCameraSettings();
        settings.isFirstPerson = false;
        settings.attachedToType = AttachedToType.None;
        settings.positionType = PositionType.Custom;
        settings.position = new Position(cx, cy, cz);
        settings.rotationType = RotationType.Custom;
        settings.rotation = new Direction((float) Math.atan2(-(tx - cx), -(tz - cz)), (float) Math.atan2(ty - cy, horizontal), 0f);
        settings.allowPitchControls = false;
        settings.positionLerpSpeed = 0.3f;
        m.player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, settings));
        m.on = true;
    }

    private void reset(Member m) {
        if (m.on) {
            m.player.getPacketHandler().writeNoCache(new SetServerCamera(ClientCameraView.Custom, false, null));
            m.on = false;
        }
    }

    /** Everyone gets their own view back at once. */
    void resetAll() {
        for (Member m : members.values()) {
            reset(m);
        }
        members.clear();
    }
}
