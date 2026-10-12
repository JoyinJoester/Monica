package takagi.ru.monica.lansync

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.importer.SteamMaFileParser
import java.io.File
import java.util.UUID

/** The real coordinator, provider, Android storage and JNI, with isolated synthetic stores. */
class NearbyAutomaticSyncTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private inner class Fixture(id: String, peer: String, mode: DeviceSyncMode) {
        val root = File(context.cacheDir, "automatic-fixture-${UUID.randomUUID()}")
        val db = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        val steam = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).build()
        val security = SecurityManager(context)
        val preferenceName = "automatic-fixture-${UUID.randomUUID()}"
        val settings = DeviceSyncPreferences(deviceId = id, mode = mode,
            peers = mutableListOf(SavedSyncPeer(peer, "Synthetic peer", "automatic-fixture", "A".repeat(43), address = "127.0.0.1")))
        val store = DeviceSyncPreferenceStore(context, security, preferenceName).also { it.save(settings) }
        val provider = DevicePasswordSyncProvider(context, security, store, db, File(root, "replica"), steam)
        val coordinator = DeviceSyncCoordinator::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(context)
        val gate: Mutex
        init {
            set("preferences", settings); set("store\$delegate", lazyOf(store)); set("dataProvider", lazyOf(provider))
            // The harness drives tick under the production mutex; no competing ticker.
            set("running", Job())
            gate = get("gate") as Mutex
            provider.onProgress = { progress ->
                DeviceSyncCoordinator::class.java.getDeclaredMethod("updateProgress", DeviceSyncProgress::class.java)
                    .apply { isAccessible = true }.invoke(coordinator, progress)
                onProgress?.invoke(progress)
            }
        }
        var onProgress: ((DeviceSyncProgress) -> Unit)? = null
        fun set(name: String, value: Any?) { DeviceSyncCoordinator::class.java.getDeclaredField(name).apply { isAccessible = true }.set(coordinator, value) }
        fun get(name: String): Any? = DeviceSyncCoordinator::class.java.getDeclaredField(name).apply { isAccessible = true }.get(coordinator)
        suspend fun tick() = gate.withLock {
            kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
                DeviceSyncCoordinator::class.java.getDeclaredMethod("tick", kotlin.coroutines.Continuation::class.java)
                    .apply { isAccessible = true }.invoke(coordinator, continuation)
            }
        }
        suspend fun manual() = gate.withLock {
            DeviceSyncCoordinator::class.java.getDeclaredMethod("beginManualSync").apply { isAccessible = true }.invoke(coordinator)
        }
        fun toggle(value: Boolean) {
            coordinator.setEnabled(value)
            assertEquals(value, coordinator.state.value.enabled)
            // Reopen the preference store, including while its encrypted snapshot is stale.
            assertEquals(value, DeviceSyncPreferenceStore(context, security, preferenceName).load().enabled)
        }
        suspend fun close() {
            (get("scope") as CoroutineScope).cancel()
            gate.withLock {
                kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
                    DeviceSyncCoordinator::class.java.getDeclaredMethod("pause", Boolean::class.javaPrimitiveType, kotlin.coroutines.Continuation::class.java)
                        .apply { isAccessible = true }.invoke(coordinator, false, continuation)
                }
            }
            provider.pause(); db.close(); steam.close(); context.deleteSharedPreferences(preferenceName); root.deleteRecursively()
        }
    }

    @Test fun toggling_during_export_and_import_preserves_data_and_stops_later_automatic_rounds() = runBlocking {
        SessionManager.markUnlocked()
        val sender = Fixture("z-automatic-sender", "a-automatic-receiver", DeviceSyncMode.SEND_ONLY)
        val receiver = Fixture("a-automatic-receiver", "z-automatic-sender", DeviceSyncMode.RECEIVE_ONLY)
        val rich = NearbyVaultFixtureData(context)
        try {
            repeat(610) { index -> sender.db.passwordEntryDao().insertPasswordEntry(takagi.ru.monica.data.PasswordEntry(
                title = "Bulk $index", website = "example.test", username = "fixture", password = sender.security.encryptData("synthetic-$index"))) }
            for (type in listOf(takagi.ru.monica.data.ItemType.NOTE, takagi.ru.monica.data.ItemType.TOTP, takagi.ru.monica.data.ItemType.BANK_CARD,
                takagi.ru.monica.data.ItemType.DOCUMENT, takagi.ru.monica.data.ItemType.BILLING_ADDRESS, takagi.ru.monica.data.ItemType.PAYMENT_ACCOUNT)) {
                sender.db.secureItemDao().insertItem(takagi.ru.monica.data.SecureItem(itemType = type, title = "Trash $type",
                    itemData = sender.security.encryptData("{\"content\":\"synthetic trash\"}"), isDeleted = true))
            }
            rich.seed(sender.db, sender.security)
            val keyPair = java.security.KeyPairGenerator.getInstance("EC").apply { initialize(java.security.spec.ECGenParameterSpec("secp256r1")) }.generateKeyPair()
            for (count in listOf(0L, 7L)) sender.db.passkeyDao().insert(takagi.ru.monica.passkey.PasskeyPrivateKeyStore.protectPasskey(context,
                takagi.ru.monica.data.PasskeyEntry(credentialId = UUID.randomUUID().toString(), rpId = "example.test", rpName = "Counter $count",
                    userId = "Zml4dHVyZQ==", userName = "fixture", userDisplayName = "Fixture", signCount = count, backupEligible = true, backupState = false,
                    publicKey = java.util.Base64.getEncoder().encodeToString(keyPair.public.encoded), privateKeyAlias = java.util.Base64.getEncoder().encodeToString(keyPair.private.encoded))))
            val secret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
            SteamAccountRepository(sender.steam.steamAccountDao(), sender.security).upsertFromMaFile(SteamMaFileParser().parse(
                JSONObject().put("steamid", "76561198000000000").put("account_name", "Synthetic Steam")
                    .put("shared_secret", secret).put("identity_secret", secret).put("device_id", "android:fixture").toString()))
            var stoppedDuringExport = false
            sender.onProgress = { progress ->
                if (!stoppedDuringExport && progress.stage == "preparing") {
                    assertTrue(sender.gate.isLocked)
                    sender.toggle(false); sender.toggle(true); sender.toggle(false)
                    stoppedDuringExport = true
                }
            }
            sender.tick()
            assertTrue(stoppedDuringExport)
            assertEquals(0L, sender.coordinator.state.value.lastAttemptAt)
            assertNull(sender.coordinator.state.value.passkeyReview)
            rich.verify(sender.db, sender.security)

            sender.toggle(true); receiver.toggle(true)
            var stoppedDuringImport = false
            var passkeyPrompts = 0
            receiver.onProgress = { progress ->
                if (!stoppedDuringImport && progress.stage == "applying") {
                    assertTrue(receiver.gate.isLocked)
                    receiver.toggle(false); receiver.toggle(true); receiver.toggle(false)
                    sender.toggle(false)
                    stoppedDuringImport = true
                }
            }
            suspend fun awaitSuccess(previous: Long = 0L) {
                val deadline = System.nanoTime() + 600_000_000_000L
                while (sender.settings.peers.single().lastSyncedAt <= previous || receiver.settings.peers.single().lastSyncedAt <= previous) {
                    check(System.nanoTime() < deadline) { "Sync timeout: ${sender.coordinator.state.value} / ${receiver.coordinator.state.value}" }
                    sender.tick(); receiver.tick()
                    sender.coordinator.state.value.passkeyReview?.let { review ->
                        passkeyPrompts++
                        sender.coordinator.confirmPasskeySync(review, true)
                        // Give the action its turn under the production gate.
                        withTimeout(30_000) { while (sender.coordinator.state.value.passkeyReview != null) delay(10) }
                    }
                    assertNull(sender.coordinator.state.value.error); assertNull(receiver.coordinator.state.value.error)
                    delay(30)
                }
            }
            awaitSuccess()
            assertTrue(stoppedDuringImport)
            assertFalse(sender.store.load().enabled); assertFalse(receiver.store.load().enabled)
            rich.verify(receiver.db, receiver.security)
            assertEquals(1, passkeyPrompts)
            assertTrue(sender.store.passkeyNoticeSuppressed())
            assertEquals(610, receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().size)
            assertEquals(0L, receiver.db.passkeyDao().getAllPasskeysSync().single().signCount)
            assertEquals(setOf(0L, 7L), sender.db.passkeyDao().getAllPasskeysSync().map { it.signCount }.toSet())
            val account = SteamAccountRepository(receiver.steam.steamAccountDao(), receiver.security).getAccounts().single()
            assertEquals(secret, account.sharedSecret); assertEquals(secret, account.identitySecret)
            val previous = maxOf(sender.settings.peers.single().lastSyncedAt, receiver.settings.peers.single().lastSyncedAt)
            val source = sender.db.passwordEntryDao().getDeviceSyncLocalPasswords().single { it.title == "Bulk 0" }
            sender.db.passwordEntryDao().updatePasswordEntry(source.copy(password = "changed-after-off"))
            repeat(4) { sender.tick(); receiver.tick() }
            fun receiverSecret(entry: takagi.ru.monica.data.PasswordEntry) = receiver.security.decryptDataIfMonicaCiphertext(entry.password)
            assertEquals("synthetic-0", receiverSecret(receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().single { it.title == "Bulk 0" }))
            // Automatic remains disabled, but explicitly requested synchronization still works.
            sender.manual(); receiver.manual(); awaitSuccess(previous)
            assertEquals("changed-after-off", receiverSecret(receiver.db.passwordEntryDao().getDeviceSyncLocalPasswords().single { it.title == "Bulk 0" }))
            rich.verify(receiver.db, receiver.security)
            assertFalse(sender.store.load().enabled); assertFalse(receiver.store.load().enabled)
            assertEquals(1, passkeyPrompts)
            assertTrue(DeviceSyncPreferenceStore(context, sender.security, sender.preferenceName).passkeyNoticeSuppressed())
        } finally {
            rich.clean(sender.db); rich.clean(receiver.db); sender.close(); receiver.close(); SessionManager.markLocked()
        }
    }
}
