package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.RemoveReason;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.vector.Rotation3f;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.damage.Damage;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageCause;
import com.hypixel.hytale.server.core.modules.entity.damage.DamageSystems;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatMap;
import com.hypixel.hytale.server.core.modules.entitystats.EntityStatValue;
import com.hypixel.hytale.server.core.modules.entitystats.asset.DefaultEntityStatTypes;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * The breach, scripted on the clock of the BREACH phase. Boom and heave, the stand-in worm shooting up, the target
 * lifted and swallowed, the worm hanging, arcing over and diving, the settling dust and the trail moving away.
 * Everything is particles, sounds, fake blocks and one prop entity: the real world is never edited. All of it is
 * undone in {@link #abort}, which also runs if the owner disconnects or the server stops.
 */
final class BreachJob implements WormTestSystem.Job {

    private final WormEffects fx;
    private final World world;
    private final UUID owner;
    private final PlayerRef victim;
    private final Ref<EntityStore> victimRef;
    private final Vector3d centre;
    private final Vector3d heading;
    private final Supplier<WormsOfArrakisConfig> config;
    private final double length;
    private final double wormHeight;
    /** Half the width of the worm: 3 blocks at scale 1, 9 at the default scale of 3. */
    private final double wormRadius;
    private final float scale;

    private double elapsed;
    private double burstClock;
    private double trailClock;
    private Vector3d lastTrail;
    private Ref<EntityStore> worm;
    private Vector3d liftFrom;
    private boolean booms;
    private boolean wormSpawned;
    private boolean swallowed;
    private boolean dived;
    private boolean secondBurst;
    private boolean wormGone;
    private final boolean[] settle = new boolean[3];
    /** Everyone caught in the swallow zone, and the camera of everyone near. */
    private final BreachHold hold = new BreachHold();
    private final BreachCamera camera = new BreachCamera();
    private boolean captured;
    private boolean hidden;
    private boolean killedHeld;
    private boolean zoomReleased;
    /** Everyone the worm has already killed, so nobody is killed twice. */
    private final Set<UUID> killed = new HashSet<>();

    /** {@code victim} is null for /worm breach here. {@code centre} is on the surface under the victim. */
    BreachJob(WormEffects fx, World world, UUID owner, PlayerRef victim, Ref<EntityStore> victimRef, Vector3d centre,
              Vector3d heading, double length, Supplier<WormsOfArrakisConfig> config) {
        this.fx = fx;
        this.world = world;
        this.owner = owner;
        this.victim = victim;
        this.victimRef = victimRef;
        this.centre = centre;
        this.heading = heading;
        this.config = config;
        WormsOfArrakisConfig cfg = config.get();
        this.scale = (float) cfg.getBreachWormSize();
        this.wormHeight = WormJob.HEIGHT * scale;
        this.wormRadius = 3.0 * scale;
        this.length = Math.max(length, cfg.getBreachDiveEnd() + 0.5);
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
        return "breach";
    }

    private static double easeOut(double q) {
        q = Math.max(0, Math.min(1, q));
        return 1 - (1 - q) * (1 - q);
    }

    private static double easeIn(double q) {
        q = Math.max(0, Math.min(1, q));
        return q * q;
    }

    private static double easeInOut(double q) {
        q = Math.max(0, Math.min(1, q));
        return q * q * (3 - 2 * q);
    }

    @Override
    public boolean tick(float dt, Store<EntityStore> store) {
        elapsed += dt;
        WormsOfArrakisConfig cfg = config.get();
        double t = elapsed;
        boolean victimHere = victim != null && victimRef != null && victimRef.isValid() && !swallowed;

        if (!booms && t >= cfg.getBreachBoomAt()) {
            booms = true;
            boom(store, cfg);
        }
        if (!wormSpawned && t >= cfg.getBreachWormAt()) {
            wormSpawned = true;
            spawnWorm(store);
        }
        // Dust wall, surface ring and thrown chunks while the worm is up and diving.
        if (t >= cfg.getBreachWormAt() && t < cfg.getBreachDiveEnd()) {
            burstClock += dt;
            if (burstClock >= 0.1) {
                burstClock = 0;
                plume(store, cfg, t);
            }
        }
        moveWorm(store, cfg, t);
        swallowZone(store, cfg, t, dt);
        if (cfg.isBreachLiftsVictim() && victimHere && t >= cfg.getBreachLiftAt() && t < cfg.getBreachSwallowAt()) {
            lift(store, cfg, t);
        }
        if (!swallowed && t >= cfg.getBreachSwallowAt()) {
            swallowed = true;
            swallow(store, cfg);
        }
        if (!dived && t >= cfg.getBreachDiveAt()) {
            dived = true;
            sounds(store, cfg, "dive", 0.8f);
        }
        if (!secondBurst && t >= cfg.getBreachDiveEnd()) {
            secondBurst = true;
            lowBurst(store, cfg);
        }
        if (!wormGone && t >= cfg.getBreachDiveEnd() + 0.3) {
            wormGone = true;
            removeWorm(store);
        }
        if (t >= cfg.getBreachDiveEnd()) {
            settling(store, cfg, t, dt);
        }
        return t < length;
    }

    @Override
    public void abort(Store<EntityStore> store) {
        removeWorm(store);
        hold.releaseAll(world, store);
        camera.resetAll();
    }

    // ------------------------------------------------------------------ the beats

    private List<PlayerRef> hearers(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        return WormEffects.near(world, store, centre, cfg.getGroupRadius(), victim);
    }

    /** Dust ring out from under the target, the heave of fake blocks, the boom and a hard shake nearby. */
    private void boom(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        List<PlayerRef> viewers = hearers(store, cfg);
        float far = (float) cfg.getGroupRadius() + 60f;
        List<Ref<EntityStore>> refs = WormEffects.refs(viewers);
        float dust = (float) cfg.getBreachDustScale();
        for (int k = 0; k < 12; k++) {
            double a = k * Math.PI * 2 / 12;
            double x = centre.x + Math.cos(a) * wormRadius * 1.3;
            double z = centre.z + Math.sin(a) * wormRadius * 1.3;
            WormEffects.particle(store, "Block_Break_Dust", x, centre.y + 0.5, z, dust, far, refs);
            WormEffects.particle(store, "Block_Land_Hard_Dust", x, centre.y + 0.4, z, dust * 0.8f, far, refs);
        }
        WormEffects.particle(store, "Block_Break_Dirt", centre.x, centre.y + 0.5, centre.z, dust, far, refs);
        // The heave: the sink-and-rebound ripple, as wide as the worm, shown to everyone near.
        int rings = (int) Math.min(cfg.getBreachRippleMaxRings(), Math.ceil(wormRadius));
        fx.sink(world, store, owner, centre, 4, rings, false, cfg.getBreachShakeRange(), victim);
        sounds(store, cfg, "boom", 1.0f);
        shakeNear(store, cfg, "Arrakis_Worm_Lock", 1.0);
    }

    private void shakeNear(Store<EntityStore> store, WormsOfArrakisConfig cfg, String id, double factor) {
        if (!cfg.isCameraShake()) {
            return;
        }
        for (PlayerRef p : WormEffects.near(world, store, centre, cfg.getBreachShakeRange(), victim)) {
            WormEffects.shake(p, id, cfg.getShakeStrength() * cfg.getBreachShakeStrength() * factor);
        }
    }

    /** Plays one of the breach sound sets for everyone within the group radius: loud near, flat and a little lower far. */
    private void sounds(Store<EntityStore> store, WormsOfArrakisConfig cfg, String which, float factor) {
        for (PlayerRef p : hearers(store, cfg)) {
            Vector3d at = WormEffects.position(store, p);
            boolean near = at != null && at.distance(centre) <= cfg.getBreachShakeRange();
            float volume = (float) (near ? cfg.getBreachBoomVolume() : cfg.getBreachFarVolume()) * factor;
            switch (which) {
                case "boom" -> boomSounds(p, volume);
                case "swallow" -> {
                    TimedEffectsJob.sound(p, "SFX_Sand_Break", volume, 0.35f);
                    TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Ice_Low", volume, 0.5f);
                }
                case "dive" -> {
                    TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Storm_Low", volume, 0.6f);
                    TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Statue_Low", volume * 0.8f, 0.5f);
                }
                default -> {
                    TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Statue_Low", volume, 0.8f);
                    TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Ice_Low", volume * 0.7f, 0.7f);
                }
            }
        }
    }

    /** The boom: also used where an event starts. */
    static void boomSounds(PlayerRef p, float volume) {
        TimedEffectsJob.sound(p, "SFX_Sand_Break", volume, 0.5f);
        TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Statue_Low", volume, 0.6f);
        TimedEffectsJob.sound(p, "Arrakis_SFX_Worm_Rumble_Storm_Low", volume, 0.7f);
    }

    private void spawnWorm(Store<EntityStore> store) {
        Rotation3f rotation = new Rotation3f(0, yaw(), 0);
        worm = WormJob.spawnProp(store, new Vector3d(centre.x, centre.y - wormHeight, centre.z), rotation, scale, false);
    }

    private float yaw() {
        return (float) Math.atan2(-heading.x, -heading.z);
    }

    /** The tall wall of dust round the worm, a ring on the surface, and chunks of sand thrown out and up. */
    private void plume(Store<EntityStore> store, WormsOfArrakisConfig cfg, double t) {
        List<PlayerRef> viewers = hearers(store, cfg);
        if (viewers.isEmpty()) {
            return;
        }
        List<Ref<EntityStore>> refs = WormEffects.refs(viewers);
        float far = (float) cfg.getGroupRadius() + 60f;
        float dust = (float) cfg.getBreachDustScale();
        float debris = (float) cfg.getBreachDebrisScale();
        // Fade the plume as the worm goes down.
        double fade = t < cfg.getBreachDiveAt() ? 1.0 : 1.0 - easeIn((t - cfg.getBreachDiveAt()) / (cfg.getBreachDiveEnd() - cfg.getBreachDiveAt())) * 0.7;
        float s = (float) (dust * fade);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int layers = (int) Math.max(3, Math.min(8, wormHeight / 1.5));
        for (int h = 0; h < layers; h++) {
            double y = centre.y + h * wormHeight / layers;
            double a = random.nextDouble(0, Math.PI * 2);
            double r = wormRadius * 0.6 + 0.2 * h;
            WormEffects.particle(store, h % 2 == 0 ? "Block_Break_Dust" : "Block_Land_Hard_Dust",
                    centre.x + Math.cos(a) * r, y + 0.5, centre.z + Math.sin(a) * r, s, far, refs);
        }
        double ring = wormRadius + random.nextDouble(0, wormRadius);
        for (int k = 0; k < 4; k++) {
            double a = random.nextDouble(0, Math.PI * 2);
            WormEffects.particle(store, "Block_Break_Dust", centre.x + Math.cos(a) * ring, centre.y + 0.4,
                    centre.z + Math.sin(a) * ring, s * 0.8f, far, refs);
        }
        // Chunks: sand and dirt breaking apart at random points out and up; they fall and vanish by themselves.
        for (int k = 0; k < 3; k++) {
            double a = random.nextDouble(0, Math.PI * 2);
            double r = random.nextDouble(wormRadius * 0.7, wormRadius * 3);
            double y = centre.y + random.nextDouble(1, wormHeight * 0.9);
            WormEffects.particle(store, k == 0 ? "Block_Break_Dirt" : "Block_Break_Sand",
                    centre.x + Math.cos(a) * r, y, centre.z + Math.sin(a) * r, debris, far, refs);
        }
    }

    /** Scripts the worm: up, hang, over and down, with an ease in and out. */
    private void moveWorm(Store<EntityStore> store, WormsOfArrakisConfig cfg, double t) {
        if (worm == null || !worm.isValid()) {
            return;
        }
        double depth;
        double tilt;
        double forward = 0;
        if (t < cfg.getBreachSwallowAt()) {
            double q = (t - cfg.getBreachWormAt()) / Math.max(1e-3, cfg.getBreachSwallowAt() - cfg.getBreachWormAt());
            depth = 1 - easeOut(q);
            tilt = 0.25 * easeInOut(q);
        } else if (t < cfg.getBreachDiveAt()) {
            double q = (t - cfg.getBreachSwallowAt()) / Math.max(1e-3, cfg.getBreachDiveAt() - cfg.getBreachSwallowAt());
            depth = 0;
            tilt = 0.25 + 0.25 * easeInOut(q);
        } else {
            double q = (t - cfg.getBreachDiveAt()) / Math.max(1e-3, cfg.getBreachDiveEnd() - cfg.getBreachDiveAt());
            depth = 1.25 * easeIn(q);
            tilt = 0.5 + 0.9 * easeInOut(q);
            forward = wormHeight * 0.9 * easeIn(q);
        }
        TransformComponent tc = store.getComponent(worm, TransformComponent.getComponentType());
        if (tc == null) {
            return;
        }
        tc.setPosition(new Vector3d(centre.x + heading.x * forward, centre.y - wormHeight * depth,
                centre.z + heading.z * forward));
        Rotation3f rotation = tc.getRotation();
        rotation.setYaw(yaw());
        rotation.setPitch((float) tilt);
        tc.setRotation(rotation);
    }

    /** Moves the target up with the worm, a little inward, a position at a time. */
    private void lift(Store<EntityStore> store, WormsOfArrakisConfig cfg, double t) {
        TransformComponent tc = store.getComponent(victimRef, TransformComponent.getComponentType());
        HeadRotation head = store.getComponent(victimRef, HeadRotation.getComponentType());
        if (tc == null || head == null) {
            return;
        }
        if (liftFrom == null) {
            liftFrom = new Vector3d(tc.getPosition());
        }
        double q = easeInOut((t - cfg.getBreachLiftAt()) / Math.max(1e-3, cfg.getBreachSwallowAt() - cfg.getBreachLiftAt()));
        double inward = 1.0 - 0.5 * q;
        Vector3d to = new Vector3d(centre.x + (liftFrom.x - centre.x) * inward, liftFrom.y + wormHeight * 0.8 * q,
                centre.z + (liftFrom.z - centre.z) * inward);
        store.putComponent(victimRef, Teleport.getComponentType(),
                Teleport.createForPlayer(world, to, new Rotation3f(head.getRotation())));
    }

    /** The swallow: its sounds and shake; the kill itself is done by {@link #swallowZone} later (or here with BreachHold off). */
    private void swallow(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        sounds(store, cfg, "swallow", 1.0f);
        shakeNear(store, cfg, "Arrakis_Worm_Lock", 1.2);
        if (cfg.isBreachHold()) {
            return; // the kill comes at BreachKillAt, once the worm is under the sand
        }
        if (victim == null || victimRef == null || !victimRef.isValid() || killed.contains(victim.getUuid())) {
            return;
        }
        devour(store, cfg, victimRef, victim.getUuid());
    }

    /**
     * The swallow zone and the camera zone, on the breach clock. Everyone within wormRadius times
     * BreachSwallowZoneFactor when the worm appears is held, hidden at BreachHideAt and killed at BreachKillAt (the
     * worm is back under the sand); everyone within BreachCameraRadius gets the pulled-out camera until
     * BreachCameraReturnAt. Membership of the swallow zone is decided once, when the worm appears.
     */
    private void swallowZone(Store<EntityStore> store, WormsOfArrakisConfig cfg, double t, double dt) {
        double hideAt = cfg.getBreachHideAt() < 0 ? cfg.getBreachSwallowAt() : cfg.getBreachHideAt();
        double killAt = cfg.getBreachKillAt() < 0 ? cfg.getBreachDiveEnd() : cfg.getBreachKillAt();
        double returnAt = cfg.getBreachCameraReturnAt() < 0 ? cfg.getBreachDiveEnd() + 1.0 : cfg.getBreachCameraReturnAt();
        double cameraRadius = cfg.getBreachCameraRadius() > 0 ? cfg.getBreachCameraRadius() : wormRadius * 4 + 20;
        UUID target = victim == null ? null : victim.getUuid();
        camera.tick(world, store, cfg, dt, centre, heading, wormHeight, cameraRadius, target, t < returnAt);
        if (!zoomReleased && t >= returnAt) {
            zoomReleased = true;
            if (target != null) {
                fx.releaseZoom(target);
            }
        }
        if (!cfg.isBreachHold()) {
            return;
        }
        if (!captured && t >= cfg.getBreachWormAt()) {
            captured = true;
            hold.capture(world, store, centre, wormRadius * cfg.getBreachSwallowZoneFactor(), victim);
        }
        if (!captured || killedHeld) {
            return;
        }
        hold.tick(world, store);
        if (!hidden && t >= hideAt) {
            hidden = true;
            hold.hide(world, store);
        }
        if (t >= killAt) {
            killedHeld = true;
            hold.finish(world, store, cfg.isDevourKills(), (s, ref, id) -> {
                if (!killed.contains(id)) {
                    devour(s, cfg, ref, id);
                }
            });
        }
    }


    /** What the worm does to someone it has caught: killed with nothing dropped, or (DevourKills off) badly hurt. */
    private void devour(Store<EntityStore> store, WormsOfArrakisConfig cfg, Ref<EntityStore> ref, UUID id) {
        killed.add(id);
        try {
            DamageCause cause = WormDevourDamage.cause();
            if (cause == null) {
                WormsOfArrakisPlugin.get().getLogger().at(Level.SEVERE).log("%s", "Damage cause "
                        + WormDevourDamage.CAUSE_ID + " is not loaded, so the worm cannot hurt anyone");
                return;
            }
            if (cfg.isDevourKills()) {
                if (!cfg.isDevourDropsItems()) {
                    clearInventory(store, ref);
                }
                DamageSystems.executeDamage(ref, store, new Damage(WormDevourDamage.SOURCE, cause, 1.0e6f));
            } else {
                EntityStatMap stats = store.getComponent(ref, EntityStatMap.getComponentType());
                EntityStatValue health = stats == null ? null : stats.get(DefaultEntityStatTypes.getHealth());
                if (health != null) {
                    float hp = health.get();
                    float amount = (float) Math.max(0, Math.min(hp * cfg.getDevourHurtFraction(), hp - 1));
                    DamageSystems.executeDamage(ref, store, new Damage(WormDevourDamage.SOURCE, cause, amount));
                }
            }
        } catch (Throwable t) {
            WormsOfArrakisPlugin.get().getLogger().at(Level.WARNING).withCause(t).log("The worm could not hurt someone it caught");
        }
    }

    /** Empties every inventory section so that nothing is dropped and the respawn is bare. */
    private void clearInventory(Store<EntityStore> store, Ref<EntityStore> ref) {
        clear(store, ref, InventoryComponent.Hotbar.getComponentType());
        clear(store, ref, InventoryComponent.Storage.getComponentType());
        clear(store, ref, InventoryComponent.Armor.getComponentType());
        clear(store, ref, InventoryComponent.Utility.getComponentType());
        clear(store, ref, InventoryComponent.Tool.getComponentType());
        clear(store, ref, InventoryComponent.Backpack.getComponentType());
    }

    private void clear(Store<EntityStore> store, Ref<EntityStore> ref,
                       com.hypixel.hytale.component.ComponentType<EntityStore, ? extends InventoryComponent> type) {
        InventoryComponent section = store.getComponent(ref, type);
        if (section != null) {
            section.getInventory().clear();
        }
    }

    /** A second, lower burst of dust as the worm goes under. */
    private void lowBurst(Store<EntityStore> store, WormsOfArrakisConfig cfg) {
        List<PlayerRef> viewers = hearers(store, cfg);
        List<Ref<EntityStore>> refs = WormEffects.refs(viewers);
        float far = (float) cfg.getGroupRadius() + 60f;
        float dust = (float) cfg.getBreachDustScale();
        double x = centre.x + heading.x * wormHeight * 0.9;
        double z = centre.z + heading.z * wormHeight * 0.9;
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI * 2 / 8;
            WormEffects.particle(store, "Block_Break_Dust", x + Math.cos(a) * wormRadius, centre.y + 0.5, z + Math.sin(a) * wormRadius,
                    dust * 0.9f, far, refs);
        }
        WormEffects.particle(store, "Block_Break_Sand", x, centre.y + 1.5, z, dust, far, refs);
        WormEffects.particle(store, "Sand_Storm", x, centre.y + 2, z, dust * 0.6f, far, refs);
        sounds(store, cfg, "dive", 0.7f);
        shakeNear(store, cfg, "Arrakis_Worm_Pass", 1.0);
    }

    /** The dust settles and the wormsign moves away along the way the worm dived, with a short fading rumble. */
    private void settling(Store<EntityStore> store, WormsOfArrakisConfig cfg, double t, double dt) {
        double since = t - cfg.getBreachDiveEnd();
        double[] at = {0.5, 2.0, 3.5};
        float[] volume = {0.4f, 0.3f, 0.2f};
        for (int i = 0; i < at.length; i++) {
            if (!settle[i] && since >= at[i]) {
                settle[i] = true;
                sounds(store, cfg, "settle", volume[i]);
            }
        }
        trailClock += dt;
        if (trailClock >= cfg.getWormsignTrailInterval()) {
            trailClock = 0;
            double d = wormHeight * 0.9 + 14 * since; // moving away at about 14 blocks per second
            Vector3d p = new Vector3d(centre.x + heading.x * d, centre.y, centre.z + heading.z * d);
            List<PlayerRef> viewers = WormEffects.near(world, store, p, cfg.getWormsignViewDistance(), null);
            if (!viewers.isEmpty() && since < (length - cfg.getBreachDiveEnd()) * 0.8) {
                fx.trail(world, store, lastTrail, p, viewers, false, cfg);
            }
            lastTrail = p;
        }
    }

    private void removeWorm(Store<EntityStore> store) {
        if (worm != null && worm.isValid()) {
            store.removeEntity(worm, RemoveReason.REMOVE);
        }
        worm = null;
    }
}
