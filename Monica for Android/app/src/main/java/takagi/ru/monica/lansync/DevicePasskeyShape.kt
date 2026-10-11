package takagi.ru.monica.lansync

import android.content.Context
import org.json.JSONObject
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.passkey.PasskeyPrivateKeyStore
import takagi.ru.monica.passkey.PasskeyPrivateKeySupport
import takagi.ru.monica.credentialexchange.CxfPasskeyMaterial
import java.security.MessageDigest
import java.util.Base64

internal object DevicePasskeyShape {
    fun identity(entry: PasskeyEntry): String {
        val value = "passkey:${entry.rpId}:${takagi.ru.monica.passkey.PasskeyCredentialIdCodec.normalize(entry.credentialId)}"
        val hex = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        return "${hex.take(8)}-${hex.substring(8,12)}-${hex.substring(12,16)}-${hex.substring(16,20)}-${hex.substring(20,32)}"
    }
    fun export(context: Context, entry: PasskeyEntry): JSONObject {
        val key = PasskeyPrivateKeySupport.normalizeForBitwardenUpload(PasskeyPrivateKeyStore.resolve(context, entry.privateKeyAlias)).orEmpty()
        val p = JSONObject().put("kind", "passkey").put("credential_id", entry.credentialId).put("rp_id", entry.rpId).put("rp_name", entry.rpName)
            .put("user_id", entry.userId).put("user_name", entry.userName).put("user_display_name", entry.userDisplayName)
            .put("public_key_algorithm", entry.publicKeyAlgorithm).put("public_key", entry.publicKey).put("private_key_alias", key)
            .put("key_status", if(key.isEmpty()) "reference" else "portable").put("created_at", entry.createdAt).put("last_used_at", entry.lastUsedAt)
            .put("use_count", entry.useCount).put("icon_url", entry.iconUrl ?: JSONObject.NULL).put("is_discoverable", entry.isDiscoverable)
            .put("is_user_verification_required", entry.isUserVerificationRequired).put("transports", entry.transports).put("aaguid", entry.aaguid)
            .put("sign_count", 0).put("notes", entry.notes).put("passkey_mode", entry.passkeyMode)
        return DeviceVaultShape.normalize(p, entry.rpName, "", "passkey")
            .put("folder_path",org.json.JSONArray()).put("labels",org.json.JSONArray()).put("relations",org.json.JSONArray()).put("assets",org.json.JSONArray()).put("trashed",false).also(::verify)
    }
    fun verify(data: JSONObject) {
        DeviceVaultShape.validate(data)
        val p = data.getJSONObject("payload")
        val key = p.optString("private_key_alias")
        if(key.isEmpty()) { if(p.optString("key_status") != "reference") throw LanSyncException("passkey_key_unavailable"); return }
        val derived = CxfPasskeyMaterial.decode(Base64.getDecoder().decode(key), p.getInt("public_key_algorithm"))
            ?: throw LanSyncException("invalid_passkey_material")
        if(derived.algorithm != p.getInt("public_key_algorithm") || !CxfPasskeyMaterial.matchesPublicKey(derived,
                Base64.getDecoder().decode(p.getString("public_key")))) throw LanSyncException("invalid_passkey_material")
    }
    fun import(context: Context, data: JSONObject, id: Long): PasskeyEntry {
        verify(data)
        val p = data.getJSONObject("payload")
        return PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(id = id, credentialId = p.getString("credential_id"),
            rpId = p.getString("rp_id"), rpName = p.optString("rp_name"), userId = p.getString("user_id"), userName = p.optString("user_name"),
            userDisplayName = p.optString("user_display_name"), publicKeyAlgorithm = p.getInt("public_key_algorithm"), publicKey = p.getString("public_key"),
            privateKeyAlias = p.optString("private_key_alias"), createdAt = p.optLong("created_at"), lastUsedAt = p.optLong("last_used_at"),
            useCount = p.optInt("use_count"), iconUrl = p.optString("icon_url").takeUnless { it == "null" || it.isBlank() },
            isDiscoverable = p.optBoolean("is_discoverable", true), isUserVerificationRequired = p.optBoolean("is_user_verification_required", true),
            transports = p.optString("transports", "internal"), aaguid = p.optString("aaguid"), signCount = 0, isBackedUp = p.optString("key_status") == "portable",
            notes = p.optString("notes"), passkeyMode = p.optString("passkey_mode", "LEGACY"), syncStatus = if(p.optString("key_status") == "reference") "REFERENCE" else "SYNCED"))
    }
    fun guard(data: JSONObject) = MessageDigest.getInstance("SHA-256").digest(canonical(data).toByteArray()).joinToString("") { "%02x".format(it) }
    private fun canonical(value: Any?): String = when(value) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> value.keys().asSequence().toList().sorted().joinToString(",", "{", "}") { JSONObject.quote(it) + ":" + canonical(value.get(it)) }
        is org.json.JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.get(it)) }
        is String -> JSONObject.quote(value)
        is Number -> JSONObject.numberToString(value)
        is Boolean -> value.toString()
        else -> throw LanSyncException("invalid_vault_payload")
    }
}
