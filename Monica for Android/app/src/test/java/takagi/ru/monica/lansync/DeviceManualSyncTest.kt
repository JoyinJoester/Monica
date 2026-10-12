package takagi.ru.monica.lansync

import android.app.Application
import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
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
            assertFalse(sender.store.load().enabled); assertFalse(receiver.store.load().enabled)
            assertFalse(sender.coordinator.state.value.manualSync); assertFalse(receiver.coordinator.state.value.manualSync)
            val received = receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().single()
            assertEquals("first-secret", SecurityManager(context).decryptDataIfMonicaCiphertext(received.password))
            sender.db.passwordEntryDao().updatePasswordEntry(sender.db.passwordEntryDao().getPasswordEntryById(source)!!.copy(password = "second-secret"))
            repeat(4) { sender.tick(); receiver.tick() }
            assertEquals("first-secret", SecurityManager(context).decryptDataIfMonicaCiphertext(receiver.db.passwordEntryDao().getPasswordEntryById(received.id)!!.password))
        } finally {
            sender.close(); receiver.close(); SessionManager.markLocked(); Dispatchers.resetMain()
        }
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
}
