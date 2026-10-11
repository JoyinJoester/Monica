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
        val p = JSONObject().put("kind", "passkey").put("credential_id", entry.credentialId).put("rp_id", entry.rpId).put("rp_name", entry.rpName)
            .put("user_id", entry.userId).put("user_name", entry.userName).put("user_display_name", entry.userDisplayName)
            .put("public_key_algorithm", entry.publicKeyAlgorithm).put("public_key", entry.publicKey).put("private_key_alias", entry.privateKeyAlias)
            .put("created_at", entry.createdAt).put("last_used_at", entry.lastUsedAt)
            .put("use_count", entry.useCount).put("icon_url", entry.iconUrl ?: JSONObject.NULL).put("is_discoverable", entry.isDiscoverable)
            .put("is_user_verification_required", entry.isUserVerificationRequired).put("transports", entry.transports).put("aaguid", entry.aaguid)
            .put("sign_count", 0).put("notes", entry.notes).put("passkey_mode", entry.passkeyMode)
            .put("backup_eligible", entry.backupEligible ?: JSONObject.NULL).put("backup_state", entry.backupState ?: JSONObject.NULL)
        preparePayload(context, p)
        return DeviceVaultShape.normalize(p, entry.rpName, "", "passkey")
            .put("folder_path",org.json.JSONArray()).put("labels",org.json.JSONArray()).put("relations",org.json.JSONArray()).put("assets",org.json.JSONArray()).put("trashed",false).also(::verify)
    }

    /** Derive a missing cache from the original key, without editing the stored credential. */
    fun preparePayload(context: Context, payload: JSONObject) {
        val stored = payload.optString("private_key_alias")
        val resolved = try { PasskeyPrivateKeyStore.resolve(context, stored) }
        catch (_: Exception) { throw LanSyncException("passkey_key_unavailable", "passkey_protected_key_read_failed") }
        if (PasskeyPrivateKeyStore.isProtectedReference(stored) && resolved.isNullOrBlank())
            throw LanSyncException("passkey_key_unavailable", "passkey_protected_key_missing")
        val key = PasskeyPrivateKeySupport.normalizeForBitwardenUpload(resolved).orEmpty()
        if (key.isEmpty() && PasskeyPrivateKeyStore.isProtectedReference(stored)) invalid("protected_key_invalid")
        if (key.isEmpty() && !resolved.isNullOrBlank() && !PasskeyPrivateKeySupport.hasUsablePrivateKey(resolved))
            throw LanSyncException("passkey_key_unavailable", "passkey_alias_unavailable")
        payload.put("private_key_alias", key).put("key_status", if (key.isEmpty()) "reference" else "portable")
        if (key.isNotEmpty() && (payload.optString("public_key").isBlank() || !payload.has("public_key_algorithm"))) {
            val algorithm = (payload.opt("public_key_algorithm") as? Number)?.toInt()
            val bytes = decodeKey(key, "private_key_encoding")
            val material = try { CxfPasskeyMaterial.decode(bytes, algorithm) }
                finally { bytes.fill(0) }
            if (material == null || (algorithm != null && material.algorithm != algorithm)) invalid("private_key_invalid")
            // Generic RSA PKCS#8 does not distinguish RS256 from PS256.
            if (!payload.has("public_key_algorithm") && material.algorithm == -257) invalid("algorithm_missing")
            if (payload.optString("public_key").isBlank()) payload.put("public_key", material.cosePublicKey)
            if (!payload.has("public_key_algorithm")) payload.put("public_key_algorithm", material.algorithm)
        }
    }

    fun withRecord(source: String, displayName: String = "", block: () -> JSONObject): JSONObject = try { block() }
        catch (failure: LanSyncException) {
            if (failure.code !in listOf("invalid_passkey_material", "passkey_key_unavailable", "passkey_conflict")) throw failure
            val reason = failure.operation.removePrefix("passkey_").ifEmpty { "validation_failed" }
            throw LanSyncException(failure.code, "passkey_${source}_$reason".take(96),
                displayName.filterNot(Char::isISOControl).take(128))
        }

    fun validateMetadata(payload: JSONObject) {
        for (field in listOf("credential_id", "rp_id", "user_id", "public_key", "private_key_alias", "key_status"))
            if (payload.opt(field) !is String) invalid("${field}_missing")
        for (field in listOf("credential_id", "rp_id", "public_key"))
            if (payload.getString(field).isBlank()) invalid("${field}_missing")
        val number = payload.opt("public_key_algorithm") as? Number ?: invalid("algorithm_missing")
        if (number.toDouble() != number.toInt().toDouble()) invalid("algorithm_invalid")
        if (payload.getInt("public_key_algorithm") !in listOf(-7, -257, -37, -8)) invalid("algorithm_unsupported")
        if (payload.getString("key_status") !in listOf("portable", "reference")) invalid("key_status_invalid")
        val eligibility = backupFlag(payload, "backup_eligible")
        val state = backupFlag(payload, "backup_state")
        if (eligibility == false && state == true) invalid("backup_flags_invalid")
    }

    private fun invalid(reason: String): Nothing = throw LanSyncException("invalid_passkey_material", "passkey_$reason")
    private fun backupFlag(payload: JSONObject, name: String): Boolean? {
        val value = payload.opt(name)
        if (value == null || value === JSONObject.NULL) return null
        if (value !is Boolean) invalid("backup_flags_invalid")
        return value
    }

    fun retainBackupFlags(incoming: JSONObject, previous: JSONObject) {
        val eligibility = try { takagi.ru.monica.passkey.PasskeyBackupFlags.mergeEligibility(
            backupFlag(previous, "backup_eligible"), backupFlag(incoming, "backup_eligible")) }
            catch (_: IllegalArgumentException) { throw LanSyncException("passkey_conflict", "passkey_backup_eligibility_changed") }
        val state = backupFlag(incoming, "backup_state") ?: backupFlag(previous, "backup_state")
        if (eligibility == false && state == true) invalid("backup_flags_invalid")
        eligibility?.let { incoming.put("backup_eligible", it) }
        state?.let { incoming.put("backup_state", it) }
    }
    private fun decodeKey(value: String, reason: String): ByteArray =
        try { Base64.getDecoder().decode(value) } catch (_: IllegalArgumentException) { invalid(reason) }

    fun matchesStoredPublicKey(context: Context, stored: PasskeyEntry, payload: JSONObject): Boolean {
        val candidate = JSONObject(payload.toString())
        if (!candidate.has("public_key_algorithm")) candidate.put("public_key_algorithm", stored.publicKeyAlgorithm)
        if (candidate.optString("public_key").isBlank() && candidate.optString("private_key_alias").isNotBlank())
            preparePayload(context, candidate)
        val algorithm = candidate.optInt("public_key_algorithm")
        if (stored.publicKeyAlgorithm != algorithm) return false
        val publicKey = if (stored.publicKey.isNotBlank()) stored.publicKey else {
            val cache = JSONObject().put("public_key", "").put("public_key_algorithm", stored.publicKeyAlgorithm)
                .put("private_key_alias", stored.privateKeyAlias)
            preparePayload(context, cache)
            cache.optString("public_key")
        }
        return CxfPasskeyMaterial.matchesPublicKeys(publicKey, candidate.optString("public_key"), algorithm)
    }

    fun retainNativeKey(context: Context, incoming: JSONObject, previous: JSONObject) {
        val algorithm = incoming.getInt("public_key_algorithm")
        val old = JSONObject(previous.toString())
        if (!old.has("public_key_algorithm")) old.put("public_key_algorithm", algorithm)
        val oldCredential = old.optString("credential_id")
        val oldRp = old.optString("rp_id")
        if ((oldCredential.isNotBlank() && takagi.ru.monica.passkey.PasskeyCredentialIdCodec.normalize(oldCredential) !=
                takagi.ru.monica.passkey.PasskeyCredentialIdCodec.normalize(incoming.getString("credential_id"))) ||
            (oldRp.isNotBlank() && !oldRp.equals(incoming.getString("rp_id"), ignoreCase = true)) ||
            old.getInt("public_key_algorithm") != algorithm)
            throw LanSyncException("passkey_conflict", "passkey_key_identity_changed")
        val originalKey = old.optString("private_key_alias")
        if (old.optString("public_key").isBlank()) preparePayload(context, old)
        if (old.optString("public_key").isNotBlank() && !CxfPasskeyMaterial.matchesPublicKeys(
                old.getString("public_key"), incoming.getString("public_key"), algorithm))
            throw LanSyncException("passkey_conflict", "passkey_public_key_changed")
        if (oldCredential.isNotBlank() || originalKey.isNotEmpty() || old.optString("public_key").isNotBlank())
            retainBackupFlags(incoming, previous)
        if (incoming.getString("private_key_alias").isNotEmpty() || originalKey.isEmpty()) return
        preparePayload(context, old)
        val normalized = old.optString("private_key_alias")
        if (normalized.isNotEmpty()) {
            val check = JSONObject(incoming.toString()).put("private_key_alias", normalized).put("key_status", "portable")
            verify(DeviceVaultShape.normalize(check, "Passkey", "", "passkey"))
        } else if (old.optString("public_key").isBlank())
            throw LanSyncException("passkey_conflict", "passkey_public_key_unverifiable")
        // The update carried metadata only; preserve the local key and backup status.
        incoming.put("private_key_alias", originalKey)
    }

    fun verify(data: JSONObject) {
        DeviceVaultShape.validate(data)
        val p = data.getJSONObject("payload")
        val key = p.optString("private_key_alias")
        if(key.isEmpty()) { if(p.optString("key_status") != "reference") throw LanSyncException("passkey_key_unavailable", "passkey_private_key_missing"); return }
        val bytes = decodeKey(key, "private_key_encoding")
        val derived = try { CxfPasskeyMaterial.decode(bytes, p.getInt("public_key_algorithm")) }
            finally { bytes.fill(0) }
        if (derived == null) invalid("private_key_invalid")
        if (derived.algorithm != p.getInt("public_key_algorithm")) invalid("algorithm_mismatch")
        if (!CxfPasskeyMaterial.matchesPublicKey(derived, decodeKey(p.getString("public_key"), "public_key_encoding")))
            invalid("public_key_mismatch")
    }
    fun import(context: Context, data: JSONObject, id: Long): PasskeyEntry {
        verify(data)
        val p = data.getJSONObject("payload")
        return PasskeyPrivateKeyStore.protectPasskey(context, PasskeyEntry(id = id, credentialId = p.getString("credential_id"),
            rpId = p.getString("rp_id"), rpName = p.optString("rp_name"), userId = p.getString("user_id"), userName = p.optString("user_name"),
            userDisplayName = p.optString("user_display_name"), publicKeyAlgorithm = p.getInt("public_key_algorithm"), publicKey = p.getString("public_key"),
            backupEligible = backupFlag(p, "backup_eligible"), backupState = backupFlag(p, "backup_state"),
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
