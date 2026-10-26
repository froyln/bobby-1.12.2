package dev.froyln.bobby112.cache;

import java.io.DataInput;
import java.io.IOException;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTSizeTracker;

final class TerrainNbt {
    private TerrainNbt() {}

    static NBTTagCompound read(DataInput input) throws IOException {
        try {
            return CompressedStreamTools.read(input, new NBTSizeTracker(16L * 1024 * 1024));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid or oversized terrain NBT", invalid);
        }
    }

    static boolean validSkyLight(NBTTagCompound root) {
        NBTTagList sections = root.getCompoundTag("Level").getTagList("Sections", 10);
        for (int i = 0; i < sections.tagCount(); i++) {
            if (!array(sections.getCompoundTagAt(i), "SkyLight", 2048)) return false;
        }
        return true;
    }

    static boolean sanitize(NBTTagCompound root, int x, int z) {
        if (root.getInteger("DataVersion") > 1343 || !root.hasKey("Level", 10)) return false;
        NBTTagCompound level = root.getCompoundTag("Level");
        if (!level.hasKey("xPos", 3) || !level.hasKey("zPos", 3)
                || level.getInteger("xPos") != x || level.getInteger("zPos") != z
                || !validHeightMap(level) || !level.hasKey("Sections", 9)
                || !level.hasKey("Biomes", 7) || level.getByteArray("Biomes").length != 256) return false;
        NBTTagList sections = (NBTTagList) level.getTag("Sections");
        if (sections.tagCount() > 16 || (sections.tagCount() > 0 && sections.getTagType() != 10)) return false;
        boolean[] seen = new boolean[16];
        for (int i = 0; i < sections.tagCount(); i++) {
            NBTTagCompound section = sections.getCompoundTagAt(i);
            int y = section.getByte("Y");
            if (!section.hasKey("Y", 1) || y < 0 || y >= 16 || seen[y]
                    || !array(section, "Blocks", 4096) || !array(section, "Data", 2048)
                    || !array(section, "BlockLight", 2048)
                    || (section.hasKey("SkyLight") && !array(section, "SkyLight", 2048))
                    || (section.hasKey("Add") && !array(section, "Add", 2048))) return false;
            seen[y] = true;
        }
        level.removeTag("Entities");
        level.removeTag("TileEntities");
        level.removeTag("TileTicks");
        return true;
    }

    private static boolean array(NBTTagCompound tag, String name, int length) {
        return tag.hasKey(name, 7) && tag.getByteArray(name).length == length;
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
