package takagi.ru.monica.lansync

import org.json.JSONArray
import org.json.JSONObject
import takagi.ru.monica.repository.Mdbx2VaultSessionExecutor
import uniffi.mdbx_ffi.MdbxObjectLabelRecord
import uniffi.mdbx_ffi.MdbxVault
import uniffi.mdbx_ffi.MdbxWriteCommand
import java.security.MessageDigest
import java.util.UUID

/** Each source owns a partition of the native root. Unclaimed content remains
 * owned by this vault; syncing another owner must not edit that partition. */
internal class DeviceNativeRootMetadata(private val vault: MdbxVault, private val databaseId: Long = 0) {
    private val root = Mdbx2VaultSessionExecutor.rootProjectId(vault.info().vaultId)
    private val kind = DeviceNativeMetadataContent.ROOT_KIND
    private fun labels() = DeviceNativeMetadataContent.labels(vault, root)
    private fun markers(): List<Pair<MdbxObjectLabelRecord, JSONObject>> {
        val result = labels().filter { DeviceNativeMetadataContent.kind(it) == kind }.map { it to JSONObject(it.payloadJson) }
        val owners = mutableSetOf<String>()
        result.forEach { (_, data) ->
            require(owners.add(data.getString("owner")) && data.getString("owner").matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "invalid_root_owner" }
        }
        listOf("tag_ids", "label_ids", "device_sync_assets").forEach { field ->
            val claims = mutableSetOf<String>()
            result.filter { it.second.getString("owner") != root }.forEach { (_, data) ->
                val list = data.optJSONArray(field) ?: JSONArray()
                for (index in 0 until list.length()) {
                    val id = if (field == "device_sync_assets") list.getJSONObject(index).getString("local_id") else list.getString(index)
                    require(claims.add(id)) { "invalid_root_ownership" }
                }
            }
        }
        return result
    }
    private fun claimed(owner: String, metadata: List<Pair<MdbxObjectLabelRecord,JSONObject>>, field: String): Set<String> =
        metadata.filter { if(owner == root) it.second.getString("owner") != root else it.second.getString("owner") == owner }
            .flatMap { (_, data) -> val list = data.optJSONArray(field) ?: JSONArray()
                (0 until list.length()).map { if(field == "device_sync_assets") list.getJSONObject(it).getString("local_id") else list.getString(it) } }.toSet()
    private fun owns(owner: String, id: String, claimed: Set<String>) = if(owner == root) id !in claimed else id in claimed
    private fun tags(owner: String, metadata: List<Pair<MdbxObjectLabelRecord,JSONObject>>): List<MdbxObjectLabelRecord> {
        val claims = claimed(owner, metadata, "tag_ids")
        return labels().filter { DeviceNativeMetadataContent.kind(it) == DeviceNativeMetadataContent.TAG_KIND && owns(owner, it.labelId, claims) }
    }
    private fun content(owner: String, metadata: List<Pair<MdbxObjectLabelRecord,JSONObject>>): DeviceNativeMetadataContent {
        val labelClaims = claimed(owner, metadata, "label_ids"); val assetClaims = claimed(owner, metadata, "device_sync_assets")
        return DeviceNativeMetadataContent(vault, root, metadata.firstOrNull { it.second.getString("owner") == owner }?.second ?: JSONObject(),
            DeviceNativeMetadataContent.standalone(vault, root).filter { owns(owner, it.labelId, labelClaims) },
            DeviceNativeMetadataContent.files(vault, root).filter { owns(owner, it.attachmentId, assetClaims) })
    }
    private fun guard(owner: String, metadata: List<Pair<MdbxObjectLabelRecord,JSONObject>>): String {
        val marker = metadata.firstOrNull { it.second.getString("owner") == owner }
        if(owner != root && marker == null) return ""
        val extra = content(owner, metadata).guardData()
        val data = JSONObject().put("marker", marker?.first?.payloadJson ?: JSONObject.NULL)
            .put("tags", JSONArray(tags(owner,metadata).sortedBy { it.labelId }.map { it.toString() })).put("content", extra)
        return MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun read(): List<DevicePasswordSnapshot> {
        if(vault.getCollectionSummary(root) == null) return emptyList()
        val metadata = markers()
        return (metadata.map { it.second.getString("owner") } + root).distinct().map { owner ->
            val marker = metadata.firstOrNull { it.second.getString("owner") == owner }?.second
            val tags = tags(owner, metadata)
            val data = marker?.optJSONObject("data")?.takeIf { it.optJSONObject("payload") != null }?.let { JSONObject(it.toString()) }
                ?: JSONObject().put("title", "Vault metadata").put("folder", "").put("entry_type", "vault-metadata").put("payload", JSONObject())
            data.getJSONObject("payload").put("tags", DeviceNativeMetadataContent.labelData(tags))
            val exported = content(owner, metadata).export(data, databaseId)
            val retained = marker?.optBoolean("deleted") == true && data.optBoolean("trashed")
            val deleted = marker?.optBoolean("deleted") == true && (retained || tags.isEmpty() && exported.second.isEmpty() &&
                (exported.first.optJSONArray("labels")?.length() ?: 0) == 0)
            DevicePasswordSnapshot("metadata:$owner", owner, "", guard(owner,metadata), deleted,
                if(deleted && !retained) JSONObject() else exported.first, if(deleted && !retained) emptyList() else exported.second)
        }
    }
    suspend fun apply(owner: String, expected: String?, newBinding: Boolean, deleted: Boolean, data: JSONObject,
        reader: (suspend (JSONObject) -> ByteArray)?): String? {
        val metadata = markers()
        val marker = metadata.firstOrNull { it.second.getString("owner") == owner }
        if ((!newBinding && guard(owner,metadata) != expected.orEmpty()) || (newBinding && marker != null)) return null
        val purge = deleted && !data.optBoolean("trashed")
        if (!purge) DeviceVaultShape.validate(data)
        val commands = mutableListOf<MdbxWriteCommand>()
        if(vault.getCollectionSummary(root) == null) commands += MdbxWriteCommand.CreateProject(root,Mdbx2VaultSessionExecutor.ROOT_PROJECT_TITLE)
        val keep = DeviceNativeMetadataContent.reconcileLabels(root, tags(owner,metadata),
            if(purge) JSONArray() else data.getJSONObject("payload").getJSONArray("tags"), commands, true)
        content(owner, metadata).plan(data, purge, reader).use { plan ->
            commands += plan.commands
            val payload = marker?.second?.let { JSONObject(it.toString()) } ?: JSONObject()
            payload.put("kind", kind).put("owner", owner).put("deleted", deleted).put("data", plan.data)
                .put("tag_ids", JSONArray(keep)).put("label_ids", JSONArray(plan.labelIds)).put("device_sync_assets", plan.mappings)
            if(marker == null) commands += MdbxWriteCommand.CreateObjectLabel(UUID.randomUUID().toString(),root,kind,payload.toString(),1u)
            else if(marker.first.payloadJson != payload.toString()) commands += MdbxWriteCommand.UpdateObjectLabel(marker.first.labelId,kind,payload.toString(),1u)
            DeviceNativeMetadataContent.execute(vault, "device-root-metadata", commands, plan.attachments)
        }
        return guard(owner,markers())
    }
}
