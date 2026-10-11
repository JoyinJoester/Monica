package takagi.ru.monica.lansync

import android.util.Base64
import org.json.JSONObject
import java.io.File
import java.security.SecureRandom

/** Device trust can be established before any password source is read. */
internal object DevicePairingSnapshot {
    const val REVISION = "pairing-only-v1"

    fun prepare(directory: File, deviceId: String, request: (JSONObject) -> JSONObject = RustLanSync::request): DevicePasswordExport {
        if (!directory.mkdirs() && !directory.isDirectory) throw LanSyncException("storage_directory_failed", "pairing_directory")
        val journal = File(directory, ".pairing.replica")
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
        var handle: Long? = null
        try {
            handle = request(JSONObject().put("operation", "replica_open").put("path", journal.absolutePath)
                .put("device_id", deviceId).put("key", Base64.encodeToString(key, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)))
                .getLong("handle")
            val exported = request(JSONObject().put("operation", "replica_export").put("handle", handle)
                .put("path", File(directory, "changes.mdbxsync").absolutePath))
            return DevicePasswordExport(REVISION, directory, exported.getString("content_key"))
        } finally {
            key.fill(0)
            try {
                handle?.let { request(JSONObject().put("operation", "replica_close").put("handle", it)) }
            } finally { if (journal.exists()) check(journal.delete()) }
        }
    }
}
