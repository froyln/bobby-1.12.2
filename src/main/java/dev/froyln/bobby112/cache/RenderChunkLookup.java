package dev.froyln.bobby112.cache;

import net.minecraft.client.multiplayer.ChunkProviderClient;
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

    public static boolean hasRenderNeighbor(World world, ChunkProviderClient provider,
                                           int centerX, int centerZ, int neighborX, int neighborZ) {
        Chunk neighbor = getCachedOrLive(world, neighborX, neighborZ, provider);
        return !neighbor.isEmpty() || (provider.getLoadedChunk(centerX, centerZ) == null
                && ChunkCache.get(world, centerX, centerZ) != null);
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
