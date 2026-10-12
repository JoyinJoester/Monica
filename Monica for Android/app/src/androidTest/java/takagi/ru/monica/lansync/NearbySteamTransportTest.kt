package takagi.ru.monica.lansync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.steam.data.SteamAccountRepository
import takagi.ru.monica.steam.data.SteamDatabase
import takagi.ru.monica.steam.importer.SteamMaFileParser
import java.io.File
import java.util.UUID

/** Synthetic, isolated stores; real Android Keystore, JNI and encrypted TCP transport. */
class NearbySteamTransportTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val secret = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTA="
    private inner class Fixture(val name: String) {
        val root = File(context.cacheDir, "nearby-steam-${UUID.randomUUID()}")
        val db = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        val steam = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).build()
        val security = SecurityManager(context)
        val preferenceName = "nearby-fixture-${UUID.randomUUID()}"
        val preferences = DeviceSyncPreferenceStore(context, security, preferenceName)
        val provider = DevicePasswordSyncProvider(context, security, preferences, db, File(root, "replica"), steam)
        val repository = SteamAccountRepository(steam.steamAccountDao(), security)
        val incoming = File(root, "incoming")
        lateinit var outgoing: DevicePasswordExport
        suspend fun seed() {
            val raw = JSONObject().put("steamid", "76561198000000000").put("account_name", name)
                .put("shared_secret", secret).put("identity_secret", secret).put("device_id", "android:fixture")
                .put("revocation_code", "R00000").put("access_token", "synthetic-access")
                .put("refresh_token", "synthetic-refresh").put("future_field", "preserved")
            val id = repository.upsertFromMaFile(SteamMaFileParser().parse(raw.toString()))
            // A shared local category can also be linked to a Bitwarden folder.
            // That link must neither block local export nor become a remote binding.
            repository.moveToCategory(id, db.categoryDao().insert(Category(name = "Steam fixture folder",
                bitwardenVaultId = 42L, bitwardenFolderId = "synthetic-external-folder")))
            outgoing = provider.prepare(File(root, "outgoing"))
        }
        fun request(operation: String) = JSONObject().put("operation", operation).put("protocol", 2)
            .put("vault_id", "monica-passwords-v1").put("outgoing_directory", outgoing.directory.absolutePath)
            .put("incoming_directory", incoming.absolutePath).put("content_key", outgoing.contentKey)
            .put("device", JSONObject().put("device_id", name).put("name", name).put("content_type", "vault")
                .put("vault_content_version", 2).put("sync_mode", "bidirectional"))
        var expectedNames = setOf("ordinary", "fdroid")
        suspend fun verify() {
            val accounts = repository.getAccounts()
            assertEquals(expectedNames.size, accounts.size)
            assertEquals(expectedNames, accounts.map { it.accountName }.toSet())
            accounts.forEach {
                assertEquals(secret, it.sharedSecret); assertEquals(secret, it.identitySecret)
                assertEquals("synthetic-refresh", it.refreshToken)
                assertEquals("synthetic-access", it.accessToken)
                assertEquals("preserved", JSONObject(it.rawSteamGuardJson).getString("future_field"))
                assertEquals("Steam fixture folder", db.categoryDao().getCategoryById(requireNotNull(it.categoryId))?.name)
            }
            steam.steamAccountDao().getAccounts().forEach {
                assertNotEquals(secret, it.sharedSecret)
                assertFalse(it.rawSteamGuardJson.contains("synthetic-refresh"))
                assertEquals(secret, security.decryptData(it.sharedSecret))
            }
            File(incoming, "changes.mdbxsync").takeIf { it.exists() }?.let {
                assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains(secret))
            }
            assertTrue(db.secureItemDao().getDeviceSyncLocalItems().isEmpty())
        }
        suspend fun close() { provider.pause(); steam.close(); db.close(); context.deleteSharedPreferences(preferenceName); root.deleteRecursively() }
    }
    private suspend fun awaitTransfers(sessions: List<Pair<Fixture, Long>>) {
        val applied = mutableSetOf<Long>()
        val deadline = System.nanoTime() + 90_000_000_000L
        while (System.nanoTime() < deadline) {
            val states = sessions.map { (fixture, handle) ->
                val state = RustLanSync.request(JSONObject().put("operation", "progress").put("handle", handle))
                assertNotEquals(state.optString("error"), "failed", state.getString("phase"))
                if (state.getString("phase") == "received" && applied.add(handle)) {
                    if (state.getJSONObject("peer").optBoolean("receive_content", true))
                        fixture.provider.apply(fixture.incoming, state.getJSONObject("peer"))
                    fixture.verify()
                    // Acknowledge only after validating real destination persistence.
                    RustLanSync.request(JSONObject().put("operation", "acknowledge").put("handle", handle).put("success", true))
                }
                state
            }
            if (states.all { it.getString("phase") == "completed" }) return
            Thread.sleep(20)
        }
        fail("Encrypted Steam transfer timed out")
    }
    private fun release(handle: Long) {
        RustLanSync.request(JSONObject().put("operation", "cancel").put("handle", handle))
        repeat(300) {
            if (runCatching { RustLanSync.request(JSONObject().put("operation", "release").put("handle", handle)) }.isSuccess) return
            Thread.sleep(10)
        }
        fail("Native session did not release")
    }
    @Test fun encryptedSteamRoundTrip() = runBlocking {
        SessionManager.markUnlocked()
        val a = Fixture("ordinary"); val b = Fixture("fdroid")
        val handles = mutableListOf<Long>()
        try {
            a.seed(); b.seed()
            val server = RustLanSync.request(a.request("start").put("listen_address", "127.0.0.1"))
            handles += server.getLong("handle")
            val invitation = "monica-lan:2:127.0.0.1:${server.getInt("port")}:${server.getString("code")}"
            val client = RustLanSync.request(b.request("connect").put("invitation", invitation))
            handles += client.getLong("handle")
            awaitTransfers(listOf(a to handles[0], b to handles[1]))
        } finally { handles.forEach(::release); a.close(); b.close(); SessionManager.markLocked() }
    }
    @Test fun sendOnlyToReceiveOnlySteamTransfer() = runBlocking {
        SessionManager.markUnlocked()
        val sender = Fixture("ordinary"); val receiver = Fixture("fdroid")
        sender.expectedNames = setOf("ordinary")
        val handles = mutableListOf<Long>()
        try {
            sender.seed(); receiver.seed()
            val request = sender.request("start").put("listen_address", "127.0.0.1")
            request.getJSONObject("device").put("sync_mode", "send_only")
            val server = RustLanSync.request(request); handles += server.getLong("handle")
            val connect = receiver.request("connect").put("invitation", "monica-lan:2:127.0.0.1:${server.getInt("port")}:${server.getString("code")}")
            connect.getJSONObject("device").put("sync_mode", "receive_only")
            connect.remove("outgoing_directory"); connect.remove("content_key")
            handles += RustLanSync.request(connect).getLong("handle")
            awaitTransfers(listOf(sender to handles[0], receiver to handles[1]))
            assertTrue(sender.incoming.listFiles().orEmpty().isEmpty())
            assertFalse(RustLanSync.request(JSONObject().put("operation", "progress").put("handle", handles[0]))
                .getJSONObject("peer").getBoolean("receive_content"))
        } finally { handles.forEach(::release); sender.close(); receiver.close(); SessionManager.markLocked() }
    }

    /** Run concurrently in ordinary and F-Droid apps using nearbyRole=server/client. */
    @Test fun crossApplicationSteamPairing() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val role = args.getString("nearbyRole")
        assumeTrue(role == "server" || role == "client")
        SessionManager.markUnlocked()
        val fixture = Fixture(if (role == "server") "ordinary" else "fdroid")
        val invitationFile = File(context.cacheDir, "nearby-test-invitation.txt")
        var handle: Long? = null
        try {
            fixture.seed()
            val session = if (role == "server") {
                RustLanSync.request(fixture.request("start").put("listen_address", "127.0.0.1")).also {
                    invitationFile.writeText("monica-lan:2:127.0.0.1:${it.getInt("port")}:${it.getString("code")}")
                }
            } else RustLanSync.request(fixture.request("connect").put("invitation", requireNotNull(args.getString("nearbyInvitation"))))
            handle = session.getLong("handle")
            awaitTransfers(listOf(fixture to handle))
        } finally { handle?.let(::release); invitationFile.delete(); fixture.close(); SessionManager.markLocked() }
    }
}
