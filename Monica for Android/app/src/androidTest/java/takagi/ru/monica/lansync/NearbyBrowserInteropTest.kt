package takagi.ru.monica.lansync

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.passkey.PasskeyPrivateKeyStore
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.security.SessionManager
import takagi.ru.monica.steam.data.SteamDatabase
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.UUID

/** Explicit opt-in, synthetic stores only. The control socket is emulator-loopback
 * and forwarded by adb; production JNI, provider and encrypted storage do the work. */
class NearbyBrowserInteropTest {
    @Test fun browserRoundTrip() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("browserInterop") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "browser-interop-${UUID.randomUUID()}")
        val db = Room.inMemoryDatabaseBuilder(context, PasswordDatabase::class.java).build()
        val steam = Room.inMemoryDatabaseBuilder(context, SteamDatabase::class.java).build()
        val security = SecurityManager(context)
        val preferenceName = "browser-interop-${UUID.randomUUID()}"
        val preferences = DeviceSyncPreferenceStore(context, security, preferenceName)
        val settings = DeviceSyncPreferences(intervalSeconds = 300)
        preferences.save(settings)
        val provider = DevicePasswordSyncProvider(context, security, preferences, db, File(root,"replica"), steam)
        var handle: Long? = null; var enrollment = false; var acknowledged = false
        var incoming = File(root,"initial")
        var running = true; var rounds = 0
        SessionManager.markUnlocked()
        try {
            db.passwordEntryDao().insertPasswordEntry(PasswordEntry(title="Android synthetic",website="",username="android",password=security.encryptData("android-original")))
            ServerSocket(39483, 2, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 240_000
                while (running) server.accept().use { socket ->
                    socket.soTimeout = 180_000
                    val text = socket.getInputStream().bufferedReader().readLine()
                    require(text.length < 4096)
                    val command = JSONObject(text)
                    val result = try {
                        when(command.getString("op")) {
                            "status" -> JSONObject().put("ready",true).put("rounds",rounds)
                            "pair", "sync" -> {
                                check(handle == null)
                                enrollment = command.getString("op") == "pair"; acknowledged = false
                                val directory = File(root,"out-${UUID.randomUUID()}")
                                val outgoing = if(enrollment) DevicePairingSnapshot.prepare(directory,settings.deviceId) else provider.prepare(directory)
                                incoming = File(root,"in-${UUID.randomUUID()}")
                                val peer = settings.peers.singleOrNull()
                                val device = JSONObject().put("device_id",settings.deviceId).put("name","Synthetic Android")
                                    .put("pair_id",if(enrollment) "" else peer!!.pairId).put("interval_seconds",settings.intervalSeconds)
                                    .put("settings_version",settings.settingsVersion).put("settings_writer",settings.settingsWriter)
                                    .put("sync_request",settings.syncRequest).put("revision",outgoing.revision)
                                    .put("content_type","vault").put("vault_content_version",2).put("sync_mode","bidirectional")
                                val request = JSONObject().put("operation","connect").put("protocol",2).put("vault_id","monica-passwords-v1")
                                    .put("device",device).put("content_key",outgoing.contentKey).put("outgoing_directory",outgoing.directory.absolutePath)
                                    .put("incoming_directory",incoming.absolutePath).put("invitation",if(enrollment) command.getString("invitation")
                                        else "monica-lan:2:10.0.2.2:${command.getInt("port")}:${peer!!.trustedKey}")
                                if(!enrollment) request.put("expected_device_id",peer!!.deviceId)
                                val started = RustLanSync.request(request); handle = started.getLong("handle"); JSONObject().put("started",true)
                            }
                            "progress" -> {
                                val id = requireNotNull(handle)
                                val state = RustLanSync.request(JSONObject().put("operation","progress").put("handle",id))
                                val phase = state.getString("phase")
                                check(phase != "failed") { state.optString("error") }
                                if(phase == "received" && !acknowledged) {
                                    val remote = state.getJSONObject("peer")
                                    if(enrollment) {
                                        val pair = remote.getJSONObject("pairing")
                                        settings.peers += SavedSyncPeer(remote.getString("device_id"),remote.getString("name"),pair.getString("pair_id"),pair.getString("trusted_key"))
                                        preferences.save(settings)
                                        assertEquals(1,db.passwordEntryDao().getDeviceSyncLocalPasswords().size)
                                    } else {
                                        provider.apply(incoming,remote)
                                        val items = db.passwordEntryDao().getDeviceSyncLocalPasswords()
                                        assertTrue(items.all { it.password != "browser-original" && it.password != "android-original" })
                                        for(key in db.passkeyDao().getAllPasskeysSync()) {
                                            val bytes = Base64.getDecoder().decode(requireNotNull(PasskeyPrivateKeyStore.resolve(context,key.privateKeyAlias)))
                                            val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes)); bytes.fill(0)
                                            val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(key.publicKey)))
                                            val challenge = "android-interop-signature".toByteArray()
                                            val signed = Signature.getInstance("SHA256withECDSA").run { initSign(privateKey); update(challenge); sign() }
                                            assertTrue(Signature.getInstance("SHA256withECDSA").run { initVerify(publicKey); update(challenge); verify(signed) })
                                            assertEquals(0L,key.signCount)
                                        }
                                    }
                                    RustLanSync.request(JSONObject().put("operation","acknowledge").put("handle",id).put("success",true)); acknowledged = true
                                }
                                if(phase == "completed") {
                                    RustLanSync.request(JSONObject().put("operation","release").put("handle",id)); handle=null
                                    if(!enrollment) rounds++
                                }
                                JSONObject().put("phase",phase)
                            }
                            "inspect" -> JSONObject().put("items",JSONArray(db.passwordEntryDao().getDeviceSyncLocalPasswords().map {
                                JSONObject().put("title",it.title).put("password",security.decryptData(it.password))
                            })).put("passkeys",db.passkeyDao().getAllPasskeysSync().size)
                            "edit" -> {
                                val entry = db.passwordEntryDao().getDeviceSyncLocalPasswords().single { it.title == "Browser synthetic" }
                                db.passwordEntryDao().updatePasswordEntry(entry.copy(password=security.encryptData("android-edited"),updatedAt=java.util.Date()))
                                JSONObject().put("edited",true)
                            }
                            "stop" -> { running=false; JSONObject().put("stopped",true) }
                            else -> error("Unknown synthetic control command")
                        }
                    } catch(error: Throwable) { JSONObject().put("error",error.javaClass.simpleName+": "+error.message) }
                    socket.getOutputStream().bufferedWriter().apply { write(result.toString()); newLine(); flush() }
                }
            }
            assertTrue(rounds >= 2)
        } finally {
            handle?.let { runCatching { RustLanSync.request(JSONObject().put("operation","cancel").put("handle",it)) } }
            for(key in db.passkeyDao().getAllPasskeysSync()) PasskeyPrivateKeyStore.removeIfProtectedReference(context,key.privateKeyAlias)
            provider.pause(); db.close(); steam.close(); context.deleteSharedPreferences(preferenceName)
            if(root.canonicalFile.parentFile == context.cacheDir.canonicalFile) root.deleteRecursively()
            SessionManager.markLocked()
        }
    }
}
