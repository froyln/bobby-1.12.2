package dev.froyln.bobby112.cache;

import com.google.gson.GsonBuilder;
import org.junit.Test;
import static org.junit.Assert.*;

public class CachedTerrainTintTest {
    @Test public void matchesCachedSkyAndBlockDistinction() {
        assertEquals((12 << 20) | (15 << 4), CachedTerrainTint.shade((15 << 20) | (15 << 4), 0));
        assertEquals((5 << 20) | (5 << 4), CachedTerrainTint.shade(15 << 20, 1));
        assertEquals(5 << 4, CachedTerrainTint.shade(0, 0));
        assertEquals(15, CachedTerrainTint.block(15));
        assertEquals(0, CachedTerrainTint.sky(3, 1));
    }

    @Test public void preservesOtherPackedBitsAndClampsGamma() {
        int other = 0xAB0BCD0B;
        assertEquals((other & ~0x00F000F0) | (5 << 4), CachedTerrainTint.shade(other, 0));
        assertEquals(CachedTerrainTint.sky(15, 0), CachedTerrainTint.sky(15, -5));
        assertEquals(CachedTerrainTint.sky(15, 1), CachedTerrainTint.sky(15, 50));
    }

    @Test public void oldConfigurationLeavesTintDisabled() {
        dev.froyln.bobby112.LiteModBobby112 mod = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(
                "{\"enabled\":true}", dev.froyln.bobby112.LiteModBobby112.class);
        assertFalse(mod.tintCachedTerrain);
    }

    @Test public void reportsOnlyActualSettingChanges() {
        CachedTerrainTint.configure(false);
        try {
            assertFalse(CachedTerrainTint.configure(false));
            assertTrue(CachedTerrainTint.configure(true));
            assertFalse(CachedTerrainTint.configure(true));
            assertTrue(CachedTerrainTint.configure(false));
        } finally { CachedTerrainTint.configure(false); }
    }
}
