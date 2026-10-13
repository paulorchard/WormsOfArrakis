package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.modules.block.BlockModule;
import com.hypixel.hytale.server.core.modules.block.components.ItemContainerBlock;
import com.hypixel.hytale.server.core.universe.world.SetBlockSettings;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockOperations;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.heightmap.HeightmapColumn;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.GetChunkFlags;

import java.util.concurrent.CompletableFuture;

/**
 * Block, height and chunk access for a loaded world on Hytale 0.7.0 (World and WorldChunk lost their block and chunk
 * accessors). Create one per operation and never keep it: it caches sections while it lives. World thread only unless a
 * method says otherwise.
 */
final class WorldAccess {

    /** What a height read returns when the column has no surface or is not loaded. */
    static final int NO_HEIGHT = Integer.MIN_VALUE;

    private final World world;
    private final ChunkStore chunks;
    private final SectionReader sections;

    WorldAccess(World world) {
        this.world = world;
        this.chunks = world.getChunkStore();
        this.sections = new SectionReader(chunks);
    }

    /** The chunk column's entity ref, or null when that chunk column is not loaded. Thread-safe. */
    Ref<ChunkStore> column(int x, int z) {
        Ref<ChunkStore> ref = chunks.getChunkReference(ChunkUtil.indexChunkFromBlock(x, z));
        return ref != null && ref.isValid() ? ref : null;
    }

    /** True when the chunk column is in the store, ticking or not. */
    boolean isLoaded(int x, int z) {
        return column(x, z) != null;
    }

    /**
     * True when the chunk column is loaded AND ticking: what the old World.getChunkIfLoaded(index) != null meant
     * (ChunkFlag.TICKING is gone; a chunk now ticks when its archetype lacks the NonTicking marker).
     */
    boolean isTicking(int x, int z) {
        Ref<ChunkStore> ref = column(x, z);
        return ref != null && !chunks.getStore().getArchetype(ref).contains(ChunkStore.REGISTRY.getNonTickingComponentType());
    }

    /** {@link #isTicking(int, int)} for a chunk index (ChunkUtil.indexChunk). */
    boolean isTickingChunk(int chunkX, int chunkZ) {
        return isTicking(chunkX << 5, chunkZ << 5);
    }

    /**
     * Loads a chunk column (replaces World.getChunkAsync). The raw ChunkStore future does not complete on the world
     * thread, so this hops back with thenApplyAsync(..., world). The ref is null if the chunk could not be loaded.
     */
    static CompletableFuture<Ref<ChunkStore>> loadColumn(World world, int chunkX, int chunkZ, boolean ticking) {
        long index = ChunkUtil.indexChunk(chunkX, chunkZ);
        return world.getChunkStore()
                .getChunkReferenceAsync(index, ticking ? GetChunkFlags.SET_TICKING : 0)
                .thenApplyAsync(ref -> ref, world);
    }

    /** The heightmap component of a loaded column (hold on to it when reading many columns of one chunk), or null. */
    HeightmapColumn heightmap(int x, int z) {
        Ref<ChunkStore> column = column(x, z);
        return column == null ? null : chunks.getStore().getComponentConcurrent(column, HeightmapColumn.getComponentType());
    }

    /** The section containing this block, or null when it is not loaded. */
    Ref<ChunkStore> sectionRef(int x, int y, int z) {
        Ref<ChunkStore> ref = chunks.getChunkSectionReferenceAtBlock(x, y, z);
        return ref != null && ref.isValid() ? ref : null;
    }

    /** Block id at a world position; 0 (air) when the section is not loaded. */
    int block(int x, int y, int z) {
        return sections.getBlock(x, y, z);
    }

    /** Null for air or an unknown id. */
    BlockType blockType(int x, int y, int z) {
        int id = block(x, y, z);
        return id <= 0 ? null : BlockType.getAssetMap().getAsset(id);
    }

    /**
     * Y of the top block of the column at world x/z, or NO_HEIGHT when the column is not loaded or has no surface.
     * Takes WORLD x/z, not chunk-local.
     */
    int height(int x, int z) {
        HeightmapColumn map = heightmap(x, z);
        return map == null ? NO_HEIGHT : map.getHeight(x, z);
    }

    /** Height of the surface at world x/z, or NO_HEIGHT when that chunk is not loaded and ticking, or the column is empty. */
    static int groundHeight(World world, double x, double z) {
        WorldAccess access = new WorldAccess(world);
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        return access.isTicking(bx, bz) ? access.height(bx, bz) : NO_HEIGHT;
    }

    /** The WorldChunk component (keep-loaded control) of a ticking chunk column, or null. */
    WorldChunk chunkComponent(int chunkX, int chunkZ) {
        if (!isTickingChunk(chunkX, chunkZ)) {
            return null;
        }
        Ref<ChunkStore> column = column(chunkX << 5, chunkZ << 5);
        return column == null ? null : chunks.getStore().getComponent(column, WorldChunk.getComponentType());
    }

    /** Removes the block at a world position (setBlock to Empty); false when its section is not loaded. */
    boolean remove(int x, int y, int z, int settings) {
        if ((settings & SetBlockSettings.NO_DROP_ITEMS) != 0) {
            emptyContainer(x, y, z);
        }
        Ref<ChunkStore> section = sectionRef(x, y, z);
        BlockType empty = BlockType.getAssetMap().getAsset(0);
        return section != null && empty != null
                && BlockOperations.setBlock(chunks, section, x, y, z, 0, empty, 0, 0, settings);
    }

    /** Sets a block by name (rotation 0, filler 0); false when its section is not loaded or the id is unknown. */
    boolean setBlock(int x, int y, int z, String blockName, int settings) {
        Ref<ChunkStore> section = sectionRef(x, y, z);
        if (section == null) {
            return false;
        }
        int id = BlockType.getAssetMap().getIndex(blockName);
        BlockType type = id == Integer.MIN_VALUE ? null : BlockType.getAssetMap().getAsset(id);
        return type != null && BlockOperations.setBlock(chunks, section, x, y, z, id, type, 0, 0, settings);
    }

    /**
     * In 0.7.0 removing a block entity spills its item container into the world whatever the settings say (the old
     * breakBlock honoured NO_DROP_ITEMS), so a removal that must not drop items empties the container first.
     */
    private void emptyContainer(int x, int y, int z) {
        Ref<ChunkStore> entity = blockEntity(x, y, z);
        if (entity == null || !entity.isValid()) {
            return;
        }
        ItemContainerBlock box = chunks.getStore().getComponent(entity, ItemContainerBlock.getComponentType());
        if (box != null) {
            box.getItemContainer().removeAllItemStacks();
        }
    }

    /** The block entity ref at a world position, or null. */
    Ref<ChunkStore> blockEntity(int x, int y, int z) {
        return BlockModule.getBlockEntity(world, x, y, z);
    }

    /** A cursor over block, filler and rotation; call moveTo(x, y, z) first. Valid while this WorldAccess lives. */
    SectionReader.SectionCursor sectionCursor() {
        return sections.sectionCursor();
    }
}
