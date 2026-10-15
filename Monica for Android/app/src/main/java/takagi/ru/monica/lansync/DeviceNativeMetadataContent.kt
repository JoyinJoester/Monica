package takagi.ru.monica.lansync

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import uniffi.mdbx_ffi.*
import java.util.UUID

/** Shared file and standalone-label adoption for folder/root metadata. Planning
 * performs no native writes; the caller commits these commands with its marker. */
internal class DeviceNativeMetadataContent(private val vault: MdbxVault, private val collection: String,
    private val saved: JSONObject, private val labels: List<MdbxObjectLabelRecord>,
    private val files: List<MdbxAttachmentRecord>) {

    fun guardData() = JSONObject().put("labels", JSONArray(labels.sortedBy { it.labelId }.map {
        JSONObject().put("id", it.labelId).put("name", it.name).put("payload", it.payloadJson).put("version", it.payloadSchemaVersion.toLong()) }))
        .put("files", JSONArray(files.sortedBy { it.attachmentId }.map {
            JSONObject().put("id", it.attachmentId).put("hash", it.contentHash).put("size", it.storedSize.toString())
                .put("name", it.fileName).put("media", it.mediaType).put("deleted", it.deleted) }))

    fun export(data: JSONObject, databaseId: Long): Pair<JSONObject, List<DeviceVaultAsset>> {
        val result = JSONObject(data.toString())
        if (labels.isNotEmpty() || result.has("labels")) result.put("labels", labelData(labels))
        val descriptors = JSONArray()
        val sources = files.filterNot { it.deleted }.sortedWith(compareBy({ it.contentHash }, { it.attachmentId })).map { file ->
            val descriptor = descriptor(file)
            descriptors.put(descriptor)
            DeviceVaultAsset(descriptor.getString("id"), file.attachmentId, databaseId, descriptor.getLong("size"))
        }
        result.put("assets", descriptors)
        return result to sources
    }

    private fun descriptor(file: MdbxAttachmentRecord): JSONObject {
        val mappings = saved.optJSONArray("device_sync_assets") ?: JSONArray()
        val matches = (0 until mappings.length()).map(mappings::getJSONObject).filter { it.optString("local_id") == file.attachmentId }
        require(matches.size <= 1 && file.storedSize <= MAX_BYTES.toULong()) { "invalid_asset" }
        val descriptor = matches.firstOrNull()?.let { JSONObject(it.toString()) } ?: JSONObject()
        descriptor.remove("local_id")
        return descriptor.put("id", file.contentHash.lowercase()).put("attachment_id", descriptor.optString("attachment_id").ifBlank { file.attachmentId })
            .put("file_name", file.fileName).put("media_type", file.mediaType ?: "application/octet-stream").put("size", file.storedSize.toLong())
    }

    class Plan(val data: JSONObject, val labelIds: List<String>, val mappings: JSONArray,
        val commands: List<MdbxWriteCommand>, val attachments: List<MdbxAttachmentBatchCommand>,
        private val buffers: List<ByteArray>) : AutoCloseable {
        override fun close() { buffers.forEach { it.fill(0) } }
        override fun toString() = "MetadataPlan"
    }

    suspend fun plan(data: JSONObject, purge: Boolean, reader: (suspend (JSONObject) -> ByteArray)?): Plan {
        val actual = if(purge) JSONObject() else JSONObject(data.toString())
        val buffers = mutableListOf<ByteArray>()
        try {
            val commands = mutableListOf<MdbxWriteCommand>()
            val labelIds = if (purge || data.has("labels")) reconcileLabels(collection, labels,
                if (purge) JSONArray() else data.getJSONArray("labels"), commands, false) else labels.map { it.labelId }
            if (!purge && !data.has("labels") && labels.isNotEmpty()) actual.put("labels", labelData(labels))
            val attachments = mutableListOf<MdbxAttachmentBatchCommand>()
            val existing = files.filterNot { it.deleted }
            val desired = if (purge) JSONArray() else data.optJSONArray("assets")
                ?: JSONArray(existing.map(::descriptor))
            require(desired.length() <= 64) { "asset_limit" }
            val keep = mutableSetOf<String>(); val identities = mutableSetOf<String>()
            val mappings = JSONArray(); val portable = JSONArray()
            var total = 0L
            for (index in 0 until desired.length()) {
                val descriptor = JSONObject(desired.getJSONObject(index).toString())
                val identity = descriptor.optString("attachment_id").takeIf { it.isNotBlank() }
                    ?: existing.firstOrNull { it.attachmentId !in keep && it.contentHash.equals(descriptor.getString("id"), true) && it.fileName == descriptor.getString("file_name") }
                        ?.let(::descriptor)?.getString("attachment_id") ?: UUID.randomUUID().toString()
                require(identities.add(identity)) { "invalid_asset_identity" }
                descriptor.put("attachment_id", identity)
                val old = existing.firstOrNull { it.attachmentId !in keep && descriptor(it).getString("attachment_id") == identity }
                val reusable = old != null && old.contentHash.equals(descriptor.getString("id"), true) && old.storedSize.toLong() == descriptor.getLong("size") &&
                    old.fileName == descriptor.getString("file_name") && (old.mediaType ?: "application/octet-stream") == descriptor.getString("media_type")
                val id = if (reusable) old!!.attachmentId else UUID.randomUUID().toString()
                if (!reusable) {
                    val bytes = reader?.invoke(descriptor) ?: throw LanSyncException("asset_unavailable")
                    buffers += bytes
                    DeviceMetadataAssets.validate(descriptor, bytes)
                    total += bytes.size
                    if (total > MAX_BYTES) throw LanSyncException("asset_limit")
                    attachments += MdbxAttachmentBatchCommand.Create(id, collection, null, descriptor.getString("file_name"), descriptor.getString("media_type"), bytes)
                }
                keep += id
                portable.put(JSONObject(descriptor.toString()))
                mappings.put(JSONObject(descriptor.toString()).put("local_id", id))
            }
            existing.filter { it.attachmentId !in keep }.forEach { attachments += MdbxAttachmentBatchCommand.Delete(it.attachmentId) }
            if (!purge) actual.put("assets", portable)
            return Plan(actual, labelIds, mappings, commands, attachments, buffers)
        } catch (error: Throwable) { buffers.forEach { it.fill(0) }; throw error }
    }

    companion object {
        const val ROOT_KIND = "monica-device-root-tags-v2"
        const val FOLDER_KIND = "monica-device-folder-data-v1"
        const val TAG_KIND = "monica-project-tag"
        private const val MAX_BYTES = 64L * 1024 * 1024
        private val reserved = setOf(ROOT_KIND, FOLDER_KIND, TAG_KIND, "monica-device-folder-title-v2")
        fun kind(label: MdbxObjectLabelRecord): String = runCatching { JSONObject(label.payloadJson).optString("kind") }.getOrDefault("")
        fun labels(vault: MdbxVault, collection: String): List<MdbxObjectLabelRecord> =
            if (vault.getCollectionSummary(collection) == null) emptyList() else vault.listObjectLabels(collection).filterNot { it.deleted }
        fun standalone(vault: MdbxVault, collection: String): List<MdbxObjectLabelRecord> = labels(vault, collection).filter { label ->
            kind(label) !in reserved && !assigned(vault, label.labelId)
        }
        private fun assigned(vault: MdbxVault, id: String): Boolean {
            var cursor: String? = null; val seen = mutableSetOf<String>()
            do {
                val page = vault.listObjectLabelAssignmentSummariesByLabel(id, 100u, cursor)
                if (page.items.any { !it.deleted }) return true
                cursor = page.nextCursor
                check(cursor == null || seen.add(cursor)) { "invalid_label_pagination" }
            } while (cursor != null)
            return false
        }
        fun files(vault: MdbxVault, collection: String): List<MdbxAttachmentRecord> =
            if (vault.getCollectionSummary(collection) == null) emptyList() else vault.listAttachments(collection, null).filter { it.entryId == null }
        fun labelData(labels: List<MdbxObjectLabelRecord>) = JSONArray(labels.sortedWith(compareBy({ it.name }, { it.labelId })).map {
            JSONObject().put("name", it.name).put("payload", it.payloadJson).put("version", it.payloadSchemaVersion.toLong()) })
        fun reconcileLabels(collection: String, existing: List<MdbxObjectLabelRecord>, incoming: JSONArray,
            commands: MutableList<MdbxWriteCommand>, tags: Boolean): List<String> {
            require(incoming.length() <= 8192) { "invalid_vault_payload" }
            val keep = mutableListOf<String>()
            for (index in 0 until incoming.length()) {
                val tag = incoming.getJSONObject(index)
                require(tag.keys().asSequence().all { it in setOf("name", "payload", "version") }) { "unsupported_label_fields" }
                val text = tag.getString("payload")
                val parser = JSONTokener(text); val parsed = parser.nextValue()
                require(parser.nextClean() == '\u0000') { "invalid_label_payload" }
                val type = (parsed as? JSONObject)?.optString("kind")
                require(if (tags) type == TAG_KIND else type !in reserved) { "invalid_label_kind" }
                val version = tag.getLong("version"); require(version in 1..UInt.MAX_VALUE.toLong()) { "invalid_label_version" }
                val name = tag.getString("name")
                val old = existing.firstOrNull { it.labelId !in keep && it.name == name }
                val id = old?.labelId ?: UUID.randomUUID().toString(); keep += id
                if (old == null) commands += MdbxWriteCommand.CreateObjectLabel(id, collection, name, text, version.toUInt())
                else if (old.payloadJson != text || old.payloadSchemaVersion != version.toUInt()) commands += MdbxWriteCommand.UpdateObjectLabel(id, name, text, version.toUInt())
            }
            existing.filter { it.labelId !in keep }.forEach { commands += MdbxWriteCommand.DeleteObjectLabel(it.labelId) }
            return keep
        }
        fun execute(vault: MdbxVault, kind: String, commands: List<MdbxWriteCommand>, attachments: List<MdbxAttachmentBatchCommand>) {
            if (attachments.isEmpty()) { if (commands.isNotEmpty()) vault.executeWriteOperation(UUID.randomUUID().toString(), kind, commands) }
            else vault.executeCompositeWriteOperationWithLimits(UUID.randomUUID().toString(), kind, commands, attachments,
                MdbxCompositeWriteOperationLimits(defaultWriteOperationLimits(), MdbxAttachmentBatchLimits(128uL, MAX_BYTES.toULong(), MAX_BYTES.toULong(), 512uL * 1024uL)))
        }
    }
}
