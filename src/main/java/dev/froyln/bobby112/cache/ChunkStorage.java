package dev.froyln.bobby112.cache;

import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.datafix.DataFixesManager;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.AnvilChunkLoader;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

final class ChunkStorage {
    private static final Object REGION_IO_LOCK = new Object();
    private static final ThreadPoolExecutor IO = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(128), runnable -> {
                Thread thread = new Thread(runnable, "Bobby 1.12.2 cache I/O");
                thread.setDaemon(true);
                return thread;
            });
    private static final PendingSaveQueue<Chunk> PENDING_SAVES = new PendingSaveQueue<>(1024);
    private static final Object SAVE_LOCK = new Object();
    private static final ExecutorService SAVE_IO = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Bobby 1.12.2 cache writer");
        thread.setDaemon(true);
        return thread;
    });
    private static volatile boolean warnedSaveBacklog;
    private static boolean saveDrainScheduled;

    private final AnvilChunkLoader loader;
    private final CacheRetention retention;
    private final File directory;

    ChunkStorage(File directory, CacheRetention retention) {
        this.directory = directory;
        this.retention = retention;
        this.loader = new AnvilChunkLoader(directory, DataFixesManager.createFixer()) {
            @Override
            public boolean writeNextIO() {
                synchronized (REGION_IO_LOCK) {
                    return super.writeNextIO();
                }
            }
        };
    }

    void save(WorldClient world, Chunk chunk) {
        Runnable task = () -> {
            synchronized (REGION_IO_LOCK) {
                if (retention == null || !retention.canSave()) {
                    return;
                }
                File region = new File(new File(directory, "region"),
                        "r." + (chunk.x >> 5) + "." + (chunk.z >> 5) + ".mca");
                long before = region.length();
                try {
                    loader.saveChunk(world, chunk);
                    while (loader.writeNextIO()) {
                        // Drain the region writer on this single I/O thread.
                    }
                } catch (IOException | MinecraftException | RuntimeException e) {
                    e.printStackTrace();
                } finally {
                    retention.recordSizeChange(region.length() - before);
                }
            }
        };
        enqueueSave(saveKey(chunk.x, chunk.z), chunk, task);
    }

    static void enqueueSave(String key, Chunk chunk, Runnable task) {
        if (PENDING_SAVES.offer(key, chunk, task) && !warnedSaveBacklog) {
            warnedSaveBacklog = true;
            System.err.println("[Bobby 1.12.2] Cache save backlog is full; the oldest pending chunk save was dropped.");
        }
        synchronized (SAVE_LOCK) {
            if (!saveDrainScheduled) {
                saveDrainScheduled = true;
                SAVE_IO.execute(ChunkStorage::drainSaves);
            }
        }
    }

    private static void drainSaves() {
        while (true) {
            Runnable task = PENDING_SAVES.poll();
            if (task != null) {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
                continue;
            }
            synchronized (SAVE_LOCK) {
                if (PENDING_SAVES.size() == 0) {
                    saveDrainScheduled = false;
                    return;
                }
            }
        }
    }

    void load(WorldClient world, int x, int z, Object token) {
        try {
            IO.execute(() -> {
                boolean generated;
                synchronized (REGION_IO_LOCK) {
                    generated = PENDING_SAVES.latest(saveKey(x, z)) != null || loader.isChunkGeneratedAt(x, z);
                }
                if (generated) {
                    loadNow(world, x, z, token);
                } else {
                    ChunkCache.loadFailed(world, x, z, token);
                }
            });
        } catch (RejectedExecutionException full) {
            ChunkCache.loadDeferred(world, x, z, token);
        }
    }

    boolean scan(WorldClient world, int centerX, int centerZ, int distance, int generation, LongSet live) {
        try {
            IO.execute(() -> {
                for (int radius = 0; radius <= distance && ChunkCache.scanCurrent(world, generation); radius++) {
                    for (int x = centerX - radius; x <= centerX + radius; x++) {
                        for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                            if (Math.max(Math.abs(x - centerX), Math.abs(z - centerZ)) != radius
                                    || live.contains(ChunkPos.asLong(x, z))
                                    || ChunkCache.contains(world, x, z)) {
                                continue;
                            }
                            Object token = ChunkCache.beginLoad(world, x, z);
                            if (token != null) {
                                boolean generated;
                                synchronized (REGION_IO_LOCK) {
                                    generated = PENDING_SAVES.latest(saveKey(x, z)) != null || loader.isChunkGeneratedAt(x, z);
                                }
                                if (generated) {
                                    loadNow(world, x, z, token);
                                } else {
                                    ChunkCache.loadFailed(world, x, z, token);
                                }
                            }
                        }
                    }
                }
            });
            return true;
        } catch (RejectedExecutionException full) {
            return false;
        }
    }

    private String saveKey(int x, int z) {
        return directory.getAbsolutePath() + '|' + x + '|' + z;
    }

    private void loadNow(WorldClient world, int x, int z, Object token) {
        try {
            Chunk chunk;
            synchronized (REGION_IO_LOCK) {
                Chunk pending = PENDING_SAVES.latest(saveKey(x, z));
                chunk = pending == null ? loader.loadChunk(world, x, z) : ChunkCache.copySnapshot(world, pending);
            }
            if (chunk != null) {
                ChunkCache.loaded(world, x, z, chunk, token);
            } else {
                ChunkCache.loadFailed(world, x, z, token);
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
            ChunkCache.loadFailed(world, x, z, token);
        }
    }

}
