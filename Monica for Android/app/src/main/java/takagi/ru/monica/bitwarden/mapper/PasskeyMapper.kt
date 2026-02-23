package takagi.ru.monica.bitwarden.mapper

import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.passkey.PasskeyCredentialIdCodec
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import android.util.Base64

/**
 * Passkey 数据映射器
 * 
 * Monica PasskeyEntry <-> Bitwarden Login (Type 1)
 * 
 * 同步策略：
 * - Monica → Bitwarden: 优先同步完整 passkey（包含私钥材料）
 * - Bitwarden → Monica: 支持导入可用 passkey；缺少私钥时降级为引用记录
 */
class PasskeyMapper : BitwardenMapper<PasskeyEntry> {

    override fun toCreateRequest(item: PasskeyEntry, folderId: String?): CipherCreateRequest {
        val bitwardenCredentialId = PasskeyCredentialIdCodec
            .toBitwardenCredentialId(item.credentialId)
            ?.takeIf { it.isNotBlank() }
        val userHandle = item.userId
            .takeIf { it.isNotBlank() }
            ?: item.userName
                .takeIf { it.isNotBlank() }
            ?: item.userDisplayName
                .takeIf { it.isNotBlank() }
            ?: bitwardenCredentialId
            ?: item.credentialId
        val counter = item.signCount
            .coerceIn(0L, Int.MAX_VALUE.toLong())
            .toString()

        val fido2Credentials = if (canUseAsBitwardenKeyValue(item.privateKeyAlias) && item.rpId.isNotBlank()) {
            listOf(
                CipherLoginFido2CredentialApiData(
                    credentialId = bitwardenCredentialId,
                    keyType = "public-key",
                    keyAlgorithm = algorithmToBitwarden(item.publicKeyAlgorithm),
                    keyCurve = "P-256",
                    keyValue = item.privateKeyAlias,
                    rpId = item.rpId,
                    rpName = item.rpName.takeIf { it.isNotBlank() },
                    counter = counter,
                    userHandle = userHandle,
                    userName = item.userName.takeIf { it.isNotBlank() },
                    userDisplayName = item.userDisplayName.takeIf { it.isNotBlank() },
                    discoverable = item.isDiscoverable.toString(),
                    creationDate = java.time.Instant.ofEpochMilli(item.createdAt).toString()
                )
            )
        } else {
            null
        }

        return CipherCreateRequest(
            type = 1, // Login
            name = "${item.rpName} [Passkey]",
            notes = buildPasskeyNotes(item),
            folderId = folderId,
            favorite = false,
            login = CipherLoginApiData(
                uris = item.rpId.takeIf { it.isNotBlank() }?.let {
                    listOf(
                        CipherUriApiData(uri = "https://${it}"),
                        CipherUriApiData(uri = it)
                    )
                },
                username = item.userName.takeIf { it.isNotBlank() } ?: item.userDisplayName,
                fido2Credentials = fido2Credentials,
            )
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): PasskeyEntry {
        val login = cipher.login
        val fido2 = login?.fido2Credentials?.firstOrNull()
        val rpId = extractRpIdFromUris(login?.uris)
        val metadata = parsePasskeyMetadata(cipher.notes)

        val credentialId = fido2?.credentialId
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.credentialId
            ?: buildReferenceCredentialId(cipher.id)
        val keyValue = fido2?.keyValue.orEmpty()
        val userName = fido2?.userName
            ?.takeIf { it.isNotBlank() }
            ?: login?.username
            ?: ""
        val userDisplayName = fido2?.userDisplayName
            ?.takeIf { it.isNotBlank() }
            ?: metadata?.userDisplayName
            ?: userName
        val resolvedRpId = fido2?.rpId?.takeIf { it.isNotBlank() } ?: (rpId ?: "")
        val resolvedRpName = fido2?.rpName?.takeIf { it.isNotBlank() }
            ?: cipher.name?.removeSuffix(" [Passkey]")
            ?: resolvedRpId

        return PasskeyEntry(
            credentialId = credentialId,
            rpId = resolvedRpId,
            rpName = resolvedRpName,
            userId = fido2?.userHandle ?: metadata?.userId ?: "",
            userName = userName,
            userDisplayName = userDisplayName,
            publicKeyAlgorithm = parseAlgorithm(
                fido2?.keyAlgorithm,
                metadata?.publicKeyAlgorithm ?: PasskeyEntry.ALGORITHM_ES256
            ),
            publicKey = "",
            privateKeyAlias = keyValue,
            createdAt = parseCreationDateMillis(fido2?.creationDate),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 0,
            iconUrl = null,
            isDiscoverable = parseDiscoverable(fido2?.discoverable),
            isUserVerificationRequired = true,
            transports = PasskeyEntry.TRANSPORT_INTERNAL,
            aaguid = "",
            signCount = fido2?.counter?.toLongOrNull() ?: 0,
            isBackedUp = false,
            notes = cipher.notes?.substringBefore("---")?.trim() ?: "",
            boundPasswordId = null,
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            syncStatus = if (keyValue.isBlank()) "REFERENCE" else "SYNCED",
            passkeyMode = PasskeyEntry.MODE_BW_COMPAT
        )
    }
    
    override fun hasDifference(item: PasskeyEntry, cipher: CipherApiResponse): Boolean {
        if (cipher.type != 1) return true
        
        val expectedName = "${item.rpName} [Passkey]"
        val login = cipher.login
        
        return cipher.name != expectedName ||
                login?.username != item.userName
    }
    
    override fun merge(
        local: PasskeyEntry,
        remote: CipherApiResponse,
        preference: MergePreference
    ): PasskeyEntry {
        // Passkey 合并比较特殊：私钥永远保留本地的
        return when (preference) {
            MergePreference.LOCAL -> local
            MergePreference.REMOTE -> {
                // 只更新元数据，保留私钥相关字段
                val remoteData = fromCipherResponse(remote, local.bitwardenVaultId ?: 0)
                local.copy(
                    rpName = remoteData.rpName,
                    userName = remoteData.userName,
                    bitwardenCipherId = remote.id
                )
            }
            MergePreference.LATEST -> {
                // Passkey 始终以本地为准（因为私钥在本地）
                local
            }
        }
    }
    
    /**
     * 构建 Passkey 笔记（包含可恢复的元数据）
     */
    private fun buildPasskeyNotes(item: PasskeyEntry): String {
        val userNotes = item.notes
            .substringBefore("---")
            .trim()

        return buildString {
            if (userNotes.isNotBlank()) {
                appendLine(userNotes)
            }
            appendLine()
            appendLine("🔐 This is a Passkey entry synced from Monica")
            appendLine("ℹ️ Private key availability depends on client capability.")
            appendLine()
            appendLine("---")
            appendLine("[Monica Passkey Metadata]")
            appendLine("credentialId: ${item.credentialId}")
            appendLine("rpId: ${item.rpId}")
            appendLine("rpName: ${item.rpName}")
            appendLine("userId: ${item.userId}")
            appendLine("userDisplayName: ${item.userDisplayName}")
            appendLine("publicKeyAlgorithm: ${item.publicKeyAlgorithm}")
            appendLine("signCount: ${item.signCount}")
            appendLine("createdAt: ${item.createdAt}")
            appendLine("lastUsedAt: ${item.lastUsedAt}")
        }
    }
    
    /**
     * 从 URI 列表提取 rpId
     */
    private fun extractRpIdFromUris(uris: List<CipherUriApiData>?): String? {
        if (uris.isNullOrEmpty()) return null
        
        return uris.mapNotNull { uri ->
            try {
                val u = uri.uri ?: return@mapNotNull null
                if (u.startsWith("https://")) {
                    java.net.URI(u).host
                } else if (!u.contains("://")) {
                    u  // 可能就是 rpId 本身
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }.firstOrNull()
    }
    
    /**
     * 从 notes 解析 Passkey 元数据
     */
    private fun parsePasskeyMetadata(notes: String?): PasskeyMetadata? {
        if (notes == null || !notes.contains("[Monica Passkey Metadata]")) return null
        
        try {
            val lines = notes.lines()
            val dataLines = lines.dropWhile { it != "[Monica Passkey Metadata]" }.drop(1)
            
            val map = dataLines.associate { line ->
                val parts = line.split(": ", limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }
            
            return PasskeyMetadata(
                credentialId = map["credentialId"] ?: "",
                userId = map["userId"] ?: "",
                userDisplayName = map["userDisplayName"] ?: "",
                publicKeyAlgorithm = map["publicKeyAlgorithm"]?.toIntOrNull() ?: PasskeyEntry.ALGORITHM_ES256
            )
        } catch (e: Exception) {
            return null
        }
    }
    
    private data class PasskeyMetadata(
        val credentialId: String,
        val userId: String,
        val userDisplayName: String,
        val publicKeyAlgorithm: Int
    )

    private fun parseDiscoverable(value: String?): Boolean {
        return when (value?.trim()?.lowercase()) {
            "false", "0", "no" -> false
            else -> true
        }
    }

    private fun parseCreationDateMillis(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
    }

    private fun parseAlgorithm(value: String?, fallback: Int): Int {
        val parsed = value?.trim()?.toIntOrNull()
        if (parsed != null) return parsed
        return when (value?.trim()?.lowercase()) {
            "es256", "ecdsa" -> PasskeyEntry.ALGORITHM_ES256
            "rs256", "rsa" -> PasskeyEntry.ALGORITHM_RS256
            "ps256" -> PasskeyEntry.ALGORITHM_PS256
            "eddsa", "ed25519" -> PasskeyEntry.ALGORITHM_EDDSA
            else -> fallback
        }
    }

    private fun algorithmToBitwarden(algorithm: Int): String {
        return when (algorithm) {
            PasskeyEntry.ALGORITHM_ES256 -> "ECDSA"
            PasskeyEntry.ALGORITHM_RS256 -> "RSA"
            PasskeyEntry.ALGORITHM_PS256 -> "PS256"
            PasskeyEntry.ALGORITHM_EDDSA -> "EdDSA"
            else -> "ECDSA"
        }
    }

    private fun canUseAsBitwardenKeyValue(value: String): Boolean {
        if (value.isBlank()) return false

        val decoded = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull() ?: return false
        val keySpec = PKCS8EncodedKeySpec(decoded)

        val ecValid = runCatching {
            KeyFactory.getInstance("EC").generatePrivate(keySpec)
            true
        }.getOrDefault(false)
        if (ecValid) return true

        return runCatching {
            KeyFactory.getInstance("RSA").generatePrivate(keySpec)
            true
        }.getOrDefault(false)
    }

    private fun buildReferenceCredentialId(cipherId: String): String {
        return "bw_ref_$cipherId"
    }
    
    companion object {
        /**
         * 判断一个 Login Cipher 是否为 Passkey 条目
         */
        fun isPasskeyCipher(cipher: CipherApiResponse): Boolean {
            if (cipher.type != 1) return false

            // 优先按 Bitwarden 原生字段识别
            if (!cipher.login?.fido2Credentials.isNullOrEmpty()) return true

            // 兼容早期 Monica 约定
            return cipher.name?.endsWith(" [Passkey]") == true ||
                cipher.notes?.contains("[Monica Passkey Metadata]") == true
        }
        
        /**
         * Passkey 私钥是否可同步
         */
        fun canSyncPrivateKey(): Boolean = true
    }
}
