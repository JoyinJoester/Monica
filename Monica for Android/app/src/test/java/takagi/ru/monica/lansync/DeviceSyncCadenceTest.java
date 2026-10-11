package takagi.ru.monica.lansync;

import java.util.ArrayList;
import org.junit.Test;
import static org.junit.Assert.*;

public class DeviceSyncCadenceTest {
    private DeviceSyncPreferences settings(String id) {
        return new DeviceSyncPreferences(id, "synthetic-unused-key", true, 0, 0, "", new ArrayList<>(), 0);
    }

    @Test public void realTimeThrottlesAndUnchangedDataDoesNotResend() {
        assertFalse(DeviceSyncCadence.INSTANCE.isDue(0, 1999, true, false));
        assertTrue(DeviceSyncCadence.INSTANCE.isDue(0, 2000, true, false));
        assertFalse(DeviceSyncCadence.INSTANCE.isDue(0, 999999, false, false));
    }

    @Test public void intervalsWaitAndReconnectOverridesTheInterval() {
        assertFalse(DeviceSyncCadence.INSTANCE.isDue(300, 299999, true, false));
        assertTrue(DeviceSyncCadence.INSTANCE.isDue(300, 300000, true, false));
        assertTrue(DeviceSyncCadence.INSTANCE.isDue(1800, 0, false, true));
    }

    @Test public void frequencyVersionsConvergeEvenWhenBothDevicesChangeAtOnce() {
        DeviceSyncPreferences a = settings("a");
        DeviceSyncPreferences b = settings("b");
        a.setInterval(300);
        b.setInterval(900);
        assertTrue(a.acceptInterval(b.getIntervalSeconds(), b.getSettingsVersion(), b.getSettingsWriter()));
        assertFalse(b.acceptInterval(300, 1, "a"));
        assertEquals(900, a.getIntervalSeconds());
        assertFalse(a.acceptInterval(-1, 2, "b"));
        assertFalse(a.acceptInterval(86401, 2, "b"));
    }
}
