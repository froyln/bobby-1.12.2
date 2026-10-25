package dev.froyln.bobby112.cache;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.util.datafix.FixTypes;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

final class FallbackWorld {
    private final File saves;
    private final int dimension;
    private DataFixer fixer;

    FallbackWorld(File saves, int dimension) {
        this.saves = saves;
        this.dimension = dimension;
    }

    NBTTagCompound read(int x, int z) throws IOException {
        Path root = saves.toPath().resolve("bobby-fallback");
        Path folder = dimension == 0 ? root : root.resolve("DIM" + dimension);
        Path region = folder.resolve("region");
        Path file = region.resolve("r." + (x >> 5) + "." + (z >> 5) + ".mca");
        for (Path path = file; path != null; path = path.getParent()) {
            if (Files.isSymbolicLink(path)) return null;
            if (path.equals(saves.toPath())) break;
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return null;
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            if (input.length() < 8192) return null;
            input.seek(4L * ((x & 31) + (z & 31) * 32));
            int entry = input.readInt();
            long offset = (entry >>> 8) * 4096L;
            int sectors = entry & 255;
            if (offset < 8192 || sectors == 0 || offset + sectors * 4096L > input.length()) return null;
            input.seek(offset);
            int length = input.readInt();
            int compression = input.readUnsignedByte();
            if (length <= 1 || length > sectors * 4096 - 4 || (compression != 1 && compression != 2)) return null;
            byte[] bytes = new byte[length - 1];
            input.readFully(bytes);
            InputStream compressed = new ByteArrayInputStream(bytes);
            try (DataInputStream decoded = new DataInputStream(compression == 1
                    ? new GZIPInputStream(compressed) : new InflaterInputStream(compressed))) {
                NBTTagCompound rootTag = CompressedStreamTools.read(decoded, new NBTSizeTracker(16L * 1024 * 1024));
                return sanitize(rootTag, x, z) ? rootTag : null;
            }
        }
    }

    Chunk load(World world, int x, int z, CachedChunkLoader decoder) throws IOException {
        NBTTagCompound root = read(x, z);
        if (root == null) return null;
        if (fixer == null) fixer = DataFixesManager.createFixer();
        root = fixer.process(FixTypes.CHUNK, root);
        if (!sanitize(root, x, z)) return null;
        if (world.provider.hasSkyLight()) {
            NBTTagList sections = root.getCompoundTag("Level").getTagList("Sections", 10);
            for (int i = 0; i < sections.tagCount(); i++) {
                if (!array(sections.getCompoundTagAt(i), "SkyLight", 2048)) return null;
            }
        }
        return decoder.checkedReadChunkFromNBT(world, x, z, root);
    }

    static boolean sanitize(NBTTagCompound root, int x, int z) {
        if (root.getInteger("DataVersion") > 1343 || !root.hasKey("Level", 10)) return false;
        NBTTagCompound level = root.getCompoundTag("Level");
        if (!level.hasKey("xPos", 3) || !level.hasKey("zPos", 3)
                || level.getInteger("xPos") != x || level.getInteger("zPos") != z
                || !CachedChunkLoader.validHeightMap(level) || !level.hasKey("Sections", 9)
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
}
