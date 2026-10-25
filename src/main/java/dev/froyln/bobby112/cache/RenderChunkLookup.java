package dev.froyln.bobby112.cache;

import net.minecraft.client.multiplayer.ChunkProviderClient;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

public final class RenderChunkLookup {
    private static final ThreadLocal<Boolean> RENDER_CACHE_BUILD = new ThreadLocal<>();

    private RenderChunkLookup() {
    }

    public static Entity camera(Minecraft client) {
        return camera(client.getRenderViewEntity(), client.player);
    }

    static Entity camera(Entity view, Entity player) {
        return view == null ? player : view;
    }

    public static ChunkCacheScope beginCacheBuild() {
        return new ChunkCacheScope();
    }

    public static Chunk get(World world, int x, int z, IChunkProvider provider) {
        if (RENDER_CACHE_BUILD.get() == null || !(world instanceof WorldClient) || !(provider instanceof ChunkProviderClient)) {
            return provider.provideChunk(x, z);
        }
        return getCachedOrLive(world, x, z, (ChunkProviderClient) provider);
    }

    public static Chunk getCachedOrLive(World world, int x, int z, ChunkProviderClient provider) {
        Chunk live = provider.getLoadedChunk(x, z);
        if (live != null) {
            return live;
        }
        Chunk cached = ChunkCache.get(world, x, z);
        return cached != null ? cached : provider.provideChunk(x, z);
    }

    public static LongSet connectedColumns(long[] cached, int centerX, int centerZ, int distance) {
        LongSet columns = new LongOpenHashSet();
        distance = Math.max(0, Math.min(33, distance));
        int minX = centerX, maxX = centerX, minZ = centerZ, maxZ = centerZ;
        for (long pos : cached) {
            if (!ChunkCache.withinDistance(pos, centerX, centerZ, distance)) continue;
            columns.add(pos);
            minX = Math.min(minX, ChunkCache.chunkX(pos));
            maxX = Math.max(maxX, ChunkCache.chunkX(pos));
            minZ = Math.min(minZ, ChunkCache.chunkZ(pos));
            maxZ = Math.max(maxZ, ChunkCache.chunkZ(pos));
        }
        if (!columns.isEmpty()) {
            for (long x = minX; x <= maxX; x++) {
                for (long z = minZ; z <= maxZ; z++) columns.add(ChunkPos.asLong((int) x, (int) z));
            }
        }
        return columns;
    }

    public static boolean hasRenderNeighbor(World world, ChunkProviderClient provider,
                                           int centerX, int centerZ, int neighborX, int neighborZ) {
        Chunk neighbor = getCachedOrLive(world, neighborX, neighborZ, provider);
        return !neighbor.isEmpty() || ChunkCache.renderWindowContains(world, centerX, centerZ);
    }

    public static final class ChunkCacheScope implements AutoCloseable {
        private final Boolean previous = RENDER_CACHE_BUILD.get();

        private ChunkCacheScope() {
            RENDER_CACHE_BUILD.set(Boolean.TRUE);
        }

        @Override
        public void close() {
            if (previous == null) {
                RENDER_CACHE_BUILD.remove();
            } else {
                RENDER_CACHE_BUILD.set(previous);
            }
        }
    }
}
