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
                .thenAccept(v -> world.execute(() -> run(context, world, world.getEntityStore().getStore(), originX, originZ)));
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
        if (ModelAsset.getAssetMap().getAsset(WormJob.MODEL_ID) == null) {
            missing.add("model " + WormJob.MODEL_ID);
        }
        if (WormTestCommand.layerBlockId() < 0) {
            missing.add("block Arrakis_Worm_Ripple_Layer");
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

        RippleJob ripple = RippleJob.create(world, java.util.UUID.randomUUID(), new ArrayList<>(),
                new double[] {spot.x, spot.y, spot.z}, 5, "both", sand.get(), WormTestCommand.layerBlockId(), new ArrayList<>());
        int ticks = 0;
        while (ripple.tick(1 / 30f, store) && ticks < 1000) {
            ticks++;
        }
        say(context, "ripple: " + ripple.cellCount() + " columns, ran to its end in " + ticks + " ticks");

        for (int radius : new int[] {5, 20, 40}) {
            long t0 = System.nanoTime();
            RippleJob big = RippleJob.create(world, java.util.UUID.randomUUID(), new ArrayList<>(),
                    new double[] {spot.x, spot.y, spot.z}, radius, "raise", sand.get(), WormTestCommand.layerBlockId(), new ArrayList<>());
            long built = System.nanoTime() - t0;
            int n = 0;
            t0 = System.nanoTime();
            while (big.tick(1 / 30f, store) && n < 1000) {
                n++;
            }
            long ran = System.nanoTime() - t0;
            com.hypixel.hytale.protocol.packets.world.SetBlockCmd[] cmds =
                    new com.hypixel.hytale.protocol.packets.world.SetBlockCmd[big.peakBlocksPerTick()];
            java.util.Arrays.fill(cmds, new com.hypixel.hytale.protocol.packets.world.SetBlockCmd((short) 0, 1, (short) 0, (byte) 0));
            int bytes = new com.hypixel.hytale.protocol.packets.world.ServerSetBlocks(0, 0, 0, cmds).computeSize();
            say(context, String.format(java.util.Locale.ROOT,
                    "ripple radius %d: %d columns (%d block updates), built in %.1f ms, %d ticks, tick loop %.1f ms, peak %d updates in one tick = %d bytes in one packet if all in one section (compressed: %s)",
                    radius, big.cellCount(), big.cellCount() * 2, built / 1e6, n, ran / 1e6, big.peakBlocksPerTick(), bytes,
                    com.hypixel.hytale.protocol.packets.world.ServerSetBlocks.IS_COMPRESSED));
        }

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
            say(context, "worm (" + (persist ? "persistent" : "not saved") + "): spawned, moved for " + n
                    + " ticks, removed");
        }
    }

    private static void say(CommandContext context, String text) {
        context.sendMessage(Message.raw(text));
    }
}
