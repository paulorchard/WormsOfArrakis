package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.server.core.modules.entity.item.ItemComponent;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * The sand disturbance, as far as it can be checked with no client: the chunk rumble's cost, the whole timeline run by
 * hand with a tower, a chest full of items and a bed on the sand, the lag fallback, and a real-time run through the
 * server's own tick that is checked twelve seconds later (the result goes to the log).
 */
final class DisturbanceSelfTest {

    private static final String STONE = "Rock_Stone";
    private static final String CHEST = "Furniture_Ancient_Chest_Small";
    private static final String BED = "Furniture_Ancient_Bed";

    private DisturbanceSelfTest() {
    }

    /** Runs a check on the world thread, unless the server is already stopping. */
    private static void later(World world, Runnable check) {
        try {
            world.execute(check);
        } catch (RuntimeException e) {
            // the world is gone: the server is stopping, and there is nothing to check
        }
    }

    static void run(Consumer<String> say, World world, Store<EntityStore> store, Vector3d spot) {
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        int sand = plugin.arrakisSandId();
        if (sand == WormsOfArrakisPlugin.NOT_INSTALLED) {
            say.accept("disturbance: Dunes of Arrakis is not installed, so nothing happens (checked: no tracking, no rumble)");
            return;
        }
        int cx = ChunkUtil.chunkCoordinate((int) Math.floor(spot.x));
        int cz = ChunkUtil.chunkCoordinate((int) Math.floor(spot.z));
        rumbleCost(say, world, cx, cz, sand);
        for (String block : new String[] {STONE, CHEST, BED}) {
            if (BlockType.getAssetMap().getIndex(block) == Integer.MIN_VALUE) {
                say.accept("disturbance: block " + block + " is not loaded; removal checks skipped");
                return;
            }
        }
        control(say, world, store, spot, sand);
        timeline(say, world, store, spot, false);
        timeline(say, world, store, spot, true);
        realTime(say, world, spot);
    }

    // ------------------------------------------------------------------ the rumble's cost

    private static void rumbleCost(Consumer<String> say, World world, int cx, int cz, int sand) {
        WormsOfArrakisConfig cfg = WormsOfArrakisPlugin.get().config();
        for (int viewers : new int[] {1, 4}) {
            RumbleSchedule schedule = new RumbleSchedule(SinkSchedule.Params.fromConfig(cfg), cfg.getDisturbancePulseSeconds(),
                    cfg.getDisturbanceSeconds(), cfg.getDisturbanceIncrementStart(), cfg.getDisturbanceIncrementEnd(), 12345L);
            ChunkRumbleJob job = ChunkRumbleJob.create(world, cx, cz, sand, schedule, (int) cfg.getDisturbanceMaxEditsPerTick());
            if (job == null) {
                say.accept("disturbance rumble: could not be built (fake blocks or chunk missing)");
                return;
            }
            job.simulateViewers(viewers);
            job.setCentre(cx * 32 + 16, cz * 32 + 16);
            int ticks = (int) Math.round(cfg.getDisturbanceSeconds() * 30);
            int maxWaiting = 0;
            for (int i = 0; i < ticks; i++) {
                job.tick(1 / 30f);
                maxWaiting = Math.max(maxWaiting, job.waitingColumns());
            }
            int dirty = job.dirtyCount();
            String summary = job.summary();
            job.end();
            boolean clean = job.dirtyCount() == 0 && job.claimsHeld() == 0;
            say.accept(String.format(Locale.ROOT, "disturbance rumble, %d simulated viewer(s): %d columns, %d ticks, peak %d edits, %d packets and %d bytes in one tick (budget %d edits), most columns waiting %d, %d columns drawn and restored: %s",
                    viewers, job.columns(), ticks, job.peakEdits(), job.peakPackets(), job.peakBytes(),
                    (int) cfg.getDisturbanceMaxEditsPerTick(), maxWaiting, dirty, clean ? "OK" : "FAILED"));
            say.accept("  " + summary);
        }
    }

    // ------------------------------------------------------------------ a whole disturbance by hand

    /** Everything the test builds, so it can be checked. */
    private static final class Rig {
        final List<int[]> positions = new ArrayList<>();
        int chestX, chestY, chestZ;
        int sandX, sandY, sandZ;
        /** Items already lying near the chest before anything was done (the world has some of its own). */
        int itemsBefore;
    }

    /** Items lying on the ground within 5 blocks of a point (the headless world spawns some of its own elsewhere). */
    private static int itemsNear(Store<EntityStore> store, double x, double y, double z) {
        int[] n = {0};
        store.forEachChunk(ItemComponent.getComponentType(), (chunk, buffer) -> {
            for (int i = 0; i < chunk.size(); i++) {
                TransformComponent t = chunk.getComponent(i, TransformComponent.getComponentType());
                if (t != null && t.getPosition().distance(new Vector3d(x, y, z)) <= 5) {
                    n[0]++;
                }
            }
        });
        return n[0];
    }

    private static int topSand(WorldChunk chunk, int x, int z, int sand) {
        int top = chunk.getHeight(x & ChunkUtil.SIZE_MASK, z & ChunkUtil.SIZE_MASK);
        return chunk.getBlock(x, top, z) == sand && chunk.getBlock(x, top + 1, z) == 0 ? top : Integer.MIN_VALUE;
    }

    /** A tower of three, a chest with items, and a two-block bed, on the sand at and near (x, z). Null if the sand is not flat enough. */
    private static Rig build(World world, Store<EntityStore> store, PlacedBlockTracker tracker, int x, int z, int sand) {
        WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
        if (chunk == null) {
            return null;
        }
        Rig rig = new Rig();
        int y0 = topSand(chunk, x, z, sand);
        if (y0 == Integer.MIN_VALUE) {
            return null;
        }
        rig.sandX = x;
        rig.sandY = y0;
        rig.sandZ = z;
        for (int h = 1; h <= 3; h++) {
            chunk.setBlock(x, y0 + h, z, STONE);
            rig.positions.add(new int[] {x, y0 + h, z});
        }
        int cx = x + 2;
        int cy = topSand(chunk, cx, z, sand);
        if (cy != Integer.MIN_VALUE) {
            chunk.setBlock(cx, cy + 1, z, CHEST);
            rig.chestX = cx;
            rig.chestY = cy + 1;
            rig.chestZ = z;
            rig.positions.add(new int[] {cx, cy + 1, z});
            Ref<ChunkStore> ref = chunk.getBlockComponentEntity(cx, cy + 1, z);
            if (ref != null && ref.isValid()) {
                ItemContainerBlock box = world.getChunkStore().getStore().getComponent(ref, ItemContainerBlock.getComponentType());
                if (box != null) {
                    for (int i = 0; i < 8; i++) {
                        box.getItemContainer().addItemStack(new ItemStack(STONE, 10 + i));
                    }
                }
            }
        }
        int bx = x + 4;
        int by = topSand(chunk, bx, z, sand);
        if (by != Integer.MIN_VALUE) {
            chunk.setBlock(bx, by + 1, z, BED);
            rig.positions.add(new int[] {bx, by + 1, z});
        }
        rig.itemsBefore = rig.chestY == 0 ? 0 : itemsNear(store, rig.chestX + 0.5, rig.chestY + 0.5, rig.chestZ + 0.5);
        for (int[] p : rig.positions) {
            tracker.addForTest(world, p[0], p[1], p[2], null);
        }
        return rig;
    }

    /** @param strictItems false when time has passed and the world may have dropped a few items of its own: only a whole chest's worth fails */
    private static String check(World world, Store<EntityStore> store, Rig rig, int sand, boolean strictItems) {
        List<String> problems = new ArrayList<>();
        for (int[] p : rig.positions) {
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(p[0], p[2]));
            if (chunk == null) {
                problems.add("the chunk is not loaded");
                break;
            }
            if (chunk.getBlock(p[0], p[1], p[2]) != 0) {
                problems.add("block left at " + p[0] + " " + p[1] + " " + p[2]);
            }
            // The cell beside the bed's origin: a half left behind would show here.
            for (int d = -1; d <= 1; d += 2) {
                for (int[] axis : new int[][] {{d, 0, 0}, {0, 0, d}}) {
                    int x = p[0] + axis[0], z = p[2] + axis[2];
                    WorldChunk c = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
                    if (c != null && c.getBlock(x, p[1], z) != 0 && c.getBlock(x, p[1], z) != sand) {
                        boolean ours = false;
                        for (int[] q : rig.positions) {
                            ours |= q[0] == x && q[1] == p[1] && q[2] == z;
                        }
                        if (!ours) {
                            problems.add("a block is left next to " + p[0] + " " + p[1] + " " + p[2] + ": " + c.getBlockType(x, p[1], z).getId());
                        }
                    }
                }
            }
        }
        WorldChunk base = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(rig.sandX, rig.sandZ));
        if (base == null) {
            return problems.isEmpty() ? "the chunk is not loaded" : String.join("; ", problems);
        }
        if (base.getBlock(rig.sandX, rig.sandY, rig.sandZ) != sand) {
            problems.add("the sand under the tower was touched");
        }
        if (rig.chestY != 0) {
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(rig.chestX, rig.chestZ));
            Ref<ChunkStore> ref = chunk.getBlockComponentEntity(rig.chestX, rig.chestY, rig.chestZ);
            if (ref != null && ref.isValid()) {
                problems.add("the chest's block entity is still there");
            }
        }
        int after = itemsNear(store, rig.chestX + 0.5, rig.chestY + 0.5, rig.chestZ + 0.5);
        if (strictItems ? after != rig.itemsBefore : after - rig.itemsBefore >= 8) {
            problems.add("item entities within 5 blocks of the chest went from " + rig.itemsBefore + " to " + after);
        }
        return problems.isEmpty() ? null : String.join("; ", problems);
    }

    /** Removes a chest full of items with the flags the feature uses, and with none, to see what the flags change. */
    private static void control(Consumer<String> say, World world, Store<EntityStore> store, Vector3d spot, int sand) {
        int x = (int) Math.floor(spot.x);
        int z = (int) Math.floor(spot.z);
        WorldChunk chunk = null;
        int y = Integer.MIN_VALUE;
        for (int d = 0; d < 40 && y == Integer.MIN_VALUE; d++) {
            x = (int) Math.floor(spot.x) + d;
            z = (int) Math.floor(spot.z) + 12;
            chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x, z));
            y = chunk == null ? Integer.MIN_VALUE : topSand(chunk, x, z, sand);
        }
        if (y == Integer.MIN_VALUE) {
            say.accept("disturbance flags control: no sand column found; skipped");
            return;
        }
        final int baseline = itemsNear(store, x + 0.5, y + 1.5, z + 0.5);
        StringBuilder out = new StringBuilder("disturbance flags control (chest with 8 stacks, new items on the ground right after):");
        for (int flags : new int[] {SandDisturbance.REMOVE_FLAGS, 0}) {
            int before = itemsNear(store, x + 0.5, y + 1.5, z + 0.5);
            chunk.setBlock(x, y + 1, z, CHEST);
            Ref<ChunkStore> ref = chunk.getBlockComponentEntity(x, y + 1, z);
            ItemContainerBlock box = ref == null ? null : world.getChunkStore().getStore().getComponent(ref, ItemContainerBlock.getComponentType());
            if (box != null) {
                for (int i = 0; i < 8; i++) {
                    box.getItemContainer().addItemStack(new ItemStack(STONE, 10 + i));
                }
            }
            chunk.breakBlock(x, y + 1, z, 0, flags);
            Ref<ChunkStore> gone = chunk.getBlockComponentEntity(x, y + 1, z);
            out.append(flags == 0 ? " no flags: " : " REMOVE_FLAGS (" + flags + "): ").append(itemsNear(store, x + 0.5, y + 1.5, z + 0.5) - before)
                    .append(box == null ? " (no container found)" : "").append(gone != null && gone.isValid() ? ", block entity left" : ", block entity gone").append(';');
        }
        say.accept(out.toString());
        // Items may only appear a tick or two later, when the block entity is processed: look again.
        final int fx = x, fy = y + 1, fz = z;
        CompletableFuture.runAsync(() -> later(world, () -> WormsOfArrakisPlugin.get().getLogger().at(Level.INFO)
                .log("%s", "Disturbance flags control, 2 s later: item entities near the chests now "
                        + itemsNear(world.getEntityStore().getStore(), fx + 0.5, fy + 0.5, fz + 0.5) + " (it was " + baseline + " before the first removal)")),
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));
    }

    private static void timeline(Consumer<String> say, World world, Store<EntityStore> store, Vector3d spot, boolean fallback) {
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        int sand = plugin.arrakisSandId();
        PlacedBlockTracker tracker = new PlacedBlockTracker(); // private, so the real manager does not pick it up
        int x = (int) Math.floor(spot.x) + (fallback ? 8 : 0);
        int z = (int) Math.floor(spot.z);
        Rig rig = null;
        // Look for a spot where the sand is flat enough for all three.
        for (int dx = 0; dx < 20 && rig == null; dx++) {
            for (int dz = 0; dz < 6 && rig == null; dz++) {
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x + dx, z + dz));
                if (chunk == null) {
                    continue;
                }
                int y0 = topSand(chunk, x + dx, z + dz, sand);
                boolean flat = y0 != Integer.MIN_VALUE && topSand(chunk, x + dx + 2, z + dz, sand) == y0
                        && topSand(chunk, x + dx + 4, z + dz, sand) == y0
                        && ChunkUtil.chunkCoordinate(x + dx) == ChunkUtil.chunkCoordinate(x + dx + 4);
                if (flat) {
                    rig = build(world, store, tracker, x + dx, z + dz, sand);
                }
            }
        }
        String name = fallback ? "disturbance lag fallback" : "disturbance timeline";
        if (rig == null) {
            say.accept(name + ": no flat sand found near the spawn; skipped");
            return;
        }
        int cx = ChunkUtil.chunkCoordinate(rig.sandX);
        int cz = ChunkUtil.chunkCoordinate(rig.sandZ);
        SandDisturbance d = new SandDisturbance(world, cx, cz, tracker, tracker.recordFor(world.getName(), cx, cz),
                plugin.disturbances(), sand, 99L);
        d.start(store, fallback ? "forced by the self-test" : null);
        int ticks = 0;
        boolean rumbled = false;
        while (ticks < 30 * 20) {
            ticks++;
            rumbled |= d.rumble() != null;
            if (!d.tick(1 / 30f, store)) {
                break;
            }
        }
        String problem = check(world, store, rig, sand, true);
        boolean timed = fallback ? ticks <= 30 * 2.2 && ticks >= 30 * 1.8 : ticks >= 30 * 9.9 && ticks <= 30 * 10.2;
        boolean ok = problem == null && d.isEnded() && timed && (fallback ? !rumbled : rumbled);
        final Rig built = rig;
        CompletableFuture.runAsync(() -> later(world, () -> WormsOfArrakisPlugin.get().getLogger().at(Level.INFO)
                .log("%s", name + ", 2 s later: " + (built.chestY == 0 ? "no chest" : itemsNear(world.getEntityStore().getStore(),
                        built.chestX + 0.5, built.chestY + 0.5, built.chestZ + 0.5) + " item entities near the chest (it was "
                        + built.itemsBefore + " before)"))),
                CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS));
        say.accept(String.format(Locale.ROOT, "%s: %d blocks placed (tower of 3, chest with 8 stacks, bed), ran %d ticks (%.2f s), rumble %s, %s%s: %s",
                name, rig.positions.size(), ticks, ticks / 30.0, rumbled ? "ran" : "did not run",
                problem == null ? "all removed, chest gone, nothing dropped, sand untouched" : problem, timed ? "" : " (WRONG END TIME)",
                ok ? "OK" : "FAILED"));
        if (!ok) {
            throw new IllegalStateException(name + " failed");
        }
    }

    // ------------------------------------------------------------------ through the server's own tick

    private static void realTime(Consumer<String> say, World world, Vector3d spot) {
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        int sand = plugin.arrakisSandId();
        Store<EntityStore> store = world.getEntityStore().getStore();
        Rig rig = null;
        int x = (int) Math.floor(spot.x) + 16;
        int z = (int) Math.floor(spot.z) + 8;
        for (int dx = 0; dx < 24 && rig == null; dx++) {
            WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(x + dx, z));
            if (chunk != null && topSand(chunk, x + dx, z, sand) != Integer.MIN_VALUE
                    && topSand(chunk, x + dx + 2, z, sand) == topSand(chunk, x + dx, z, sand)
                    && topSand(chunk, x + dx + 4, z, sand) == topSand(chunk, x + dx, z, sand)
                    && ChunkUtil.chunkCoordinate(x + dx) == ChunkUtil.chunkCoordinate(x + dx + 4)) {
                // The real tracker: this starts a real disturbance, ticked by the server and ended by its own clock.
                SandDisturbance.secondsOverride = 4.0; // the headless server unloads these chunks after about seven seconds
                try {
                    rig = build(world, store, plugin.disturbances().tracker(), x + dx, z, sand);
                } finally {
                    SandDisturbance.secondsOverride = null;
                }
            }
        }
        if (rig == null) {
            say.accept("disturbance real time: no flat sand found; skipped");
            return;
        }
        final Rig built = rig;
        say.accept("disturbance real time: " + built.positions.size() + " blocks placed through the tracker; checked in 6 s (result in the log), status: "
                + plugin.disturbances().status());
        CompletableFuture.runAsync(() -> later(world, () -> {
            String problem = check(world, world.getEntityStore().getStore(), built, sand, false);
            plugin.getLogger().at(Level.INFO).log("Disturbance real-time self-test: %s", problem == null
                    ? "OK, all removed by the server's own tick, chest gone, nothing dropped, sand untouched, " + plugin.disturbances().status().size() + " still running"
                    : "FAILED: " + problem);
        }), CompletableFuture.delayedExecutor(6, TimeUnit.SECONDS));
    }
}
