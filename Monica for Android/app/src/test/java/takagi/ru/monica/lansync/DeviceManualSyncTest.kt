package takagi.ru.monica.lansync

import android.app.Application
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.steam.data.SteamDatabase
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class,
    shadows = [DeviceVaultProviderTest.SecureStoreShadow::class, DeviceVaultProviderTest.SecurityShadow::class])
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceManualSyncTest {
    private class Fixture(context: Context, id: String, peer: String, mode: DeviceSyncMode) {
        val db = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).allowMainThreadQueries().setQueryExecutor { it.run() }.build()
        val steam = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).allowMainThreadQueries().build()
        val settings = DeviceSyncPreferences(deviceId = id, enabled = false, mode = mode,
            peers = mutableListOf(SavedSyncPeer(peer, "Fixture", "manual-pair", "A".repeat(43), address = "127.0.0.1")))
        val store = DeviceSyncPreferenceStore(context, SecurityManager(context), "manual-${UUID.randomUUID()}").also { it.save(settings) }
        val provider = DevicePasswordSyncProvider(context, SecurityManager(context), store, db,
            File(context.cacheDir, "manual-${UUID.randomUUID()}.replica"), steam)
        val coordinator = ReflectionHelpers.callConstructor(DeviceSyncCoordinator::class.java, ClassParameter.from(Context::class.java, context)).also {
            ReflectionHelpers.setField(it, "preferences", settings)
            ReflectionHelpers.setField(it, "store\$delegate", lazyOf(store))
            ReflectionHelpers.setField(it, "dataProvider", lazyOf(provider))
        }
        suspend fun tick() = invoke("tick")
        fun request() { ReflectionHelpers.callInstanceMethod<Void>(coordinator, "beginManualSync") }
        fun expireConnectionWindow() { ReflectionHelpers.setField(coordinator, "manualDeadline", 0L) }
        suspend fun invoke(name: String): Any? = kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn { continuation ->
            val method = DeviceSyncCoordinator::class.java.getDeclaredMethod(name, kotlin.coroutines.Continuation::class.java)
            method.isAccessible = true; method.invoke(coordinator, continuation)
        }
        suspend fun close() {
            ReflectionHelpers.getField<CoroutineScope>(coordinator, "scope").cancel()
            kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
                val method = DeviceSyncCoordinator::class.java.getDeclaredMethod("pause", Boolean::class.javaPrimitiveType, kotlin.coroutines.Continuation::class.java)
                method.isAccessible = true; method.invoke(coordinator, false, continuation)
            }
            provider.pause(); db.close(); steam.close()
        }
    }

    @Test fun manual_send_only_to_receive_only_runs_once_without_enabling_automatic_sync() = runBlocking { withTimeout(90_000) {
        DeviceVaultProviderTest.loadNative()
        Dispatchers.setMain(Dispatchers.Unconfined)
        SessionManager.markUnlocked()
        val context = RuntimeEnvironment.getApplication()
        val sender = Fixture(context, "z-sender", "a-receiver", DeviceSyncMode.SEND_ONLY)
        val receiver = Fixture(context, "a-receiver", "z-sender", DeviceSyncMode.RECEIVE_ONLY)
        try {
            val source = sender.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title = "Manual fixture",
                website = "example.test", username = "fixture", password = "first-secret"))
            sender.tick(); receiver.tick()
            assertNull(ReflectionHelpers.getField<Any?>(sender.coordinator, "snapshot"))
            assertTrue(receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().isEmpty())
            sender.request(); receiver.request()
            sender.tick()
            assertTrue(sender.coordinator.state.value.lastAttemptAt > 0)
            DeviceSyncEditGate.hold().use {
                val receiveDeadline = System.nanoTime() + 60_000_000_000L
                while (receiver.coordinator.state.value.phase != "received" && System.nanoTime() < receiveDeadline) {
                    sender.tick(); receiver.tick()
                    assertNull(sender.coordinator.state.value.error)
                    assertNull(receiver.coordinator.state.value.error)
                    Thread.sleep(30)
                }
                assertEquals("received", receiver.coordinator.state.value.phase)
                receiver.expireConnectionWindow()
                receiver.tick()
                assertNull(receiver.coordinator.state.value.error)
                assertTrue(receiver.coordinator.state.value.manualSync)
                assertTrue(receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().isEmpty())
            }
            val deadline = System.nanoTime() + 60_000_000_000L
            while (System.nanoTime() < deadline && (sender.settings.peers.single().lastSyncedAt == 0L || receiver.settings.peers.single().lastSyncedAt == 0L)) {
                sender.tick(); receiver.tick()
                assertNull(sender.coordinator.state.value.error)
                assertNull(receiver.coordinator.state.value.error)
                Thread.sleep(30)
            }
            assertTrue(sender.settings.peers.single().lastSyncedAt > 0L)
            assertTrue(receiver.settings.peers.single().lastSyncedAt > 0L)
            assertTrue(receiver.coordinator.state.value.lastDiscoveryAt > 0)
            assertEquals(1, receiver.coordinator.state.value.discoveredPeers)
            assertEquals(0, receiver.coordinator.state.value.activeConnections)
            assertFalse(sender.store.load().enabled); assertFalse(receiver.store.load().enabled)
            assertFalse(sender.coordinator.state.value.manualSync); assertFalse(receiver.coordinator.state.value.manualSync)
            val received = receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().single()
            assertEquals("first-secret", SecurityManager(context).decryptDataIfMonicaCiphertext(received.password))
            sender.db.passwordEntryDao().updatePasswordEntry(sender.db.passwordEntryDao().getPasswordEntryById(source)!!.copy(password = "second-secret"))
            repeat(4) { sender.tick(); receiver.tick() }
            assertEquals("first-secret", SecurityManager(context).decryptDataIfMonicaCiphertext(receiver.db.passwordEntryDao().getPasswordEntryById(received.id)!!.password))
            // Re-enable automatic sync and verify a later edit arrives without another request.
            sender.settings.enabled = true; sender.store.save(sender.settings)
            receiver.settings.enabled = true; receiver.store.save(receiver.settings)
            val automaticDeadline = System.nanoTime() + 60_000_000_000L
            while (System.nanoTime() < automaticDeadline) {
                sender.tick(); receiver.tick()
                val password = receiver.db.passwordEntryDao().getPasswordEntryById(received.id)!!.password
                if (SecurityManager(context).decryptDataIfMonicaCiphertext(password) == "second-secret") break
                Thread.sleep(30)
            }
            assertEquals("second-secret", SecurityManager(context).decryptDataIfMonicaCiphertext(receiver.db.passwordEntryDao().getPasswordEntryById(received.id)!!.password))
            assertFalse(sender.coordinator.state.value.manualSync); assertFalse(receiver.coordinator.state.value.manualSync)
        } finally {
            sender.close(); receiver.close(); SessionManager.markLocked(); Dispatchers.resetMain()
        }
    } }

    @Test fun receiver_write_failure_reaches_sender_as_rejection_and_retry_completes() = runBlocking { withTimeout(90000) {
        DeviceVaultProviderTest.loadNative(); Dispatchers.setMain(Dispatchers.Unconfined); SessionManager.markUnlocked()
        val context = RuntimeEnvironment.getApplication()
        val sender = Fixture(context,"z-reject-sender","a-reject-receiver",DeviceSyncMode.SEND_ONLY)
        val receiver = Fixture(context,"a-reject-receiver","z-reject-sender",DeviceSyncMode.RECEIVE_ONLY)
        try {
            sender.db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title="Rejected fixture",website="",username="",password="synthetic"))
            receiver.db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_fixture BEFORE INSERT ON password_entries BEGIN SELECT RAISE(ABORT, 'synthetic write failure'); END")
            sender.request(); receiver.request()
            val deadline = System.nanoTime() + 60_000_000_000L
            while (System.nanoTime() < deadline && sender.coordinator.state.value.error == null) {
                sender.tick(); receiver.tick(); Thread.sleep(30)
            }
            assertEquals("peer_merge_failed",sender.coordinator.state.value.error)
            assertEquals("peer_apply",sender.coordinator.state.value.failureOperation)
            assertEquals("applying",receiver.coordinator.state.value.failureStage)
            assertEquals(0L,sender.settings.peers.single().lastSyncedAt)
            assertEquals(0L,receiver.settings.peers.single().lastSyncedAt)
            assertTrue(receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().isEmpty())
            receiver.db.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_fixture")
            // Finish native rejection cleanup before explicitly requesting the retry.
            repeat(3) { receiver.tick(); sender.tick() }
            sender.request(); receiver.request()
            val retryDeadline = System.nanoTime() + 60_000_000_000L
            while (System.nanoTime() < retryDeadline && (sender.settings.peers.single().lastSyncedAt == 0L || receiver.settings.peers.single().lastSyncedAt == 0L)) {
                sender.tick(); receiver.tick(); Thread.sleep(30)
            }
            assertTrue(sender.settings.peers.single().lastSyncedAt > 0L)
            assertTrue(receiver.settings.peers.single().lastSyncedAt > 0L)
            assertEquals(1,receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().size)
        } finally { sender.close(); receiver.close(); SessionManager.markLocked(); Dispatchers.resetMain() }
    } }

    @Test fun manual_connection_window_expires_when_the_peer_never_connects() = runBlocking {
        DeviceVaultProviderTest.loadNative()
        Dispatchers.setMain(Dispatchers.Unconfined)
        SessionManager.markUnlocked()
        val sender = Fixture(RuntimeEnvironment.getApplication(), "z-timeout", "a-absent", DeviceSyncMode.SEND_ONLY)
        try {
            sender.request(); sender.tick()
            assertTrue(sender.coordinator.state.value.manualSync)
            sender.expireConnectionWindow(); sender.tick()
            assertEquals("manual_timeout", sender.coordinator.state.value.failureOperation)
            assertFalse(sender.coordinator.state.value.manualSync)
            assertFalse(sender.store.load().enabled)
            assertTrue(ReflectionHelpers.getField<Map<String, Any>>(sender.coordinator, "transfers").isEmpty())
        } finally {
            sender.close(); SessionManager.markLocked(); Dispatchers.resetMain()
        }
    }

    @Test fun automatic_toggle_is_immediate_and_persisted_while_sync_holds_the_gate() = runBlocking {
        Dispatchers.setMain(Dispatchers.Unconfined); SessionManager.markUnlocked()
        val context = RuntimeEnvironment.getApplication()
        val fixture = Fixture(context, "z-toggle", "a-toggle", DeviceSyncMode.SEND_ONLY)
        // Drive the real action API, but hold the same gate as a long export/import.
        ReflectionHelpers.setField(fixture.coordinator, "running", Job())
        val gate = ReflectionHelpers.getField<Mutex>(fixture.coordinator, "gate")
        gate.lock()
        try {
            for (phase in listOf("preparing", "sending", "applying", "confirming", "failed", "waiting")) {
                ReflectionHelpers.setField(fixture.coordinator, "phase", phase)
                ReflectionHelpers.callInstanceMethod<Void>(fixture.coordinator, "publish")
                for (enabled in listOf(true, false, true, false)) {
                    fixture.coordinator.setEnabled(enabled)
                    assertEquals("Switch must update during $phase", enabled, fixture.coordinator.state.value.enabled)
                    assertEquals("Setting must persist during $phase", enabled, fixture.store.load().enabled)
                    // Progress updates and saves of the older snapshot must not undo a tap.
                    fixture.store.save(fixture.settings)
                    ReflectionHelpers.callInstanceMethod<Void>(fixture.coordinator, "publish")
                    assertEquals(enabled, fixture.coordinator.state.value.enabled)
                    assertEquals(enabled, fixture.store.load().enabled)
                    assertEquals(phase, fixture.coordinator.state.value.phase)
                }
            }
        } finally {
            ReflectionHelpers.getField<CoroutineScope>(fixture.coordinator, "scope").cancel()
            gate.unlock(); fixture.close(); SessionManager.markLocked(); Dispatchers.resetMain()
        }
    }
}
