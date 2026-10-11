package takagi.ru.monica.lansync

import org.json.JSONArray
import org.json.JSONObject
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.SecurityManager
import java.security.MessageDigest
import java.util.Date

internal data class DeviceVaultAsset(val id: String, val nativeId: String, val databaseId: Long, val size: Long,
    val local: takagi.ru.monica.attachments.model.Attachment? = null, val image: String? = null, val icon: String? = null)
internal data class DevicePasswordSnapshot(val localId: String, val globalId: String, val scope: String,
    val guard: String, val deleted: Boolean, val data: JSONObject, val assets: List<DeviceVaultAsset> = emptyList()) {
    fun json() = JSONObject().put("local_id", localId).put("global_id", globalId).put("scope", scope)
        .put("guard", guard).put("deleted", deleted).put("data", data)
    override fun toString() = "DevicePasswordSnapshot($localId)"
}

internal object DevicePasswordShape {
    val stringFields = listOf("website", "username", "password_plain", "notes", "app_package_name", "app_name",
        "authenticator_key", "ssh_key_data", "wifi_metadata", "email", "phone", "address_line", "city", "state", "zip_code",
        "country", "credit_card_number_plain", "credit_card_holder", "credit_card_expiry", "credit_card_cvv_plain", "sso_provider")

    fun normalize(payload: JSONObject, title: String, folder: String, type: String = "login"): JSONObject {
        val data = JSONObject().put("title", title).put("folder", folder).put("entry_type", type)
        stringFields.forEach { key ->
            val camel = key.split('_').let { parts -> parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } }
            val value = payload.opt(key) ?: payload.opt(camel) ?: if (key.endsWith("_plain"))
                payload.opt(key.removeSuffix("_plain")) ?: payload.opt(camel.removeSuffix("Plain")) ?:
                    if (key == "credit_card_cvv_plain") payload.opt("creditCardCVV") else null else null
            data.put(key, when {
                value == null || value == JSONObject.NULL -> ""
                value is String -> value
                key == "wifi_metadata" && value is JSONObject -> value.toString()
                else -> throw LanSyncException("invalid_password_payload")
            })
        }
        val custom = payload.optJSONArray("custom_fields") ?: payload.optJSONArray("customFields") ?: JSONArray()
        val fields = (0 until custom.length()).map(custom::getJSONObject).sortedBy { it.optInt("sort_order", it.optInt("sortOrder")) }
        data.put("login_type", payload.optString("login_type", "PASSWORD"))
            .put("is_archived", payload.optBoolean("is_archived"))
            .put("archived_at", if (payload.optBoolean("is_archived")) payload.opt("archived_at") ?: JSONObject.NULL else JSONObject.NULL)
            .put("custom_fields", JSONArray().apply { fields.forEachIndexed { index, field ->
                put(JSONObject().put("title", field.getString("title")).put("value", field.optString("value", ""))
                    .put("is_protected", field.optBoolean("is_protected", field.optBoolean("isProtected"))).put("sort_order", index))
            } })
        val extensions = JSONObject(payload.toString())
        (DeviceVaultShape.localFields + stringFields + listOf("custom_fields", "customFields", "password", "passwordPlain", "password_history", "attachments")).forEach(extensions::remove)
        data.put("extensions", extensions)
        payload.optJSONArray("password_history")?.let { data.put("password_history", it) }
        validate(data)
        return data
    }

    fun validate(data: JSONObject) {
        fun invalid(): Nothing = throw LanSyncException("invalid_password_payload")
        if (data.toString().toByteArray().size > 1024 * 1024) invalid()
        (stringFields + listOf("title", "folder", "entry_type", "login_type")).forEach { if (data.opt(it) !is String) invalid() }
        if (data.getString("entry_type") !in listOf("login", "ssh-key") || data.getString("title").length > 4096 || data.getString("folder").length > 4096) invalid()
        if (data.opt("is_archived") !is Boolean) invalid()
        val archived = data.opt("archived_at")
        if (archived != null && archived != JSONObject.NULL &&
            ((archived !is Long && archived !is Int) || (archived as Number).toLong() !in 0..253402300799999L)) invalid()
        val fields = data.optJSONArray("custom_fields") ?: invalid()
        if (fields.length() > 1024) invalid()
        for (index in 0 until fields.length()) {
            val field = fields.optJSONObject(index) ?: invalid()
            if (field.opt("title") !is String || field.opt("value") !is String || field.opt("is_protected") !is Boolean) invalid()
        }
    }

    fun fromNative(payload: JSONObject, title: String, folder: String, type: String, security: SecurityManager): JSONObject {
        val source = nativePayload(payload)
        if (listOf("password_plain", "passwordPlain", "password").none(source::has)) {
            throw LanSyncException("invalid_password_payload")
        }
        val data = normalize(source, title, folder, type)
        if (payload.optString("monica_password_encoding") != "plaintext-v1") {
            data.put("password_plain", security.decryptDataIfMonicaCiphertext(data.getString("password_plain")))
        }
        return data
    }

    /** Older desktop releases wrapped camelCase password rows in data/entry. */
    fun nativePayload(payload: JSONObject): JSONObject {
        val envelope = payload.optJSONObject("data") ?: return payload
        val entry = envelope.optJSONObject("entry") ?: envelope.takeIf { it.has("id") } ?: return payload
        if (listOf("password", "password_plain", "username", "website").none(entry::has)) throw LanSyncException("invalid_password_payload")
        val flat = JSONObject(entry.toString()).put("kind", "password")
            .put("room_id", entry.optLong("id")).put("is_archived", entry.optBoolean("isArchived"))
            .put("bitwarden_mode", !entry.isNull("bitwardenVaultId")).put("keepass_mode", !entry.isNull("keepassDatabaseId"))
            .put("custom_fields", envelope.optJSONArray("customFields") ?: entry.optJSONArray("customFields") ?: JSONArray())
        payload.opt("monica_password_encoding")?.let { flat.put("monica_password_encoding", it) }
        val login = entry.opt("loginType")
        val loginType = when (login) {
            is Number -> listOf("PASSWORD", "API_KEY", "SSO", "WIFI", "SSH_KEY", "BARCODE").getOrNull(login.toInt())
            is String -> when (login.uppercase()) { "APIKEY" -> "API_KEY"; "SSHKEY" -> "SSH_KEY"; else -> login.uppercase() }
            null, JSONObject.NULL -> "PASSWORD"
            else -> null
        } ?: throw LanSyncException("invalid_password_payload")
        flat.put("login_type", loginType)
        val archived = entry.opt("archivedAt")
        flat.put("archived_at", when (archived) {
            null, JSONObject.NULL -> JSONObject.NULL
            is Number -> archived.toLong()
            is String -> java.time.OffsetDateTime.parse(archived).toInstant().toEpochMilli()
            else -> throw LanSyncException("invalid_password_payload")
        })
        envelope.opt("passwordHistory")?.let { flat.put("password_history", it) }
        envelope.opt("attachments")?.let { flat.put("attachments", it) }
        return flat
    }

    fun fromLocal(entry: PasswordEntry, fields: List<CustomField>, folder: String, security: SecurityManager): JSONObject {
        fun plain(value: String) = if (value.isEmpty()) "" else security.decryptDataIfMonicaCiphertext(value)
        val data = JSONObject().put("website", entry.website).put("username", entry.username)
            .put("password_plain", plain(entry.password)).put("notes", entry.notes)
            .put("app_package_name", entry.appPackageName).put("app_name", entry.appName)
            .put("authenticator_key", plain(entry.authenticatorKey)).put("ssh_key_data", entry.sshKeyData)
            .put("wifi_metadata", entry.wifiMetadata).put("email", entry.email).put("phone", entry.phone)
            .put("address_line", entry.addressLine).put("city", entry.city).put("state", entry.state)
            .put("zip_code", entry.zipCode).put("country", entry.country)
            .put("credit_card_number_plain", plain(entry.creditCardNumber)).put("credit_card_holder", entry.creditCardHolder)
            .put("credit_card_expiry", entry.creditCardExpiry).put("credit_card_cvv_plain", plain(entry.creditCardCVV))
            .put("sso_provider", entry.ssoProvider).put("login_type", entry.loginType).put("is_archived", entry.isArchived)
            .put("passkey_bindings",entry.passkeyBindings).put("custom_icon_type",entry.customIconType)
            .put("custom_icon_value",entry.customIconValue ?: JSONObject.NULL).put("custom_icon_updated_at",entry.customIconUpdatedAt)
            .put("archived_at", entry.archivedAt?.time ?: JSONObject.NULL)
        val custom = JSONArray()
        fields.sortedWith(compareBy<CustomField> { it.sortOrder }.thenBy { it.id }).forEach { field ->
            custom.put(JSONObject().put("title", field.title).put("value", if (field.isProtected) plain(field.value) else field.value)
                .put("is_protected", field.isProtected).put("sort_order", field.sortOrder))
        }
        data.put("custom_fields", custom)
        return normalize(data, entry.title, folder)
    }

    fun guard(entry: PasswordEntry, data: JSONObject): String = MessageDigest.getInstance("SHA-256")
        .digest((entry.updatedAt.time.toString() + ":" + entry.isDeleted + ":" + data.toString()).toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun applyLocal(data: JSONObject, previous: PasswordEntry?, deleted: Boolean, categoryId: Long?, security: SecurityManager): PasswordEntry {
        val old = previous ?: PasswordEntry(title = "", website = "", username = "", password = "")
        if (deleted && !data.optBoolean("trashed")) return old.copy(isDeleted = true, deletedAt = old.deletedAt ?: Date(), isArchived = false, archivedAt = null, updatedAt = Date())
        validate(data)
        fun secret(key: String) = data.optString(key).let { if (it.isEmpty()) "" else security.encryptDataLegacyCompat(it) }
        return old.copy(title = data.getString("title"), website = data.optString("website"), username = data.optString("username"),
            password = secret("password_plain"), notes = data.optString("notes"), appPackageName = data.optString("app_package_name"),
            appName = data.optString("app_name"), authenticatorKey = secret("authenticator_key"), sshKeyData = data.optString("ssh_key_data"),
            passkeyBindings = data.optJSONObject("extensions")?.optString("passkey_bindings") ?: old.passkeyBindings,
            customIconType = data.optJSONObject("extensions")?.optString("custom_icon_type",old.customIconType) ?: old.customIconType,
            customIconValue = data.optJSONObject("extensions")?.optString("custom_icon_value")?.takeUnless { it.isBlank() || it == "null" },
            customIconUpdatedAt = data.optJSONObject("extensions")?.optLong("custom_icon_updated_at",old.customIconUpdatedAt) ?: old.customIconUpdatedAt,
            wifiMetadata = data.optString("wifi_metadata"), email = data.optString("email"), phone = data.optString("phone"),
            addressLine = data.optString("address_line"), city = data.optString("city"), state = data.optString("state"),
            zipCode = data.optString("zip_code"), country = data.optString("country"), creditCardNumber = secret("credit_card_number_plain"),
            creditCardHolder = data.optString("credit_card_holder"), creditCardExpiry = data.optString("credit_card_expiry"),
            creditCardCVV = secret("credit_card_cvv_plain"), ssoProvider = data.optString("sso_provider"), loginType = data.optString("login_type", "PASSWORD"),
            isArchived = data.optBoolean("is_archived"), archivedAt = if (!data.isNull("archived_at")) Date(data.getLong("archived_at")) else null,
            isFavorite = data.optJSONObject("extensions")?.optBoolean("is_favorite") ?: old.isFavorite,
            sortOrder = data.optJSONObject("extensions")?.optInt("sort_order") ?: old.sortOrder,
            isGroupCover = data.optJSONObject("extensions")?.optBoolean("is_group_cover") ?: old.isGroupCover,
            passwordGroupId = data.optJSONObject("extensions")?.optString("password_group_id")?.takeUnless { it == "null" || it.isBlank() },
            createdAt = Date(data.optJSONObject("extensions")?.optLong("created_at", old.createdAt.time) ?: old.createdAt.time),
            categoryId = categoryId, isDeleted = deleted, deletedAt = if(deleted) old.deletedAt ?: Date() else null, updatedAt = Date())
    }

    fun applyNative(data: JSONObject, previous: String?, id: String, folder: String?): JSONObject {
        validate(data)
        val payload = if (previous == null) JSONObject() else nativePayload(JSONObject(previous))
        data.optJSONObject("extensions")?.let { extensions -> extensions.keys().forEach { key ->
            if (key !in DeviceVaultShape.localFields) payload.put(key, extensions.get(key))
        } }
        data.optJSONArray("password_history")?.let { payload.put("password_history", it) }
        stringFields.forEach { field ->
            payload.put(field, data.getString(field))
            val camel = field.split('_').let { parts -> parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar(Char::uppercaseChar) } }
            if (camel != field) payload.remove(camel)
            if (field.endsWith("_plain")) { payload.remove(field.removeSuffix("_plain")); payload.remove(camel.removeSuffix("Plain")) }
        }
        listOf("customFields", "loginType", "isArchived", "archivedAt", "creditCardCVV").forEach(payload::remove)
        payload.put("kind", "password").put("monica_password_encoding", "plaintext-v1").put("monica_entry_id", id)
            .put("mdbx_folder_id", folder ?: JSONObject.NULL).put("login_type", data.optString("login_type", "PASSWORD"))
            .put("is_archived", data.optBoolean("is_archived")).put("archived_at", data.opt("archived_at") ?: JSONObject.NULL)
            .put("custom_fields", data.optJSONArray("custom_fields") ?: JSONArray())
        return payload
    }
}
