package dev.froyln.bobby112.cache;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.zip.DeflaterOutputStream;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FallbackWorldTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @Test public void readsNegativeCoordinatesWithoutChangingSourceAndStripsSimulation() throws Exception {
        File saves = temporary.newFolder();
        NBTTagCompound root = terrain(-33, -1);
        NBTTagCompound level = root.getCompoundTag("Level");
        level.setTag("Entities", entities());
        level.setTag("TileEntities", entities());
        level.setTag("TileTicks", entities());
        File file = region(saves, 0, -33, -1, root);
        byte[] before = Files.readAllBytes(file.toPath());
        long modified = file.lastModified();
        file.setReadOnly();
        try {
            NBTTagCompound read = new FallbackWorld(saves, 0).read(-33, -1);
            assertNotNull(read);
            assertFalse(read.getCompoundTag("Level").hasKey("Entities"));
            assertFalse(read.getCompoundTag("Level").hasKey("TileEntities"));
            assertFalse(read.getCompoundTag("Level").hasKey("TileTicks"));
            assertArrayEquals(before, Files.readAllBytes(file.toPath()));
            assertEquals(modified, file.lastModified());
        } finally { file.setWritable(true); }
    }

    @Test public void dimensionsAreSeparateAndMissingChunksCreateNothing() throws Exception {
        File saves = temporary.newFolder();
        FallbackWorld overworld = new FallbackWorld(saves, 0);
        assertNull(overworld.read(0, 0));
        assertEquals(0, saves.list().length);
        region(saves, -1, 0, 0, terrain(0, 0));
        assertNull(overworld.read(0, 0));
        assertNotNull(new FallbackWorld(saves, -1).read(0, 0));
        assertNull(new FallbackWorld(saves, 1).read(0, 0));
    }

    @Test public void rejectsInvalidOffsetsCompressionModernDataAndWrongCoordinates() throws Exception {
        File saves = temporary.newFolder();
        NBTTagCompound root = terrain(0, 0);
        root.setInteger("DataVersion", 3465);
        File file = region(saves, 0, 0, 0, root);
        FallbackWorld reader = new FallbackWorld(saves, 0);
        assertNull(reader.read(0, 0));
        region(saves, 0, 0, 0, terrain(1, 0));
        assertNull(reader.read(0, 0));
        region(saves, 0, 0, 0, terrain(0, 0));
        try (RandomAccessFile f = new RandomAccessFile(file, "rw")) { f.seek(8196); f.writeByte(99); }
        assertNull(reader.read(0, 0));
        try (RandomAccessFile f = new RandomAccessFile(file, "rw")) { f.seek(0); f.writeInt(0x7fffffff); }
        assertNull(reader.read(0, 0));
    }

    @Test public void rejectsSymlinkedWorldAndRegion() throws Exception {
        File saves = temporary.newFolder();
        File real = temporary.newFolder();
        region(real, 0, 0, 0, terrain(0, 0));
        Files.createSymbolicLink(new File(saves, "bobby-fallback").toPath(), new File(real, "bobby-fallback").toPath());
        assertNull(new FallbackWorld(saves, 0).read(0, 0));
        Files.delete(new File(saves, "bobby-fallback").toPath());
        File file = region(saves, 0, 0, 0, terrain(0, 0));
        Files.delete(file.toPath());
        Files.createSymbolicLink(file.toPath(), new File(real, "bobby-fallback/region/r.0.0.mca").toPath());
        assertNull(new FallbackWorld(saves, 0).read(0, 0));
    }

    @Test public void rejectsMalformedSectionArraysAndDuplicateSections() {
        NBTTagCompound root = terrain(0, 0);
        NBTTagList sections = root.getCompoundTag("Level").getTagList("Sections", 10);
        sections.getCompoundTagAt(0).setByteArray("Data", new byte[1]);
        assertFalse(TerrainNbt.sanitize(root, 0, 0));
        root = terrain(0, 0);
        sections = root.getCompoundTag("Level").getTagList("Sections", 10);
        sections.appendTag(sections.getCompoundTagAt(0).copy());
        assertFalse(TerrainNbt.sanitize(root, 0, 0));
    }

    static NBTTagCompound terrain(int x, int z) {
        NBTTagCompound root = new NBTTagCompound(); root.setInteger("DataVersion", 1343);
        NBTTagCompound level = new NBTTagCompound(); level.setInteger("xPos", x); level.setInteger("zPos", z);
        level.setIntArray("HeightMap", new int[256]); level.setByteArray("Biomes", new byte[256]);
        NBTTagCompound section = new NBTTagCompound(); section.setByte("Y", (byte) 0);
        section.setByteArray("Blocks", new byte[4096]); section.setByteArray("Data", new byte[2048]);
        section.setByteArray("BlockLight", new byte[2048]); section.setByteArray("SkyLight", new byte[2048]);
        NBTTagList sections = new NBTTagList(); sections.appendTag(section); level.setTag("Sections", sections);
        root.setTag("Level", level); return root;
    }

    private static NBTTagList entities() {
        NBTTagList entities = new NBTTagList(); NBTTagCompound entity = new NBTTagCompound();
        entity.setString("id", "minecraft:pig"); entities.appendTag(entity); return entities;
    }

    static File region(File saves, int dimension, int x, int z, NBTTagCompound root) throws Exception {
        File world = new File(saves, "bobby-fallback");
        File folder = dimension == 0 ? world : new File(world, "DIM" + dimension);
        File directory = new File(folder, "region"); directory.mkdirs();
        File file = new File(directory, "r." + (x >> 5) + "." + (z >> 5) + ".mca");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new DeflaterOutputStream(bytes))) { CompressedStreamTools.write(root, out); }
        byte[] payload = bytes.toByteArray(); int sectors = (payload.length + 5 + 4095) / 4096;
        try (RandomAccessFile out = new RandomAccessFile(file, "rw")) {
            out.setLength((2 + sectors) * 4096L); out.seek(4L * ((x & 31) + (z & 31) * 32));
            out.writeInt((2 << 8) | sectors); out.seek(8192); out.writeInt(payload.length + 1); out.writeByte(2); out.write(payload);
        }
        return file;
    }
}
