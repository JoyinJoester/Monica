package takagi.ru.monica.lansync

import org.json.JSONObject

internal class LanSyncException @JvmOverloads constructor(val code: String, val operation: String = "", val recordName: String = "") : java.io.IOException("LAN synchronization failed ($code)")

internal object RustLanSync {
    private val available = runCatching {
        val hostLibrary = System.getProperty("monica.lan.native.path")
        if(hostLibrary.isNullOrBlank()) System.loadLibrary("monica_rust_jni") else System.load(hostLibrary)
    }.isSuccess

    @JvmStatic
    private external fun nativeRequest(request: String): String?

    fun request(request: JSONObject): JSONObject {
        val operation = request.optString("operation")
        if (!available) throw LanSyncException("native_unavailable", operation)
        val response = try { nativeRequest(request.toString()) }
        catch (_: LinkageError) { throw LanSyncException("native_unavailable", operation) }
        val envelope = JSONObject(response ?: throw LanSyncException("native_unavailable", operation))
        if (!envelope.optBoolean("ok")) throw LanSyncException(envelope.optString("error", "failed"), operation)
        return envelope.getJSONObject("data")
    }
}
