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
    public void emptyRenderSectionsCanBridgeUnvisitedGapsWithinTheWindow() {
        ChunkProviderClient provider = new ChunkProviderClient(null);
        assertTrue(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
        assertNull(provider.getLoadedChunk(0, 0));
        assertFalse(RenderChunkLookup.hasRenderNeighbor(null, provider, 10, 0, 11, 0));
        ChunkCache.setEnabled(false);
        assertFalse(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
        ChunkCache.setEnabled(true);
        ChunkCache.updateWindow(0, 0, 4, new LongOpenHashSet());
        ChunkCache.store(null, new Chunk(null, 0, 0));
        provider.loadChunk(0, 0);
        assertFalse(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
        provider.loadChunk(1, 0);
        assertTrue(RenderChunkLookup.hasRenderNeighbor(null, provider, 0, 0, 1, 0));
    }
    @Test
    public void renderCameraWinsOverStationaryPlayerAndFallsBackSafely() {
        net.minecraft.entity.Entity player = new CameraEntity();
        net.minecraft.entity.Entity camera = new CameraEntity();
        camera.posX = 320;
        assertSame(camera, RenderChunkLookup.camera(camera, player));
        assertEquals(320, RenderChunkLookup.camera(camera, player).posX, 0);
        assertEquals(0, player.posX, 0);
        assertSame(player, RenderChunkLookup.camera(null, player));
        assertNull(RenderChunkLookup.camera((net.minecraft.entity.Entity) null, null));
    }

    private static class CameraEntity extends net.minecraft.entity.Entity {
        CameraEntity() { super(null); }
        protected void entityInit() {}
        protected void readEntityFromNBT(net.minecraft.nbt.NBTTagCompound tag) {}
        protected void writeEntityToNBT(net.minecraft.nbt.NBTTagCompound tag) {}
    }
}
