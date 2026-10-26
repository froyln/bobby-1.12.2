package dev.froyln.bobby112.cache;

import java.io.*;
import net.minecraft.nbt.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class TerrainNbtTest {
    @Test public void acceptsEverySupportedPayloadType() throws Exception {
        NBTTagCompound root = new NBTTagCompound();
        root.setByte("byte", (byte) 1); root.setShort("short", (short) 2);
        root.setInteger("int", 3); root.setLong("long", 4);
        root.setFloat("float", 5); root.setDouble("double", 6);
        root.setByteArray("bytes", new byte[]{1, 2}); root.setString("string", "abcdefghijkl");
        NBTTagList list = new NBTTagList(); list.appendTag(new NBTTagInt(7)); root.setTag("list", list);
        root.setTag("empty", new NBTTagList()); root.setTag("compound", new NBTTagCompound());
        root.setIntArray("ints", new int[]{8, 9}); root.setTag("longs", new NBTTagLongArray(new long[]{10, 11}));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) { CompressedStreamTools.write(root, out); }
        assertEquals(root, TerrainNbt.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    @Test public void rejectsArrayOverflowNegativeAndTruncatedLengths() throws Exception {
        for (int type : new int[]{7, 11, 12}) for (int count : new int[]{Integer.MAX_VALUE, 1 << 29, -1, 4}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF(""); out.writeByte(type); out.writeUTF("bad");
                out.writeInt(count); out.writeByte(0);
            }
            reject(bytes.toByteArray());
        }
    }

    @Test public void rejectsInvalidListsAndExcessiveDepth() throws Exception {
        for (int type : new int[]{0, 13}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeByte(10); out.writeUTF(""); out.writeByte(9); out.writeUTF("bad");
                out.writeByte(type); out.writeInt(1); out.writeByte(0);
            }
            reject(bytes.toByteArray());
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(10); out.writeUTF("");
            for (int i = 0; i < 513; i++) { out.writeByte(10); out.writeUTF("nested"); }
            for (int i = 0; i < 514; i++) out.writeByte(0);
        }
        reject(bytes.toByteArray());
    }

    private static void reject(byte[] bytes) throws Exception {
        try {
            TerrainNbt.read(new DataInputStream(new ByteArrayInputStream(bytes)));
            fail("malformed NBT was admitted");
        } catch (IOException expected) { assertNotNull(expected.getMessage()); }
    }
}
