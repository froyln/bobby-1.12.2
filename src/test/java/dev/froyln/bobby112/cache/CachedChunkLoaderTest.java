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
        assertFalse(TerrainNbt.validHeightMap(level));
        level.setIntArray("HeightMap", new int[3]);
        assertFalse(TerrainNbt.validHeightMap(level));
        int[] heights = new int[256];
        level.setIntArray("HeightMap", heights);
        assertTrue(TerrainNbt.validHeightMap(level));
        heights[0] = 256;
        assertTrue(TerrainNbt.validHeightMap(level));
        heights[0] = 257;
        assertFalse(TerrainNbt.validHeightMap(level));
        heights[0] = -1;
        assertFalse(TerrainNbt.validHeightMap(level));
        level.setString("HeightMap", "invalid");
        assertFalse(TerrainNbt.validHeightMap(level));
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
    @Test
    public void recordedReadsRejectOversizedNbtBeforeTerrainDecoding() throws Exception {
        java.io.File directory = temporary.newFolder();
        NBTTagCompound root = new NBTTagCompound();
        root.setByteArray("oversized", new byte[17 * 1024 * 1024]);
        try (java.io.DataOutputStream out = net.minecraft.world.chunk.storage.RegionFileCache.getChunkOutputStream(directory, 0, 0)) {
            net.minecraft.nbt.CompressedStreamTools.write(root, out);
        }
        CachedChunkLoader loader = new CachedChunkLoader(directory, new DataFixer(1343));
        try {
            loader.loadChunk(null, 0, 0);
            fail("oversized recorded NBT was admitted");
        } catch (java.io.IOException | RuntimeException expected) {
            assertNotNull(expected.getMessage());
        }
    }

    @Test
    public void rejectsInvalidSectionsBeforeAccessingTheWorld() throws Exception {
        CachedChunkLoader loader = new CachedChunkLoader(temporary.newFolder(), new DataFixer(1343));
        NBTTagCompound root = FallbackWorldTest.terrain(0, 0);
        root.getCompoundTag("Level").getTagList("Sections", 10).getCompoundTagAt(0).setByte("Y", (byte) 16);
        assertNull(loader.checkedReadChunkFromNBT(null, 0, 0, root));
    }

    @Test
    public void verificationReadbackAlsoRejectsOversizedNbt() throws Exception {
        CachedChunkLoader loader = new CachedChunkLoader(temporary.newFolder(), new DataFixer(1343));
        NBTTagCompound root = new NBTTagCompound();
        root.setByteArray("oversized", new byte[17 * 1024 * 1024]);
        try {
            loader.writeVerified(new net.minecraft.util.math.ChunkPos(0, 0), root);
            fail("oversized verification readback was admitted");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("Terrain NBT"));
        }
    }

    @Test
    public void stripsSimulationBeforeFixerAndNativeDecoder() throws Exception {
        java.util.concurrent.atomic.AtomicBoolean fixed = new java.util.concurrent.atomic.AtomicBoolean();
        DataFixer fixer = new DataFixer(1343) {
            @Override public NBTTagCompound process(net.minecraft.util.datafix.IFixType type, NBTTagCompound root) {
                fixed.set(true);
                assertFalse(root.getCompoundTag("Level").hasKey("Entities"));
                assertFalse(root.getCompoundTag("Level").hasKey("TileEntities"));
                assertFalse(root.getCompoundTag("Level").hasKey("TileTicks"));
                return root;
            }
        };
        NBTTagCompound root = FallbackWorldTest.terrain(0, 0);
        for (String key : new String[]{"Entities", "TileEntities", "TileTicks"}) {
            net.minecraft.nbt.NBTTagList list = new net.minecraft.nbt.NBTTagList();
            list.appendTag(new NBTTagCompound()); root.getCompoundTag("Level").setTag(key, list);
        }
        CachedChunkLoader loader = new CachedChunkLoader(temporary.newFolder(), fixer) {
            @Override protected net.minecraft.world.chunk.Chunk checkedReadChunkFromNBT(net.minecraft.world.World world, int x, int z, NBTTagCompound data) {
                assertFalse(data.getCompoundTag("Level").hasKey("Entities"));
                assertFalse(data.getCompoundTag("Level").hasKey("TileEntities"));
                assertFalse(data.getCompoundTag("Level").hasKey("TileTicks"));
                return null;
            }
        };
        assertNull(loader.decode(null, 0, 0, root));
        assertTrue(fixed.get());
    }

    @Test
    public void rejectsOverflowingArrayLengthsBeforeNativeAllocation() throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.DataOutputStream out = new java.io.DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(7); out.writeUTF("bad");
            out.writeInt(Integer.MAX_VALUE); out.writeByte(0);
        }
        try {
            TerrainNbt.read(new java.io.DataInputStream(new java.io.ByteArrayInputStream(bytes.toByteArray())));
            fail("overflowing array length was admitted");
        } catch (java.io.IOException expected) {
            assertNotNull(expected.getMessage());
        } catch (OutOfMemoryError unsafeAllocation) {
            fail("native parser attempted allocation for an overflowing array length");
        }
    }

}
