package takagi.ru.monica.lansync

import android.app.Application
import android.os.Looper
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import takagi.ru.monica.security.SessionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
@LooperMode(LooperMode.Mode.PAUSED)
class DeviceSyncServiceTest {
    private fun field(name: String, value: Any) {
        DeviceSyncService::class.java.getDeclaredField(name).apply { isAccessible = true }.set(null, value)
    }
    @After fun reset() {
        listOf("running", "starting", "wanted").forEach { field(it, false) }
        field("visible", 0)
        SessionManager.markLocked()
    }
    @Test fun immediatePairingFailureDoesNotCancelPendingForegroundPromotion() {
        val app = RuntimeEnvironment.getApplication()
        SessionManager.markUnlocked()
        field("visible", 1)
        DeviceSyncService.update(app, DeviceSyncState(pairing = true, phase = "preparing"))
        DeviceSyncService.update(app, DeviceSyncState(error = "native_unavailable", phase = "failed"))
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(shadowOf(app).nextStartedService)
        assertNull("Android must receive startForeground before stopping the queued start", shadowOf(app).nextStoppedService)
        val wanted = DeviceSyncService::class.java.getDeclaredField("wanted").apply { isAccessible = true }.getBoolean(null)
        assertFalse("onStartCommand must stop after promotion", wanted)
    }
    @Test fun cancellationStopsAnAlreadyPromotedService() {
        val app = RuntimeEnvironment.getApplication()
        SessionManager.markUnlocked()
        field("running", true); field("starting", false); field("wanted", true)
        DeviceSyncService.update(app, DeviceSyncState(phase = "idle"))
        shadowOf(Looper.getMainLooper()).idle()
        assertNotNull(shadowOf(app).nextStoppedService)
    }
}
