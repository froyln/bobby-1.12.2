package dev.froyln.bobby112.cache;

public final class CacheRenderDistance {
    private static volatile int configured = 16;

    private CacheRenderDistance() {}

    public static void configure(int distance) {
        configured = Math.max(2, Math.min(32, distance));
    }

    public static int get(int liveDistance) {
        return effective(liveDistance, configured, ChunkCache.isEnabled());
    }

    static int effective(int liveDistance, int cachedDistance, boolean enabled) {
        return enabled ? Math.max(liveDistance, Math.max(2, Math.min(32, cachedDistance))) : liveDistance;
    }
}
