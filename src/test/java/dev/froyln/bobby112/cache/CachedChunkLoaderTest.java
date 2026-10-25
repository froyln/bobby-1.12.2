package dev.froyln.bobby112.cache;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class CachedChunkLoaderTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void validatesHeightMapShapeTypeAndRange() {
        NBTTagCompound level = new NBTTagCompound();
        assertFalse(CachedChunkLoader.validHeightMap(level));
        level.setIntArray("HeightMap", new int[3]);
        assertFalse(CachedChunkLoader.validHeightMap(level));
        int[] heights = new int[256];
        level.setIntArray("HeightMap", heights);
        assertTrue(CachedChunkLoader.validHeightMap(level));
        heights[0] = 256;
        assertTrue(CachedChunkLoader.validHeightMap(level));
        heights[0] = 257;
        assertFalse(CachedChunkLoader.validHeightMap(level));
        heights[0] = -1;
        assertFalse(CachedChunkLoader.validHeightMap(level));
        level.setString("HeightMap", "invalid");
        assertFalse(CachedChunkLoader.validHeightMap(level));
    }

    @Test
    public void malformedMetadataIsRejectedBeforeWorldAccess() throws Exception {
        CachedChunkLoader loader = new CachedChunkLoader(temporary.newFolder(), new DataFixer(1343));
        NBTTagCompound root = new NBTTagCompound();
        assertNull(loader.checkedReadChunkFromNBT(null, 0, 0, root));
        NBTTagCompound level = new NBTTagCompound();
        level.setIntArray("HeightMap", new int[1]);
        root.setTag("Level", level);
        assertNull(loader.checkedReadChunkFromNBT(null, 0, 0, root));
    }
    @Test
    public void detectsSwallowedRegionErrorsAndRetriesWithANewHandle() throws Exception {
        java.io.File directory = temporary.newFolder();
        CachedChunkLoader loader = new CachedChunkLoader(directory, new DataFixer(1343));
        net.minecraft.util.math.ChunkPos pos = new net.minecraft.util.math.ChunkPos(0, 0);
        NBTTagCompound old = new NBTTagCompound(); old.setInteger("value", 1);
        loader.writeVerified(pos, old);
        net.minecraft.world.chunk.storage.RegionFileCache.getRegionFileIfExists(directory, 0, 0).close();
        NBTTagCompound updated = new NBTTagCompound(); updated.setInteger("value", 2);
        PendingSaveQueue<Integer> queue = new PendingSaveQueue<>(1);
        java.util.concurrent.atomic.AtomicInteger attempts = new java.util.concurrent.atomic.AtomicInteger();
        queue.offer("chunk", 2, () -> {
            attempts.incrementAndGet();
            try { loader.writeVerified(pos, updated); }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        });
        queue.poll().run();
        assertEquals(2, attempts.get());
        try (java.io.DataInputStream in = net.minecraft.world.chunk.storage.RegionFileCache.getChunkInputStream(directory, 0, 0)) {
            assertEquals(updated, net.minecraft.nbt.CompressedStreamTools.read(in));
        }
        assertNull(queue.latest("chunk"));
    }
}
