package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.builtin.weather.components.WeatherTracker;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.Color;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractCommandCollection;
import com.hypixel.hytale.server.core.modules.entity.component.HeadRotation;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * /wormtest ripple | vignette | slow | worm | burst | rumble | speed. Throwaway commands that answer engine
 * questions for the worm mod; they are removed or hidden once the real features are built.
 * No permission group is set, so only operators can run them.
 */
public class WormTestCommand extends AbstractCommandCollection {

    private static final String LANG = "server.commands.wormtest.";

    private final WormTestSystem system;
    private final Supplier<Set<Integer>> sand;

    public WormTestCommand(WormTestSystem system, Supplier<Set<Integer>> sand) {
        super("wormtest", LANG + "desc");
        this.system = system;
        this.sand = sand;
        addSubCommand(Positional.build("ripple", LANG + "ripple.desc", this::ripple, "radius", "mode", "scope"));
        addSubCommand(Positional.build("vignette", LANG + "vignette.desc", this::vignette, "state", "variant"));
        addSubCommand(Positional.build("slow", LANG + "slow.desc", this::slow, "percent", "seconds", "fields", "hz"));
        addSubCommand(Positional.build("worm", LANG + "worm.desc", this::worm, "scale", "persist"));
        addSubCommand(Positional.build("burst", LANG + "burst.desc", this::burst, "scale", "variant"));
        addSubCommand(Positional.build("rumble", LANG + "rumble.desc", this::rumble, "seconds", "combo", "pitch"));
        addSubCommand(new WormSelfTest(sand));
        addSubCommand(Positional.build("speed", LANG + "speed.desc", this::speed, "seconds"));
    }

    /** The thin sand layer block shown instead of a full block, or -1 if it is not loaded. */
    static int layerBlockId() {
        int id = com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.getAssetMap().getIndex("Arrakis_Worm_Ripple_Layer");
        return id == Integer.MIN_VALUE ? -1 : id;
    }

    private static void say(CommandContext context, String text) {
        context.sendMessage(Message.raw(text));
    }

    private static Vector3d position(Store<EntityStore> store, Ref<EntityStore> ref) {
        TransformComponent transform = store.getComponent(ref, TransformComponent.getComponentType());
        return transform != null ? new Vector3d(transform.getPosition()) : null;
    }

    // ------------------------------------------------------------------ 1. ripple

    /** /wormtest ripple [radius] [raise|trough|both] [self|nearby] */
    private void ripple(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                        World world, String[] args) {
        int radius = (int) Math.min(64, Math.max(1, Args.number(args, 0, 5)));
        String mode = Args.text(args, 1, "raise");
        String scope = Args.text(args, 2, "self");
        Vector3d p = position(store, ref);
        if (p == null) {
            return;
        }
        List<PlayerRef> viewers = new ArrayList<>();
        List<double[]> avoid = new ArrayList<>();
        viewers.add(player);
        avoid.add(new double[] {p.x, p.y, p.z});
        if (scope.equals("nearby")) {
            for (PlayerRef other : world.getPlayerRefs()) {
                Ref<EntityStore> otherRef = other.getReference();
                if (other == player || otherRef == null || !otherRef.isValid()) {
                    continue;
                }
                Vector3d q = position(store, otherRef);
                if (q != null && q.distance(p) < radius + 48) {
                    viewers.add(other);
                    avoid.add(new double[] {q.x, q.y, q.z});
                }
            }
        }
        Set<Integer> sandIds = sand.get();
        RippleJob job = RippleJob.create(world, player.getUuid(), viewers, new double[] {p.x, p.y, p.z}, radius,
                mode, sandIds, layerBlockId(), avoid);
        if (job.cellCount() == 0) {
            say(context, "ripple: no sand columns found within " + radius + " blocks (sand ids " + sandIds + ")");
            return;
        }
        system.add(job);
        say(context, String.format(Locale.ROOT, "ripple: %d columns, mode %s, %d viewer(s), radius %d",
                job.cellCount(), mode, viewers.size(), radius));
    }

    // ------------------------------------------------------------------ 2. vignette

    /** /wormtest vignette on|off [weather-soft|weather-sand|effect-soft|effect-sand] */
    private void vignette(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                          World world, String[] args) {
        String state = Args.text(args, 0, "on");
        if (state.equals("off")) {
            int n = system.stop(player.getUuid(), "vignette");
            say(context, n > 0 ? "vignette off" : "vignette was not on");
            return;
        }
        String variant = Args.text(args, 1, "weather-soft");
        boolean weatherRoute = variant.startsWith("weather");
        String texture = variant.endsWith("sand") ? "Sand" : "Soft";
        String assetId = weatherRoute
                ? "Arrakis_Worm_Vignette_" + texture + "_" + baseLook(store, ref)
                : "Arrakis_Worm_Vignette_" + texture;
        system.stop(player.getUuid(), "vignette");
        VignetteJob job = new VignetteJob(world, player, ref, weatherRoute, assetId, "vignette");
        String error = job.start(store);
        if (error != null) {
            say(context, "vignette: " + error);
            return;
        }
        system.add(job);
        say(context, "vignette on: " + assetId + " (" + (weatherRoute ? "weather override" : "entity effect") + ")");
    }

    /** Sunny or Haze: whichever of the two forecast weathers the player's current weather is closest to. */
    private static String baseLook(Store<EntityStore> store, Ref<EntityStore> ref) {
        WeatherTracker tracker = store.getComponent(ref, WeatherTracker.getComponentType());
        if (tracker != null) {
            Weather weather = Weather.getAssetMap().getAsset(tracker.getWeatherIndex());
            if (weather != null && weather.getId().contains("Haze")) {
                return "Haze";
            }
        }
        return "Sunny";
    }

    // ------------------------------------------------------------------ 3. slow

    /** /wormtest slow <percent|off> [seconds] [base|mult|jump|air|accel|all|effect, joined with +] [updatesPerSecond] */
    private void slow(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                      World world, String[] args) {
        String first = Args.text(args, 0, "off");
        if (first.equals("off")) {
            int n = system.stop(player.getUuid(), "slow");
            say(context, n > 0 ? "slow off, settings restored" : "slow was not on");
            return;
        }
        double percent = Args.number(args, 0, 50);
        float seconds = (float) Args.number(args, 1, 3);
        String fieldText = Args.text(args, 2, "base+mult");
        float hz = (float) Args.number(args, 3, 30);
        system.stop(player.getUuid(), "slow");
        if (fieldText.equals("effect")) {
            VignetteJob job = new VignetteJob(world, player, ref, false, "Arrakis_Worm_Slow_Effect", "slow");
            String error = job.start(store);
            if (error != null) {
                say(context, "slow: " + error);
                return;
            }
            system.add(job);
            say(context, "slow: entity effect Arrakis_Worm_Slow_Effect (horizontal speed x0.5, no ramp); '/wormtest slow off' ends it");
            return;
        }
        Set<String> fields = new HashSet<>();
        for (String f : fieldText.split("\\+")) {
            if (f.equals("all")) {
                fields.addAll(List.of("base", "jump", "air"));
            } else {
                fields.add(f);
            }
        }
        system.add(new SlowJob(world, player, ref, (float) (percent / 100.0), seconds, fields, hz));
        say(context, String.format(Locale.ROOT,
                "slow: ramping to %.0f%% over %.1f s using %s at %.0f updates/s, holding %.0f s, then restoring",
                percent, seconds, fields, hz, (double) SlowJob.HOLD_SECONDS));
    }

    // ------------------------------------------------------------------ 4. worm

    /** /wormtest worm [scale|remove] [persist] */
    private void worm(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                      World world, String[] args) {
        if (Args.text(args, 0, "").equals("remove")) {
            int n = system.stop(player.getUuid(), "worm");
            say(context, n > 0 ? "worm removed" : "no worm to remove");
            return;
        }
        float scale = (float) Math.min(10, Math.max(0.1, Args.number(args, 0, 3)));
        boolean persist = Args.text(args, 1, "").equals("persist");
        Vector3d feet = position(store, ref);
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (feet == null || head == null) {
            return;
        }
        // Spawn where the ground is, not where the player's feet are, in case they are in the air.
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(feet.x, feet.z));
        if (chunk != null) {
            feet.y = chunk.getHeight((int) Math.floor(feet.x) & ChunkUtil.SIZE_MASK,
                    (int) Math.floor(feet.z) & ChunkUtil.SIZE_MASK) + 1;
        }
        system.stop(player.getUuid(), "worm");
        WormJob job = WormJob.spawn(world, store, player, feet, head.getDirection(), scale, persist);
        if (job == null) {
            say(context, "worm: model asset " + WormJob.MODEL_ID + " is not loaded");
            return;
        }
        system.add(job);
        say(context, String.format(Locale.ROOT,
                "worm: spawned at scale %.1f (about %.0f wide, %.0f tall), %s. It rises, sways, sinks, travels buried and returns every %.0f s. '/wormtest worm remove' removes it",
                scale, 6 * scale, 10 * scale, persist ? "persistent (saved with its chunk)" : "not saved", (double) WormJob.CYCLE));
    }

    // ------------------------------------------------------------------ 5. burst and rumble

    private static final Color TAN = new Color((byte) 0xc8, (byte) 0xa4, (byte) 0x6e);

    /** /wormtest burst [scale] [variant 1-5]: a dust plume ten blocks ahead. */
    private void burst(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                       World world, String[] args) {
        float scale = (float) Math.min(20, Math.max(0.2, Args.number(args, 0, 9)));
        int variant = (int) Args.number(args, 1, 4);
        Vector3d p = position(store, ref);
        HeadRotation head = store.getComponent(ref, HeadRotation.getComponentType());
        if (p == null || head == null) {
            return;
        }
        Vector3d dir = head.getDirection();
        double len = Math.hypot(dir.x, dir.z);
        double x = p.x + (len < 1e-6 ? 0 : dir.x / len * 10);
        double z = p.z + (len < 1e-6 ? 1 : dir.z / len * 10);
        double y = p.y;
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk != null) {
            y = chunk.getHeight((int) Math.floor(x) & ChunkUtil.SIZE_MASK, (int) Math.floor(z) & ChunkUtil.SIZE_MASK) + 1;
        }
        final double fy = y;
        final double fx = x;
        final double fz = z;
        TimedEffectsJob job = new TimedEffectsJob(world, player.getUuid(), "burst");
        // The plume climbs for the first half and sinks for the second: each wave is placed higher, then lower,
        // along a sine arc, and gets a little smaller as the dust thins.
        int waves = variant >= 4 ? 12 : 1;
        double peak = scale * 1.5;
        for (int i = 0; i < waves; i++) {
            final int wave = i;
            double u = waves == 1 ? 0 : i / (waves - 1.0);
            final double lift = peak * Math.sin(Math.PI * u);
            final float s = scale * (float) (1.0 - 0.5 * u);
            job.at(i * 0.2f, () -> plume(world, store, variant, fx, fy + lift, fz, s, wave));
        }
        system.add(job);
        say(context, String.format(Locale.ROOT, "burst: variant %d, scale %.1f at %.0f %.0f %.0f", variant, scale, x, y, z));
    }

    private static void plume(World world, Store<EntityStore> store, int variant, double x, double y, double z,
                              float scale, int wave) {
        final float far = 160f;
        switch (variant) {
            case 1 -> TimedEffectsJob.particle(world, store, "Block_Break_Dust", x, y, z, scale, TAN, far);
            case 2 -> TimedEffectsJob.particle(world, store, "Block_Land_Hard_Dust", x, y, z, scale, TAN, far);
            case 3 -> {
                TimedEffectsJob.particle(world, store, "Block_Break_Sand", x, y, z, scale, TAN, far);
                TimedEffectsJob.particle(world, store, "Block_Land_Sand_Hard", x, y, z, scale, TAN, far);
            }
            default -> {
                // A tall column: dust and sand systems stacked, one per couple of blocks of height.
                int layers = Math.min(6, Math.max(1, Math.round(scale / 2)));
                for (int h = 0; h < layers; h++) {
                    double yy = y + h * scale * 0.4;
                    TimedEffectsJob.particle(world, store, "Block_Break_Dust", x, yy, z, scale, TAN, far);
                    TimedEffectsJob.particle(world, store, h % 2 == 0 ? "Block_Land_Hard_Dust" : "Block_Break_Sand",
                            x, yy, z, scale, TAN, far);
                }
                if (variant >= 5) {
                    TimedEffectsJob.particle(world, store, "Block_Break_Dirt", x, y, z, scale, TAN, far);
                    TimedEffectsJob.particle(world, store, "Sand_Storm", x, y + 2, z, scale, TAN, far);
                }
                if (wave == 0) {
                    TimedEffectsJob.particle(world, store, "Block_Break_Sand", x, y, z, scale * 1.5f, TAN, far);
                }
            }
        }
    }

    /**
     * /wormtest rumble [seconds] [combo] [pitch]. Plays the combo at 70% volume, and again at 100% halfway through,
     * re-triggering every few seconds to imitate a loop. combo: statue, ice, storm, each with a -low variant
     * (an octave down in the asset), layered (all three low), or ambience (a weather-tag AmbienceFX loop).
     * pitch is the packet's pitchModifier (1 = unchanged).
     */
    private void rumble(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                        World world, String[] args) {
        float seconds = (float) Math.min(60, Math.max(1, Args.number(args, 0, 8)));
        String combo = Args.text(args, 1, "layered");
        float pitch = (float) Args.number(args, 2, 1.0);
        if (combo.equals("stop")) {
            system.stop(player.getUuid(), "rumble");
            say(context, "rumble stopped (a sound already playing cannot be recalled; ambience loops end here)");
            return;
        }
        system.stop(player.getUuid(), "rumble");
        if (combo.equals("ambience")) {
            String assetId = "Arrakis_Worm_Rumble_" + baseLook(store, ref);
            VignetteJob job = new VignetteJob(world, player, ref, true, assetId, "rumble");
            String error = job.start(store);
            if (error != null) {
                say(context, "rumble: " + error);
                return;
            }
            system.add(job);
            say(context, "rumble: ambience loop on through weather tag Worm_Rumble (" + assetId + "); '/wormtest rumble 1 stop' ends it");
            return;
        }
        List<String> sounds = new ArrayList<>();
        if (combo.equals("layered")) {
            sounds.addAll(List.of("Arrakis_SFX_Worm_Rumble_Statue_Low", "Arrakis_SFX_Worm_Rumble_Ice_Low",
                    "Arrakis_SFX_Worm_Rumble_Storm_Low"));
        } else {
            sounds.add("Arrakis_SFX_Worm_Rumble_" + capital(combo));
        }
        TimedEffectsJob job = new TimedEffectsJob(world, player.getUuid(), "rumble");
        float interval = 3.0f;
        for (float t = 0; t < seconds; t += interval) {
            final float volume = t < seconds / 2 ? 0.7f : 1.0f;
            job.at(t, () -> {
                for (String id : sounds) {
                    TimedEffectsJob.sound(player, id, volume, pitch);
                }
            });
        }
        job.at(seconds, () -> { });
        system.add(job);
        say(context, String.format(Locale.ROOT, "rumble: %s at pitch %.2f for %.0f s, 70%% volume then 100%%", sounds, pitch, seconds));
    }

    private static String capital(String text) {
        String[] parts = text.split("-");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) {
                out.append('_');
            }
            out.append(part.isEmpty() ? part : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ extra: real speeds

    /** /wormtest speed [seconds]: walk, run and sprint on flat ground meanwhile, then read the report. */
    private void speed(CommandContext context, Store<EntityStore> store, Ref<EntityStore> ref, PlayerRef player,
                       World world, String[] args) {
        float seconds = (float) Math.min(120, Math.max(3, Args.number(args, 0, 20)));
        system.stop(player.getUuid(), "speed");
        system.add(new SpeedProbeJob(world, player, ref, seconds));
        say(context, String.format(Locale.ROOT,
                "speed: measuring for %.0f s. Walk, then run, then sprint in a straight line on flat ground", seconds));
    }
}
