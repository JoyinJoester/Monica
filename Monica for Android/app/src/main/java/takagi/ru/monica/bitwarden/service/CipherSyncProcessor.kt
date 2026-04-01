package takagi.ru.monica.bitwarden.service

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.mapper.*
import takagi.ru.monica.bitwarden.sync.SyncItemType
import takagi.ru.monica.data.*
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.data.model.SecureCustomField
import takagi.ru.monica.data.model.SecureCustomFieldType
import takagi.ru.monica.data.model.SshKeyData
import takagi.ru.monica.data.model.SshKeyDataCodec
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.passkey.PasskeyCredentialIdCodec
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.util.TotpDataResolver
import java.time.Instant
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

    private data class ParsedLoginUris(
        val website: String = "",
        val appPackageName: String = ""
    )
    
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
        val serverDeletedAt = parseBitwardenDeletedAt(cipher.deletedDate)
        val serverArchivedAt = parseBitwardenArchivedAt(cipher.archivedDate)

        return when (cipher.type) {
            1 -> syncLoginCipher(vault, cipher, symmetricKey, serverDeletedAt, serverArchivedAt)
            2 -> syncSecureNoteCipher(vault, cipher, symmetricKey, serverDeletedAt)
            3 -> syncCardCipher(vault, cipher, symmetricKey, serverDeletedAt)
            4 -> syncIdentityCipher(vault, cipher, symmetricKey, serverDeletedAt)
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
        symmetricKey: SymmetricCryptoKey,
        serverDeletedAt: Date?,
        serverArchivedAt: Date?
    ): CipherSyncResult {
        return when {
            PasskeyMapper.isPasskeyCipher(cipher) -> {
                syncPasskeyCipher(vault, cipher, symmetricKey)
            }
            TotpMapper.isStandaloneTotpCipher(cipher) -> {
                syncTotpCipher(vault, cipher, symmetricKey, serverDeletedAt)
            }
            else -> {
                // 标准密码条目 - 使用现有逻辑
                syncPasswordCipher(vault, cipher, symmetricKey, serverDeletedAt, serverArchivedAt)
            }
        }
    }
    
    /**
     * 同步密码条目
     */
    private suspend fun syncPasswordCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey,
        serverDeletedAt: Date?,
        serverArchivedAt: Date?
    ): CipherSyncResult {
        val login = cipher.login ?: return CipherSyncResult.Skipped("No login data")
        val isServerDeleted = serverDeletedAt != null

        // 先按 cipherId 收敛历史重复副本，避免“同一条目异常膨胀”。
        val existing = resolveCanonicalPasswordEntry(vault.id, cipher.id)
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
        val parsedUris = parseLoginUris(login.uris, symmetricKey)
        val customFields = decryptCustomFieldMap(cipher.fields, symmetricKey)
        val remoteAppPackage = customFields["monica_app_package"]
            ?: customFields["appPackageName"]
            ?: parsedUris.appPackageName
        val remoteAppName = customFields["monica_app_name"]
            ?: customFields["appName"]
            ?: ""
        val remoteEmail = customFields["monica_email"]
            ?: customFields["email"]
            ?: ""
        val remotePhone = customFields["monica_phone"]
            ?: customFields["phone"]
            ?: ""
        val remoteAddress = customFields["monica_address_line"]
            ?: customFields["addressLine"]
            ?: customFields["address"]
            ?: ""
        val remoteCity = customFields["monica_city"] ?: customFields["city"] ?: ""
        val remoteState = customFields["monica_state"] ?: customFields["state"] ?: ""
        val remoteZip = customFields["monica_zip_code"]
            ?: customFields["zipCode"]
            ?: ""
        val remoteCountry = customFields["monica_country"] ?: customFields["country"] ?: ""
        val remotePasskeyBindings = customFields["monica_passkey_bindings"].orEmpty()
        val remoteSshKeyData = buildSshKeyDataFromCustomFields(customFields)
        val encryptedPassword = encryptBitwardenPasswordForOfflineDisplay(password, cipher.id)
        
        if (existing == null) {
            if (hasPendingDelete && !isServerDeleted) {
                return CipherSyncResult.Skipped("Pending local delete")
            }
            // 创建新条目（不吞并本地同名条目，保持数据源独立）
            val newEntry = PasswordEntry(
                title = name,
                website = parsedUris.website,
                username = username,
                password = encryptedPassword,
                notes = notes,
                authenticatorKey = totp,
                appPackageName = remoteAppPackage,
                appName = remoteAppName,
                email = remoteEmail,
                phone = remotePhone,
                addressLine = remoteAddress,
                city = remoteCity,
                state = remoteState,
                zipCode = remoteZip,
                country = remoteCountry,
                passkeyBindings = remotePasskeyBindings,
                sshKeyData = remoteSshKeyData,
                isFavorite = cipher.favorite == true,
                createdAt = Date(),
                updatedAt = Date(),
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenCipherType = 1,
                bitwardenLocalModified = false,
                isDeleted = isServerDeleted,
                deletedAt = serverDeletedAt,
                isArchived = serverArchivedAt != null,
                archivedAt = serverArchivedAt
            )
            passwordEntryDao.insert(newEntry)
            return CipherSyncResult.Added
        } else {
            if (hasPendingDelete && !isServerDeleted) {
                return CipherSyncResult.Skipped("Local delete wins")
            }
            if (isServerDeleted) {
                passwordEntryDao.update(
                    existing.copy(
                        title = name,
                        website = parsedUris.website.ifBlank { existing.website },
                        username = username,
                        password = encryptedPassword,
                        notes = notes,
                        authenticatorKey = totp,
                        appPackageName = remoteAppPackage.ifBlank { existing.appPackageName },
                        appName = remoteAppName.ifBlank { existing.appName },
                        email = remoteEmail.ifBlank { existing.email },
                        phone = remotePhone.ifBlank { existing.phone },
                        addressLine = remoteAddress.ifBlank { existing.addressLine },
                        city = remoteCity.ifBlank { existing.city },
                        state = remoteState.ifBlank { existing.state },
                        zipCode = remoteZip.ifBlank { existing.zipCode },
                        country = remoteCountry.ifBlank { existing.country },
                        passkeyBindings = remotePasskeyBindings.ifBlank { existing.passkeyBindings },
                        sshKeyData = remoteSshKeyData.ifBlank { existing.sshKeyData },
                        isFavorite = cipher.favorite == true,
                        isDeleted = true,
                        deletedAt = serverDeletedAt,
                        isArchived = false,
                        archivedAt = null,
                        updatedAt = Date(),
                        bitwardenVaultId = vault.id,
                        bitwardenFolderId = cipher.folderId,
                        bitwardenRevisionDate = cipher.revisionDate,
                        bitwardenLocalModified = false
                    )
                )
                return CipherSyncResult.Updated
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
                website = parsedUris.website.ifBlank { existing.website },
                username = username,
                password = encryptedPassword,
                notes = notes,
                authenticatorKey = totp,
                appPackageName = remoteAppPackage.ifBlank { existing.appPackageName },
                appName = remoteAppName.ifBlank { existing.appName },
                email = remoteEmail.ifBlank { existing.email },
                phone = remotePhone.ifBlank { existing.phone },
                addressLine = remoteAddress.ifBlank { existing.addressLine },
                city = remoteCity.ifBlank { existing.city },
                state = remoteState.ifBlank { existing.state },
                zipCode = remoteZip.ifBlank { existing.zipCode },
                country = remoteCountry.ifBlank { existing.country },
                passkeyBindings = remotePasskeyBindings.ifBlank { existing.passkeyBindings },
                sshKeyData = remoteSshKeyData.ifBlank { existing.sshKeyData },
                isFavorite = cipher.favorite == true,
                isDeleted = false,
                deletedAt = null,
                isArchived = serverArchivedAt != null || existing.isArchived,
                archivedAt = serverArchivedAt ?: existing.archivedAt,
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

    private suspend fun resolveCanonicalPasswordEntry(vaultId: Long, cipherId: String): PasswordEntry? {
        val allEntries = passwordEntryDao.getAllByBitwardenCipherIdInVault(vaultId, cipherId)
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

    private fun buildSshKeyDataFromCustomFields(fields: Map<String, String>): String {
        return SshKeyDataCodec.encode(
            SshKeyData(
                algorithm = fields["monica_ssh_algorithm"].orEmpty(),
                keySize = fields["monica_ssh_key_size"]?.toIntOrNull() ?: 0,
                publicKeyOpenSsh = fields["monica_ssh_public_key"].orEmpty(),
                privateKeyOpenSsh = fields["monica_ssh_private_key"].orEmpty(),
                fingerprintSha256 = fields["monica_ssh_fingerprint"].orEmpty(),
                comment = fields["monica_ssh_comment"].orEmpty(),
                format = fields["monica_ssh_format"].orEmpty().ifBlank { SshKeyData.FORMAT_OPENSSH }
            )
        )
    }

    /**
     * Bitwarden 条目在离线浏览时只能依赖本地缓存。
     * 若当前 MDK 状态不稳定，优先降级到可立即读回的兼容密文，避免详情页显示空密码。
     */
    private fun encryptBitwardenPasswordForOfflineDisplay(
        plainPassword: String,
        cipherId: String
    ): String {
        val primaryEncrypted = securityManager.encryptData(plainPassword)
        val primaryReadable = runCatching { securityManager.decryptData(primaryEncrypted) }
            .getOrNull()
            ?.let { it == plainPassword }
            ?: false
        if (primaryReadable) {
            return primaryEncrypted
        }

        android.util.Log.w(
            TAG,
            "Bitwarden password payload is not immediately readable; fallback to legacy V1, cipherId=$cipherId"
        )

        val legacyEncrypted = securityManager.encryptDataLegacyCompat(plainPassword)
        val legacyReadable = runCatching { securityManager.decryptData(legacyEncrypted) }
            .getOrNull()
            ?.let { it == plainPassword }
            ?: false

        return if (legacyReadable) {
            legacyEncrypted
        } else {
            android.util.Log.w(
                TAG,
                "Legacy fallback is still unreadable; keep primary encrypted payload, cipherId=$cipherId"
            )
            primaryEncrypted
        }
    }
    
    /**
     * 同步独立 TOTP
     */
    private suspend fun syncTotpCipher(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey,
        serverDeletedAt: Date?
    ): CipherSyncResult {
        val login = cipher.login ?: return CipherSyncResult.Skipped("No login data")
        val isServerDeleted = serverDeletedAt != null
        
        // 解密 TOTP 密钥
        val decryptedTotp = decryptString(login.totp, symmetricKey) ?: ""
        if (decryptedTotp.isBlank()) {
            return CipherSyncResult.Skipped("No TOTP secret")
        }
        
        val name = decryptString(cipher.name, symmetricKey) ?: "Authenticator"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val account = decryptString(login.username, symmetricKey) ?: ""

        val resolvedTotpData = TotpDataResolver.fromAuthenticatorKey(
            rawKey = decryptedTotp,
            fallbackIssuer = name,
            fallbackAccountName = account
        ) ?: return CipherSyncResult.Skipped("Invalid TOTP payload")
        
        // 查找本地是否存在
        val existing = secureItemDao.getByBitwardenCipherIdInVault(vault.id, cipher.id)
        
        // 构建 TOTP 数据
        val totpData = resolvedTotpData.copy(
            issuer = resolvedTotpData.issuer.ifBlank { name },
            accountName = resolvedTotpData.accountName.ifBlank { account }
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
                syncStatus = "SYNCED",
                isDeleted = isServerDeleted,
                deletedAt = serverDeletedAt
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (isServerDeleted) {
                secureItemDao.update(
                    existing.copy(
                        title = name,
                        notes = notes,
                        itemData = itemData,
                        isFavorite = cipher.favorite == true,
                        isDeleted = true,
                        deletedAt = serverDeletedAt,
                        updatedAt = Date(),
                        bitwardenVaultId = vault.id,
                        bitwardenFolderId = cipher.folderId,
                        bitwardenRevisionDate = cipher.revisionDate,
                        bitwardenLocalModified = false,
                        syncStatus = "SYNCED"
                    )
                )
                return CipherSyncResult.Updated
            }
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
                isDeleted = false,
                deletedAt = null,
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
        symmetricKey: SymmetricCryptoKey,
        serverDeletedAt: Date?
    ): CipherSyncResult {
        val isServerDeleted = serverDeletedAt != null
        val hasPendingDelete = pendingOpDao.hasActiveDeleteByCipher(vault.id, cipher.id)
        if (hasPendingDelete && !isServerDeleted) {
            return CipherSyncResult.Skipped("Pending local delete")
        }

        val name = decryptString(cipher.name, symmetricKey) ?: "Note"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        
        val existing = secureItemDao.getByBitwardenCipherIdInVault(vault.id, cipher.id)
        
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
                syncStatus = "SYNCED",
                isDeleted = isServerDeleted,
                deletedAt = serverDeletedAt
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (isServerDeleted) {
                secureItemDao.update(
                    existing.copy(
                        title = name,
                        notes = notes,
                        itemData = itemData,
                        isFavorite = cipher.favorite == true,
                        isDeleted = true,
                        deletedAt = serverDeletedAt,
                        updatedAt = Date(),
                        bitwardenVaultId = vault.id,
                        bitwardenFolderId = cipher.folderId,
                        bitwardenRevisionDate = cipher.revisionDate,
                        bitwardenLocalModified = false,
                        syncStatus = "SYNCED"
                    )
                )
                return CipherSyncResult.Updated
            }
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
                isDeleted = false,
                deletedAt = null,
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
        symmetricKey: SymmetricCryptoKey,
        serverDeletedAt: Date?
    ): CipherSyncResult {
        val card = cipher.card ?: return CipherSyncResult.Skipped("No card data")
        val isServerDeleted = serverDeletedAt != null
        
        val name = decryptString(cipher.name, symmetricKey) ?: "Card"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        
        // 解密卡片字段
        val cardNumber = decryptString(card.number, symmetricKey) ?: ""
        val cardHolder = decryptString(card.cardholderName, symmetricKey) ?: ""
        val expMonth = decryptString(card.expMonth, symmetricKey) ?: ""
        val expYear = decryptString(card.expYear, symmetricKey) ?: ""
        val cvv = decryptString(card.code, symmetricKey) ?: ""
        val brand = decryptString(card.brand, symmetricKey) ?: ""
        val decryptedFields = decryptCustomFields(cipher.fields, symmetricKey)
        val fieldMap = decryptedFields.associate { it.name to it.value }
        
        val existing = secureItemDao.getByBitwardenCipherIdInVault(vault.id, cipher.id)
        
        val cardData = BankCardData(
            cardNumber = cardNumber,
            cardholderName = cardHolder,
            expiryMonth = expMonth,
            expiryYear = expYear,
            cvv = cvv,
            bankName = fieldMap["monica_bank_name"].orEmpty(),
            cardType = parseCardType(fieldMap["monica_card_type"]),
            billingAddress = fieldMap["monica_billing_address"].orEmpty(),
            brand = brand,
            nickname = fieldMap["monica_nickname"].orEmpty(),
            validFromMonth = fieldMap["monica_valid_from_month"].orEmpty(),
            validFromYear = fieldMap["monica_valid_from_year"].orEmpty(),
            pin = fieldMap["monica_pin"].orEmpty(),
            iban = fieldMap["monica_iban"].orEmpty(),
            swiftBic = fieldMap["monica_swift_bic"].orEmpty(),
            routingNumber = fieldMap["monica_routing_number"].orEmpty(),
            accountNumber = fieldMap["monica_account_number"].orEmpty(),
            branchCode = fieldMap["monica_branch_code"].orEmpty(),
            currency = fieldMap["monica_currency"].orEmpty(),
            customerServicePhone = fieldMap["monica_customer_service_phone"].orEmpty(),
            customFields = decryptedFields.toCardCustomFields()
        )
        val itemData = CardWalletDataCodec.encodeBankCardData(cardData)
        
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
                syncStatus = "SYNCED",
                isDeleted = isServerDeleted,
                deletedAt = serverDeletedAt
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (isServerDeleted) {
                secureItemDao.update(
                    existing.copy(
                        title = name,
                        notes = notes,
                        itemData = itemData,
                        isFavorite = cipher.favorite == true,
                        isDeleted = true,
                        deletedAt = serverDeletedAt,
                        updatedAt = Date(),
                        bitwardenVaultId = vault.id,
                        bitwardenFolderId = cipher.folderId,
                        bitwardenRevisionDate = cipher.revisionDate,
                        bitwardenLocalModified = false,
                        syncStatus = "SYNCED"
                    )
                )
                return CipherSyncResult.Updated
            }
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
                isDeleted = false,
                deletedAt = null,
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
        symmetricKey: SymmetricCryptoKey,
        serverDeletedAt: Date?
    ): CipherSyncResult {
        val identity = cipher.identity ?: return CipherSyncResult.Skipped("No identity data")
        val isServerDeleted = serverDeletedAt != null
        
        val name = decryptString(cipher.name, symmetricKey) ?: "Identity"
        val notes = decryptString(cipher.notes, symmetricKey) ?: ""
        val decryptedFields = decryptCustomFields(cipher.fields, symmetricKey)
        val customFieldMap = decryptedFields.associate { it.name to it.value }
        val fieldDocumentType = parseDocumentType(customFieldMap["monica_document_type"])
        val resolvedDocumentType = fieldDocumentType ?: guessDocumentType(identity)
        
        // 解密身份字段
        val firstName = decryptString(identity.firstName, symmetricKey) ?: ""
        val middleName = decryptString(identity.middleName, symmetricKey) ?: ""
        val lastName = decryptString(identity.lastName, symmetricKey) ?: ""
        val fullName = listOf(firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val passportNumber = decryptString(identity.passportNumber, symmetricKey).orEmpty()
        val licenseNumber = decryptString(identity.licenseNumber, symmetricKey).orEmpty()
        val ssn = decryptString(identity.ssn, symmetricKey).orEmpty()
        val idNumber = when (resolvedDocumentType) {
            DocumentType.PASSPORT -> passportNumber.ifBlank { ssn.ifBlank { licenseNumber } }
            DocumentType.DRIVER_LICENSE -> licenseNumber.ifBlank { ssn.ifBlank { passportNumber } }
            DocumentType.ID_CARD, DocumentType.SOCIAL_SECURITY, DocumentType.OTHER -> {
                ssn.ifBlank { licenseNumber.ifBlank { passportNumber } }
            }
        }
        
        val existing = secureItemDao.getByBitwardenCipherIdInVault(vault.id, cipher.id)
        
        val docData = DocumentData(
            documentType = resolvedDocumentType,
            documentNumber = idNumber,
            fullName = fullName,
            issuedDate = customFieldMap["monica_issue_date"].orEmpty(),
            expiryDate = customFieldMap["monica_expiry_date"].orEmpty(),
            issuedBy = customFieldMap["monica_issued_by"].orEmpty(),
            nationality = customFieldMap["monica_nationality"].orEmpty(),
            additionalInfo = customFieldMap["monica_additional_info"].orEmpty(),
            title = decryptString(identity.title, symmetricKey) ?: "",
            firstName = firstName,
            middleName = middleName,
            lastName = lastName,
            address1 = decryptString(identity.address1, symmetricKey) ?: "",
            address2 = decryptString(identity.address2, symmetricKey) ?: "",
            address3 = decryptString(identity.address3, symmetricKey) ?: "",
            city = decryptString(identity.city, symmetricKey) ?: "",
            stateProvince = decryptString(identity.state, symmetricKey) ?: "",
            postalCode = decryptString(identity.postalCode, symmetricKey) ?: "",
            country = decryptString(identity.country, symmetricKey) ?: "",
            company = decryptString(identity.company, symmetricKey) ?: "",
            email = decryptString(identity.email, symmetricKey) ?: "",
            phone = decryptString(identity.phone, symmetricKey) ?: "",
            ssn = ssn,
            username = decryptString(identity.username, symmetricKey) ?: "",
            passportNumber = passportNumber,
            licenseNumber = licenseNumber,
            customFields = decryptedFields.toDocumentCustomFields()
        )
        val itemData = CardWalletDataCodec.encodeDocumentData(docData)
        
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
                syncStatus = "SYNCED",
                isDeleted = isServerDeleted,
                deletedAt = serverDeletedAt
            )
            secureItemDao.insert(newItem)
            return CipherSyncResult.Added
        } else {
            if (isServerDeleted) {
                secureItemDao.update(
                    existing.copy(
                        title = name,
                        notes = notes,
                        itemData = itemData,
                        isFavorite = cipher.favorite == true,
                        isDeleted = true,
                        deletedAt = serverDeletedAt,
                        updatedAt = Date(),
                        bitwardenVaultId = vault.id,
                        bitwardenFolderId = cipher.folderId,
                        bitwardenRevisionDate = cipher.revisionDate,
                        bitwardenLocalModified = false,
                        syncStatus = "SYNCED"
                    )
                )
                return CipherSyncResult.Updated
            }
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
                isDeleted = false,
                deletedAt = null,
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
        val notes = extractPasskeyUserNotes(decryptString(cipher.notes, symmetricKey))
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
            val existing = passkeyDao.getByBitwardenCipherIdInVault(vault.id, cipher.id)
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
                        syncStatus = "REFERENCE",
                        passkeyMode = PasskeyEntry.MODE_BW_COMPAT
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
                    syncStatus = "REFERENCE",
                    passkeyMode = PasskeyEntry.MODE_BW_COMPAT
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
                        syncStatus = syncStatus,
                        passkeyMode = PasskeyEntry.MODE_BW_COMPAT
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
                        syncStatus = syncStatus,
                        passkeyMode = PasskeyEntry.MODE_BW_COMPAT
                    )
                )
                updated++
            }
        }

        val staleEntries = passkeyDao.getAllByBitwardenCipherIdInVault(vault.id, cipher.id)
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
        return PasskeyCredentialIdCodec.normalize(credentialId)
    }

    private fun extractPasskeyUserNotes(notes: String?): String {
        if (notes.isNullOrBlank()) return ""
        return notes.substringBefore("---").trim()
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
        if (!looksLikeCipherString(encrypted)) return null
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

    private fun parseCardType(raw: String?): CardType {
        return when (raw?.trim()?.uppercase()) {
            "DEBIT" -> CardType.DEBIT
            "PREPAID" -> CardType.PREPAID
            else -> CardType.CREDIT
        }
    }

    private fun decryptCustomFields(
        fields: List<CipherFieldApiData>?,
        key: SymmetricCryptoKey
    ): List<DecryptedCustomField> {
        if (fields.isNullOrEmpty()) return emptyList()

        return fields.mapNotNull { field ->
            val name = decryptOrPlain(field.name, key).orEmpty().trim()
            if (name.isBlank()) return@mapNotNull null
            val value = decryptOrPlain(field.value, key).orEmpty()
            DecryptedCustomField(
                name = name,
                value = value,
                type = field.type
            )
        }
    }

    private fun decryptCustomFieldMap(
        fields: List<CipherFieldApiData>?,
        key: SymmetricCryptoKey
    ): Map<String, String> {
        return decryptCustomFields(fields, key).associate { it.name to it.value }
    }

    private fun parseLoginUris(
        uris: List<CipherUriApiData>?,
        key: SymmetricCryptoKey
    ): ParsedLoginUris {
        if (uris.isNullOrEmpty()) return ParsedLoginUris()

        var website = ""
        var appPackageName = ""
        uris.forEach { uriData ->
            val uri = decryptString(uriData.uri, key) ?: return@forEach
            when {
                uri.startsWith("androidapp://", ignoreCase = true) -> {
                    if (appPackageName.isBlank()) {
                        appPackageName = uri.removePrefix("androidapp://")
                    }
                }
                website.isBlank() -> website = uri
            }
        }
        return ParsedLoginUris(website = website, appPackageName = appPackageName)
    }

    private fun parseDocumentType(raw: String?): DocumentType? {
        val normalized = raw?.trim()?.uppercase() ?: return null
        return when (normalized) {
            "PASSPORT" -> DocumentType.PASSPORT
            "DRIVER_LICENSE", "DRIVERLICENCE", "DRIVING_LICENSE", "DRIVING_LICENCE" -> DocumentType.DRIVER_LICENSE
            "SOCIAL_SECURITY", "SSN" -> DocumentType.SOCIAL_SECURITY
            "ID_CARD", "IDENTITY_CARD", "IDENTITY" -> DocumentType.ID_CARD
            "OTHER" -> DocumentType.OTHER
            else -> null
        }
    }

    private fun parseBitwardenDeletedAt(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            Date.from(Instant.parse(raw))
        }.getOrNull() ?: Date()
    }

    private fun parseBitwardenArchivedAt(raw: String?): Date? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            Date.from(Instant.parse(raw))
        }.getOrNull()
    }

    private data class DecryptedCustomField(
        val name: String,
        val value: String,
        val type: Int
    )

    private fun List<DecryptedCustomField>.toCardCustomFields(): List<SecureCustomField> {
        val reserved = setOf(
            "monica_bank_name",
            "monica_card_type",
            "monica_billing_address",
            "monica_nickname",
            "monica_valid_from_month",
            "monica_valid_from_year",
            "monica_pin",
            "monica_iban",
            "monica_swift_bic",
            "monica_routing_number",
            "monica_account_number",
            "monica_branch_code",
            "monica_currency",
            "monica_customer_service_phone"
        )
        return filterNot { it.name in reserved }
            .map { it.toSecureCustomField() }
    }

    private fun List<DecryptedCustomField>.toDocumentCustomFields(): List<SecureCustomField> {
        val reserved = setOf(
            "monica_document_type",
            "monica_issue_date",
            "monica_expiry_date",
            "monica_issued_by",
            "monica_nationality",
            "monica_additional_info"
        )
        return filterNot { it.name in reserved }
            .map { it.toSecureCustomField() }
    }

    private fun DecryptedCustomField.toSecureCustomField(): SecureCustomField {
        return SecureCustomField(
            label = name,
            value = value,
            type = when (type) {
                1 -> SecureCustomFieldType.HIDDEN
                2 -> SecureCustomFieldType.BOOLEAN
                else -> SecureCustomFieldType.TEXT
            }
        )
    }
}

// CipherSyncResult 定义在 BitwardenSyncService.kt 中
