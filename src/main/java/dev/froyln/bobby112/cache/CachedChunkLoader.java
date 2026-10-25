package dev.froyln.bobby112.cache;

import java.io.File;
import java.io.IOException;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.chunk.storage.RegionFile;
import net.minecraft.world.chunk.storage.RegionFileCache;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.datafix.DataFixer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

class CachedChunkLoader extends AnvilChunkLoader {
    private static final Field REGION_FILES = regionFilesField();
    private final File directory;
    private boolean written;
    private IOException failure;

    CachedChunkLoader(File directory, DataFixer fixer) {
        super(directory, fixer);
        this.directory = directory;
    }

    void saveSnapshot(World world, Chunk chunk) throws IOException, MinecraftException {
        written = false;
        failure = null;
        super.saveChunk(world, chunk);
        if (failure != null) {
            throw failure;
        }
        if (!written) {
            throw new IOException("Chunk serialization did not complete");
        }
    }

    @Override
    protected void addChunkToPending(ChunkPos pos, NBTTagCompound root) {
        try {
            writeVerified(pos, root);
            written = true;
        } catch (IOException e) {
            failure = e;
        }
    }

    void writeVerified(ChunkPos pos, NBTTagCompound root) throws IOException {
        synchronized (RegionFileCache.class) {
            try {
                try (DataOutputStream out = RegionFileCache.getChunkOutputStream(directory, pos.x, pos.z)) {
                    if (out == null) throw new IOException("No region output stream");
                    CompressedStreamTools.write(root, out);
                }
                try (DataInputStream in = RegionFileCache.getChunkInputStream(directory, pos.x, pos.z)) {
                    if (in == null || !root.equals(CompressedStreamTools.read(in))) {
                        throw new IOException("Region readback differs from the terrain snapshot");
                    }
                }
            } catch (IOException | RuntimeException e) {
                IOException error = e instanceof IOException ? (IOException) e : new IOException(e);
                discardRegion(pos, error);
                throw error;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void discardRegion(ChunkPos pos, IOException failure) {
        try {
            Map<File, RegionFile> files = (Map<File, RegionFile>) REGION_FILES.get(null);
            File file = new File(new File(directory, "region"), "r." + (pos.x >> 5) + "." + (pos.z >> 5) + ".mca");
            RegionFile region = files.remove(file);
            if (region != null) region.close();
        } catch (IllegalAccessException | IOException | RuntimeException e) {
            failure.addSuppressed(e);
        }
    }

    private static Field regionFilesField() {
        for (Field field : RegionFileCache.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Map.class) {
                field.setAccessible(true);
                return field;
            }
        }
        throw new IllegalStateException("Minecraft region-file registry is unavailable");
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
