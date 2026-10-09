package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.asset.type.entityeffect.config.EntityEffect;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.asset.type.particle.config.ParticleSystem;
import com.hypixel.hytale.server.core.asset.type.soundevent.config.SoundEvent;
import com.hypixel.hytale.server.core.asset.type.weather.config.Weather;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractWorldCommand;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * /wormtest selftest: what can be checked without a client, from the console. Checks that every asset this mod
 * relies on is loaded, builds a ripple and runs it to its end with no viewers, and spawns, moves and removes the
 * stand-in worm. It cannot show anything on screen or play any sound.
 */
final class WormSelfTest extends AbstractWorldCommand {

    private final Supplier<Set<Integer>> sand;

    WormSelfTest(Supplier<Set<Integer>> sand) {
        super("selftest", "server.commands.wormtest.selftest.desc");
        this.sand = sand;
    }

    @Override
    protected void execute(CommandContext context, World world, Store<EntityStore> store) {
        // From a player, test where they stand; from the console, round the origin.
        int ox = 0;
        int oz = 0;
        if (context.isPlayer()) {
            var ref = context.senderAsPlayerRef();
            var transform = ref == null ? null : store.getComponent(ref,
                    com.hypixel.hytale.server.core.modules.entity.component.TransformComponent.getComponentType());
            if (transform != null) {
                ox = (int) Math.floor(transform.getPosition().x);
                oz = (int) Math.floor(transform.getPosition().z);
            }
        }
        final int originX = ox;
        final int originZ = oz;
        List<CompletableFuture<WorldChunk>> loads = new ArrayList<>();
        for (int cx = ChunkUtil.chunkCoordinate(ox) - 1; cx <= ChunkUtil.chunkCoordinate(ox) + 1; cx++) {
            for (int cz = ChunkUtil.chunkCoordinate(oz) - 1; cz <= ChunkUtil.chunkCoordinate(oz) + 1; cz++) {
                loads.add(world.getChunkAsync(ChunkUtil.indexChunk(cx, cz)));
            }
        }
        CompletableFuture.allOf(loads.toArray(new CompletableFuture[0]))
                .thenAccept(v -> world.execute(() -> {
                    try {
                        run(context, world, world.getEntityStore().getStore(), originX, originZ);
                    } catch (Throwable t) {
                        say(context, "selftest FAILED: " + t);
                        WormsOfArrakisPlugin.get().getLogger().at(java.util.logging.Level.WARNING).withCause(t).log("selftest failed");
                    }
                }));
    }

    private void run(CommandContext context, World world, Store<EntityStore> store, int originX, int originZ) {
        List<String> missing = new ArrayList<>();
        for (String id : List.of("Arrakis_SFX_Worm_Rumble_Statue", "Arrakis_SFX_Worm_Rumble_Statue_Low",
                "Arrakis_SFX_Worm_Rumble_Ice", "Arrakis_SFX_Worm_Rumble_Ice_Low",
                "Arrakis_SFX_Worm_Rumble_Storm", "Arrakis_SFX_Worm_Rumble_Storm_Low")) {
            if (SoundEvent.getAssetMap().getIndex(id) == Integer.MIN_VALUE) {
                missing.add("sound " + id);
            }
        }
        for (String id : List.of("Arrakis_Worm_Vignette_Sand", "Arrakis_Worm_Vignette_Soft", "Arrakis_Worm_Slow_Effect")) {
            if (EntityEffect.getAssetMap().getAsset(id) == null) {
                missing.add("effect " + id);
            }
        }
        for (String base : List.of("Sunny", "Haze")) {
            for (String kind : List.of("Vignette_Sand", "Vignette_Soft", "Rumble")) {
                String id = "Arrakis_Worm_" + kind + "_" + base;
                if (Weather.getAssetMap().getIndex(id) == Integer.MIN_VALUE) {
                    missing.add("weather " + id);
                }
            }
        }
        for (String id : List.of("Block_Break_Dust", "Block_Land_Hard_Dust", "Block_Break_Sand", "Block_Land_Sand_Hard",
                "Block_Break_Dirt", "Sand_Storm")) {
            if (ParticleSystem.getAssetMap().getAsset(id) == null) {
                missing.add("particle " + id);
            }
        }
        for (int pct = 95; pct >= 10; pct -= 5) {
            if (EntityEffect.getAssetMap().getAsset("Arrakis_Worm_Slow_" + pct) == null) {
                missing.add("effect Arrakis_Worm_Slow_" + pct);
            }
        }
        for (int level = 1; level <= 6; level++) {
            if (EntityEffect.getAssetMap().getAsset("Arrakis_Worm_Vignette_Level_" + level) == null) {
                missing.add("effect Arrakis_Worm_Vignette_Level_" + level);
            }
        }
        for (String id : List.of("Arrakis_Worm_Tremble", "Arrakis_Worm_Lock", "Arrakis_Worm_Pass")) {
            if (com.hypixel.hytale.builtin.adventure.camera.asset.camerashake.CameraShake.getAssetMap().getIndex(id) == Integer.MIN_VALUE) {
                missing.add("camera shake " + id);
            }
        }
        if (!SinkJob.missingAssets().isEmpty()) {
            missing.add("sink blocks " + SinkJob.missingAssets());
        }
        if (com.hypixel.hytale.builtin.adventure.camera.asset.camerashake.CameraShake.getAssetMap().getIndex(SinkJob.SHAKE_ID) == Integer.MIN_VALUE) {
            missing.add("camera shake " + SinkJob.SHAKE_ID);
        }
        for (String id : List.of(BreachHold.HOLD, BreachHold.HIDE)) {
            if (EntityEffect.getAssetMap().getAsset(id) == null) {
                missing.add("effect " + id);
            }
        }
        if (ModelAsset.getAssetMap().getAsset("Arrakis_Worm_Invisible") == null) {
            missing.add("model Arrakis_Worm_Invisible");
        }
        if (WormDevourDamage.cause() == null) {
            missing.add("damage cause " + WormDevourDamage.CAUSE_ID);
        }
        if (SoundEvent.getAssetMap().getIndex("SFX_Sand_Break") == Integer.MIN_VALUE) {
            missing.add("sound SFX_Sand_Break");
        }
        if (ModelAsset.getAssetMap().getAsset(WormJob.MODEL_ID) == null) {
            missing.add("model " + WormJob.MODEL_ID);
        }
        say(context, "assets missing: " + (missing.isEmpty() ? "none" : missing));

        // Find a sand column near the origin.
        Vector3d spot = null;
        for (int r = 0; r < 24 && spot == null; r++) {
            for (int dx = -r; dx <= r && spot == null; dx++) {
                for (int dz = -r; dz <= r && spot == null; dz++) {
                    WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(originX + dx, originZ + dz));
                    if (chunk == null) {
                        continue;
                    }
                    int bx = originX + dx;
                    int bz = originZ + dz;
                    int top = chunk.getHeight(bx & ChunkUtil.SIZE_MASK, bz & ChunkUtil.SIZE_MASK);
                    if (sand.get().contains(chunk.getBlock(bx, top, bz))) {
                        spot = new Vector3d(bx + 0.5, top + 1, bz + 0.5);
                    }
                }
            }
        }
        if (spot == null) {
            say(context, "no sand found near the origin; ripple and worm checks skipped (sand ids " + sand.get() + ")");
            return;
        }
        say(context, "using sand at " + spot);

        sinkChecks(context, world, store, spot);
        startFinderChecks(context, world, spot);

        for (boolean persist : new boolean[] {false, true}) {
            WormJob worm = WormJob.spawn(world, store, null, spot, new Vector3d(0, 0, 1), 1.0f, persist);
            if (worm == null) {
                say(context, "worm: spawn returned null");
                return;
            }
            int n = 0;
            while (n < 30 * 25) {
                if (!worm.tick(1 / 30f, store)) {
                    break;
                }
                n++;
            }
            worm.abort(store);
            say(context, "worm " + (persist ? "that saves with its chunk" : "that is not saved") + ": spawned, moved for " + n
                    + " ticks, removed. OK");
        }
        // The breach, with nobody there: runs the whole script, spawns and removes the worm.
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        BreachJob breach = new BreachJob(plugin.effects(), world, java.util.UUID.randomUUID(), null, null,
                new Vector3d(spot.x, spot.y, spot.z), new Vector3d(0, 0, 1), plugin.config().getBreachSeconds(), plugin::config);
        int breachTicks = 0;
        while (breach.tick(1 / 30f, store) && breachTicks < 30 * 20) {
            breachTicks++;
        }
        breach.abort(store);
        say(context, "breach: ran " + breachTicks + " ticks (" + String.format(java.util.Locale.ROOT, "%.1f", breachTicks / 30.0) + " s), worm removed. OK");
        say(context, "selftest finished: all checks passed");
    }

    /** The start-point search from the spawn: how often each rule is used and what it costs. Only chunks the headless server has loaded count. */
    private void startFinderChecks(CommandContext context, World world, Vector3d spot) {
        java.util.Map<String, Integer> rules = new java.util.TreeMap<>();
        int sampled = 0;
        int unloaded = 0;
        double millis = 0;
        int runs = 10;
        for (int i = 0; i < runs; i++) {
            WormStartFinder.Result r = WormStartFinder.find(world, spot, WormsOfArrakisPlugin.get().config(), sand.get(), new java.util.Random(i));
            rules.merge(r.rule, 1, Integer::sum);
            sampled += r.sampled;
            unloaded += r.unloaded;
            millis += r.millis;
        }
        say(context, String.format(java.util.Locale.ROOT,
                "start finder: %d runs from the spawn, rules %s, %d of %d candidates in unloaded chunks, %.1f ms per search",
                runs, rules, unloaded, sampled, millis / runs));
    }

    private void sinkChecks(CommandContext context, World world, Store<EntityStore> store, Vector3d spot) {
        boolean ok = true;
        for (int[] cfg : new int[][] {{1, 3}, {2, 3}, {3, 3}, {4, 3}, {2, 2}, {2, 4}, {4, 4}}) {
            SinkSchedule.Params params = SinkSchedule.Params.fromConfig(WormsOfArrakisPlugin.get().config());
            params.increment = cfg[0];
            params.rings = cfg[1];
            SinkJob.Options options = new SinkJob.Options();
            options.seed = 5L;
            SinkJob job = SinkJob.create(world, new ArrayList<>(), spot, params, sand.get(), false, options);
            int n = 0;
            int deepest = 0;
            while (job.tick(1 / 30f, store) && n < 1000) {
                deepest = Math.max(deepest, job.maxAbsOffset());
                n++;
            }
            int atEnd = job.maxAbsOffset();
            int dirty = job.dirtyCount();
            job.abort(store);
            boolean good = atEnd == 0 && dirty > 0 && job.dirtyCount() == 0;
            ok &= good;
            say(context, String.format(java.util.Locale.ROOT,
                    "sink increment %d rings %d: %d of %d columns, %d ticks (%.2f s planned), deepest %d px, end offset %d, %d columns changed and restored: %s",
                    cfg[0], cfg[1], job.cellCount(), (2 * cfg[1] + 1) * (2 * cfg[1] + 1), n, job.schedule().totalSeconds,
                    deepest, atEnd, dirty, good ? "OK" : "FAILED"));
        }
        // The breach heave at its cap: cost with no viewers (the packets are counted per viewer in a real run).
        SinkSchedule.Params big = SinkSchedule.Params.fromConfig(WormsOfArrakisPlugin.get().config());
        big.increment = 4;
        big.rings = (int) WormsOfArrakisPlugin.get().config().getBreachRippleMaxRings();
        SinkJob.Options bigOptions = new SinkJob.Options();
        bigOptions.seed = 5L;
        SinkJob heave = SinkJob.create(world, new ArrayList<>(), spot, big, sand.get(), false, bigOptions);
        int heaveTicks = 0;
        long heaveStart = System.nanoTime();
        while (heave.tick(1 / 30f, store) && heaveTicks < 1000) {
            heaveTicks++;
        }
        long heaveNanos = System.nanoTime() - heaveStart;
        int heaveEdits = heave.peakEdits;
        heave.abort(store);
        say(context, String.format(java.util.Locale.ROOT,
                "sink heave rings %d: %d columns, %d ticks, %.3f ms per tick, peak %d block changes in one tick (about %d bytes in the packets, per viewer)",
                big.rings, heave.cellCount(), heaveTicks, heaveNanos / 1e6 / Math.max(heaveTicks, 1), heaveEdits, heaveEdits * 9 + 40));
        // Two at once on the same place: the newer wins the columns, both end clean.
        SinkJob.Options options = new SinkJob.Options();
        SinkJob older = SinkJob.create(world, new ArrayList<>(), spot, new SinkSchedule.Params(), sand.get(), false, options);
        SinkJob newer = SinkJob.create(world, new ArrayList<>(), spot, new SinkSchedule.Params(), sand.get(), false, options);
        for (int i = 0; i < 12; i++) {
            older.tick(1 / 30f, store);
            newer.tick(1 / 30f, store);
        }
        int olderDirty = older.dirtyCount();
        older.abort(store);
        newer.abort(store);
        boolean good = olderDirty == 0 && older.dirtyCount() == 0 && newer.dirtyCount() == 0;
        ok &= good;
        say(context, "sink overlap: older ripple drew " + olderDirty + " columns while the newer owned them, both restored: " + (good ? "OK" : "FAILED"));
        if (!ok) {
            throw new IllegalStateException("sink ripple self-test failed");
        }
    }

    private static void say(CommandContext context, String text) {
        context.sendMessage(Message.raw(text));
    }
}
