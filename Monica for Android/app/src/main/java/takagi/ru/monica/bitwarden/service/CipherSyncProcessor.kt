package takagi.ru.monica.bitwarden.service

import android.content.Context
import android.util.Base64
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.mapper.*
import takagi.ru.monica.bitwarden.sync.SyncItemType
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.data.model.TotpData
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
    private val pendingOpDao = database.bitwardenPendingOperationDao()
    private val securityManager = SecurityManager(context)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
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

        // 先按 cipherId 收敛历史重复副本，避免“同一条目异常膨胀”。
        val existing = resolveCanonicalPasswordEntry(cipher.id)
        val hasPendingDelete = pendingOpDao.hasActiveDeleteByCipher(vault.id, cipher.id)
        
        // 解密字段
        val name = decryptString(cipher.name, symmetricKey) ?: "Untitled"
        val username = decryptString(login.username, symmetricKey) ?: ""
        val decryptedPassword = decryptString(login.password, symmetricKey)
        // login.password 有值但无法解密时，不能回写为空，否则会制造“幽灵空密码”副本。
        if (!login.password.isNullOrBlank() && decryptedPassword == null) {
            android.util.Log.w(TAG, "Skip cipher ${cipher.id}: password decrypt failed")
            return CipherSyncResult.Skipped("Password decrypt failed")
        }
        val password = decryptedPassword ?: ""
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val totp = decryptString(login.totp, symmetricKey) ?: ""
        val primaryUri = login.uris?.firstOrNull()?.let { 
            decryptString(it.uri, symmetricKey) 
        } ?: ""
        val encryptedPassword = securityManager.encryptData(password)
        
        if (existing == null) {
            if (hasPendingDelete) {
                return CipherSyncResult.Skipped("Pending local delete")
            }
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
            if (existing.isDeleted || hasPendingDelete) {
                return CipherSyncResult.Skipped("Local delete wins")
            }
            // 更新现有条目
            if (existing.bitwardenLocalModified) {
                if (existing.bitwardenVaultId != vault.id || existing.bitwardenFolderId != cipher.folderId) {
                    passwordEntryDao.update(
                        existing.copy(
                            bitwardenVaultId = vault.id,
                            bitwardenFolderId = cipher.folderId,
                            updatedAt = Date()
                        )
                    )
                }
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
                bitwardenVaultId = vault.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false
            )
            passwordEntryDao.update(updated)
            return CipherSyncResult.Updated
        }
    }

    private suspend fun resolveCanonicalPasswordEntry(cipherId: String): PasswordEntry? {
        val allEntries = passwordEntryDao.getAllByBitwardenCipherId(cipherId)
        if (allEntries.isEmpty()) return null

        val canonical = allEntries.maxWithOrNull(
            compareBy<PasswordEntry> { if (it.isDeleted) 2 else if (it.bitwardenLocalModified) 1 else 0 }
                .thenBy { if (hasLikelyNonBlankPassword(it)) 1 else 0 }
                .thenBy { it.updatedAt.time }
                .thenBy { it.id }
        ) ?: allEntries.first()

        if (allEntries.size > 1) {
            val duplicates = allEntries.filter { it.id != canonical.id }
            duplicates.forEach { passwordEntryDao.delete(it) }
            android.util.Log.w(
                TAG,
                "Removed ${duplicates.size} duplicate password rows for cipherId=$cipherId"
            )
        }

        return canonical
    }

    private fun hasLikelyNonBlankPassword(entry: PasswordEntry): Boolean {
        if (entry.password.isBlank()) return false

        val decrypted = runCatching { securityManager.decryptData(entry.password) }.getOrNull()
        return when {
            decrypted == null -> true
            decrypted.isBlank() -> false
            else -> true
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
        val totpData = TotpData(
            secret = totpSecret,
            issuer = name,
            accountName = account
        )
        val itemData = json.encodeToString(totpData)
        
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
                if (existing.bitwardenVaultId != vault.id || existing.bitwardenFolderId != cipher.folderId) {
                    secureItemDao.update(
                        existing.copy(
                            bitwardenVaultId = vault.id,
                            bitwardenFolderId = cipher.folderId,
                            updatedAt = Date()
                        )
                    )
                }
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
                bitwardenVaultId = vault.id,
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
        val noteData = NoteData(content = notes)
        val itemData = json.encodeToString(noteData)
        
        if (existing == null) {
            val newItem = SecureItem(
                itemType = ItemType.NOTE,
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
                if (existing.bitwardenVaultId != vault.id || existing.bitwardenFolderId != cipher.folderId) {
                    secureItemDao.update(
                        existing.copy(
                            bitwardenVaultId = vault.id,
                            bitwardenFolderId = cipher.folderId,
                            updatedAt = Date()
                        )
                    )
                }
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
                bitwardenVaultId = vault.id,
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
        val cardData = BankCardData(
            cardNumber = cardNumber,
            cardholderName = cardHolder,
            expiryMonth = expMonth,
            expiryYear = expYear,
            cvv = cvv,
            bankName = brand,
            cardType = CardType.CREDIT
        )
        val itemData = json.encodeToString(cardData)
        
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
                if (existing.bitwardenVaultId != vault.id || existing.bitwardenFolderId != cipher.folderId) {
                    secureItemDao.update(
                        existing.copy(
                            bitwardenVaultId = vault.id,
                            bitwardenFolderId = cipher.folderId,
                            updatedAt = Date()
                        )
                    )
                }
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
                bitwardenVaultId = vault.id,
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
        val docData = DocumentData(
            documentType = guessDocumentType(identity),
            documentNumber = idNumber,
            fullName = fullName,
            issuedBy = decryptString(identity.company, symmetricKey) ?: "",
            nationality = decryptString(identity.country, symmetricKey) ?: ""
        )
        val itemData = json.encodeToString(docData)
        
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
                if (existing.bitwardenVaultId != vault.id || existing.bitwardenFolderId != cipher.folderId) {
                    secureItemDao.update(
                        existing.copy(
                            bitwardenVaultId = vault.id,
                            bitwardenFolderId = cipher.folderId,
                            updatedAt = Date()
                        )
                    )
                }
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
                bitwardenVaultId = vault.id,
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
        val hasPendingDelete = pendingOpDao.hasActiveDeleteByCipher(vault.id, cipher.id)
        if (hasPendingDelete) {
            return CipherSyncResult.Skipped("Pending local delete")
        }

        val login = cipher.login
        val name = decryptString(cipher.name, symmetricKey) ?: "Passkey"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val fallbackUserName = decryptString(login?.username, symmetricKey) ?: ""

        val fallbackRpId = login?.uris
            ?.asSequence()
            ?.mapNotNull { uri ->
                val uriStr = decryptString(uri.uri, symmetricKey) ?: return@mapNotNull null
                extractRpIdFromUri(uriStr).takeIf { it.isNotBlank() }
            }
            ?.firstOrNull()
            .orEmpty()

        val decodedCredentials = decodeFido2Credentials(login?.fido2Credentials, symmetricKey)

        if (decodedCredentials.isEmpty()) {
            // 兼容历史 Monica marker-only passkey：至少落一个引用记录
            val referenceId = buildReferenceCredentialId(cipher.id, 0)
            val existing = passkeyDao.getByBitwardenCipherId(cipher.id)
                ?: passkeyDao.getPasskeyById(referenceId)

            val rpId = fallbackRpId
            val rpName = name.removeSuffix(" [Passkey]").ifBlank { rpId }
            val userName = fallbackUserName
            val now = System.currentTimeMillis()

            if (existing == null) {
                passkeyDao.insert(
                    PasskeyEntry(
                        credentialId = referenceId,
                        rpId = rpId,
                        rpName = rpName,
                        userId = "",
                        userName = userName,
                        userDisplayName = userName,
                        publicKeyAlgorithm = PasskeyEntry.ALGORITHM_ES256,
                        publicKey = "",
                        privateKeyAlias = "",
                        createdAt = now,
                        lastUsedAt = now,
                        useCount = 0,
                        iconUrl = null,
                        isDiscoverable = true,
                        isUserVerificationRequired = true,
                        transports = PasskeyEntry.TRANSPORT_INTERNAL,
                        aaguid = "",
                        signCount = 0,
                        isBackedUp = false,
                        notes = notes,
                        bitwardenVaultId = vault.id,
                        bitwardenCipherId = cipher.id,
                        syncStatus = "REFERENCE"
                    )
                )
                android.util.Log.i(TAG, "Created reference-only passkey for cipher ${cipher.id}")
                return CipherSyncResult.Added
            }

            passkeyDao.update(
                existing.copy(
                    rpId = rpId.ifBlank { existing.rpId },
                    rpName = rpName.ifBlank { existing.rpName },
                    userName = userName.ifBlank { existing.userName },
                    userDisplayName = userName.ifBlank { existing.userDisplayName },
                    notes = notes.ifBlank { existing.notes },
                    bitwardenVaultId = vault.id,
                    bitwardenCipherId = cipher.id,
                    syncStatus = "REFERENCE"
                )
            )
            return CipherSyncResult.Updated
        }

        var added = 0
        var updated = 0
        val keepCredentialIds = mutableSetOf<String>()
        val now = System.currentTimeMillis()

        decodedCredentials.forEachIndexed { index, decoded ->
            val resolvedCredentialId = normalizeCredentialId(decoded.credentialId)
                ?: buildReferenceCredentialId(cipher.id, index)
            keepCredentialIds += resolvedCredentialId

            val rpId = decoded.rpId.ifBlank { fallbackRpId }
            val rpName = decoded.rpName
                .ifBlank { name.removeSuffix(" [Passkey]") }
                .ifBlank { rpId }
            val userName = decoded.userName.ifBlank { fallbackUserName }
            val userDisplayName = decoded.userDisplayName.ifBlank { userName }

            val existing = passkeyDao.getPasskeyById(resolvedCredentialId)
            if (existing == null) {
                val syncStatus = if (decoded.keyValue.isBlank()) "REFERENCE" else "SYNCED"
                passkeyDao.insert(
                    PasskeyEntry(
                        credentialId = resolvedCredentialId,
                        rpId = rpId,
                        rpName = rpName,
                        userId = decoded.userHandle,
                        userName = userName,
                        userDisplayName = userDisplayName,
                        publicKeyAlgorithm = decoded.publicKeyAlgorithm,
                        publicKey = "",
                        privateKeyAlias = decoded.keyValue,
                        createdAt = decoded.creationDateMillis ?: now,
                        lastUsedAt = now,
                        useCount = 0,
                        iconUrl = null,
                        isDiscoverable = decoded.discoverable,
                        isUserVerificationRequired = true,
                        transports = PasskeyEntry.TRANSPORT_INTERNAL,
                        aaguid = "",
                        signCount = decoded.counter,
                        isBackedUp = false,
                        notes = notes,
                        bitwardenVaultId = vault.id,
                        bitwardenCipherId = cipher.id,
                        syncStatus = syncStatus
                    )
                )
                added++
            } else {
                val mergedPrivateKey = decoded.keyValue.ifBlank { existing.privateKeyAlias }
                val syncStatus = if (mergedPrivateKey.isBlank()) "REFERENCE" else "SYNCED"

                passkeyDao.update(
                    existing.copy(
                        rpId = rpId.ifBlank { existing.rpId },
                        rpName = rpName.ifBlank { existing.rpName },
                        userId = decoded.userHandle.ifBlank { existing.userId },
                        userName = userName.ifBlank { existing.userName },
                        userDisplayName = userDisplayName.ifBlank { existing.userDisplayName },
                        publicKeyAlgorithm = decoded.publicKeyAlgorithm,
                        privateKeyAlias = mergedPrivateKey,
                        isDiscoverable = decoded.discoverable,
                        signCount = maxOf(existing.signCount, decoded.counter),
                        notes = notes.ifBlank { existing.notes },
                        bitwardenVaultId = vault.id,
                        bitwardenCipherId = cipher.id,
                        syncStatus = syncStatus
                    )
                )
                updated++
            }
        }

        val staleEntries = passkeyDao.getAllByBitwardenCipherId(cipher.id)
            .filterNot { keepCredentialIds.contains(it.credentialId) }
        staleEntries.forEach { passkeyDao.delete(it) }

        return when {
            added > 0 -> CipherSyncResult.Added
            updated > 0 || staleEntries.isNotEmpty() -> CipherSyncResult.Updated
            else -> CipherSyncResult.Skipped("No passkey changes")
        }
    }
    
    // ========== 辅助方法 ==========

    private data class DecodedPasskeyCredential(
        val credentialId: String,
        val keyValue: String,
        val rpId: String,
        val rpName: String,
        val userHandle: String,
        val userName: String,
        val userDisplayName: String,
        val counter: Long,
        val discoverable: Boolean,
        val creationDateMillis: Long?,
        val publicKeyAlgorithm: Int
    )

    private fun decodeFido2Credentials(
        credentials: List<CipherLoginFido2CredentialApiData>?,
        key: SymmetricCryptoKey
    ): List<DecodedPasskeyCredential> {
        if (credentials.isNullOrEmpty()) return emptyList()

        return credentials.mapNotNull { credential ->
            val credentialId = decryptOrPlain(credential.credentialId, key).orEmpty()
            val keyValue = decryptOrPlain(credential.keyValue, key).orEmpty()
            val rpId = decryptOrPlain(credential.rpId, key).orEmpty()
            val rpName = decryptOrPlain(credential.rpName, key).orEmpty()
            val userHandle = decryptOrPlain(credential.userHandle, key).orEmpty()
            val userName = decryptOrPlain(credential.userName, key).orEmpty()
            val userDisplayName = decryptOrPlain(credential.userDisplayName, key).orEmpty()
            val counter = decryptOrPlain(credential.counter, key)?.toLongOrNull() ?: 0L
            val discoverable = parseBooleanText(decryptOrPlain(credential.discoverable, key))
            val creationDate = parseCreationDateMillis(decryptOrPlain(credential.creationDate, key))
            val keyAlgorithm = decryptOrPlain(credential.keyAlgorithm, key)
            val publicKeyAlgorithm = parseAlgorithm(keyAlgorithm)

            val hasAnySignal = credentialId.isNotBlank() ||
                keyValue.isNotBlank() ||
                rpId.isNotBlank() ||
                userName.isNotBlank()
            if (!hasAnySignal) return@mapNotNull null

            DecodedPasskeyCredential(
                credentialId = credentialId,
                keyValue = keyValue,
                rpId = rpId,
                rpName = rpName,
                userHandle = userHandle,
                userName = userName,
                userDisplayName = userDisplayName,
                counter = counter,
                discoverable = discoverable,
                creationDateMillis = creationDate,
                publicKeyAlgorithm = publicKeyAlgorithm
            )
        }
    }

    private fun decryptOrPlain(value: String?, key: SymmetricCryptoKey): String? {
        if (value.isNullOrBlank()) return null
        val decrypted = decryptString(value, key)
        if (decrypted != null) return decrypted
        if (looksLikeCipherString(value)) return null
        return value
    }

    private fun looksLikeCipherString(value: String): Boolean {
        val dotIndex = value.indexOf('.')
        if (dotIndex <= 0) return false
        return value.substring(0, dotIndex).all(Char::isDigit)
    }

    private fun normalizeCredentialId(credentialId: String): String? {
        if (credentialId.isBlank()) return null
        val urlSafeFlags = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        return try {
            val decoded = Base64.decode(credentialId, urlSafeFlags)
            Base64.encodeToString(decoded, urlSafeFlags)
        } catch (_: Exception) {
            try {
                val decoded = Base64.decode(credentialId, Base64.DEFAULT)
                Base64.encodeToString(decoded, urlSafeFlags)
            } catch (_: Exception) {
                credentialId
            }
        }
    }

    private fun parseBooleanText(value: String?): Boolean {
        return when (value?.trim()?.lowercase()) {
            "false", "0", "no" -> false
            else -> true
        }
    }

    private fun parseCreationDateMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return runCatching { java.time.Instant.parse(value).toEpochMilli() }.getOrNull()
    }

    private fun parseAlgorithm(value: String?): Int {
        val parsed = value?.trim()?.toIntOrNull()
        if (parsed != null) return parsed
        return when (value?.trim()?.lowercase()) {
            "es256", "ecdsa" -> PasskeyEntry.ALGORITHM_ES256
            "rs256", "rsa" -> PasskeyEntry.ALGORITHM_RS256
            "ps256" -> PasskeyEntry.ALGORITHM_PS256
            "eddsa", "ed25519" -> PasskeyEntry.ALGORITHM_EDDSA
            else -> PasskeyEntry.ALGORITHM_ES256
        }
    }

    private fun buildReferenceCredentialId(cipherId: String, index: Int): String {
        return "bw_ref_${cipherId}_$index"
    }
    
    private fun decryptString(encrypted: String?, key: SymmetricCryptoKey): String? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            BitwardenCrypto.decryptToString(encrypted, key)
        } catch (e: Throwable) {
            android.util.Log.w(
                TAG,
                "decryptString failed: len=${encrypted.length}, typeHint=${extractCipherTypeHint(encrypted)}, error=${e.javaClass.simpleName}"
            )
            null
        }
    }

    private fun extractCipherTypeHint(cipherString: String): String {
        val dotIndex = cipherString.indexOf('.')
        if (dotIndex <= 0) return "none"
        return cipherString.substring(0, dotIndex).take(8)
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
    
    private fun guessDocumentType(identity: CipherIdentityApiData): DocumentType {
        return when {
            !identity.passportNumber.isNullOrBlank() -> DocumentType.PASSPORT
            !identity.licenseNumber.isNullOrBlank() -> DocumentType.DRIVER_LICENSE
            !identity.ssn.isNullOrBlank() -> DocumentType.ID_CARD
            else -> DocumentType.OTHER
        }
    }
}

// CipherSyncResult 定义在 BitwardenSyncService.kt 中
