package takagi.ru.monica.lansync

import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class NearbyPresenceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(message: String, condition: () -> Boolean) {
        val deadline = android.os.SystemClock.elapsedRealtime() + 30_000
        while (!condition() && android.os.SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        assertTrue(message, condition())
    }

    @Test fun serviceAnnouncementsDiscoverAnOpenedPeerAndRemoveAClosedPeer() {
        val firstId = UUID.randomUUID().toString().replace("-", "")
        val secondId = UUID.randomUUID().toString().replace("-", "")
        val first = AndroidDeviceSyncPresence(context)
        val second = AndroidDeviceSyncPresence(context)
        try {
            first.start(firstId, setOf(secondId))
            Thread.sleep(1_000)
            assertTrue(first.online.value.isEmpty())
            second.start(secondId, setOf(firstId))
            await("Both foreground peers must be discovered") {
                first.online.value == setOf(secondId) && second.online.value == setOf(firstId)
            }
            assertTrue(first.available); assertTrue(second.available)
            second.stop()
            await("A closed peer must disappear without an app probe") { first.online.value.isEmpty() }
            second.start(secondId, setOf(firstId))
            await("Reopening the peer must be detected") { secondId in first.online.value }
        } finally { first.stop(); second.stop() }
        assertTrue(first.online.value.isEmpty()); assertTrue(second.online.value.isEmpty())
    }

    @Test fun stoppedActivityPausesCoordinatorAndResumingRestoresVisibility() {
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            val coordinator = DeviceSyncCoordinator.get(context)
            await("Started app visible") { coordinator.state.value.appVisible }
            scenario.moveToState(Lifecycle.State.CREATED)
            await("Background app stops participating") { !coordinator.state.value.appVisible }
            await("No background connections remain") { coordinator.state.value.activeConnections == 0 && coordinator.state.value.listeningPeers == 0 }
            scenario.moveToState(Lifecycle.State.RESUMED)
            await("Foreground app visible again") { coordinator.state.value.appVisible }
        }
    }
}
