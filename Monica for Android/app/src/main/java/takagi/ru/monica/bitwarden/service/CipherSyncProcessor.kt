package takagi.ru.monica.bitwarden.service

import android.content.Context
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.mapper.*
import takagi.ru.monica.bitwarden.sync.SyncItemType
import takagi.ru.monica.data.*
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.security.SecurityManager
import java.util.Date

/**
 * 多类型 Cipher 同步处理器
 * 
 * 扩展现有的同步服务，支持所有 Cipher 类型：
 * - Type 1 (Login): PasswordEntry, TOTP, Passkey
 * - Type 2 (SecureNote): SecureItem(NOTE)
 * - Type 3 (Card): SecureItem(BANK_CARD)
 * - Type 4 (Identity): SecureItem(DOCUMENT)
 */
class CipherSyncProcessor(
    private val context: Context
) {
    companion object {
        private const val TAG = "CipherSyncProcessor"
    }
    
    private val database = PasswordDatabase.getDatabase(context)
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()
    private val passkeyDao = database.passkeyDao()
    private val securityManager = SecurityManager(context)
    
    /**
     * 处理从服务器同步的 Cipher
     * 自动识别类型并路由到对应的处理器
     */
    suspend fun syncCipherFromServer(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        // 跳过已删除的 Cipher
        if (cipher.deletedDate != null) {
            return CipherSyncResult.Skipped("Cipher is deleted")
        }
        
        return when (cipher.type) {
            1 -> syncLoginCipher(vault, cipher, symmetricKey)
            2 -> syncSecureNoteCipher(vault, cipher, symmetricKey)
            3 -> syncCardCipher(vault, cipher, symmetricKey)
            4 -> syncIdentityCipher(vault, cipher, symmetricKey)
            else -> CipherSyncResult.Skipped("Unknown cipher type: ${cipher.type}")
        }
    }
    
    /**
     * 同步 Login 类型 Cipher (Type 1)
     * 可能是: Password, TOTP, 或 Passkey
     */
    private suspend fun syncLoginCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        return when {
            PasskeyMapper.isPasskeyCipher(cipher) -> {
                syncPasskeyCipher(vault, cipher, symmetricKey)
            }
            TotpMapper.isStandaloneTotpCipher(cipher) -> {
                syncTotpCipher(vault, cipher, symmetricKey)
            }
            else -> {
                // 标准密码条目 - 使用现有逻辑
                syncPasswordCipher(vault, cipher, symmetricKey)
            }
        }
    }
    
    /**
     * 同步密码条目
     */
    private suspend fun syncPasswordCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        val login = cipher.login ?: return CipherSyncResult.Skipped("No login data")
        
        // 解密字段
        val name = decryptString(cipher.name, symmetricKey) ?: "Untitled"
        val username = decryptString(login.username, symmetricKey) ?: ""
        val password = decryptString(login.password, symmetricKey) ?: ""
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val totp = decryptString(login.totp, symmetricKey) ?: ""
        val primaryUri = login.uris?.firstOrNull()?.let { 
            decryptString(it.uri, symmetricKey) 
        } ?: ""
        val encryptedPassword = securityManager.encryptData(password)
        
        // 查找本地是否存在
        val existing = passwordEntryDao.getByBitwardenCipherId(cipher.id)
        
        if (existing == null) {
            // 创建新条目（不吞并本地同名条目，保持数据源独立）
            val newEntry = PasswordEntry(
                title = name,
                website = primaryUri,
                username = username,
                password = encryptedPassword,
                notes = notes,
                authenticatorKey = totp,
                isFavorite = cipher.favorite == true,
                createdAt = Date(),
                updatedAt = Date(),
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenCipherType = 1,
                bitwardenLocalModified = false
            )
            passwordEntryDao.insert(newEntry)
            return CipherSyncResult.Added
        } else {
            // 更新现有条目
            if (existing.bitwardenLocalModified) {
                if (existing.bitwardenRevisionDate != cipher.revisionDate) {
                    return CipherSyncResult.Conflict
                }
                return CipherSyncResult.Skipped("Local changes pending upload")
            }
            
            val updated = existing.copy(
                title = name,
                website = primaryUri,
                username = username,
                password = encryptedPassword,
                notes = notes,
                authenticatorKey = totp,
                isFavorite = cipher.favorite == true,
                updatedAt = Date(),
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false
            )
            passwordEntryDao.update(updated)
            return CipherSyncResult.Updated
        }
    }
    
    /**
     * 同步独立 TOTP
     */
    private suspend fun syncTotpCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        val login = cipher.login ?: return CipherSyncResult.Skipped("No login data")
        
        // 解密 TOTP 密钥
        val totpSecret = decryptString(login.totp, symmetricKey) ?: ""
        if (totpSecret.isBlank()) {
            return CipherSyncResult.Skipped("No TOTP secret")
        }
        
        val name = decryptString(cipher.name, symmetricKey) ?: "Authenticator"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val account = decryptString(login.username, symmetricKey) ?: ""
        
        // 查找本地是否存在
        val existing = secureItemDao.getByBitwardenCipherId(cipher.id)
        
        // 构建 TOTP 数据
        val totpData = TotpItemData(
            secret = totpSecret,
            issuer = name,
            account = account
        )
        val itemData = kotlinx.serialization.json.Json.encodeToString(
            TotpItemData.serializer(), totpData
        )
        
        if (existing == null) {
            val newItem = SecureItem(
                itemType = ItemType.TOTP,
                title = name,
                notes = notes,
                isFavorite = cipher.favorite == true,
                createdAt = Date(),
                updatedAt = Date(),
                itemData = itemData,
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                syncStatus = "SYNCED"
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (existing.bitwardenLocalModified == true) {
                if (existing.bitwardenRevisionDate != cipher.revisionDate) {
                    return CipherSyncResult.Conflict
                }
                return CipherSyncResult.Skipped("Local changes pending upload")
            }
            
            val updated = existing.copy(
                title = name,
                notes = notes,
                itemData = itemData,
                isFavorite = cipher.favorite == true,
                updatedAt = Date(),
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false,
                syncStatus = "SYNCED"
            )
            secureItemDao.update(updated)
            return CipherSyncResult.Updated
        }
    }
    
    /**
     * 同步 SecureNote (Type 2) -> SecureItem(NOTE)
     */
    private suspend fun syncSecureNoteCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        val name = decryptString(cipher.name, symmetricKey) ?: "Note"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        
        val existing = secureItemDao.getByBitwardenCipherId(cipher.id)
        
        // 构建笔记数据
        val noteData = NoteItemData(content = notes)
        val itemData = kotlinx.serialization.json.Json.encodeToString(
            NoteItemData.serializer(), noteData
        )
        
        if (existing == null) {
            val newItem = SecureItem(
                itemType = ItemType.NOTE,
                title = name,
                notes = "",  // 内容在 itemData 中
                isFavorite = cipher.favorite == true,
                createdAt = Date(),
                updatedAt = Date(),
                itemData = itemData,
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                syncStatus = "SYNCED"
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (existing.bitwardenLocalModified == true) {
                if (existing.bitwardenRevisionDate != cipher.revisionDate) {
                    return CipherSyncResult.Conflict
                }
                return CipherSyncResult.Skipped("Local changes pending upload")
            }
            
            val updated = existing.copy(
                title = name,
                itemData = itemData,
                isFavorite = cipher.favorite == true,
                updatedAt = Date(),
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false,
                syncStatus = "SYNCED"
            )
            secureItemDao.update(updated)
            return CipherSyncResult.Updated
        }
    }
    
    /**
     * 同步 Card (Type 3) -> SecureItem(BANK_CARD)
     */
    private suspend fun syncCardCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        val card = cipher.card ?: return CipherSyncResult.Skipped("No card data")
        
        val name = decryptString(cipher.name, symmetricKey) ?: "Card"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        
        // 解密卡片字段
        val cardNumber = decryptString(card.number, symmetricKey) ?: ""
        val cardHolder = decryptString(card.cardholderName, symmetricKey) ?: ""
        val expMonth = decryptString(card.expMonth, symmetricKey) ?: ""
        val expYear = decryptString(card.expYear, symmetricKey) ?: ""
        val cvv = decryptString(card.code, symmetricKey) ?: ""
        val brand = decryptString(card.brand, symmetricKey) ?: ""
        
        val existing = secureItemDao.getByBitwardenCipherId(cipher.id)
        
        // 构建银行卡数据（使用 CardMapper.kt 中的 CardItemData 结构）
        val cardData = CardItemData(
            cardholderName = cardHolder,
            number = cardNumber,
            expMonth = expMonth,
            expYear = expYear,
            cvv = cvv,
            brand = brand
        )
        val itemData = kotlinx.serialization.json.Json.encodeToString(
            CardItemData.serializer(), cardData
        )
        
        if (existing == null) {
            val newItem = SecureItem(
                itemType = ItemType.BANK_CARD,
                title = name,
                notes = notes,
                isFavorite = cipher.favorite == true,
                createdAt = Date(),
                updatedAt = Date(),
                itemData = itemData,
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                syncStatus = "SYNCED"
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (existing.bitwardenLocalModified == true) {
                if (existing.bitwardenRevisionDate != cipher.revisionDate) {
                    return CipherSyncResult.Conflict
                }
                return CipherSyncResult.Skipped("Local changes pending upload")
            }
            
            val updated = existing.copy(
                title = name,
                notes = notes,
                itemData = itemData,
                isFavorite = cipher.favorite == true,
                updatedAt = Date(),
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false,
                syncStatus = "SYNCED"
            )
            secureItemDao.update(updated)
            return CipherSyncResult.Updated
        }
    }
    
    /**
     * 同步 Identity (Type 4) -> SecureItem(DOCUMENT)
     */
    private suspend fun syncIdentityCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        val identity = cipher.identity ?: return CipherSyncResult.Skipped("No identity data")
        
        val name = decryptString(cipher.name, symmetricKey) ?: "Identity"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        
        // 解密身份字段
        val firstName = decryptString(identity.firstName, symmetricKey) ?: ""
        val lastName = decryptString(identity.lastName, symmetricKey) ?: ""
        val fullName = "$firstName $lastName".trim()
        val idNumber = decryptString(identity.licenseNumber, symmetricKey) 
            ?: decryptString(identity.passportNumber, symmetricKey) 
            ?: decryptString(identity.ssn, symmetricKey) 
            ?: ""
        
        val existing = secureItemDao.getByBitwardenCipherId(cipher.id)
        
        // 构建证件数据（使用 IdentityMapper.kt 中的 DocumentItemData 结构）
        val docData = DocumentItemData(
            documentType = guessDocumentType(identity),
            documentNumber = idNumber,
            firstName = firstName,
            lastName = lastName,
            issuingAuthority = decryptString(identity.company, symmetricKey) ?: ""
        )
        val itemData = kotlinx.serialization.json.Json.encodeToString(
            DocumentItemData.serializer(), docData
        )
        
        if (existing == null) {
            val newItem = SecureItem(
                itemType = ItemType.DOCUMENT,
                title = name,
                notes = notes,
                isFavorite = cipher.favorite == true,
                createdAt = Date(),
                updatedAt = Date(),
                itemData = itemData,
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                syncStatus = "SYNCED"
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (existing.bitwardenLocalModified == true) {
                if (existing.bitwardenRevisionDate != cipher.revisionDate) {
                    return CipherSyncResult.Conflict
                }
                return CipherSyncResult.Skipped("Local changes pending upload")
            }
            
            val updated = existing.copy(
                title = name,
                notes = notes,
                itemData = itemData,
                isFavorite = cipher.favorite == true,
                updatedAt = Date(),
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false,
                syncStatus = "SYNCED"
            )
            secureItemDao.update(updated)
            return CipherSyncResult.Updated
        }
    }
    
    /**
     * 同步 Passkey 元数据
     */
    private suspend fun syncPasskeyCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        val login = cipher.login
        val name = decryptString(cipher.name, symmetricKey) ?: "Passkey"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val userName = decryptString(login?.username, symmetricKey) ?: ""
        
        // 从 URI 提取 rpId
        val rpId = login?.uris?.firstOrNull()?.let { uri ->
            val uriStr = decryptString(uri.uri, symmetricKey) ?: ""
            extractRpIdFromUri(uriStr)
        } ?: ""
        
        // 查找本地是否存在
        val existing = passkeyDao.getByBitwardenCipherId(cipher.id)
        
        if (existing == null) {
            // Passkey 从远程同步只能作为"引用"
            // 真正的 Passkey 需要在本地重新创建
            android.util.Log.i(TAG, "Passkey from Bitwarden detected: $name (reference only)")
            // 不自动创建本地 Passkey，因为没有私钥
            return CipherSyncResult.Skipped("Passkey can only be created locally")
        } else {
            // 更新元数据
            val updated = existing.copy(
                rpName = name.removeSuffix(" [Passkey]"),
                userName = userName,
                bitwardenCipherId = cipher.id
            )
            passkeyDao.update(updated)
            return CipherSyncResult.Updated
        }
    }
    
    // ========== 辅助方法 ==========
    
    private fun decryptString(encrypted: String?, key: SymmetricCryptoKey): String? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            BitwardenCrypto.decryptToString(encrypted, key)
        } catch (e: Exception) {
            null
        }
    }
    
    private fun extractRpIdFromUri(uri: String): String {
        return try {
            if (uri.startsWith("https://")) {
                java.net.URI(uri).host ?: ""
            } else {
                uri
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    private fun guessDocumentType(identity: CipherIdentityApiData): String {
        return when {
            identity.passportNumber != null -> "PASSPORT"
            identity.licenseNumber != null -> "DRIVER_LICENSE"
            identity.ssn != null -> "ID_CARD"
            else -> "OTHER"
        }
    }
}

// CipherSyncResult 定义在 BitwardenSyncService.kt 中
