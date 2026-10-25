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
}
