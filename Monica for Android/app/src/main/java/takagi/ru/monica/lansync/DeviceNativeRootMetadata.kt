package takagi.ru.monica.lansync

import org.json.JSONArray
import org.json.JSONObject
import takagi.ru.monica.repository.Mdbx2VaultSessionExecutor
import uniffi.mdbx_ffi.MdbxObjectLabelRecord
import uniffi.mdbx_ffi.MdbxVault
import uniffi.mdbx_ffi.MdbxWriteCommand
import java.security.MessageDigest
import java.util.UUID

/** Root tag ownership is separate from folders and ordinary object labels. */
internal class DeviceNativeRootMetadata(private val vault: MdbxVault) {
    private val root = Mdbx2VaultSessionExecutor.rootProjectId(vault.info().vaultId)
    private val kind = "monica-device-root-tags-v2"
    private fun labels(): List<MdbxObjectLabelRecord> = if(vault.getCollectionSummary(root) == null) emptyList() else vault.listObjectLabels(root).filterNot { it.deleted }
    private fun markers() = labels().mapNotNull { label -> runCatching { JSONObject(label.payloadJson) }.getOrNull()
        ?.takeIf { it.optString("kind") == kind }?.let { label to it } }
    private fun tags(owner: String,metadata: List<Pair<MdbxObjectLabelRecord,JSONObject>>): List<MdbxObjectLabelRecord> {
        val ids = metadata.filter { if(owner == root) it.second.getString("owner") != root else it.second.getString("owner") == owner }
            .flatMap { (_,data) -> val list = data.getJSONArray("tag_ids"); (0 until list.length()).map(list::getString) }.toSet()
        return labels().filter { runCatching { JSONObject(it.payloadJson).optString("kind") == "monica-project-tag" }.getOrDefault(false) }
            .filter { if(owner == root) it.labelId !in ids else it.labelId in ids }
    }
    private fun guard(owner: String,metadata: List<Pair<MdbxObjectLabelRecord,JSONObject>>): String {
        val marker = metadata.firstOrNull { it.second.getString("owner") == owner }
        if(owner != root && marker == null) return ""
        val data = JSONObject().put("marker",marker?.first?.payloadJson ?: JSONObject.NULL).put("tags",JSONArray(tags(owner,metadata).sortedBy { it.labelId }.map {
            JSONObject().put("id",it.labelId).put("name",it.name).put("payload",it.payloadJson).put("version",it.payloadSchemaVersion.toLong()) }))
        return MessageDigest.getInstance("SHA-256").digest(data.toString().toByteArray()).joinToString("") { "%02x".format(it) }
    }
    fun read(): List<DevicePasswordSnapshot> {
        if(vault.getCollectionSummary(root) == null) return emptyList()
        val metadata = markers()
        return (metadata.map { it.second.getString("owner") }+root).distinct().map { owner ->
            val marker = metadata.firstOrNull { it.second.getString("owner") == owner }?.second
            val tags = tags(owner,metadata); val deleted = marker?.optBoolean("deleted") == true && tags.isEmpty()
            val data = if(deleted) JSONObject() else marker?.optJSONObject("data")?.takeIf { it.optJSONObject("payload") != null }?.let { JSONObject(it.toString()) }
                ?: JSONObject().put("title","Vault metadata").put("folder","").put("entry_type","vault-metadata").put("payload",JSONObject())
            if(!deleted) data.getJSONObject("payload").put("tags",JSONArray(tags.sortedBy { it.name }.map {
                JSONObject().put("name",it.name).put("payload",it.payloadJson).put("version",it.payloadSchemaVersion.toLong()) }))
            DevicePasswordSnapshot("metadata:$owner",owner,"",guard(owner,metadata),deleted,data)
        }
    }
    fun apply(owner: String,expected: String?,newBinding: Boolean,deleted: Boolean,data: JSONObject): String? {
        val metadata = markers()
        if(!newBinding && guard(owner,metadata) != expected.orEmpty()) return null
        val incoming = if(deleted) JSONArray() else data.getJSONObject("payload").getJSONArray("tags")
        val existing = tags(owner,metadata); val keep = mutableListOf<String>(); val commands = mutableListOf<MdbxWriteCommand>()
        if(vault.getCollectionSummary(root) == null) commands += MdbxWriteCommand.CreateProject(root,Mdbx2VaultSessionExecutor.ROOT_PROJECT_TITLE)
        for(index in 0 until incoming.length()) {
            val tag = incoming.getJSONObject(index); val old = existing.firstOrNull { it.labelId !in keep && it.name == tag.getString("name") }
            val id = old?.labelId ?: UUID.randomUUID().toString(); keep += id
            val version = tag.getLong("version").toUInt()
            if(old == null) commands += MdbxWriteCommand.CreateObjectLabel(id,root,tag.getString("name"),tag.getString("payload"),version)
            else if(old.payloadJson != tag.getString("payload") || old.payloadSchemaVersion != version)
                commands += MdbxWriteCommand.UpdateObjectLabel(id,old.name,tag.getString("payload"),version)
        }
        existing.filter { it.labelId !in keep }.forEach { commands += MdbxWriteCommand.DeleteObjectLabel(it.labelId) }
        val content = JSONObject().put("kind",kind).put("owner",owner).put("deleted",deleted).put("data",data).put("tag_ids",JSONArray(keep))
        val marker = metadata.firstOrNull { it.second.getString("owner") == owner }?.first
        if(marker == null) commands += MdbxWriteCommand.CreateObjectLabel(UUID.randomUUID().toString(),root,kind,content.toString(),1u)
        else if(marker.payloadJson != content.toString()) commands += MdbxWriteCommand.UpdateObjectLabel(marker.labelId,kind,content.toString(),1u)
        if(commands.isNotEmpty()) vault.executeWriteOperation(UUID.randomUUID().toString(),"device-root-metadata",commands)
        return guard(owner,markers())
    }
}
