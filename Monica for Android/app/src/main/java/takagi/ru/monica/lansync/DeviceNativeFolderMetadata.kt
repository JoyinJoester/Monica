package takagi.ru.monica.lansync

import org.json.JSONArray
import org.json.JSONObject
import uniffi.mdbx_ffi.MdbxVault
import uniffi.mdbx_ffi.MdbxWriteCommand
import java.security.MessageDigest
import java.util.UUID

internal class DeviceNativeFolderMetadata(private val vault: MdbxVault, private val id: String, private val databaseId: Long) {
    private val kind = DeviceNativeMetadataContent.FOLDER_KIND
    private fun labels() = DeviceNativeMetadataContent.labels(vault, id)
    private fun marker() = labels().filter { DeviceNativeMetadataContent.kind(it) == kind }.also {
        require(it.size <= 1) { "invalid_folder_marker" }
    }.singleOrNull()
    private fun saved() = marker()?.let { JSONObject(it.payloadJson) } ?: JSONObject()
    private fun content() = DeviceNativeMetadataContent(vault, id, saved(),
        DeviceNativeMetadataContent.standalone(vault, id), DeviceNativeMetadataContent.files(vault, id))

    fun guard(): String {
        val project = vault.getCollectionSummary(id) ?: return ""
        val data = JSONObject().put("project", project.toString()).put("labels", JSONArray(labels().sortedBy { it.labelId }.map { it.toString() }))
            .put("content", content().guardData())
        return MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun read(path: JSONArray): DevicePasswordSnapshot {
        val project = requireNotNull(vault.getCollectionSummary(id))
        val saved = saved()
        if (project.deleted && saved.optBoolean("purged")) return DevicePasswordSnapshot(
            "mdbx:$databaseId:folder:$id", id, "mdbx:$databaseId", guard(), true, JSONObject())
        val data = saved.optJSONObject("data")?.let { JSONObject(it.toString()) } ?: JSONObject()
        val payload = data.optJSONObject("payload") ?: JSONObject()
        val oldPath = payload.optJSONArray("path") ?: JSONArray()
        val mergedPath = JSONArray()
        for (index in 0 until path.length()) {
            val node = path.getJSONObject(index)
            val old = (0 until oldPath.length()).map(oldPath::getJSONObject).firstOrNull { it.optString("id") == node.getString("id") }
            val merged = old?.let { JSONObject(it.toString()) } ?: JSONObject()
            node.keys().forEach { merged.put(it, node.get(it)) }; mergedPath.put(merged)
        }
        data.put("title", path.getJSONObject(path.length()-1).getString("title")).put("folder", "").put("entry_type", "folder").put("trashed", project.deleted)
        payload.put("path", mergedPath).put("tags", DeviceNativeMetadataContent.labelData(labels().filter {
            DeviceNativeMetadataContent.kind(it) == DeviceNativeMetadataContent.TAG_KIND }))
        data.put("payload", payload)
        val exported = content().export(data, databaseId)
        return DevicePasswordSnapshot("mdbx:$databaseId:folder:$id", id, "mdbx:$databaseId", guard(), project.deleted, exported.first, exported.second)
    }

    suspend fun apply(data: JSONObject, deleted: Boolean, baseCommands: List<MdbxWriteCommand>, reader: (suspend (JSONObject) -> ByteArray)?) {
        val purge = deleted && !data.optBoolean("trashed")
        val existingFiles = DeviceNativeMetadataContent.files(vault, id).filterNot { it.deleted }
        // The bundled engine applies project commands before file mutations and
        // cannot trash a folder with live files atomically. Fail before any write.
        if (deleted && (existingFiles.isNotEmpty() || (data.optJSONArray("assets")?.length() ?: 0) > 0))
            throw LanSyncException("folder_attachment_trash_unavailable")
        val marker = marker()
        val previous = saved()
        val commands = baseCommands.toMutableList()
        val tags = labels().filter { DeviceNativeMetadataContent.kind(it) == DeviceNativeMetadataContent.TAG_KIND }
        if (purge || data.getJSONObject("payload").has("tags")) DeviceNativeMetadataContent.reconcileLabels(id, tags,
            if(purge) JSONArray() else data.getJSONObject("payload").getJSONArray("tags"), commands, true)
        content().plan(data, purge, reader).use { plan ->
            commands += plan.commands
            previous.put("kind", kind).put("data", plan.data).put("purged", purge).put("device_sync_assets", plan.mappings)
            if (marker == null) commands += MdbxWriteCommand.CreateObjectLabel(UUID.randomUUID().toString(), id, kind, previous.toString(), 1u)
            else commands += MdbxWriteCommand.UpdateObjectLabel(marker.labelId, kind, previous.toString(), 1u)
            if (deleted) commands += MdbxWriteCommand.DeleteProject(id)
            DeviceNativeMetadataContent.execute(vault, "device-folder-metadata", commands, plan.attachments)
        }
    }
}
