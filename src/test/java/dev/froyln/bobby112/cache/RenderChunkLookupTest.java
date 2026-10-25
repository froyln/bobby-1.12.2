package dev.froyln.bobby112.cache;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.world.chunk.Chunk;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

public class RenderChunkLookupTest {
    @Before
    public void reset() {
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
        ChunkCache.begin(null);
        ChunkCache.updateWindow(0, 0, 4, new LongOpenHashSet());
    }
    @After
    public void clear() { ChunkCache.setEnabled(false); ChunkCache.setEnabled(true); }

    @Test
    public void cachedCenterCanMeshWithoutUnknownNeighbors() {
        ChunkProviderClient provider = new ChunkProviderClient(null);
        ChunkCache.store(null, new Chunk(null, 0, 0));
        assertTrue(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
        assertTrue(provider.getLoadedChunk(1, 0) == null);
    }

    @Test
    public void missingCenterAndLiveCenterKeepNormalNeighborChecks() {
        ChunkProviderClient provider = new ChunkProviderClient(null);
        assertFalse(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
        ChunkCache.store(null, new Chunk(null, 0, 0));
        provider.loadChunk(0, 0);
        assertFalse(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
        provider.loadChunk(1, 0);
        assertTrue(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
    }
}
