package dev.froyln.bobby112.cache;

import net.minecraft.world.World;

public final class CachedTerrainTint {
    private static volatile boolean enabled;

    private CachedTerrainTint() {}

    public static boolean configure(boolean value) {
        boolean changed = enabled != value;
        enabled = value;
        return changed;
    }

    public static boolean applies(World world, int x, int z) {
        return enabled && ChunkCache.isEnabled() && ChunkCache.contains(world, x, z);
    }

    private static int clamp(int level) {
        return Math.max(0, Math.min(15, level));
    }

    public static int sky(int level, float gamma) {
        int reduction = 3 + (int) (7 * Math.max(0, Math.min(1, gamma)));
        return clamp(level - reduction);
    }

    public static int block(int level) {
        return clamp(level + 5);
    }

    public static int shade(int light, float gamma) {
        int sky = sky((light >>> 20) & 15, gamma);
        int block = block((light >>> 4) & 15);
        return (light & ~0x00F000F0) | (sky << 20) | (block << 4);
    }
}
