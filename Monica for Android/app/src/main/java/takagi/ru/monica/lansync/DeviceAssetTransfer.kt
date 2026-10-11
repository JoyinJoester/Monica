package takagi.ru.monica.lansync

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.Base64

internal class DeviceAssetTransfer(directory: File, private val key: String) {
    private val path = File(directory, "assets.monica").absolutePath
    private var offsets: Map<String, Long>? = null
    fun contains(id: String): Boolean {
        if(!File(path).isFile) return false
        return index().containsKey(id)
    }
    private fun index(): Map<String, Long> = offsets ?: RustLanSync.request(JSONObject().put("operation", "asset_list").put("path", path)).getJSONArray("items").let { list ->
        buildMap { for(i in 0 until list.length()) { val item = list.getJSONObject(i); if(put(item.getString("id"), item.getLong("offset")) != null) throw LanSyncException("invalid_asset") } }
    }.also { offsets = it }
    suspend fun append(id: String, bytes: ByteArray, progress: (Long) -> Unit = {}) {
        val chunk = 512 * 1024
        var start = 0
        do {
            currentCoroutineContext().ensureActive()
            val size = minOf(chunk, bytes.size - start)
            val encoded = Base64.getEncoder().encodeToString(bytes.copyOfRange(start, start + size))
            RustLanSync.request(JSONObject().put("operation", "asset_append").put("path", path).put("key", key).put("id", id)
                .put("index", start / chunk).put("final", start + size == bytes.size).put("data", encoded))
            start += size; progress(start.toLong())
        } while(start < bytes.size)
    }
    suspend fun read(descriptor: JSONObject): ByteArray {
        val id = descriptor.getString("id")
        val size = descriptor.getLong("size")
        if(size !in 0..64L*1024*1024) throw LanSyncException("asset_limit")
        val indexMap = index()
        var offset = indexMap[id] ?: throw LanSyncException("asset_unavailable")
        val bytes = ByteArray(size.toInt()); var written = 0; var index = 0
        try {
            while(true) {
                currentCoroutineContext().ensureActive()
                val result = RustLanSync.request(JSONObject().put("operation", "asset_read").put("path", path).put("key", key).put("offset", offset))
                if(result.getString("id") != id || result.getInt("index") != index++) throw LanSyncException("invalid_asset")
                val chunk = Base64.getDecoder().decode(result.getString("data"))
                try { if(written + chunk.size > bytes.size) throw LanSyncException("invalid_asset"); chunk.copyInto(bytes, written); written += chunk.size }
                finally { chunk.fill(0) }
                if(result.getBoolean("final")) break
                offset = result.getLong("next")
            }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            if(written.toLong() != size || hash != id) throw LanSyncException("invalid_asset")
            return bytes
        } catch(error: Throwable) { bytes.fill(0); throw error }
    }
}
