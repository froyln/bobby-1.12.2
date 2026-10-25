package dev.froyln.bobby112.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.init.Blocks;
import net.minecraft.init.Bootstrap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ChunkCacheTest {
    @Test
    public void decodesNegativeAndPositiveChunkCoordinates() {
        long packed = ChunkPos.asLong(-1234567, 7654321);
        assertTrue(ChunkCache.chunkX(packed) == -1234567);
        assertTrue(ChunkCache.chunkZ(packed) == 7654321);
    }

    @Test
    public void checksSquareRenderBoundsWithoutIntegerOverflow() {
        long edge = ChunkPos.asLong(-9, 11);
        assertTrue(ChunkCache.withinDistance(edge, -4, 6, 5));
        assertFalse(ChunkCache.withinDistance(edge, -4, 6, 4));
        assertFalse(ChunkCache.withinDistance(ChunkPos.asLong(Integer.MIN_VALUE, Integer.MAX_VALUE), 0, 0, 32));
    }

    @Test
    public void togglesCaching() {
        ChunkCache.setEnabled(false);
        assertFalse(ChunkCache.isEnabled());
        ChunkCache.setEnabled(true);
        assertTrue(ChunkCache.isEnabled());
    }

    @Test
    public void boundsFailedLoadHistory() {
        Long2ObjectLinkedOpenHashMap<Long> failures = new Long2ObjectLinkedOpenHashMap<>();
        for (int pos = 0; pos <= 256; pos++) {
            ChunkCache.rememberFailedLoad(failures, pos, 100L);
        }
        assertEquals(256, failures.size());
        assertFalse(failures.containsKey(0L));
        assertTrue(failures.containsKey(256L));
    }

    @Test
    public void retriesFailuresAfterBackoff() {
        Long2ObjectLinkedOpenHashMap<Long> failures = new Long2ObjectLinkedOpenHashMap<>();
        ChunkCache.rememberFailedLoad(failures, 5L, 100L);
        assertFalse(ChunkCache.retryAllowed(failures, 5L, 101L));
        assertTrue(ChunkCache.retryAllowed(failures, 5L, 2000000100L));
        assertFalse(failures.containsKey(5L));
    }

    @Test
    public void queueRejectionReleasesAdmissionWithoutPoisoningTheChunk() {
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
        ChunkCache.begin(null);
        ChunkCache.updateWindow(0, 0, 2, new LongOpenHashSet());
        Object first = ChunkCache.beginLoad(null, 1, 1);
        assertTrue(first != null);
        assertTrue(ChunkCache.beginLoad(null, 1, 1) == null);
        ChunkCache.loadDeferred(null, 1, 1, first);
        Object retry = ChunkCache.beginLoad(null, 1, 1);
        assertTrue(retry != null);
        ChunkCache.loadDeferred(null, 1, 1, first);
        assertTrue(ChunkCache.beginLoad(null, 1, 1) == null);
        ChunkCache.loadDeferred(null, 1, 1, retry);
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
    }

    @Test
    public void copiesChunkSectionsAndLightArraysIndependently() {
        Bootstrap.register();
        ExtendedBlockStorage source = new ExtendedBlockStorage(0, true);
        source.set(2, 3, 4, Blocks.STONE.getDefaultState());
        source.setBlockLight(2, 3, 4, 9);
        source.setSkyLight(2, 3, 4, 12);
        ExtendedBlockStorage[] copy = ChunkCache.copyStorageArrays(
                new ExtendedBlockStorage[]{source, Chunk.NULL_BLOCK_STORAGE});

        source.set(2, 3, 4, Blocks.AIR.getDefaultState());
        source.setBlockLight(2, 3, 4, 1);
        source.setSkyLight(2, 3, 4, 2);

        assertTrue(copy[0].get(2, 3, 4).getBlock() == Blocks.STONE);
        assertFalse(copy[0].isEmpty());
        assertEquals(9, copy[0].getBlockLight(2, 3, 4));
        assertEquals(12, copy[0].getSkyLight(2, 3, 4));
        assertTrue(copy[1] == Chunk.NULL_BLOCK_STORAGE);
    }

    @Test
    public void retainsTheVisibleWorkingSetWithoutEvictingEarlierColumns() {
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
        ChunkCache.begin(null);
        ChunkCache.updateWindow(0, 0, 14, new LongOpenHashSet());
        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                ChunkCache.store(null, new Chunk(null, x, z));
            }
        }
        assertEquals(841, ChunkCache.size(null));
        assertTrue(ChunkCache.get(null, -14, -14) != null);
        assertTrue(ChunkCache.get(null, 14, 14) != null);
        ChunkCache.store(null, new Chunk(null, 15, 0));
        assertEquals(841, ChunkCache.size(null));

        LongOpenHashSet live = new LongOpenHashSet();
        live.add(ChunkPos.asLong(0, 0));
        ChunkCache.updateWindow(1, 0, 14, live);
        assertEquals(811, ChunkCache.size(null));
        assertTrue(ChunkCache.get(null, -14, 0) == null);
        assertTrue(ChunkCache.get(null, -13, 0) != null);
        assertTrue(ChunkCache.get(null, 0, 0) == null);
        ChunkCache.liveLoaded(null, 1, 1);
        assertTrue(ChunkCache.get(null, 1, 1) == null);
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
    }
    @Test
    public void capturesRemainingLiveSnapshotsIncludingLatestChanges() {
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
        ChunkCache.begin(null);
        ChunkCache.updateWindow(0, 0, 34, new LongOpenHashSet());
        java.util.List<Chunk> live = new java.util.ArrayList<>();
        for (int i = 0; i < 1100; i++) {
            live.add(new Chunk(null, i % 34, i / 34));
        }
        live.get(0).getBiomeArray()[0] = 42;
        ChunkCache.captureRemaining(null, live);
        assertEquals(1100, ChunkCache.size(null));
        live.get(0).getBiomeArray()[0] = 53;
        assertEquals(42, ChunkCache.get(null, 0, 0).getBiomeArray()[0]);
        ChunkCache.setEnabled(false);
        ChunkCache.setEnabled(true);
    }

    @Test
    public void disabledCacheDoesNotCaptureDepartingWorld() {
        ChunkCache.setEnabled(false);
        ChunkCache.captureRemaining(null, () -> { throw new AssertionError("must not enumerate live chunks"); });
        assertEquals(0, ChunkCache.size(null));
        ChunkCache.setEnabled(true);
    }
}
