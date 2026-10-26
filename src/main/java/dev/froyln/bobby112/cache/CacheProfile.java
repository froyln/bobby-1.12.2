package dev.froyln.bobby112.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

public final class CacheProfile {
    private static final Pattern NAME = Pattern.compile("[\\p{L}\\p{N} _.-]{0,64}");
    private String configured = "";
    private String active;

    public static boolean isValid(String value) {
        return value != null && value.length() <= 64 && NAME.matcher(value).matches();
    }

    public static String normalize(String value) {
        if (value == null) return "";
        if (!isValid(value)) throw new IllegalArgumentException("Cache profile must use at most 64 letters, numbers, spaces, dots, underscores or hyphens");
        return value.trim();
    }

    String configure(String value) {
        configured = normalize(value);
        return configured;
    }

    void connect() {
        if (active == null) active = configured;
    }

    void disconnect() {
        active = null;
    }

    String active() {
        return active == null ? configured : active;
    }

    public static String directoryKey(String identity, String profile) {
        if (identity == null || identity.trim().isEmpty()) throw new IllegalArgumentException("Cache identity is missing");
        String name = normalize(profile);
        return name.isEmpty() ? hash(identity) : hash("bobby112-profile-v1\0" + hash(identity) + '\0' + name);
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder key = new StringBuilder();
            for (byte part : digest) key.append(String.format("%02x", part & 255));
            return key.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }
}
