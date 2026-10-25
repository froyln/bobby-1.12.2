package dev.froyln.bobby112.cache;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CacheRetentionTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void prunesOldestRegionFilesAndStopsWritesAtBudget() throws Exception {
        File root = temporaryFolder.newFolder("cache");
        File regionDirectory = new File(new File(new File(root, "profile"), "0"), "region");
        assertTrue(regionDirectory.mkdirs());
        File oldRegion = new File(regionDirectory, "r.0.0.mca");
        File newRegion = new File(regionDirectory, "r.1.0.mca");
        File unrelated = new File(regionDirectory, "readme.txt");
        Files.write(oldRegion.toPath(), new byte[5 * 1024 * 1024]);
        Files.write(newRegion.toPath(), new byte[5 * 1024 * 1024]);
        Files.write(unrelated.toPath(), new byte[32]);
        Files.setLastModifiedTime(oldRegion.toPath(), FileTime.fromMillis(1000L));
        Files.setLastModifiedTime(newRegion.toPath(), FileTime.fromMillis(2000L));

        CacheRetention retention = new CacheRetention(root, 8L * 1024 * 1024);

        assertFalse(oldRegion.exists());
        assertTrue(newRegion.exists());
        assertTrue(unrelated.exists());
        assertTrue(retention.canSave());
        retention.recordSizeChange(1024L * 1024);
        assertFalse(retention.canSave());
    }

    @Test
    public void refusesToPruneThroughCacheRootSymlink() throws Exception {
        File outside = temporaryFolder.newFolder("outside");
        File regionDirectory = new File(outside, "region");
        assertTrue(regionDirectory.mkdir());
        File protectedFile = new File(regionDirectory, "r.0.0.mca");
        Files.write(protectedFile.toPath(), new byte[32]);
        File link = new File(temporaryFolder.getRoot(), "bobby112");
        Files.createSymbolicLink(link.toPath(), outside.toPath());

        CacheRetention retention = new CacheRetention(new File(link, "v1"), 1L);

        assertFalse(retention.isValid());
        assertTrue(protectedFile.exists());
    }

    @Test
    public void refusesCacheDirectoryThatEscapesThroughSymlink() throws Exception {
        File root = temporaryFolder.newFolder("cache");
        File outside = temporaryFolder.newFolder("outside");
        File profile = new File(root, "profile");
        Files.createSymbolicLink(profile.toPath(), outside.toPath());
        CacheRetention retention = new CacheRetention(root, 1024L * 1024);

        assertFalse(retention.isSafeDirectory(new File(profile, "0")));
    }
}
