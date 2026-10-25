package dev.froyln.bobby112.cache;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import io.netty.buffer.Unpooled;
import dev.froyln.bobby112.mixin.ChunkProviderClientAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.network.PacketBuffer;
import net.minecraft.world.World;
import net.minecraft.world.chunk.NibbleArray;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

public final class ChunkCache {
    private static final int MAX_CACHE_DISTANCE = 34;
    private static final int MAX_FAILED_LOADS = 256;
    private static final long FAILED_LOAD_RETRY_NANOS = java.util.concurrent.TimeUnit.SECONDS.toNanos(2);
    private static final long MAX_DISK_BYTES = 2L * 1024 * 1024 * 1024;
    private static final Long2ObjectLinkedOpenHashMap<Chunk> CHUNKS = new Long2ObjectLinkedOpenHashMap<>();
    private static World currentWorld;
    private static ChunkStorage storage;
    private static CacheRetention retention;
    private static volatile boolean enabled = true;
    private static final Map<Long, Object> pendingLoads = new HashMap<>();
    private static final Long2ObjectLinkedOpenHashMap<Long> failedLoads = new Long2ObjectLinkedOpenHashMap<>();
    private static int scanGeneration;
    private static int lastScanX = Integer.MIN_VALUE;
    private static int lastScanZ = Integer.MIN_VALUE;
    private static int lastScanDistance = Integer.MIN_VALUE;
    private static int viewX;
    private static int viewZ;
    private static int viewDistance = -1;

    private ChunkCache() {
    }

    public static synchronized void initialize(File gameDirectory) {
        retention = new CacheRetention(new File(new File(gameDirectory, ".bobby112"), "v1"), MAX_DISK_BYTES);
        if (currentWorld != null) {
            storage = createStorage(currentWorld);
        }
    }

    public static synchronized void setEnabled(boolean value) {
        if (enabled != value) {
            enabled = value;
            CHUNKS.clear();
            pendingLoads.clear();
            failedLoads.clear();
            scanGeneration++;
            lastScanX = lastScanZ = lastScanDistance = Integer.MIN_VALUE;
            viewDistance = -1;
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void captureRemaining(WorldClient world) {
        if (world != null) {
            captureRemaining(world, ((ChunkProviderClientAccessor) world.getChunkProvider()).bobby112$loadedChunks().values());
        }
    }

    static void captureRemaining(WorldClient world, Iterable<Chunk> chunks) {
        synchronized (ChunkCache.class) {
            if (!enabled || currentWorld != world) {
                return;
            }
        }
        flushPendingSaves();
        int count = 0;
        for (Chunk chunk : chunks) {
            store(world, chunk);
            if (++count % 512 == 0) {
                flushPendingSaves();
            }
        }
        flushPendingSaves();
    }

    public static void flushPendingSaves() {
        if (!ChunkStorage.flush()) {
            System.err.println("[Bobby 1.12.2] Some terrain snapshots could not be saved after retries.");
        }
    }

    public static synchronized void begin(World world) {
        if (currentWorld != world) {
            CHUNKS.clear();
            pendingLoads.clear();
            failedLoads.clear();
            scanGeneration++;
            lastScanX = lastScanZ = Integer.MIN_VALUE;
            lastScanDistance = Integer.MIN_VALUE;
            viewDistance = -1;
            currentWorld = world;
            storage = createStorage(world);
        }
    }

    private static ChunkStorage createStorage(World world) {
        File directory = world == null ? null : cacheDirectory(world);
        ChunkStorage result = retention == null || !retention.isValid() || directory == null
                || !retention.isSafeDirectory(directory) ? null : new ChunkStorage(directory, retention);
        if (world != null && result == null) {
            System.err.println("[Bobby 1.12.2] Cache identity unavailable or path unsafe; disk caching is disabled for this world.");
        }
        return result;
    }

    public static void store(WorldClient world, Chunk source) {
        synchronized (ChunkCache.class) {
            if (!enabled || currentWorld != world) {
                return;
            }
        }
        long pos = ChunkPos.asLong(source.x, source.z);
        Chunk copy = copySnapshot(world, source);
        ChunkStorage currentStorage;
        synchronized (ChunkCache.class) {
            pendingLoads.remove(pos);
            failedLoads.remove(pos);
            if (withinView(pos)) {
                CHUNKS.putAndMoveToFirst(pos, copy);
            }
            currentStorage = storage;
        }
        if (currentStorage != null) {
            currentStorage.save(world, copy);
        }
    }

    static Chunk copySnapshot(World world, Chunk source) {
        Chunk copy = new Chunk(world, source.x, source.z);
        copy.setStorageArrays(copyStorageArrays(source.getBlockStorageArray()));
        copy.setBiomeArray(source.getBiomeArray().clone());
        copy.setHeightMap(source.getHeightMap().clone());
        copy.setTerrainPopulated(source.isTerrainPopulated());
        copy.setLightPopulated(source.isLightPopulated());
        copy.markLoaded(false);
        return copy;
    }

    static ExtendedBlockStorage[] copyStorageArrays(ExtendedBlockStorage[] sections) {
        ExtendedBlockStorage[] copies = sections.clone();
        for (int i = 0; i < sections.length; i++) {
            ExtendedBlockStorage source = sections[i];
            if (source == null || source == Chunk.NULL_BLOCK_STORAGE) {
                continue;
            }
            ExtendedBlockStorage copy = new ExtendedBlockStorage(source.getYLocation(), source.getSkyLight() != null);
            PacketBuffer buffer = new PacketBuffer(Unpooled.buffer());
            try {
                source.getData().write(buffer);
                copy.getData().read(buffer);
            } finally {
                buffer.release();
            }
            copy.recalculateRefCounts();
            copy.setBlockLight(new NibbleArray(source.getBlockLight().getData().clone()));
            if (source.getSkyLight() != null) {
                copy.setSkyLight(new NibbleArray(source.getSkyLight().getData().clone()));
            }
            copies[i] = copy;
        }
        return copies;
    }

    public static Chunk get(World world, int x, int z) {
        long pos = ChunkPos.asLong(x, z);
        Chunk chunk;
        ChunkStorage currentStorage;
        Object token;
        synchronized (ChunkCache.class) {
            if (!enabled || currentWorld != world || !withinView(pos)) {
                return null;
            }
            chunk = CHUNKS.getAndMoveToFirst(pos);
            currentStorage = storage;
            if (chunk != null) {
                return chunk.isAtLocation(x, z) ? chunk : null;
            }
            if (!(world instanceof WorldClient) || currentStorage == null || !retryAllowed(failedLoads, pos, System.nanoTime()) || pendingLoads.containsKey(pos)) {
                return null;
            }
            token = new Object();
            pendingLoads.put(pos, token);
        }
        currentStorage.load((WorldClient) world, x, z, token);
        return null;
    }

    static synchronized Object beginLoad(WorldClient world, int x, int z) {
        long pos = ChunkPos.asLong(x, z);
        if (!enabled || currentWorld != world || !withinView(pos) || CHUNKS.containsKey(pos) || !retryAllowed(failedLoads, pos, System.nanoTime()) || pendingLoads.containsKey(pos)) {
            return null;
        }
        Object token = new Object();
        pendingLoads.put(pos, token);
        return token;
    }

    static void loaded(WorldClient world, int x, int z, Chunk chunk, Object token) {
        Minecraft.getMinecraft().addScheduledTask(() -> {
            Minecraft client = Minecraft.getMinecraft();
            long pos = ChunkPos.asLong(x, z);
            synchronized (ChunkCache.class) {
                if (client.world != world || currentWorld != world || pendingLoads.get(pos) != token || CHUNKS.containsKey(pos)) {
                    return;
                }
                pendingLoads.remove(pos);
                if (!enabled || !withinView(pos) || world.getChunkProvider().getLoadedChunk(x, z) != null) {
                    return;
                }
                failedLoads.remove(pos);
                chunk.markLoaded(false);
                CHUNKS.putAndMoveToFirst(pos, chunk);
            }
            invalidateColumn(world, x, z);
        });
    }

    static synchronized void loadFailed(WorldClient world, int x, int z, Object token) {
        long pos = ChunkPos.asLong(x, z);
        if (currentWorld == world && pendingLoads.get(pos) == token) {
            pendingLoads.remove(pos);
            if (!CHUNKS.containsKey(pos)) {
                rememberFailedLoad(failedLoads, pos, System.nanoTime());
            }
        }
    }

    static void rememberFailedLoad(Long2ObjectLinkedOpenHashMap<Long> failures, long pos, long now) {
        failures.putAndMoveToFirst(pos, now);
        if (failures.size() > MAX_FAILED_LOADS) {
            failures.removeLast();
        }
    }

    static boolean retryAllowed(Long2ObjectLinkedOpenHashMap<Long> failures, long pos, long now) {
        Long failedAt = failures.get(pos);
        if (failedAt == null) {
            return true;
        }
        if (now - failedAt < FAILED_LOAD_RETRY_NANOS) {
            return false;
        }
        failures.remove(pos);
        return true;
    }

    static synchronized void loadDeferred(WorldClient world, int x, int z, Object token) {
        long pos = ChunkPos.asLong(x, z);
        if (currentWorld == world && pendingLoads.get(pos) == token) {
            pendingLoads.remove(pos);
        }
    }

    public static void scanAround(WorldClient world, int centerX, int centerZ, int distance) {
        distance = Math.max(0, Math.min(MAX_CACHE_DISTANCE, distance));
        ChunkStorage currentStorage;
        int generation;
        synchronized (ChunkCache.class) {
            if (!enabled || currentWorld != world || (centerX == lastScanX && centerZ == lastScanZ && distance == lastScanDistance)) {
                return;
            }
            currentStorage = storage;
            generation = ++scanGeneration;
        }
        LongSet live = new LongOpenHashSet();
        for (int x = centerX - distance; x <= centerX + distance; x++) {
            for (int z = centerZ - distance; z <= centerZ + distance; z++) {
                if (world.getChunkProvider().getLoadedChunk(x, z) != null) {
                    live.add(ChunkPos.asLong(x, z));
                }
            }
        }
        for (long pos : updateWindow(centerX, centerZ, distance, live)) {
            invalidateColumn(world, chunkX(pos), chunkZ(pos));
        }
        if (currentStorage == null || currentStorage.scan(world, centerX, centerZ, distance, generation, live)) {
            synchronized (ChunkCache.class) {
                if (currentWorld == world && scanGeneration == generation) {
                    lastScanX = centerX;
                    lastScanZ = centerZ;
                    lastScanDistance = distance;
                }
            }
        }
    }

    static synchronized long[] updateWindow(int centerX, int centerZ, int distance, LongSet live) {
        viewX = centerX;
        viewZ = centerZ;
        viewDistance = Math.max(0, Math.min(MAX_CACHE_DISTANCE, distance));
        LongArrayList removed = new LongArrayList();
        LongIterator it = CHUNKS.keySet().iterator();
        while (it.hasNext()) {
            long pos = it.nextLong();
            if (!withinView(pos) || live.contains(pos)) {
                removed.add(pos);
                it.remove();
            }
        }
        pendingLoads.keySet().removeIf(pos -> !withinView(pos) || live.contains(pos));
        return removed.toLongArray();
    }

    private static boolean withinView(long pos) {
        return viewDistance >= 0 && withinDistance(pos, viewX, viewZ, viewDistance);
    }

    public static synchronized void liveLoaded(World world, int x, int z) {
        if (currentWorld == world) {
            long pos = ChunkPos.asLong(x, z);
            CHUNKS.remove(pos);
            pendingLoads.remove(pos);
            failedLoads.remove(pos);
        }
    }

    public static void invalidateColumn(WorldClient world, int x, int z) {
        Minecraft client = Minecraft.getMinecraft();
        if (client.world == world && client.renderGlobal != null) {
            int minX = x << 4;
            int minZ = z << 4;
            client.renderGlobal.markBlockRangeForRenderUpdate(minX, 0, minZ, minX + 15, 255, minZ + 15);
        }
    }

    static synchronized boolean scanCurrent(WorldClient world, int generation) {
        return currentWorld == world && scanGeneration == generation;
    }

    static synchronized boolean contains(World world, int x, int z) {
        return currentWorld == world && CHUNKS.containsKey(ChunkPos.asLong(x, z));
    }

    static synchronized int size(World world) {
        return currentWorld == world ? CHUNKS.size() : 0;
    }

    private static File cacheDirectory(World world) {
        // shortcut: remote identity is address plus server-list name; add a selectable profile for proxy multiworld servers.
        Minecraft client = Minecraft.getMinecraft();
        String identity;
        if (client.isSingleplayer()) {
            if (client.getIntegratedServer() == null) {
                return null;
            }
            identity = client.getIntegratedServer().getFolderName();
        } else {
            ServerData server = client.getCurrentServerData();
            if (server == null || server.serverIP == null || server.serverIP.trim().isEmpty()) {
                return null;
            }
            identity = server.serverIP.toLowerCase(java.util.Locale.ROOT) + "|" + server.serverName;
        }
        if (identity == null || identity.trim().isEmpty()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte value : digest) {
                key.append(String.format("%02x", value & 0xff));
            }
            return new File(new File(new File(new File(client.gameDir, ".bobby112"), "v1"), key.toString()),
                    Integer.toString(world.provider.getDimensionType().getId()));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    public static int chunkX(long pos) {
        return (int) pos;
    }

    public static int chunkZ(long pos) {
        return (int) (pos >> 32);
    }

    public static boolean withinDistance(long pos, int centerX, int centerZ, int distance) {
        return Math.max(Math.abs((long) chunkX(pos) - centerX), Math.abs((long) chunkZ(pos) - centerZ)) <= distance;
    }

    public static synchronized long[] positions(World world) {
        return currentWorld == world ? CHUNKS.keySet().toLongArray() : new long[0];
    }
}
