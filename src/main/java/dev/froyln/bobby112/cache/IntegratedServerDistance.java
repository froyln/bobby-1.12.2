package dev.froyln.bobby112.cache;

public final class IntegratedServerDistance {
    private static volatile int configured;

    private IntegratedServerDistance() {}

    public static int normalize(int distance) {
        return distance <= 0 ? 0 : Math.max(3, Math.min(32, distance));
    }

    public static void configure(int distance) {
        configured = normalize(distance);
    }

    public static int get(int clientDistance) {
        return effective(clientDistance, configured, ChunkCache.isEnabled());
    }

    static int effective(int clientDistance, int configuredDistance, boolean enabled) {
        int distance = normalize(configuredDistance);
        return enabled && distance != 0 ? distance : clientDistance;
    }
}
