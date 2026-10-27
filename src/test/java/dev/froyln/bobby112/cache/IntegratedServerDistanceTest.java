package dev.froyln.bobby112.cache;

import com.google.gson.GsonBuilder;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class IntegratedServerDistanceTest {
    @Test public void automaticAndDisabledPreserveClientDistance() {
        assertEquals(12, IntegratedServerDistance.effective(12, 0, true));
        assertEquals(12, IntegratedServerDistance.effective(12, -1, true));
        assertEquals(12, IntegratedServerDistance.effective(12, 3, false));
    }

    @Test public void overrideUsesVanillaServerBounds() {
        assertEquals(3, IntegratedServerDistance.effective(12, 1, true));
        assertEquals(5, IntegratedServerDistance.effective(12, 5, true));
        assertEquals(32, IntegratedServerDistance.effective(12, Integer.MAX_VALUE, true));
        assertEquals(0, IntegratedServerDistance.normalize(Integer.MIN_VALUE));
    }

    @Test public void oldConfigurationKeepsAutomaticServerDistance() {
        dev.froyln.bobby112.LiteModBobby112 config = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().create().fromJson(
                "{\"enabled\":true}", dev.froyln.bobby112.LiteModBobby112.class);
        assertEquals(0, config.integratedServerDistance);
    }
}
