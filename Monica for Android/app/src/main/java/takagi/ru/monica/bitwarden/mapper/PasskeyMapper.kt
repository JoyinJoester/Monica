package takagi.ru.monica.bitwarden.mapper

import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.data.PasskeyEntry

/**
 * Passkey 数据映射器
 * 
 * Monica PasskeyEntry <-> Bitwarden Login (Type 1)
 * 
 * ⚠️ 重要限制：
 * - Passkey 的私钥无法导出到 Bitwarden（安全设计）
 * - 只能同步 Passkey 的元数据（rpId、用户信息等）
 * - 从 Bitwarden 导入时，只能作为"引用"记录
 * 
 * 同步策略：
 * - Monica → Bitwarden: 同步元数据，私钥不发送
 * - Bitwarden → Monica: 只能创建"占位"记录，需要用户重新注册
 */
class PasskeyMapper : BitwardenMapper<PasskeyEntry> {
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    override fun toCreateRequest(item: PasskeyEntry, folderId: String?): CipherCreateRequest {
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
                username = item.userName.takeIf { it.isNotBlank() } ?: item.userDisplayName
            )
        )
    }
    
    override fun fromCipherResponse(cipher: CipherApiResponse, vaultId: Long): PasskeyEntry {
        // 从 Bitwarden 创建 Passkey 占位记录
        // 注意：这只是元数据，真正的 Passkey 需要重新在设备上注册
        
        val login = cipher.login
        val rpId = extractRpIdFromUris(login?.uris)
        
        // 尝试从 notes 解析 Monica 元数据
        val metadata = parsePasskeyMetadata(cipher.notes)
        
        return PasskeyEntry(
            credentialId = metadata?.credentialId ?: "",  // 空的，需要重新注册
            rpId = rpId ?: "",
            rpName = cipher.name?.removeSuffix(" [Passkey]") ?: "",
            userId = metadata?.userId ?: "",
            userName = login?.username ?: "",
            userDisplayName = metadata?.userDisplayName ?: login?.username ?: "",
            publicKeyAlgorithm = metadata?.publicKeyAlgorithm ?: PasskeyEntry.ALGORITHM_ES256,
            publicKey = "",  // 公钥无法恢复
            privateKeyAlias = "",  // 私钥无法恢复
            createdAt = System.currentTimeMillis(),
            lastUsedAt = System.currentTimeMillis(),
            useCount = 0,
            iconUrl = null,
            isDiscoverable = true,
            isUserVerificationRequired = true,
            transports = PasskeyEntry.TRANSPORT_INTERNAL,
            aaguid = "",
            signCount = 0,
            isBackedUp = false,
            notes = cipher.notes?.substringBefore("---")?.trim() ?: "",
            boundPasswordId = null,
            bitwardenVaultId = vaultId,
            bitwardenCipherId = cipher.id,
            syncStatus = "REFERENCE"  // 标记为引用，需要重新注册
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
        return buildString {
            if (item.notes.isNotBlank()) {
                appendLine(item.notes)
            }
            appendLine()
            appendLine("🔐 This is a Passkey entry synced from Monica")
            appendLine("⚠️ Private key is stored locally only and cannot be synced.")
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
    
    companion object {
        /**
         * 判断一个 Login Cipher 是否为 Passkey 条目
         */
        fun isPasskeyCipher(cipher: CipherApiResponse): Boolean {
            if (cipher.type != 1) return false
            
            // 通过名称后缀或 notes 中的标记判断
            return cipher.name?.endsWith(" [Passkey]") == true ||
                   cipher.notes?.contains("[Monica Passkey Metadata]") == true
        }
        
        /**
         * Passkey 私钥是否可同步
         * 返回 false - Passkey 设计上私钥不可导出
         */
        fun canSyncPrivateKey(): Boolean = false
    }
}
