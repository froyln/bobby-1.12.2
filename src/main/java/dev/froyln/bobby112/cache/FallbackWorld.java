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
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

final class FallbackWorld {
    private final File saves;
    private final int dimension;

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
                NBTTagCompound rootTag = TerrainNbt.read(decoded);
                return TerrainNbt.sanitize(rootTag, x, z) ? rootTag : null;
            }
        }
    }

    Chunk load(World world, int x, int z, CachedChunkLoader decoder) throws IOException {
        NBTTagCompound root = read(x, z);
        if (root == null) return null;
        return decoder.decode(world, x, z, root);
    }

}
