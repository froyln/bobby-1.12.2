package dev.froyln.bobby112.cache;

import java.io.File;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

class CachedChunkLoader extends AnvilChunkLoader {
    CachedChunkLoader(File directory, DataFixer fixer) {
        super(directory, fixer);
    }

    @Override
    protected Chunk checkedReadChunkFromNBT(World world, int x, int z, NBTTagCompound root) {
        if (!validHeightMap(root.getCompoundTag("Level"))) {
            System.err.println("[Bobby 1.12.2] Ignoring cached chunk with invalid height map: " + x + "," + z);
            return null;
        }
        return super.checkedReadChunkFromNBT(world, x, z, root);
    }

    static boolean validHeightMap(NBTTagCompound level) {
        if (!level.hasKey("HeightMap", 11)) {
            return false;
        }
        int[] heights = level.getIntArray("HeightMap");
        if (heights.length != 256) {
            return false;
        }
        for (int height : heights) {
            if (height < 0 || height > 256) {
                return false;
            }
        }
        return true;
    }
}
