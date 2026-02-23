package takagi.ru.monica.bitwarden.service

import android.util.Base64
import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.mapper.*
import takagi.ru.monica.bitwarden.sync.SyncItemType
import takagi.ru.monica.data.*
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.DocumentType
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.data.model.TotpData
import java.util.Date
import java.security.KeyFactory
import java.security.KeyStore
import java.security.spec.PKCS8EncodedKeySpec

/**
 * 多类型 Cipher 上传处理器
 * 
 * 负责将本地创建的各类条目上传到 Bitwarden 服务器
 * 支持所有类型：Password, TOTP, Card, Note, Document, Passkey
 */
class CipherUploadProcessor(
    private val context: Context,
    private val apiManager: BitwardenApiManager = BitwardenApiManager()
) {
    companion object {
        private const val TAG = "CipherUploadProcessor"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private val CIPHER_STRING_PATTERN =
            Regex("^[0-9]+\\.[A-Za-z0-9+/_=-]+\\|[A-Za-z0-9+/_=-]+(?:\\|[A-Za-z0-9+/_=-]+)?$")
    }
    
    private val database = PasswordDatabase.getDatabase(context)
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()
    private val passkeyDao = database.passkeyDao()
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    /**
     * 上传单个 SecureItem 到 Bitwarden
     */
    suspend fun uploadSecureItem(
        vault: BitwardenVault,
        item: SecureItem,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): UploadItemResult {
        return try {
            val request = when (item.itemType) {
                ItemType.TOTP -> createTotpCipherRequest(item, symmetricKey)
                ItemType.BANK_CARD -> createCardCipherRequest(item, symmetricKey)
                ItemType.NOTE -> createSecureNoteCipherRequest(item, symmetricKey)
                ItemType.DOCUMENT -> createIdentityCipherRequest(item, symmetricKey)
                else -> return UploadItemResult.Error("Unsupported item type: ${item.itemType}")
            }
            
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.createCipher(
                authorization = "Bearer $accessToken",
                cipher = request
            )
            
            if (!response.isSuccessful) {
                return UploadItemResult.Error("Create cipher failed: ${response.code()}")
            }
            
            val createdCipher = response.body() ?: return UploadItemResult.Error("Empty response")
            
            // 更新本地条目
            val updatedItem = item.copy(
                bitwardenCipherId = createdCipher.id,
                bitwardenRevisionDate = createdCipher.revisionDate,
                bitwardenLocalModified = false,
                syncStatus = "SYNCED",
                updatedAt = Date()
            )
            secureItemDao.update(updatedItem)
            
            android.util.Log.d(TAG, "Uploaded SecureItem ${item.id} as cipher ${createdCipher.id}")
            UploadItemResult.Success(createdCipher.id)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Upload SecureItem failed: ${e.message}", e)
            UploadItemResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 更新单个 SecureItem 到 Bitwarden
     */
    suspend fun updateSecureItem(
        vault: BitwardenVault,
        item: SecureItem,
        cipherId: String,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): UploadItemResult {
        return try {
            val request = when (item.itemType) {
                ItemType.TOTP -> createTotpCipherRequest(item, symmetricKey)
                ItemType.BANK_CARD -> createCardCipherRequest(item, symmetricKey)
                ItemType.NOTE -> createSecureNoteCipherRequest(item, symmetricKey)
                ItemType.DOCUMENT -> createIdentityCipherRequest(item, symmetricKey)
                else -> return UploadItemResult.Error("Unsupported item type: ${item.itemType}")
            }

            val updateRequest = request.toUpdateRequest()
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.updateCipher(
                authorization = "Bearer $accessToken",
                cipherId = cipherId,
                cipher = updateRequest
            )

            if (!response.isSuccessful) {
                return UploadItemResult.Error("Update cipher failed: ${response.code()}")
            }

            val updatedCipher = response.body() ?: return UploadItemResult.Error("Empty response")
            val updatedItem = item.copy(
                bitwardenRevisionDate = updatedCipher.revisionDate,
                bitwardenLocalModified = false,
                syncStatus = "SYNCED",
                updatedAt = Date()
            )
            secureItemDao.update(updatedItem)
            UploadItemResult.Success(updatedCipher.id)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Update SecureItem failed: ${e.message}", e)
            UploadItemResult.Error(e.message ?: "Unknown error")
        }
    }
    
    /**
     * 上传 Passkey 元数据到 Bitwarden
     */
    suspend fun uploadPasskey(
        vault: BitwardenVault,
        passkey: PasskeyEntry,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): UploadItemResult {
        return try {
            suspend fun fail(message: String): UploadItemResult {
                passkeyDao.markFailed(passkey.credentialId)
                return UploadItemResult.Error(message)
            }

            if (!canSyncPasskeyToBitwarden(passkey)) {
                return fail("Legacy passkey cannot be synced to Bitwarden")
            }

            val normalizedPasskey = normalizePasskeyForUpload(passkey)
            val mapper = PasskeyMapper()
            val request = mapper.toCreateRequest(normalizedPasskey, null)
            if (request.login?.fido2Credentials.isNullOrEmpty()) {
                return fail(
                    "Passkey key material is missing or invalid; cannot sync as FIDO2 credential"
                )
            }
            
            // 加密请求
            val encryptedRequest = encryptCipherRequest(request, symmetricKey)
            
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.createCipher(
                authorization = "Bearer $accessToken",
                cipher = encryptedRequest
            )
            
            if (!response.isSuccessful) {
                return fail("Create cipher failed: ${response.code()}")
            }
            
            val createdCipher = response.body() ?: return fail("Empty response")
            if (createdCipher.login?.fido2Credentials.isNullOrEmpty()) {
                return fail("Server created cipher without FIDO2 credential")
            }
            
            // 更新本地 Passkey
            passkeyDao.markSynced(passkey.credentialId, createdCipher.id)
            
            android.util.Log.d(TAG, "Uploaded Passkey ${passkey.credentialId} as cipher ${createdCipher.id}")
            UploadItemResult.Success(createdCipher.id)
        } catch (e: Exception) {
            runCatching { passkeyDao.markFailed(passkey.credentialId) }
            android.util.Log.e(TAG, "Upload Passkey failed: ${e.message}", e)
            UploadItemResult.Error(e.message ?: "Unknown error")
        }
    }

    /**
     * 更新已存在的 Passkey Cipher（用于修复历史兼容字段）
     */
    suspend fun updatePasskey(
        vault: BitwardenVault,
        passkey: PasskeyEntry,
        cipherId: String,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): UploadItemResult {
        return try {
            suspend fun fail(message: String): UploadItemResult {
                passkeyDao.markFailed(passkey.credentialId)
                return UploadItemResult.Error(message)
            }

            if (!canSyncPasskeyToBitwarden(passkey)) {
                return fail("Legacy passkey cannot be synced to Bitwarden")
            }

            val normalizedPasskey = normalizePasskeyForUpload(passkey)
            val mapper = PasskeyMapper()
            val createRequest = mapper.toCreateRequest(normalizedPasskey, null)
            if (createRequest.login?.fido2Credentials.isNullOrEmpty()) {
                return fail(
                    "Passkey key material is missing or invalid; cannot sync as FIDO2 credential"
                )
            }

            val encryptedCreate = encryptCipherRequest(createRequest, symmetricKey)
            val updateRequest = encryptedCreate.toUpdateRequest()

            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.updateCipher(
                authorization = "Bearer $accessToken",
                cipherId = cipherId,
                cipher = updateRequest
            )

            if (!response.isSuccessful) {
                return fail("Update cipher failed: ${response.code()}")
            }

            val updatedCipher = response.body() ?: return fail("Empty response")
            if (updatedCipher.login?.fido2Credentials.isNullOrEmpty()) {
                return fail("Server updated cipher without FIDO2 credential")
            }

            passkeyDao.markSynced(passkey.credentialId, updatedCipher.id)
            UploadItemResult.Success(updatedCipher.id)
        } catch (e: Exception) {
            runCatching { passkeyDao.markFailed(passkey.credentialId) }
            android.util.Log.e(TAG, "Update Passkey failed: ${e.message}", e)
            UploadItemResult.Error(e.message ?: "Unknown error")
        }
    }

    private fun normalizePasskeyForUpload(passkey: PasskeyEntry): PasskeyEntry {
        if (isPkcs8PrivateKeyBase64(passkey.privateKeyAlias)) return passkey

        val alias = passkey.privateKeyAlias
        if (alias.isBlank()) return passkey

        return try {
            val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER)
            keyStore.load(null)
            val entry = keyStore.getEntry(alias, null) as? KeyStore.PrivateKeyEntry
            val encoded = entry?.privateKey?.encoded
            if (encoded == null || encoded.isEmpty()) {
                passkey
            } else {
                passkey.copy(privateKeyAlias = Base64.encodeToString(encoded, Base64.NO_WRAP))
            }
        } catch (_: Exception) {
            passkey
        }
    }

    private fun isPkcs8PrivateKeyBase64(value: String): Boolean {
        if (value.isBlank()) return false
        val decoded = runCatching { Base64.decode(value, Base64.NO_WRAP) }.getOrNull()
            ?: runCatching {
                Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            }.getOrNull()
            ?: return false
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
    
    /**
     * 批量上传待同步的 SecureItems
     */
    suspend fun uploadPendingSecureItems(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): BatchUploadResult {
        val pending = secureItemDao.getLocalEntriesPendingUpload(vault.id)
        
        if (pending.isEmpty()) {
            return BatchUploadResult(uploaded = 0, failed = 0, total = 0)
        }
        
        var uploaded = 0
        var failed = 0
        
        for (item in pending) {
            val result = uploadSecureItem(vault, item, accessToken, symmetricKey)
            when (result) {
                is UploadItemResult.Success -> uploaded++
                is UploadItemResult.Error -> failed++
            }
        }
        
        return BatchUploadResult(uploaded = uploaded, failed = failed, total = pending.size)
    }

    /**
     * 批量上传已修改的 SecureItems（已有 cipherId）
     */
    suspend fun uploadModifiedSecureItems(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): BatchUploadResult {
        val modifiedItems = secureItemDao.getLocalModifiedEntries(vault.id)
            .filter { !it.bitwardenCipherId.isNullOrBlank() }

        if (modifiedItems.isEmpty()) {
            return BatchUploadResult(uploaded = 0, failed = 0, total = 0)
        }

        var uploaded = 0
        var failed = 0

        for (item in modifiedItems) {
            val cipherId = item.bitwardenCipherId
            if (cipherId.isNullOrBlank()) {
                failed++
                continue
            }
            val result = updateSecureItem(vault, item, cipherId, accessToken, symmetricKey)
            when (result) {
                is UploadItemResult.Success -> uploaded++
                is UploadItemResult.Error -> failed++
            }
        }

        return BatchUploadResult(uploaded = uploaded, failed = failed, total = modifiedItems.size)
    }
    
    /**
     * 批量上传待同步的 Passkeys
     */
    suspend fun uploadPendingPasskeys(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): BatchUploadResult {
        val pending = passkeyDao.getLocalEntriesPendingUpload(vault.id)
            .filter(::canSyncPasskeyToBitwarden)
            .toMutableList()
        val vaultPasswordIds = passwordEntryDao.getEntriesByVaultId(vault.id).map { it.id }
        if (vaultPasswordIds.isNotEmpty()) {
            val boundCandidates = passkeyDao.getByBoundPasswordIds(vaultPasswordIds)
                .filter { passkey ->
                    canSyncPasskeyToBitwarden(passkey) &&
                    passkey.syncStatus != "REFERENCE" &&
                        passkey.bitwardenCipherId.isNullOrBlank() &&
                        passkey.bitwardenVaultId != vault.id
                }

            boundCandidates.forEach { candidate ->
                val reassigned = candidate.copy(
                    bitwardenVaultId = vault.id,
                    syncStatus = "PENDING"
                )
                passkeyDao.update(reassigned)
                pending.add(reassigned)
            }
        }

        val uniquePending = pending.distinctBy { it.credentialId }
        if (uniquePending.isEmpty()) {
            return BatchUploadResult(uploaded = 0, failed = 0, total = 0)
        }
        
        var uploaded = 0
        var failed = 0
        
        for (passkey in uniquePending) {
            val result = uploadPasskey(vault, passkey, accessToken, symmetricKey)
            when (result) {
                is UploadItemResult.Success -> uploaded++
                is UploadItemResult.Error -> failed++
            }
        }

        return BatchUploadResult(uploaded = uploaded, failed = failed, total = uniquePending.size)
    }

    /**
     * 批量更新已同步的 Passkeys（修复 counter / userHandle 等字段）
     */
    suspend fun uploadModifiedPasskeys(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): BatchUploadResult {
        val candidates = passkeyDao.getByBitwardenVaultId(vault.id)
            .filter { passkey ->
                canSyncPasskeyToBitwarden(passkey) &&
                passkey.syncStatus != "REFERENCE" &&
                    !passkey.bitwardenCipherId.isNullOrBlank() &&
                    passkey.privateKeyAlias.isNotBlank()
            }

        if (candidates.isEmpty()) {
            return BatchUploadResult(uploaded = 0, failed = 0, total = 0)
        }

        var uploaded = 0
        var failed = 0

        for (passkey in candidates) {
            val cipherId = passkey.bitwardenCipherId
            if (cipherId.isNullOrBlank()) {
                failed++
                continue
            }
            val result = updatePasskey(vault, passkey, cipherId, accessToken, symmetricKey)
            when (result) {
                is UploadItemResult.Success -> uploaded++
                is UploadItemResult.Error -> failed++
            }
        }

        return BatchUploadResult(uploaded = uploaded, failed = failed, total = candidates.size)
    }

    private fun canSyncPasskeyToBitwarden(passkey: PasskeyEntry): Boolean {
        return passkey.passkeyMode == PasskeyEntry.MODE_BW_COMPAT
    }
    
    // ========== 创建各类型 Cipher 请求 ==========
    
    private fun createTotpCipherRequest(
        item: SecureItem,
        symmetricKey: SymmetricCryptoKey
    ): CipherCreateRequest {
        val totpData = parseTotpData(item)
        
        val crypto = BitwardenCrypto
        
        return CipherCreateRequest(
            type = 1,  // Login with TOTP
            name = crypto.encryptString(item.title, symmetricKey),
            notes = item.notes.takeIf { it.isNotBlank() }?.let { 
                crypto.encryptString(it, symmetricKey) 
            },
            folderId = item.bitwardenFolderId,
            favorite = item.isFavorite,
            login = CipherLoginApiData(
                username = totpData.account.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                totp = crypto.encryptString(totpData.secret, symmetricKey),
                uris = totpData.issuer.takeIf { it.isNotBlank() }?.let {
                    listOf(CipherUriApiData(uri = crypto.encryptString("otpauth://totp/$it", symmetricKey)))
                }
            )
        )
    }
    
    private fun createCardCipherRequest(
        item: SecureItem,
        symmetricKey: SymmetricCryptoKey
    ): CipherCreateRequest {
        val cardData = parseBankCardData(item)
        
        val crypto = BitwardenCrypto
        
        return CipherCreateRequest(
            type = 3,  // Card
            name = crypto.encryptString(item.title, symmetricKey),
            notes = item.notes.takeIf { it.isNotBlank() }?.let { 
                crypto.encryptString(it, symmetricKey) 
            },
            folderId = item.bitwardenFolderId,
            favorite = item.isFavorite,
            card = CipherCardApiData(
                cardholderName = cardData.cardholderName.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                number = cardData.number.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                expMonth = cardData.expMonth.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                expYear = cardData.expYear.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                code = cardData.cvv.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                brand = cardData.bankName.ifBlank { cardData.brand }.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                }
            )
        )
    }
    
    private fun createSecureNoteCipherRequest(
        item: SecureItem,
        symmetricKey: SymmetricCryptoKey
    ): CipherCreateRequest {
        val noteData = parseNoteData(item)
        
        val crypto = BitwardenCrypto
        
        return CipherCreateRequest(
            type = 2,  // SecureNote
            name = crypto.encryptString(item.title, symmetricKey),
            notes = crypto.encryptString(noteData.content, symmetricKey),
            folderId = item.bitwardenFolderId,
            favorite = item.isFavorite,
            secureNote = CipherSecureNoteApiData(type = 0)
        )
    }
    
    private fun createIdentityCipherRequest(
        item: SecureItem,
        symmetricKey: SymmetricCryptoKey
    ): CipherCreateRequest {
        val docData = parseDocumentData(item)
        
        val crypto = BitwardenCrypto
        val identityNumberForLicense = docData.documentNumber.takeIf {
            it.isNotBlank() && docData.documentType == DocumentType.DRIVER_LICENSE.name
        }
        val identityNumberForPassport = docData.documentNumber.takeIf {
            it.isNotBlank() && docData.documentType == DocumentType.PASSPORT.name
        }
        val identityNumberForSsn = docData.documentNumber.takeIf {
            it.isNotBlank() && (
                docData.documentType == DocumentType.ID_CARD.name ||
                    docData.documentType == DocumentType.SOCIAL_SECURITY.name ||
                    docData.documentType == DocumentType.OTHER.name
                )
        }
        
        return CipherCreateRequest(
            type = 4,  // Identity
            name = crypto.encryptString(item.title, symmetricKey),
            notes = item.notes.takeIf { it.isNotBlank() }?.let { 
                crypto.encryptString(it, symmetricKey) 
            },
            folderId = item.bitwardenFolderId,
            favorite = item.isFavorite,
            identity = CipherIdentityApiData(
                firstName = docData.firstName.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                lastName = docData.lastName.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                licenseNumber = identityNumberForLicense?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                passportNumber = identityNumberForPassport?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                ssn = identityNumberForSsn?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                company = docData.issuingAuthority.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                },
                country = docData.country.takeIf { it.isNotBlank() }?.let {
                    crypto.encryptString(it, symmetricKey)
                }
            ),
            fields = buildEncryptedDocumentFields(docData, symmetricKey)
        )
    }

    private fun parseTotpData(item: SecureItem): TotpItemData {
        return try {
            val appData = json.decodeFromString<TotpData>(item.itemData)
            TotpItemData(
                secret = appData.secret,
                issuer = appData.issuer,
                account = appData.accountName,
                algorithm = appData.algorithm,
                digits = appData.digits,
                period = appData.period
            )
        } catch (_: Exception) {
            try {
                json.decodeFromString(TotpItemData.serializer(), item.itemData)
            } catch (_: Exception) {
                TotpItemData()
            }
        }
    }

    private fun parseBankCardData(item: SecureItem): CardItemData {
        return try {
            val appData = json.decodeFromString<BankCardData>(item.itemData)
            CardItemData(
                cardholderName = appData.cardholderName,
                number = appData.cardNumber,
                expMonth = appData.expiryMonth,
                expYear = appData.expiryYear,
                cvv = appData.cvv,
                bankName = appData.bankName,
                billingAddress = appData.billingAddress
            )
        } catch (_: Exception) {
            try {
                json.decodeFromString(CardItemData.serializer(), item.itemData)
            } catch (_: Exception) {
                CardItemData()
            }
        }
    }

    private fun parseNoteData(item: SecureItem): NoteItemData {
        return try {
            val appData = json.decodeFromString<NoteData>(item.itemData)
            NoteItemData(
                content = appData.content,
                isMarkdown = appData.isMarkdown,
                tags = appData.tags
            )
        } catch (_: Exception) {
            try {
                json.decodeFromString(NoteItemData.serializer(), item.itemData)
            } catch (_: Exception) {
                NoteItemData(content = item.notes)
            }
        }
    }

    private fun parseDocumentData(item: SecureItem): DocumentItemData {
        return try {
            val appData = json.decodeFromString<DocumentData>(item.itemData)
            val names = splitName(appData.fullName)
            DocumentItemData(
                documentType = appData.documentType.name,
                documentNumber = appData.documentNumber,
                issueDate = appData.issuedDate,
                expiryDate = appData.expiryDate,
                issuingAuthority = appData.issuedBy,
                country = appData.nationality,
                additionalInfo = appData.additionalInfo,
                firstName = names.first,
                lastName = names.second
            )
        } catch (_: Exception) {
            try {
                json.decodeFromString(DocumentItemData.serializer(), item.itemData)
            } catch (_: Exception) {
                DocumentItemData()
            }
        }
    }

    private fun buildEncryptedDocumentFields(
        docData: DocumentItemData,
        symmetricKey: SymmetricCryptoKey
    ): List<CipherFieldApiData>? {
        val crypto = BitwardenCrypto
        val rawFields = listOf(
            "monica_document_type" to docData.documentType,
            "monica_issue_date" to docData.issueDate,
            "monica_expiry_date" to docData.expiryDate,
            "monica_additional_info" to docData.additionalInfo
        ).filter { it.second.isNotBlank() }

        if (rawFields.isEmpty()) return null

        return rawFields.map { (name, value) ->
            CipherFieldApiData(
                name = crypto.encryptString(name, symmetricKey),
                value = crypto.encryptString(value, symmetricKey),
                type = 0
            )
        }
    }

    private fun splitName(fullName: String): Pair<String, String> {
        val normalized = fullName.trim()
        if (normalized.isBlank()) return "" to ""
        val parts = normalized.split(Regex("\\s+"))
        if (parts.size == 1) return parts[0] to ""
        return parts.dropLast(1).joinToString(" ") to parts.last()
    }

    private fun CipherCreateRequest.toUpdateRequest(): CipherUpdateRequest {
        return CipherUpdateRequest(
            type = type,
            folderId = folderId,
            name = name,
            notes = notes,
            login = login,
            card = card,
            identity = identity,
            secureNote = secureNote,
            fields = fields,
            favorite = favorite,
            reprompt = reprompt
        )
    }
    
    /**
     * 加密 CipherCreateRequest
     */
    private fun encryptCipherRequest(
        request: CipherCreateRequest,
        symmetricKey: SymmetricCryptoKey
    ): CipherCreateRequest {
        val crypto = BitwardenCrypto
        
        fun isEncrypted(value: String?): Boolean {
            if (value.isNullOrBlank()) return false
            if (!CIPHER_STRING_PATTERN.matches(value)) return false
            return runCatching { crypto.parseCipherString(value) }.isSuccess
        }

        fun encryptIfNeeded(value: String?): String? {
            if (value.isNullOrBlank()) return value
            return if (isEncrypted(value)) value else crypto.encryptString(value, symmetricKey)
        }
        
        return request.copy(
            name = encryptIfNeeded(request.name) ?: request.name,
            notes = encryptIfNeeded(request.notes),
            login = request.login?.let { login ->
                login.copy(
                    username = encryptIfNeeded(login.username),
                    password = encryptIfNeeded(login.password),
                    totp = encryptIfNeeded(login.totp),
                    uris = login.uris?.map { uri ->
                        uri.copy(
                            uri = encryptIfNeeded(uri.uri)
                        )
                    },
                    fido2Credentials = login.fido2Credentials?.map { fido ->
                        fido.copy(
                            credentialId = encryptIfNeeded(fido.credentialId),
                            keyType = encryptIfNeeded(fido.keyType),
                            keyAlgorithm = encryptIfNeeded(fido.keyAlgorithm),
                            keyCurve = encryptIfNeeded(fido.keyCurve),
                            keyValue = encryptIfNeeded(fido.keyValue),
                            rpId = encryptIfNeeded(fido.rpId),
                            rpName = encryptIfNeeded(fido.rpName),
                            counter = encryptIfNeeded(fido.counter),
                            userHandle = encryptIfNeeded(fido.userHandle),
                            userName = encryptIfNeeded(fido.userName),
                            userDisplayName = encryptIfNeeded(fido.userDisplayName),
                            discoverable = encryptIfNeeded(fido.discoverable),
                            // Bitwarden expects a parseable DateTime here, not a cipher string.
                            creationDate = fido.creationDate
                        )
                    },
                )
            }
        )
    }
}

/**
 * 单项上传结果
 */
sealed class UploadItemResult {
    data class Success(val cipherId: String) : UploadItemResult()
    data class Error(val message: String) : UploadItemResult()
}

/**
 * 批量上传结果
 */
data class BatchUploadResult(
    val uploaded: Int,
    val failed: Int,
    val total: Int
) {
    val success: Boolean get() = failed == 0
}
