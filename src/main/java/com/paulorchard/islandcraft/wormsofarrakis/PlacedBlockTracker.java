package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.EnvironmentBreakBlockEvent;
import com.hypixel.hytale.server.core.event.events.ecs.PlaceBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.joml.Vector3i;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Remembers the blocks players place on Arrakis_Sand, per chunk, in memory only. The server raises
 * {@link PlaceBlockEvent} on the placing player's entity before the block is set (and other systems may still cancel
 * it), so a placement is only a pending claim until the next tick, when the block is looked for in the world.
 *
 * <p>A placement counts if the block goes on Arrakis_Sand or on a block already tracked in that chunk. The event has
 * only the position, not the face that was clicked, so "on" means the block below for sand, and the block below or one
 * of the four sides for a tracked block: a block in the air beside a tracked tower counts, a block against a rock wall
 * or on rock beside a dune does not.
 */
final class PlacedBlockTracker {

    /** A block a player placed. */
    static final class Placed {
        final int x, y, z;
        /** The block type's item key when it was found in the world (state variants of one block share it). */
        String key;
        int blockId;
        final UUID by;
        final long placedAtMillis;

        Placed(int x, int y, int z, UUID by, long placedAtMillis) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.by = by;
            this.placedAtMillis = placedAtMillis;
        }
    }

    /** The placed blocks of one chunk. */
    static final class ChunkRecord {
        final String world;
        final int cx, cz;
        final Map<Long, Placed> blocks = new LinkedHashMap<>();

        ChunkRecord(String world, int cx, int cz) {
            this.world = world;
            this.cx = cx;
            this.cz = cz;
        }

        long chunkIndex() {
            return ChunkUtil.indexChunk(cx, cz);
        }
    }

    /** A placement seen this tick that has not been looked for in the world yet. */
    private record Pending(int x, int y, int z, UUID by, long atMillis, boolean viaTracked) {
    }

    /** A position whose block may have been broken: looked at next tick. */
    private record Recheck(int x, int y, int z) {
    }

    /** What the disturbance side wants to hear. */
    interface Listener {
        /** A placement was confirmed and added to the record. */
        void placed(World world, ChunkRecord record, Placed block);

        /** A tracked block is gone from the world (broken, replaced). */
        void forgotten(World world, ChunkRecord record, Placed block);
    }

    private final Map<String, Map<Long, ChunkRecord>> records = new ConcurrentHashMap<>();
    private final Map<String, List<Pending>> pending = new ConcurrentHashMap<>();
    private final Map<String, List<Recheck>> rechecks = new ConcurrentHashMap<>();
    private volatile Listener listener;

    void setListener(Listener listener) {
        this.listener = listener;
    }

    static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | ((long) y & 0xFFFL);
    }

    ChunkRecord recordOf(String world, long chunkIndex) {
        Map<Long, ChunkRecord> byChunk = records.get(world);
        return byChunk == null ? null : byChunk.get(chunkIndex);
    }

    boolean isTracked(String world, int x, int y, int z) {
        ChunkRecord r = recordOf(world, ChunkUtil.indexChunkFromBlock(x, z));
        return r != null && r.blocks.containsKey(pack(x, y, z));
    }

    /** Forgets a chunk's record (the disturbance ended, or was stopped). */
    void clear(String world, long chunkIndex) {
        Map<Long, ChunkRecord> byChunk = records.get(world);
        if (byChunk != null) {
            byChunk.remove(chunkIndex);
        }
    }

    int trackedCount(String world) {
        Map<Long, ChunkRecord> byChunk = records.get(world);
        int n = 0;
        if (byChunk != null) {
            for (ChunkRecord r : byChunk.values()) {
                n += r.blocks.size();
            }
        }
        return n;
    }

    ChunkRecord recordFor(String world, int cx, int cz) {
        return records.computeIfAbsent(world, w -> new ConcurrentHashMap<>())
                .computeIfAbsent(ChunkUtil.indexChunk(cx, cz), k -> new ChunkRecord(world, cx, cz));
    }

    // ------------------------------------------------------------------ the rule

    /** True if the block placed at (x,y,z) goes on Arrakis_Sand (the block below) or on a tracked block (below it, or at its sides). */
    boolean counts(World world, int x, int y, int z, int arrakisSand) {
        int[][] supports = {{0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] d : supports) {
            if (isTracked(world.getName(), x + d[0], y + d[1], z + d[2])) {
                return true;
            }
        }
        // Sand only counts from below: a block set on rock beside a dune is not on the sand.
        WorldAccess access = new WorldAccess(world);
        return access.isTicking(x, z) && y >= 1 && access.block(x, y - 1, z) == arrakisSand;
    }

    /** Item key of a block id, so that open and closed doors, chests and so on count as the same block. */
    static String keyOf(int blockId) {
        BlockType type = BlockType.getAssetMap().getAsset(blockId);
        if (type == null) {
            return null;
        }
        return type.getItem() != null ? type.getItem().getId() : type.getId();
    }

    // ------------------------------------------------------------------ the next tick

    /** Looks for last tick's placements and rechecks in the world. Call once per world tick on the world thread. */
    void tick(World world) {
        List<Pending> list = pending.remove(world.getName());
        if (list != null) {
            for (Pending p : list) {
                confirm(world, p);
            }
        }
        List<Recheck> again = rechecks.remove(world.getName());
        if (again != null) {
            for (Recheck r : again) {
                recheck(world, r.x(), r.y(), r.z());
            }
        }
    }

    private void confirm(World world, Pending p) {
        WorldAccess access = new WorldAccess(world);
        if (!access.isTicking(p.x(), p.z())) {
            return;
        }
        int id = access.block(p.x(), p.y(), p.z());
        if (id == 0) {
            return; // cancelled, or never set
        }
        ChunkRecord record = recordFor(world.getName(), ChunkUtil.chunkCoordinate(p.x()), ChunkUtil.chunkCoordinate(p.z()));
        long key = pack(p.x(), p.y(), p.z());
        if (record.blocks.containsKey(key)) {
            return;
        }
        Placed block = new Placed(p.x(), p.y(), p.z(), p.by(), p.atMillis());
        block.blockId = id;
        block.key = keyOf(id);
        record.blocks.put(key, block);
        Listener l = listener;
        if (l != null) {
            l.placed(world, record, block);
        }
    }

    /** Drops a tracked position that is now air or a different block. Returns true if it was dropped. */
    boolean recheck(World world, int x, int y, int z) {
        ChunkRecord record = recordOf(world.getName(), ChunkUtil.indexChunkFromBlock(x, z));
        if (record == null) {
            return false;
        }
        Placed block = record.blocks.get(pack(x, y, z));
        if (block == null) {
            return false;
        }
        WorldAccess access = new WorldAccess(world);
        if (!access.isTicking(x, z)) {
            return false; // not loaded: nothing can be said
        }
        int id = access.block(x, y, z);
        if (id == block.blockId || (id != 0 && block.key != null && block.key.equals(keyOf(id)))) {
            return false;
        }
        forget(world, record, block);
        return true;
    }

    void forget(World world, ChunkRecord record, Placed block) {
        if (record.blocks.remove(pack(block.x, block.y, block.z)) != null) {
            Listener l = listener;
            if (l != null) {
                l.forgotten(world, record, block);
            }
        }
    }

    /** Checks every tracked block of a chunk against the world; returns how many were dropped. */
    int sweep(World world, ChunkRecord record) {
        int dropped = 0;
        for (Placed block : new ArrayList<>(record.blocks.values())) {
            if (recheck(world, block.x, block.y, block.z)) {
                dropped++;
            }
        }
        return dropped;
    }

    // ------------------------------------------------------------------ the events

    private void claim(Store<EntityStore> store, ArchetypeChunk<EntityStore> chunk, int index, PlaceBlockEvent event) {
        WormsOfArrakisPlugin plugin = WormsOfArrakisPlugin.get();
        WormsOfArrakisConfig cfg = plugin.config();
        if (!cfg.isDisturbanceEnabled()) {
            return;
        }
        int sand = plugin.arrakisSandId();
        if (sand == WormsOfArrakisPlugin.NOT_INSTALLED) {
            return; // no Dunes of Arrakis: nothing happens, and it never falls back to vanilla sand
        }
        // Only a player's placement counts: the entity the event is raised on has to be a player.
        Player player = chunk.getComponent(index, Player.getComponentType());
        PlayerRef ref = chunk.getComponent(index, PlayerRef.getComponentType());
        if (player == null || ref == null) {
            return;
        }
        if (player.getGameMode() == GameMode.Creative && !cfg.isDisturbanceCreativeTriggers()) {
            return;
        }
        Vector3i at = event.getTargetBlock();
        World world = store.getExternalData().getWorld();
        if (event.isCancelled() || !counts(world, at.x, at.y, at.z, sand)) {
            return;
        }
        pending.computeIfAbsent(world.getName(), w -> new ArrayList<>())
                .add(new Pending(at.x, at.y, at.z, ref.getUuid(), System.currentTimeMillis(), false));
    }

    private void touch(Store<EntityStore> store, Vector3i at) {
        World world = store.getExternalData().getWorld();
        if (recordOf(world.getName(), ChunkUtil.indexChunkFromBlock(at.x, at.z)) != null) {
            rechecks.computeIfAbsent(world.getName(), w -> new ArrayList<>()).add(new Recheck(at.x, at.y, at.z));
        }
    }

    /** Placed by a player: raised on the player's entity, before the block is set. */
    final class Place extends EntityEventSystem<EntityStore, PlaceBlockEvent> {
        Place() {
            super(PlaceBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, PlaceBlockEvent event) {
            claim(store, chunk, index, event);
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }
    }

    /** Broken by a player. The block is looked at again next tick, in case another system cancelled the break. */
    final class Break extends EntityEventSystem<EntityStore, BreakBlockEvent> {
        Break() {
            super(BreakBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, BreakBlockEvent event) {
            if (!event.isCancelled()) {
                touch(store, event.getTargetBlock());
            }
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }
    }

    /** Broken by the environment (physics, fluids, and so on). */
    final class EnvironmentBreak extends EntityEventSystem<EntityStore, EnvironmentBreakBlockEvent> {
        EnvironmentBreak() {
            super(EnvironmentBreakBlockEvent.class);
        }

        @Override
        public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
                           CommandBuffer<EntityStore> buffer, EnvironmentBreakBlockEvent event) {
            touch(store, event.getTargetBlock());
        }

        @Override
        public Query<EntityStore> getQuery() {
            return Query.any();
        }
    }

    Place placeSystem() {
        return new Place();
    }

    Break breakSystem() {
        return new Break();
    }

    EnvironmentBreak environmentBreakSystem() {
        return new EnvironmentBreak();
    }

    /** Adds a block as if a player had placed it and the next tick had confirmed it (for the self-test and commands). */
    Placed addForTest(World world, int x, int y, int z, UUID by) {
        WorldAccess access = new WorldAccess(world);
        ChunkRecord record = recordFor(world.getName(), ChunkUtil.chunkCoordinate(x), ChunkUtil.chunkCoordinate(z));
        Placed block = new Placed(x, y, z, by, System.currentTimeMillis());
        block.blockId = access.isTicking(x, z) ? access.block(x, y, z) : 0;
        block.key = keyOf(block.blockId);
        record.blocks.put(pack(x, y, z), block);
        Listener l = listener;
        if (l != null) {
            l.placed(world, record, block);
        }
        return block;
    }

    /** Map from the world name to how many are waiting (for the status line). */
    Map<String, Integer> pendingCounts() {
        Map<String, Integer> out = new HashMap<>();
        pending.forEach((k, v) -> out.put(k, v.size()));
        return out;
    }
}
