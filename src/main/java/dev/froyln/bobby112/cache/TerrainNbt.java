package dev.froyln.bobby112.cache;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTSizeTracker;

final class TerrainNbt {
    private TerrainNbt() {}

    private static final int MAX_BYTES = 16 * 1024 * 1024;

    static NBTTagCompound read(DataInputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (bytes.size() + count > MAX_BYTES) throw new IOException("Terrain NBT exceeds the decoded byte limit");
            bytes.write(buffer, 0, count);
        }
        byte[] encoded = bytes.toByteArray();
        DataInputStream check = new DataInputStream(new ByteArrayInputStream(encoded));
        if (check.readUnsignedByte() != 10) throw new IOException("Terrain NBT root must be a compound");
        skipString(check);
        checkPayload(check, 10, 0);
        try {
            return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(encoded)), new NBTSizeTracker(MAX_BYTES));
        } catch (RuntimeException invalid) {
            throw new IOException("Invalid or oversized terrain NBT", invalid);
        }
    }

    private static void checkPayload(DataInputStream input, int type, int depth) throws IOException {
        if (depth > 512) throw new IOException("Terrain NBT nesting is too deep");
        switch (type) {
            case 1: skip(input, 1); break;
            case 2: skip(input, 2); break;
            case 3: case 5: skip(input, 4); break;
            case 4: case 6: skip(input, 8); break;
            case 7: skipArray(input, 1); break;
            case 8: skipString(input); break;
            case 9:
                int element = input.readUnsignedByte();
                int count = input.readInt();
                if (element > 12 || count < 0 || count > input.available() || count > MAX_BYTES / 4
                        || (element == 0 && count != 0)) throw new IOException("Invalid terrain NBT list");
                for (int i = 0; i < count; i++) checkPayload(input, element, depth + 1);
                break;
            case 10:
                int child;
                while ((child = input.readUnsignedByte()) != 0) {
                    skipString(input);
                    checkPayload(input, child, depth + 1);
                }
                break;
            case 11: skipArray(input, 4); break;
            case 12: skipArray(input, 8); break;
            default: throw new IOException("Unknown terrain NBT type");
        }
    }

    private static void skipArray(DataInputStream input, int width) throws IOException {
        int count = input.readInt();
        if (count < 0 || count > input.available() / width) throw new IOException("Invalid terrain NBT array length");
        skip(input, count * width);
    }

    private static void skipString(DataInputStream input) throws IOException {
        skip(input, input.readUnsignedShort());
    }

    private static void skip(DataInputStream input, int bytes) throws IOException {
        if (bytes > input.available() || input.skipBytes(bytes) != bytes) throw new IOException("Truncated terrain NBT");
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
