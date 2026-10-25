package dev.froyln.bobby112.cache;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class CacheRenderDistanceTest {
    @Test public void extendsTerrainWithoutReducingLiveDistance() {
        assertEquals(16, CacheRenderDistance.effective(2, 16, true));
        assertEquals(24, CacheRenderDistance.effective(24, 16, true));
        assertEquals(2, CacheRenderDistance.effective(2, 16, false));
    }
    @Test public void oldConfigurationKeepsDefaultCachedDistance() {
        dev.froyln.bobby112.LiteModBobby112 config = new com.google.gson.Gson().fromJson(
                "{\"enabled\":true}", dev.froyln.bobby112.LiteModBobby112.class);
        assertEquals(16, config.cacheRenderDistance);
        org.junit.Assert.assertFalse(config.fallbackEnabled);
    }
    @Test public void boundsConfiguredDistance() {
        assertEquals(32, CacheRenderDistance.effective(2, Integer.MAX_VALUE, true));
        assertEquals(2, CacheRenderDistance.effective(2, Integer.MIN_VALUE, true));
    }
}
