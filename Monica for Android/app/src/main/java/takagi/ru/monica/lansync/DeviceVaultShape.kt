package takagi.ru.monica.lansync

import org.json.JSONObject
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.security.SecurityManager

internal object DeviceVaultShape {
    val types = setOf("login", "ssh-key", "totp", "note", "card", "document-ref", "identity", "billing-address", "payment-account", "passkey", "api-token", "steam-mafile", "folder", "vault-metadata")
    val localFields = listOf("room_id", "roomId", "id", "monica_entry_id", "category_id", "categoryId", "mdbx_folder_id", "mdbxFolderId",
        "mdbx_database_id", "mdbxDatabaseId", "bitwarden_mode", "keepass_mode", "bitwarden_vault_id", "bitwarden_cipher_id", "bitwarden_folder_id",
        "bitwarden_revision_date", "bitwarden_local_modified", "bitwardenVaultId", "bitwardenCipherId", "keepassDatabaseId", "keepassGroupPath", "storagePath", "device_sync_assets", "device_sync_images", "monica_password_encoding", "device_sync_pending_relations", "device_sync_key_projection")

    fun normalize(source: JSONObject, title: String, folder: String, type: String): JSONObject {
        if (type in listOf("login", "ssh-key")) return DevicePasswordShape.normalize(source, title, folder, type)
        val envelope = source.optJSONObject("data")
        val payload = JSONObject((envelope?.optJSONObject("item") ?: envelope ?: source).toString())
        if (source.has("data")) {
            payload.put("kind", when(type) { "card" -> "bank_card"; "document-ref", "identity" -> "document"; "billing-address" -> "billing_address"; "payment-account" -> "payment_account"; else -> type })
            listOf("itemData" to "item_data", "imagePaths" to "image_paths", "sortOrder" to "sort_order", "isFavorite" to "is_favorite").forEach { (camel, snake) ->
                if (payload.has(camel)) { payload.put(snake, payload.get(camel)); payload.remove(camel) }
            }
            listOf("createdAt" to "created_at", "updatedAt" to "updated_at").forEach { (camel, snake) ->
                (payload.opt(camel) as? String)?.let { payload.put(snake, java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli()); payload.remove(camel) }
            }
        }
        localFields.forEach(payload::remove)
        payload.remove("attachments")
        return JSONObject().put("title", title).put("folder", folder).put("entry_type", type).put("payload", payload).also(::validate)
    }

    fun validate(data: JSONObject) {
        val password = data.optString("entry_type") in listOf("login", "ssh-key")
        if (password) DevicePasswordShape.validate(data)
        else if (data.toString().toByteArray().size > 1024 * 1024 || data.optString("entry_type") !in types ||
            data.opt("title") !is String || data.getString("title").length > 4096 || data.opt("folder") !is String ||
            data.getString("folder").length > 4096 || data.optJSONObject("payload") == null) throw LanSyncException("invalid_vault_payload")
        if(!password) {
            val payload = data.getJSONObject("payload"); val type = data.getString("entry_type")
            if(payload.length() == 0) throw LanSyncException("invalid_vault_payload")
            if(type in listOf("note","totp","card","document-ref","billing-address","payment-account")) text(payload,"item_data")
            if(type == "passkey") DevicePasskeyShape.validateMetadata(payload)
            if(type == "steam-mafile") DeviceSteamSyncStore.parse(data)
            if(type in listOf("folder","vault-metadata")) {
                if(type == "folder") path(payload.opt("path"))
                else if(payload.optJSONArray("tags") == null) throw LanSyncException("invalid_vault_payload")
                objects(payload,"tags").forEach { tag -> text(tag,"name"); text(tag,"payload")
                    if(tag.opt("version") !is Number || tag.getLong("version") !in 0..UInt.MAX_VALUE.toLong()) throw LanSyncException("invalid_vault_payload") }
            }
        }
        if(data.has("folder_path")) path(data.get("folder_path"),true)
        objects(data,"assets").forEach { asset ->
            listOf("id","file_name","media_type").forEach { text(asset,it) }
            if(!asset.getString("id").matches(Regex("[0-9a-fA-F]{64}")) || asset.opt("size") !is Number ||
                asset.getLong("size") !in 0..64L*1024*1024) throw LanSyncException("invalid_asset")
        }
        listOf("labels","relations").forEach { name -> objects(data,name).forEach { item ->
            text(item,if(name == "labels") "name" else "target"); text(item,"payload")
            if(name == "relations") text(item,"kind")
            if(item.opt("version") !is Number || item.getLong("version") !in 0..UInt.MAX_VALUE.toLong()) throw LanSyncException("invalid_vault_payload")
        } }
        objects(data,"password_history").forEach { text(it,"password") }
    }

    private fun text(item: JSONObject,name: String) { if(item.opt(name) !is String) throw LanSyncException("invalid_vault_payload") }
    private fun objects(data: JSONObject,name: String): List<JSONObject> {
        if(!data.has(name)) return emptyList()
        val list = data.optJSONArray(name) ?: throw LanSyncException("invalid_vault_payload")
        if(list.length() > 8192) throw LanSyncException("invalid_vault_payload")
        return (0 until list.length()).map { list.optJSONObject(it) ?: throw LanSyncException("invalid_vault_payload") }
    }
    private fun path(value: Any?,allowEmpty: Boolean = false) {
        val list = value as? org.json.JSONArray ?: throw LanSyncException("invalid_folder")
        if(list.length() > 128 || (!allowEmpty && list.length() == 0)) throw LanSyncException("invalid_folder")
        val seen = mutableSetOf<String>()
        for(index in 0 until list.length()) {
            val item = list.getJSONObject(index); text(item,"id"); text(item,"title")
            val id = item.getString("id")
            if(runCatching { java.util.UUID.fromString(id) }.isFailure || !seen.add(id) || item.getString("title").length > 4096) throw LanSyncException("invalid_folder")
            if(!item.isNull("parent") && (runCatching { java.util.UUID.fromString(item.getString("parent")) }.isFailure || item.getString("parent") == id)) throw LanSyncException("invalid_folder")
        }
    }

    fun remapAssetPath(data: JSONObject,from: String,to: String): JSONObject {
        val note = data.optString("entry_type") == "note" || data.optString("kind") == "note"
        fun visit(node: Any,key: String? = null) {
            if(node is JSONObject) node.keys().asSequence().toList().forEach { name ->
                val value = node.get(name)
                if(value is String) when(name) {
                    "image_paths", "imagePaths", "photoPaths" -> if(value == from) node.put(name,to) else if(value.startsWith("[")) {
                        val paths = org.json.JSONArray(value); visit(paths,name); node.put(name,paths.toString())
                    }
                    "storagePath", "storage_path", "localPath", "local_path", "imagePath", "custom_icon_value", "customIconValue" -> if(value == from) node.put(name,to)
                    "item_data" -> if(value.startsWith("{")) { val payload = JSONObject(value); visit(payload); node.put(name,payload.toString()) }
                    "content" -> if(note) node.put(name,Regex("(!\\[[^\\]]*\\]\\()([^\\s)]+)(\\))").replace(value) { match ->
                        if(match.groupValues[2] == from) match.groupValues[1]+to+match.groupValues[3] else match.value })
                } else if(value is JSONObject || value is org.json.JSONArray) visit(value,name)
            }
            else if(node is org.json.JSONArray) for(index in 0 until node.length()) {
                val value = node.get(index)
                if(key in listOf("image_paths","imagePaths","photoPaths") && value is String) { if(value == from) node.put(index,to) }
                else if(value is JSONObject || value is org.json.JSONArray) visit(value,key)
            }
        }
        visit(data); return data
    }

    fun referenceIds(data: JSONObject): List<String> {
        val result = linkedSetOf<String>(); remapReferences(data) { result += it; it }; return result.toList()
    }

    fun readIcon(context: android.content.Context,value: String): ByteArray {
        val file = takagi.ru.monica.ui.icons.PasswordCustomIconStore.resolveIconFile(context,value) ?: throw LanSyncException("asset_unavailable")
        if(file.length() > 64L*1024*1024) throw LanSyncException("asset_limit")
        return file.readBytes()
    }
    fun icons(data: JSONObject): List<String> {
        val ext = data.optJSONObject("extensions")
        val details = data.optJSONObject("payload")?.optString("item_data")?.takeIf { it.startsWith("{") }?.let { JSONObject(it) }
        return listOf(ext?.takeIf { it.optString("custom_icon_type") == "UPLOADED" }?.optString("custom_icon_value"),
            details?.takeIf { it.optString("customIconType") == "UPLOADED" }?.optString("customIconValue"))
            .filterNotNull().filter { it.isNotBlank() && it != "null" && !it.startsWith("device-asset:") && !it.startsWith("mdbx:") }.distinct()
    }
    fun saveIcon(context: android.content.Context,bytes: ByteArray,hash: String): String {
        val name = "sync-$hash.png"
        val file = android.util.AtomicFile(java.io.File(takagi.ru.monica.ui.icons.PasswordCustomIconStore.getIconDir(context),name))
        val stream = file.startWrite()
        try { stream.write(bytes); file.finishWrite(stream) } catch(error: Throwable) { file.failWrite(stream); throw error }
        return name
    }

    fun localItemData(data: JSONObject): String {
        val payload = data.getJSONObject("payload")
        if(data.getString("entry_type") != "steam-mafile") {
            if(payload.opt("item_data") !is String) throw LanSyncException("unsupported_vault_content")
            return payload.getString("item_data")
        }
        val maFile = payload.optString("mafile_json").takeIf { it.isNotBlank() } ?: throw LanSyncException("invalid_vault_payload")
        val steam = takagi.ru.monica.steam.importer.SteamMaFileParser().parse(maFile,allowMissingSteamId=true)
        return kotlinx.serialization.json.Json.encodeToString(takagi.ru.monica.data.model.TotpData.serializer(),
            takagi.ru.monica.data.model.TotpData(secret="steam://${steam.sharedSecret}",issuer="Steam",accountName=steam.accountName,
                period=30,digits=5,otpType=takagi.ru.monica.data.model.OtpType.STEAM,steamSharedSecretBase64=steam.sharedSecret,
                steamIdentitySecret=steam.identitySecret.orEmpty(),steamDeviceId=steam.deviceId,steamRevocationCode=steam.revocationCode.orEmpty(),
                steamTokenGid=steam.tokenGid.orEmpty(),steamRawJson=steam.rawJson))
    }
    fun remapReferences(data: JSONObject,map: (String) -> String) {
        fun visit(node: Any,key: String? = null) {
            if(node is JSONObject) node.keys().asSequence().toList().forEach { name ->
                val value = node.get(name)
                if((name in listOf("bound_password_entry_id","bound_note_entry_id","sso_ref_entry_id") ||
                    (key in listOf("folder_path","path") && name in listOf("id","parent")) || (key == "relations" && name == "target")) && value is String && value.isNotBlank()) node.put(name,map(value))
                else if(value is JSONObject || value is org.json.JSONArray) visit(value,name)
            }
            else if(node is org.json.JSONArray) for(index in 0 until node.length()) {
                val value = node.get(index); if(value is JSONObject || value is org.json.JSONArray) visit(value,key)
            }
        }
        visit(data)
    }

    fun applyNative(data: JSONObject, previous: String?, id: String, folder: String?): JSONObject {
        validate(data)
        if (data.getString("entry_type") in listOf("login", "ssh-key")) return DevicePasswordShape.applyNative(data, previous, id, folder)
        return JSONObject(data.getJSONObject("payload").toString()).apply {
            localFields.forEach(::remove)
            put("monica_entry_id", id); put("mdbx_folder_id", folder ?: JSONObject.NULL)
        }
    }

    fun fromLocal(item: SecureItem, folder: String, security: SecurityManager): JSONObject {
        val type = when(item.itemType.name) { "BANK_CARD" -> "card"; "DOCUMENT" -> "document-ref"; "BILLING_ADDRESS" -> "billing-address"; "PAYMENT_ACCOUNT" -> "payment-account"; else -> item.itemType.name.lowercase() }
        val payload = JSONObject().put("kind", item.itemType.name.lowercase()).put("notes", item.notes)
            .put("item_data", security.decryptDataIfMonicaCiphertext(item.itemData)).put("image_paths", item.imagePaths)
            .put("sort_order", item.sortOrder).put("is_favorite", item.isFavorite).put("created_at", item.createdAt.time)
        return normalize(payload, item.title, folder, type).put("trashed", item.isDeleted)
    }
}
