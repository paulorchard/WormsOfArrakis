package com.paulorchard.islandcraft.wormsofarrakis;

import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.packets.world.ServerSetBlocks;
import com.hypixel.hytale.protocol.packets.world.SetBlockCmd;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends client-only block changes: one ServerSetBlocks per 32-block section per viewer, and only to viewers whose
 * client has that section loaded. A block id of {@link #REAL} sends the block the world has now.
 */
final class FakeBlockSender {

    static final int REAL = -1;

    record Edit(int x, int y, int z, int blockId) {
    }

    private final World world;
    private final List<PlayerRef> viewers;
    long packets;
    long blocks;
    long bytes;

    FakeBlockSender(World world, List<PlayerRef> viewers) {
        this.world = world;
        this.viewers = viewers;
    }

    /** Returns the packets written. */
    int send(List<Edit> edits) {
        if (edits.isEmpty()) {
            return 0;
        }
        Map<Long, List<SetBlockCmd>> bySection = new HashMap<>();
        Map<Long, int[]> sectionCoords = new HashMap<>();
        for (Edit edit : edits) {
            int blockId = edit.blockId();
            int filler = 0;
            int rotation = 0;
            int local = ChunkUtil.indexBlock(edit.x() & ChunkUtil.SIZE_MASK, edit.y() & ChunkUtil.SIZE_MASK,
                    edit.z() & ChunkUtil.SIZE_MASK);
            if (blockId == REAL) {
                WorldChunk chunk = world.getChunkIfLoaded(ChunkUtil.indexChunkFromBlock(edit.x(), edit.z()));
                if (chunk == null) {
                    continue; // The chunk is gone from the server; the client gets the real chunk when it loads again.
                }
                BlockSection section = chunk.getBlockChunk().getSectionAtBlockY(edit.y());
                blockId = section.get(local);
                filler = section.getFiller(local);
                rotation = section.getRotationIndex(local);
            }
            int sx = ChunkUtil.chunkCoordinate(edit.x());
            int sy = ChunkUtil.chunkCoordinate(edit.y());
            int sz = ChunkUtil.chunkCoordinate(edit.z());
            long key = ((long) sx & 0x1FFFFF) << 42 | ((long) sy & 0x1FFFFF) << 21 | ((long) sz & 0x1FFFFF);
            sectionCoords.putIfAbsent(key, new int[] {sx, sy, sz});
            bySection.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new SetBlockCmd((short) local, blockId, (short) filler, (byte) rotation));
        }
        int sent = 0;
        for (Map.Entry<Long, List<SetBlockCmd>> entry : bySection.entrySet()) {
            int[] s = sectionCoords.get(entry.getKey());
            ServerSetBlocks packet = new ServerSetBlocks(s[0], s[1], s[2], entry.getValue().toArray(new SetBlockCmd[0]));
            int size = -1;
            for (PlayerRef viewer : viewers) {
                if (viewer.getReference() != null && viewer.getChunkTracker().isLoaded(s[0], s[1], s[2])) {
                    viewer.getPacketHandler().writeNoCache(packet);
                    if (size < 0) {
                        size = packet.computeSize();
                    }
                    sent++;
                    blocks += entry.getValue().size();
                    bytes += size;
                }
            }
        }
        packets += sent;
        return sent;
    }
}
