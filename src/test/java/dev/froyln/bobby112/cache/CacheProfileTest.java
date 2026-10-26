package dev.froyln.bobby112.cache;

import org.junit.Test;
import static org.junit.Assert.*;

public class CacheProfileTest {
    @org.junit.Rule public final org.junit.rules.TemporaryFolder temporary = new org.junit.rules.TemporaryFolder();
    @Test public void blankProfilePreservesLegacyCacheKey() {
        assertEquals("2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                CacheProfile.directoryKey("hello", ""));
        assertEquals(CacheProfile.directoryKey("hello", ""), CacheProfile.directoryKey("hello", "   "));
    }

    @Test public void namesAreIsolatedWithinEachWorldAndNormalizeSpaces() {
        String first = CacheProfile.directoryKey("server|name", "world-a");
        assertTrue(first.matches("[a-f0-9]{64}"));
        assertEquals(first, CacheProfile.directoryKey("server|name", " world-a "));
        assertNotEquals(first, CacheProfile.directoryKey("server|name", "world-b"));
        assertNotEquals(first, CacheProfile.directoryKey("other-server|name", "world-a"));
        assertNotEquals(first, CacheProfile.directoryKey("server|name", "World-a"));
        assertNotEquals(first, CacheProfile.directoryKey("server|name", ""));
        assertEquals("Mundo_2.世界", CacheProfile.normalize(" Mundo_2.世界 "));
    }

    @Test public void invalidNamesCannotBecomePathsOrAmbiguousEncoding() {
        for (String name : new String[]{"../world", "world\\a", "a\nb", "a\0b", "a|b", "\uD800", new String(new char[65]).replace('\0', 'a')}) {
            assertFalse(name, CacheProfile.isValid(name));
            try { CacheProfile.normalize(name); fail("invalid profile accepted"); }
            catch (IllegalArgumentException expected) { }
        }
        String supplementary = new String(Character.toChars(0x10400));
        StringBuilder name = new StringBuilder();
        for (int i = 0; i < 32; i++) name.append(supplementary);
        assertTrue(CacheProfile.isValid(name.toString()));
        assertFalse(CacheProfile.isValid(name.toString() + supplementary));
        assertEquals("", CacheProfile.normalize(null));
    }

    @Test public void activeProfileIsFrozenUntilDisconnectIncludingRepeatedWorldBegins() {
        CacheProfile profile = new CacheProfile();
        profile.configure("a"); profile.connect();
        profile.configure("b"); profile.connect();
        assertEquals("a", profile.active());
        profile.disconnect(); profile.connect();
        assertEquals("b", profile.active());
        try { profile.configure("../bad"); fail("invalid profile accepted"); }
        catch (IllegalArgumentException expected) { }
        profile.disconnect(); profile.connect();
        assertEquals("b", profile.active());
    }

    @Test public void invalidJsonProfilePreservesTheWholePreviousConfiguration() throws Exception {
        dev.froyln.bobby112.LiteModBobby112 mod = new dev.froyln.bobby112.LiteModBobby112();
        mod.setCacheProfile("prior");
        java.io.File file = temporary.newFile("bobby112.json");
        java.lang.reflect.Field path = mod.getClass().getDeclaredField("configFile");
        path.setAccessible(true); path.set(mod, file);
        java.lang.reflect.Method reload = mod.getClass().getDeclaredMethod("reloadConfig"); reload.setAccessible(true);
        try {
            java.nio.file.Files.write(file.toPath(), "{\"enabled\":false,\"cacheRenderDistance\":2,\"cacheProfile\":\"bad/name\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            reload.invoke(mod);
            assertTrue(mod.enabled); assertEquals(16, mod.cacheRenderDistance); assertEquals("prior", mod.cacheProfile);
            long previous = file.lastModified();
            java.nio.file.Files.write(file.toPath(), "{\"enabled\":false,\"cacheRenderDistance\":8,\"cacheProfile\":\" valid \"}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            assertTrue(file.setLastModified(previous + 2000));
            reload.invoke(mod);
            assertFalse(mod.enabled); assertEquals(8, mod.cacheRenderDistance); assertEquals("valid", mod.cacheProfile);
        } finally { mod.setCacheProfile(""); }
    }

}
