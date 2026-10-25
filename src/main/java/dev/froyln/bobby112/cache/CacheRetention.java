package dev.froyln.bobby112.cache;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

final class CacheRetention {
    private static final Pattern REGION_FILE = Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.mca");
    private static final long WRITE_HEADROOM = 2L * 1024 * 1024;

    private final File root;
    private final long maxBytes;
    private final boolean valid;
    private long bytes;
    private boolean warned;

    CacheRetention(File root, long maxBytes) {
        this.root = root;
        this.maxBytes = maxBytes;
        File parent = root.getParentFile();
        this.valid = !Files.isSymbolicLink(parent.toPath()) && !Files.isSymbolicLink(root.toPath());
        this.bytes = valid ? prune(root, Math.max(0L, maxBytes - WRITE_HEADROOM - 1L)) : maxBytes;
        if (!valid) {
            System.err.println("[Bobby 1.12.2] Cache root is a symbolic link; persistent cache is disabled.");
        }
    }

    synchronized boolean isValid() {
        return valid;
    }

    boolean isSafeDirectory(File directory) {
        try {
            Path base = root.getAbsoluteFile().toPath().normalize();
            Path target = directory.getAbsoluteFile().toPath().normalize();
            if (!target.startsWith(base) || !target.toFile().getCanonicalFile().toPath()
                    .startsWith(base.toFile().getCanonicalFile().toPath())) {
                return false;
            }
            Path current = base;
            for (Path part : base.relativize(target)) {
                current = current.resolve(part);
                if (Files.isSymbolicLink(current)) {
                    return false;
                }
            }
            return !Files.isSymbolicLink(target.resolve("region"));
        } catch (IOException e) {
            return false;
        }
    }

    synchronized boolean canSave() {
        if (valid && bytes < maxBytes - WRITE_HEADROOM) {
            return true;
        }
        if (!warned) {
            warned = true;
            System.err.println("[Bobby 1.12.2] Terrain cache limit reached; new chunks will not persist until old cache data is pruned.");
        }
        return false;
    }

    synchronized void recordSizeChange(long change) {
        bytes = Math.max(0L, bytes + change);
    }

    private static long prune(File root, long maxBytes) {
        List<Region> regions = new ArrayList<>();
        Path rootPath = root.toPath();
        if (!Files.exists(rootPath)) {
            return 0L;
        }
        try {
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && "region".equals(file.getParent().getFileName().toString())
                            && REGION_FILE.matcher(file.getFileName().toString()).matches()) {
                        regions.add(new Region(file.toFile(), attributes.size(), attributes.lastModifiedTime().toMillis()));
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            System.err.println("[Bobby 1.12.2] Could not inspect cache for retention: " + e.getMessage());
            return maxBytes;
        }

        long total = 0L;
        for (Region region : regions) {
            total = total > Long.MAX_VALUE - region.size ? Long.MAX_VALUE : total + region.size;
        }
        if (total <= maxBytes) {
            return total;
        }

        regions.sort(Comparator.comparingLong(region -> region.modified));
        for (Region region : regions) {
            if (total <= maxBytes) {
                break;
            }
            try {
                if (Files.deleteIfExists(region.file.toPath())) {
                    total -= region.size;
                }
            } catch (IOException e) {
                System.err.println("[Bobby 1.12.2] Could not prune " + region.file + ": " + e.getMessage());
            }
        }
        return total;
    }

    private static final class Region {
        private final File file;
        private final long size;
        private final long modified;

        private Region(File file, long size, long modified) {
            this.file = file;
            this.size = size;
            this.modified = modified;
        }
    }
}
