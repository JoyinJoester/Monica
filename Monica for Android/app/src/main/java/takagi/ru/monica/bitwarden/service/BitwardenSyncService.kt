package takagi.ru.monica.bitwarden.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.bitwarden.api.*
import takagi.ru.monica.bitwarden.mapper.BitwardenSendMapper
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.SymmetricCryptoKey
import takagi.ru.monica.bitwarden.sync.EmptyVaultProtection
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.bitwarden.*
import takagi.ru.monica.security.SecurityManager
import java.util.Date

/**
 * Bitwarden 同步服务
 * 
 * 负责:
 * 1. 全量同步 - 首次登录或强制刷新
 * 2. 增量同步 - 基于 revision date 的差异同步
 * 3. 冲突检测和处理
 * 4. 离线操作队列管理
 * 
 * 安全规则:
 * - 同步失败时保留本地数据，不删除
 * - 冲突时优先保留本地修改，并备份服务器版本
 * - 所有数据库操作使用事务
 */
class BitwardenSyncService(
    private val context: Context,
    private val apiManager: BitwardenApiManager = BitwardenApiManager()
) {
    
    companion object {
        private const val TAG = "BitwardenSyncService"
    }
    
    private val database = PasswordDatabase.getDatabase(context)
    private val vaultDao = database.bitwardenVaultDao()
    private val folderDao = database.bitwardenFolderDao()
    private val sendDao = database.bitwardenSendDao()
    private val conflictDao = database.bitwardenConflictBackupDao()
    private val pendingOpDao = database.bitwardenPendingOperationDao()
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()
    private val passkeyDao = database.passkeyDao()
    private val securityManager = SecurityManager(context)
    
    // 多类型 Cipher 同步处理器
    private val cipherSyncProcessor = CipherSyncProcessor(context)
    private val cipherUploadProcessor = CipherUploadProcessor(context, apiManager)
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private data class ParsedLoginUris(
        val website: String = "",
        val appPackageName: String = ""
    )
    
    /**
     * 执行全量同步
     * 
     * @param vault Vault 配置
     * @param accessToken 访问令牌
     * @param symmetricKey 对称加密密钥
     */
    suspend fun fullSync(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): SyncResult = withContext(Dispatchers.IO) {
        android.util.Log.i(TAG, "Starting full sync for vault ${vault.id}")
        
        try {
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.sync(
                authorization = "Bearer $accessToken"
            )
            
            if (!response.isSuccessful) {
                return@withContext SyncResult.Error(
                    "Sync failed: ${response.code()} ${response.message()}"
                )
            }
            
            val syncResponse = response.body() ?: return@withContext SyncResult.Error(
                "Empty sync response"
            )
            
            // ===== 空 Vault 保护检查 =====
            val serverCipherCount = syncResponse.ciphers.size
            val localCipherCount = passwordEntryDao.getBitwardenEntriesCount(vault.id)
            val isFirstSync = vault.lastSyncAt == null
            
            val protectionResult = EmptyVaultProtection.checkSyncAllowed(
                vaultId = vault.id,
                localCipherCount = localCipherCount,
                serverCipherCount = serverCipherCount,
                isFirstSync = isFirstSync
            )
            
            when (protectionResult) {
                is EmptyVaultProtection.CheckResult.Blocked -> {
                    android.util.Log.w(TAG, "⚠️ 空 Vault 保护触发: ${protectionResult.reason}")
                    // 发送警告事件
                    EmptyVaultProtection.emitEmptyVaultDetected(
                        vaultId = vault.id,
                        localCount = protectionResult.localCount,
                        serverCount = protectionResult.serverCount
                    )
                    return@withContext SyncResult.EmptyVaultBlocked(
                        localCount = protectionResult.localCount,
                        serverCount = protectionResult.serverCount,
                        reason = protectionResult.reason
                    )
                }
                is EmptyVaultProtection.CheckResult.FirstSyncAllowed -> {
                    android.util.Log.i(TAG, "首次同步，允许空 Vault")
                }
                is EmptyVaultProtection.CheckResult.Allowed -> {
                    android.util.Log.d(TAG, "同步检查通过")
                }
            }
            
            // 额外检查：是否有显著数据丢失风险
            if (EmptyVaultProtection.checkSignificantDataLoss(localCipherCount, serverCipherCount)) {
                android.util.Log.w(TAG, "⚠️ 检测到潜在数据丢失: 本地 $localCipherCount 条 → 服务器 $serverCipherCount 条")
                // 这里不阻止同步，但记录警告
            }
            // ===== 空 Vault 保护检查结束 =====
            
            // 处理同步数据
            val result = processSyncResponse(vault, syncResponse, symmetricKey)
            
            // 更新 Vault 同步状态
            val now = System.currentTimeMillis()
            vaultDao.updateSyncStatus(
                vaultId = vault.id,
                lastSyncAt = now,
                revisionDate = syncResponse.profile.securityStamp
            )
            
            android.util.Log.i(TAG, "Full sync completed: $result")
            result
            
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Full sync failed", e)
            SyncResult.Error("Sync failed: ${e.message}")
        }
    }
    
    /**
     * 处理同步响应数据
     */
    private suspend fun processSyncResponse(
        vault: BitwardenVault,
        response: SyncResponse,
        symmetricKey: SymmetricCryptoKey
    ): SyncResult {
        var foldersAdded = 0
        var ciphersAdded = 0
        var ciphersUpdated = 0
        var conflictsDetected = 0
        var sendsSynced = 0
        val activeServerCipherIds = response.ciphers
            .asSequence()
            .filter { it.deletedDate == null }
            .map { it.id }
            .toList()
        
        // 1. 同步文件夹
        response.folders.forEach { folderApi ->
            try {
                val decryptedName = decryptFolderName(folderApi.name, symmetricKey)
                
                val existingFolder = folderDao.getFolderByBitwardenId(folderApi.id)
                if (existingFolder == null) {
                    // 新文件夹
                    folderDao.upsert(
                        BitwardenFolder(
                            vaultId = vault.id,
                            bitwardenFolderId = folderApi.id,
                            name = decryptedName,
                            encryptedName = folderApi.name,
                            revisionDate = folderApi.revisionDate
                        )
                    )
                    foldersAdded++
                } else {
                    // 更新文件夹
                    folderDao.update(
                        existingFolder.copy(
                            name = decryptedName,
                            encryptedName = folderApi.name,
                            revisionDate = folderApi.revisionDate,
                            lastSyncedAt = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to sync folder ${folderApi.id}: ${e.message}")
            }
        }
        
        // 2. 同步 Ciphers (使用新的多类型处理器)
        response.ciphers.forEach { cipherApi ->
            try {
                // 使用 CipherSyncProcessor 处理所有类型
                val result = cipherSyncProcessor.syncCipherFromServer(vault, cipherApi, symmetricKey)
                when (result) {
                    is CipherSyncResult.Added -> ciphersAdded++
                    is CipherSyncResult.Updated -> ciphersUpdated++
                    is CipherSyncResult.Conflict -> conflictsDetected++
                    is CipherSyncResult.Skipped -> { /* 跳过 */ }
                    is CipherSyncResult.Error -> {
                        android.util.Log.w(TAG, "Cipher sync error: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to sync cipher ${cipherApi.id}: ${e.message}")
            }
        }

        // 2.1 清理服务器已不存在的本地 Cipher（delete-wins）
        if (activeServerCipherIds.isEmpty()) {
            passwordEntryDao.deleteAllSyncedBitwardenEntries(vault.id)
            secureItemDao.deleteAllSyncedBitwardenEntries(vault.id)
            passkeyDao.deleteAllByBitwardenVaultId(vault.id)
        } else {
            passwordEntryDao.deleteBitwardenEntriesNotIn(vault.id, activeServerCipherIds)
            secureItemDao.deleteBitwardenEntriesNotIn(vault.id, activeServerCipherIds)
            passkeyDao.deleteBitwardenEntriesNotIn(vault.id, activeServerCipherIds)
        }

        // 3. 清理已删除的文件夹 (服务器上不存在的)
        val serverFolderIds = response.folders.map { it.id }
        folderDao.deleteNotIn(vault.id, serverFolderIds)

        // 4. 同步 Sends
        response.sends?.forEach { sendApi ->
            try {
                val synced = syncSend(vault, sendApi, symmetricKey)
                if (synced) {
                    sendsSynced++
                }
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to sync send ${sendApi.id}: ${e.message}")
            }
        }
        response.sends?.let { sends ->
            val serverSendIds = sends.map { it.id }
            if (serverSendIds.isEmpty()) {
                sendDao.deleteByVault(vault.id)
            } else {
                sendDao.deleteNotIn(vault.id, serverSendIds)
            }
        }

        if (sendsSynced > 0) {
            android.util.Log.i(TAG, "Sends synced: $sendsSynced")
        }
        
        return SyncResult.Success(
            foldersAdded = foldersAdded,
            ciphersAdded = ciphersAdded,
            ciphersUpdated = ciphersUpdated,
            conflictsDetected = conflictsDetected
        )
    }

    private suspend fun syncSend(
        vault: BitwardenVault,
        sendApi: SendApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): Boolean {
        val mapped = BitwardenSendMapper.mapApiToEntity(
            vaultId = vault.id,
            serverUrl = vault.serverUrl,
            api = sendApi,
            vaultKey = symmetricKey
        ) ?: return false

        val existing = sendDao.getBySendId(vault.id, mapped.bitwardenSendId)
        val now = System.currentTimeMillis()
        val entity = if (existing == null) {
            mapped.copy(
                createdAt = now,
                updatedAt = now,
                lastSyncedAt = now
            )
        } else {
            mapped.copy(
                id = existing.id,
                createdAt = existing.createdAt,
                updatedAt = now,
                lastSyncedAt = now
            )
        }
        sendDao.upsert(entity)
        return true
    }
    
    /**
     * 同步单个 Cipher
     */
    private suspend fun syncCipher(
        vault: BitwardenVault,
        cipherApi: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): CipherSyncResult {
        // 只处理 Login 类型的 Cipher (类型 1)
        if (cipherApi.type != 1) {
            return CipherSyncResult.Skipped("Only login ciphers are supported")
        }
        
        // 跳过已删除的 Cipher
        if (cipherApi.deletedDate != null) {
            return CipherSyncResult.Skipped("Cipher is deleted")
        }
        
        // 查找本地是否存在此 Cipher
        val existingEntry = passwordEntryDao.getByBitwardenCipherId(cipherApi.id)
        
        if (existingEntry == null) {
            // 新建条目
            val newEntry = cipherToPasswordEntry(vault, cipherApi, symmetricKey)
            if (newEntry != null) {
                passwordEntryDao.insert(newEntry)
                return CipherSyncResult.Added
            } else {
                return CipherSyncResult.Error("Failed to convert cipher")
            }
        } else {
            // 检查是否有本地修改
            if (existingEntry.bitwardenLocalModified) {
                // 检测冲突
                if (existingEntry.bitwardenRevisionDate != cipherApi.revisionDate) {
                    // 版本冲突 - 创建备份
                    createConflictBackup(
                        vault = vault,
                        entry = existingEntry,
                        serverCipher = cipherApi,
                        conflictType = BitwardenConflictBackup.TYPE_CONCURRENT_EDIT
                    )
                    return CipherSyncResult.Conflict
                }
            }
            
            // 更新条目
            val updatedEntry = updatePasswordEntryFromCipher(
                existingEntry, vault.id, cipherApi, symmetricKey
            )
            if (updatedEntry != null) {
                passwordEntryDao.update(updatedEntry)
                return CipherSyncResult.Updated
            } else {
                return CipherSyncResult.Error("Failed to update entry")
            }
        }
    }
    
    /**
     * 将 Cipher 转换为 PasswordEntry
     */
    private fun cipherToPasswordEntry(
        vault: BitwardenVault,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): PasswordEntry? {
        try {
            val login = cipher.login ?: return null
            
            // 解密字段
            val name = decryptString(cipher.name, symmetricKey) ?: "Untitled"
            val username = decryptString(login.username, symmetricKey) ?: ""
            val decryptedPassword = decryptString(login.password, symmetricKey)
            if (!login.password.isNullOrBlank() && decryptedPassword == null) {
                android.util.Log.w(TAG, "Skip cipher ${cipher.id}: password decrypt failed")
                return null
            }
            val password = decryptedPassword ?: ""
            val notes = decryptString(cipher.notes, symmetricKey) ?: ""
            val totp = decryptString(login.totp, symmetricKey) ?: ""
            val parsedUris = parseLoginUris(login.uris, symmetricKey)
            val customFields = parsePasswordCustomFieldMap(cipher.fields, symmetricKey)
            val encryptedPassword = securityManager.encryptData(password)
            
            return PasswordEntry(
                title = name,
                website = parsedUris.website,
                username = username,
                password = encryptedPassword,
                notes = notes,
                authenticatorKey = totp,
                appPackageName = customFields["monica_app_package"]
                    ?: customFields["appPackageName"]
                    ?: parsedUris.appPackageName,
                appName = customFields["monica_app_name"]
                    ?: customFields["appName"]
                    ?: "",
                email = customFields["monica_email"]
                    ?: customFields["email"]
                    ?: "",
                phone = customFields["monica_phone"]
                    ?: customFields["phone"]
                    ?: "",
                addressLine = customFields["monica_address_line"]
                    ?: customFields["addressLine"]
                    ?: customFields["address"]
                    ?: "",
                city = customFields["monica_city"] ?: customFields["city"] ?: "",
                state = customFields["monica_state"] ?: customFields["state"] ?: "",
                zipCode = customFields["monica_zip_code"]
                    ?: customFields["zipCode"]
                    ?: "",
                country = customFields["monica_country"] ?: customFields["country"] ?: "",
                passkeyBindings = customFields["monica_passkey_bindings"].orEmpty(),
                isFavorite = cipher.favorite,
                createdAt = Date(),
                updatedAt = Date(),
                // Bitwarden 关联字段
                bitwardenVaultId = vault.id,
                bitwardenCipherId = cipher.id,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenCipherType = cipher.type,
                bitwardenLocalModified = false
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to convert cipher: ${e.message}")
            return null
        }
    }
    
    /**
     * 从 Cipher 更新 PasswordEntry
     */
    private fun updatePasswordEntryFromCipher(
        entry: PasswordEntry,
        vaultId: Long,
        cipher: CipherApiResponse,
        symmetricKey: SymmetricCryptoKey
    ): PasswordEntry? {
        try {
            val login = cipher.login ?: return null
            
            val name = decryptString(cipher.name, symmetricKey) ?: entry.title
            val username = decryptString(login.username, symmetricKey) ?: entry.username
            val decryptedPassword = decryptString(login.password, symmetricKey)
            val encryptedPassword = decryptedPassword?.let { securityManager.encryptData(it) } ?: entry.password
            val notes = decryptString(cipher.notes, symmetricKey) ?: entry.notes
            val totp = decryptString(login.totp, symmetricKey) ?: entry.authenticatorKey
            val parsedUris = parseLoginUris(login.uris, symmetricKey)
            val customFields = parsePasswordCustomFieldMap(cipher.fields, symmetricKey)
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
            
            return entry.copy(
                title = name,
                website = parsedUris.website.ifBlank { entry.website },
                username = username,
                password = encryptedPassword,
                notes = notes,
                authenticatorKey = totp,
                appPackageName = remoteAppPackage.ifBlank { entry.appPackageName },
                appName = remoteAppName.ifBlank { entry.appName },
                email = remoteEmail.ifBlank { entry.email },
                phone = remotePhone.ifBlank { entry.phone },
                addressLine = remoteAddress.ifBlank { entry.addressLine },
                city = remoteCity.ifBlank { entry.city },
                state = remoteState.ifBlank { entry.state },
                zipCode = remoteZip.ifBlank { entry.zipCode },
                country = remoteCountry.ifBlank { entry.country },
                passkeyBindings = remotePasskeyBindings.ifBlank { entry.passkeyBindings },
                isFavorite = cipher.favorite,
                updatedAt = Date(),
                bitwardenVaultId = vaultId,
                bitwardenFolderId = cipher.folderId,
                bitwardenRevisionDate = cipher.revisionDate,
                bitwardenLocalModified = false
            )
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to update entry from cipher: ${e.message}")
            return null
        }
    }
    
    /**
     * 创建冲突备份
     */
    private suspend fun createConflictBackup(
        vault: BitwardenVault,
        entry: PasswordEntry,
        serverCipher: CipherApiResponse,
        conflictType: String
    ) {
        try {
            val localJson = json.encodeToString(
                mapOf(
                    "id" to entry.id.toString(),
                    "title" to entry.title,
                    "website" to entry.website,
                    "username" to entry.username,
                    "notes" to entry.notes
                    // 不包含密码等敏感数据的明文
                )
            )
            
            val serverJson = json.encodeToString(
                mapOf(
                    "id" to serverCipher.id,
                    "revisionDate" to serverCipher.revisionDate
                )
            )
            
            conflictDao.insert(
                BitwardenConflictBackup(
                    vaultId = vault.id,
                    entryId = entry.id,
                    bitwardenCipherId = serverCipher.id,
                    conflictType = conflictType,
                    localDataJson = localJson,
                    serverDataJson = serverJson,
                    localRevisionDate = entry.bitwardenRevisionDate,
                    serverRevisionDate = serverCipher.revisionDate,
                    entryTitle = entry.title,
                    description = "本地和服务器同时修改了此条目"
                )
            )
            
            android.util.Log.w(TAG, "Created conflict backup for entry ${entry.id}")
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to create conflict backup: ${e.message}")
        }
    }
    
    /**
     * 解密文件夹名称
     */
    private fun decryptFolderName(encrypted: String?, key: SymmetricCryptoKey): String {
        if (encrypted.isNullOrBlank()) return "Unnamed Folder"
        return try {
            takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.decryptToString(encrypted, key)
        } catch (e: Exception) {
            "Unnamed Folder"
        }
    }
    
    /**
     * 解密字符串
     */
    private fun decryptString(encrypted: String?, key: SymmetricCryptoKey): String? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            takagi.ru.monica.bitwarden.crypto.BitwardenCrypto.decryptToString(encrypted, key)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 处理待处理的操作队列
     */
    suspend fun processPendingOperations(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): Int = withContext(Dispatchers.IO) {
        val pendingOps = pendingOpDao.getRunnableOperationsByVault(vault.id)
        var processed = 0
        
        for (op in pendingOps) {
            try {
                val success = when (op.operationType) {
                    BitwardenPendingOperation.OP_CREATE -> processCreateOperation(vault, op, accessToken, symmetricKey)
                    BitwardenPendingOperation.OP_UPDATE -> processUpdateOperation(vault, op, accessToken, symmetricKey)
                    BitwardenPendingOperation.OP_DELETE -> processDeleteOperation(vault, op, accessToken)
                    BitwardenPendingOperation.OP_RESTORE -> processRestoreOperation(vault, op, accessToken)
                    else -> false
                }
                
                if (success) {
                    pendingOpDao.markCompleted(op.id)
                    processed++
                } else {
                    pendingOpDao.updateStatus(op.id, BitwardenPendingOperation.STATUS_FAILED)
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to process operation ${op.id}: ${e.message}")
                pendingOpDao.updateStatus(
                    id = op.id,
                    status = BitwardenPendingOperation.STATUS_FAILED,
                    lastError = e.message
                )
            }
        }
        
        processed
    }
    
    private suspend fun processCreateOperation(
        vault: BitwardenVault,
        op: BitwardenPendingOperation,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): Boolean {
        val entryId = op.entryId ?: return false
        val entry = passwordEntryDao.getPasswordEntryById(entryId) ?: return false
        return uploadLocalEntry(vault, entry, accessToken, symmetricKey)
    }
    
    private suspend fun processUpdateOperation(
        vault: BitwardenVault,
        op: BitwardenPendingOperation,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): Boolean {
        val entryId = op.entryId ?: return false
        val entry = passwordEntryDao.getPasswordEntryById(entryId) ?: return false
        val cipherId = entry.bitwardenCipherId ?: return false
        return updateRemoteCipher(vault, entry, cipherId, accessToken, symmetricKey)
    }
    
    private suspend fun processDeleteOperation(
        vault: BitwardenVault,
        op: BitwardenPendingOperation,
        accessToken: String
    ): Boolean {
        val cipherId = op.bitwardenCipherId ?: return false
        return deleteRemoteCipher(vault, cipherId, accessToken)
    }

    private suspend fun processRestoreOperation(
        vault: BitwardenVault,
        op: BitwardenPendingOperation,
        accessToken: String
    ): Boolean {
        val cipherId = op.bitwardenCipherId ?: return false
        return restoreRemoteCipher(vault, cipherId, accessToken)
    }
    
    // ========== 上传本地条目到 Bitwarden ==========
    
    /**
     * 上传本地创建的条目到 Bitwarden 服务器
     * 用于处理在 Monica 本地创建但标记为 Bitwarden 存储的条目
     */
    suspend fun uploadLocalEntries(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): UploadResult = withContext(Dispatchers.IO) {
        android.util.Log.i(TAG, "Checking for local entries to upload for vault ${vault.id}")
        
        // 查找所有需要上传的条目：有 bitwardenVaultId 但没有 bitwardenCipherId
        val entriesToUpload = passwordEntryDao.getLocalEntriesPendingUpload(vault.id)
        
        var uploaded = 0
        var failed = 0

        if (entriesToUpload.isNotEmpty()) {
            android.util.Log.i(TAG, "Found ${entriesToUpload.size} password entries to upload")
        }

        for (entry in entriesToUpload) {
            try {
                val success = uploadLocalEntry(vault, entry, accessToken, symmetricKey)
                if (success) {
                    uploaded++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to upload entry ${entry.id}: ${e.message}")
                failed++
            }
        }
        
        // 同步上传 SecureItems
        val secureResult = cipherUploadProcessor.uploadPendingSecureItems(vault, accessToken, symmetricKey)
        uploaded += secureResult.uploaded
        failed += secureResult.failed

        // 同步上传 Passkeys（仅新增）
        val passkeyResult = cipherUploadProcessor.uploadPendingPasskeys(vault, accessToken, symmetricKey)
        uploaded += passkeyResult.uploaded
        failed += passkeyResult.failed

        android.util.Log.i(
            TAG,
            "Upload complete: $uploaded uploaded, $failed failed (password + secure items + passkeys)"
        )
        UploadResult.Success(uploaded = uploaded, failed = failed)
    }

    /**
     * 上传本地已修改的 Bitwarden 条目（已有 cipherId）
     * 用于处理在 Monica 中编辑过的 Bitwarden 密码条目
     */
    suspend fun uploadModifiedEntries(
        vault: BitwardenVault,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): UploadResult = withContext(Dispatchers.IO) {
        android.util.Log.i(TAG, "Checking for modified entries to upload for vault ${vault.id}")

        val modifiedEntries = passwordEntryDao
            .getEntriesWithPendingBitwardenSync(vault.id)
            .filter { !it.bitwardenCipherId.isNullOrBlank() }

        var uploaded = 0
        var failed = 0

        if (modifiedEntries.isNotEmpty()) {
            android.util.Log.i(TAG, "Found ${modifiedEntries.size} modified password entries to upload")
        }

        for (entry in modifiedEntries) {
            try {
                val cipherId = entry.bitwardenCipherId
                if (cipherId.isNullOrBlank()) {
                    failed++
                    continue
                }
                val success = updateRemoteCipher(vault, entry, cipherId, accessToken, symmetricKey)
                if (success) {
                    uploaded++
                } else {
                    failed++
                }
            } catch (e: Exception) {
                android.util.Log.e(TAG, "Failed to upload modified entry ${entry.id}: ${e.message}")
                failed++
            }
        }

        // 同步上传已修改的 SecureItems（Passkey 暂不支持修改上传）
        val secureResult = cipherUploadProcessor.uploadModifiedSecureItems(vault, accessToken, symmetricKey)
        uploaded += secureResult.uploaded
        failed += secureResult.failed

        android.util.Log.i(TAG, "Modified upload complete: $uploaded uploaded, $failed failed")
        UploadResult.Success(uploaded = uploaded, failed = failed)
    }
    
    /**
     * 上传单个本地条目到 Bitwarden
     */
    private suspend fun uploadLocalEntry(
        vault: BitwardenVault,
        entry: PasswordEntry,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): Boolean {
        try {
            // 构建加密的 Cipher 请求
            val createRequest = passwordEntryToCipherRequest(entry, symmetricKey)
            
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.createCipher(
                authorization = "Bearer $accessToken",
                cipher = createRequest
            )
            
            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "Create cipher failed: ${response.code()} ${response.message()}")
                return false
            }
            
            val createdCipher = response.body() ?: return false
            
            // 更新本地条目，添加服务器返回的 cipherId 和 revisionDate
            val updatedEntry = entry.copy(
                bitwardenCipherId = createdCipher.id,
                bitwardenRevisionDate = createdCipher.revisionDate,
                bitwardenLocalModified = false,
                updatedAt = Date()
            )
            passwordEntryDao.update(updatedEntry)
            
            android.util.Log.d(TAG, "Successfully uploaded entry ${entry.id} as cipher ${createdCipher.id}")
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Upload entry failed: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 更新远程 Cipher
     */
    private suspend fun updateRemoteCipher(
        vault: BitwardenVault,
        entry: PasswordEntry,
        cipherId: String,
        accessToken: String,
        symmetricKey: SymmetricCryptoKey
    ): Boolean {
        try {
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val mergedFields = runCatching {
                val remote = vaultApi.getCipher(
                    authorization = "Bearer $accessToken",
                    cipherId = cipherId
                )
                if (remote.isSuccessful) {
                    mergeCipherFieldsPreservingUnknown(
                        localFields = buildEncryptedPasswordCustomFields(entry, symmetricKey),
                        remoteFields = remote.body()?.fields,
                        symmetricKey = symmetricKey
                    )
                } else {
                    null
                }
            }.getOrNull()

            val updateRequest = passwordEntryToCipherUpdateRequest(
                entry = entry,
                symmetricKey = symmetricKey,
                mergedFields = mergedFields
            )

            val response = vaultApi.updateCipher(
                authorization = "Bearer $accessToken",
                cipherId = cipherId,
                cipher = updateRequest
            )
            
            if (!response.isSuccessful) {
                android.util.Log.e(TAG, "Update cipher failed: ${response.code()}")
                return false
            }
            
            val updatedCipher = response.body() ?: return false
            
            // 更新本地条目
            val updatedEntry = entry.copy(
                bitwardenRevisionDate = updatedCipher.revisionDate,
                bitwardenLocalModified = false,
                updatedAt = Date()
            )
            passwordEntryDao.update(updatedEntry)
            
            return true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Update remote cipher failed: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 删除远程 Cipher
     */
    private suspend fun deleteRemoteCipher(
        vault: BitwardenVault,
        cipherId: String,
        accessToken: String
    ): Boolean {
        try {
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.deleteCipher(
                authorization = "Bearer $accessToken",
                cipherId = cipherId
            )
             
            return response.isSuccessful || response.code() == 404
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Delete remote cipher failed: ${e.message}", e)
            return false
        }
    }

    private suspend fun restoreRemoteCipher(
        vault: BitwardenVault,
        cipherId: String,
        accessToken: String
    ): Boolean {
        try {
            val vaultApi = apiManager.getVaultApi(vault.apiUrl)
            val response = vaultApi.restoreCipher(
                authorization = "Bearer $accessToken",
                cipherId = cipherId
            )

            return response.isSuccessful || response.code() == 400 || response.code() == 404
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Restore remote cipher failed: ${e.message}", e)
            return false
        }
    }
    
    /**
     * 将 PasswordEntry 转换为加密的 CipherCreateRequest
     */
    private fun passwordEntryToCipherRequest(
        entry: PasswordEntry,
        symmetricKey: SymmetricCryptoKey
    ): CipherCreateRequest {
        val crypto = takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
        val plainPassword = resolvePlainPasswordForBitwardenUpload(entry.password, entry.id)
        
        // 加密各个字段
        val encryptedName = crypto.encryptString(entry.title, symmetricKey)
        val encryptedNotes = if (entry.notes.isNotBlank()) {
            crypto.encryptString(entry.notes, symmetricKey)
        } else null
        
        // 构建 Login 数据
        val loginData = CipherLoginApiData(
            username = if (entry.username.isNotBlank()) {
                crypto.encryptString(entry.username, symmetricKey)
            } else null,
            password = if (plainPassword.isNotBlank()) {
                crypto.encryptString(plainPassword, symmetricKey)
            } else null,
            totp = if (entry.authenticatorKey.isNotBlank()) {
                crypto.encryptString(entry.authenticatorKey, symmetricKey)
            } else null,
            uris = buildEncryptedLoginUris(entry, symmetricKey)
        )
        
        return CipherCreateRequest(
            type = 1, // Login type
            folderId = entry.bitwardenFolderId,
            name = encryptedName,
            notes = encryptedNotes,
            login = loginData,
            fields = buildEncryptedPasswordCustomFields(entry, symmetricKey),
            favorite = entry.isFavorite
        )
    }
    
    /**
     * 将 PasswordEntry 转换为加密的 CipherUpdateRequest
     */
    private fun passwordEntryToCipherUpdateRequest(
        entry: PasswordEntry,
        symmetricKey: SymmetricCryptoKey,
        mergedFields: List<CipherFieldApiData>? = null
    ): CipherUpdateRequest {
        val crypto = takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
        val plainPassword = resolvePlainPasswordForBitwardenUpload(entry.password, entry.id)
        
        val encryptedName = crypto.encryptString(entry.title, symmetricKey)
        val encryptedNotes = if (entry.notes.isNotBlank()) {
            crypto.encryptString(entry.notes, symmetricKey)
        } else null
        
        val loginData = CipherLoginApiData(
            username = if (entry.username.isNotBlank()) {
                crypto.encryptString(entry.username, symmetricKey)
            } else null,
            password = if (plainPassword.isNotBlank()) {
                crypto.encryptString(plainPassword, symmetricKey)
            } else null,
            totp = if (entry.authenticatorKey.isNotBlank()) {
                crypto.encryptString(entry.authenticatorKey, symmetricKey)
            } else null,
            uris = buildEncryptedLoginUris(entry, symmetricKey)
        )
        
        return CipherUpdateRequest(
            type = 1,
            folderId = entry.bitwardenFolderId,
            name = encryptedName,
            notes = encryptedNotes,
            login = loginData,
            fields = mergedFields ?: buildEncryptedPasswordCustomFields(entry, symmetricKey),
            favorite = entry.isFavorite
        )
    }

    private fun mergeCipherFieldsPreservingUnknown(
        localFields: List<CipherFieldApiData>?,
        remoteFields: List<CipherFieldApiData>?,
        symmetricKey: SymmetricCryptoKey
    ): List<CipherFieldApiData>? {
        if (localFields.isNullOrEmpty()) return remoteFields
        if (remoteFields.isNullOrEmpty()) return localFields

        val localFieldNames = localFields.mapNotNull { field ->
            decryptOrPlain(field.name, symmetricKey)?.trim()?.takeIf { it.isNotEmpty() }
        }.toSet()

        val preservedRemote = remoteFields.filter { remote ->
            val remoteName = decryptOrPlain(remote.name, symmetricKey)?.trim()
            if (remoteName.isNullOrEmpty()) {
                true
            } else {
                remoteName !in localFieldNames
            }
        }

        return (preservedRemote + localFields).ifEmpty { null }
    }

    private fun buildEncryptedLoginUris(
        entry: PasswordEntry,
        symmetricKey: SymmetricCryptoKey
    ): List<CipherUriApiData>? {
        val crypto = takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
        val uris = mutableListOf<CipherUriApiData>()
        if (entry.website.isNotBlank()) {
            uris.add(
                CipherUriApiData(
                    uri = crypto.encryptString(entry.website, symmetricKey),
                    match = null
                )
            )
        }
        if (entry.appPackageName.isNotBlank()) {
            val pkg = entry.appPackageName.removePrefix("androidapp://")
            uris.add(
                CipherUriApiData(
                    uri = crypto.encryptString("androidapp://$pkg", symmetricKey),
                    match = null
                )
            )
        }
        return uris.ifEmpty { null }
    }

    private fun buildEncryptedPasswordCustomFields(
        entry: PasswordEntry,
        symmetricKey: SymmetricCryptoKey
    ): List<CipherFieldApiData>? {
        val crypto = takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
        val fields = mutableListOf<Pair<String, String>>()

        fun addField(name: String, value: String) {
            if (value.isNotBlank()) fields.add(name to value)
        }

        addField("monica_app_package", entry.appPackageName)
        addField("appPackageName", entry.appPackageName)
        addField("monica_app_name", entry.appName)
        addField("appName", entry.appName)
        addField("monica_email", entry.email)
        addField("email", entry.email)
        addField("monica_phone", entry.phone)
        addField("phone", entry.phone)
        addField("monica_address_line", entry.addressLine)
        addField("monica_city", entry.city)
        addField("monica_state", entry.state)
        addField("monica_zip_code", entry.zipCode)
        addField("monica_country", entry.country)
        addField("monica_passkey_bindings", entry.passkeyBindings)

        val legacyAddress = listOf(entry.addressLine, entry.city, entry.state, entry.zipCode, entry.country)
            .filter { it.isNotBlank() }
            .joinToString(", ")
        addField("address", legacyAddress)

        if (fields.isEmpty()) return null

        return fields.map { (name, value) ->
            CipherFieldApiData(
                name = crypto.encryptString(name, symmetricKey),
                value = crypto.encryptString(value, symmetricKey),
                type = 0
            )
        }
    }

    private fun parseLoginUris(
        uris: List<CipherUriApiData>?,
        symmetricKey: SymmetricCryptoKey
    ): ParsedLoginUris {
        if (uris.isNullOrEmpty()) return ParsedLoginUris()

        var website = ""
        var appPackageName = ""
        uris.forEach { uriData ->
            val uri = decryptString(uriData.uri, symmetricKey) ?: return@forEach
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

    private fun parsePasswordCustomFieldMap(
        fields: List<CipherFieldApiData>?,
        symmetricKey: SymmetricCryptoKey
    ): Map<String, String> {
        if (fields.isNullOrEmpty()) return emptyMap()
        return buildMap {
            fields.forEach { field ->
                val name = decryptOrPlain(field.name, symmetricKey).orEmpty().trim()
                if (name.isBlank()) return@forEach
                val value = decryptOrPlain(field.value, symmetricKey).orEmpty()
                put(name, value)
            }
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

    /**
     * Resolve local stored password to plain text before uploading to Bitwarden.
     *
     * Local DB stores encrypted payloads. If we upload that payload directly,
     * it will be encrypted again by Bitwarden and eventually show as "garbled" text.
     */
    private fun resolvePlainPasswordForBitwardenUpload(storedPassword: String, entryId: Long): String {
        if (storedPassword.isBlank()) return ""

        var candidate = storedPassword
        repeat(3) {
            val decrypted = try {
                securityManager.decryptData(candidate)
            } catch (e: Exception) {
                // For prefixed payloads, decrypt failure means auth/key state is invalid.
                // Failing closed avoids uploading ciphertext as if it were plaintext.
                if (candidate.startsWith("MDK|") || candidate.startsWith("V2|")) {
                    throw IllegalStateException(
                        "Cannot decrypt local password for Bitwarden upload, entryId=$entryId",
                        e
                    )
                }
                return candidate
            }

            if (decrypted == candidate) {
                return candidate
            }
            candidate = decrypted
        }
        return candidate
    }
}

// ========== 同步结果 ==========

sealed class SyncResult {
    data class Success(
        val foldersAdded: Int,
        val ciphersAdded: Int,
        val ciphersUpdated: Int,
        val conflictsDetected: Int
    ) : SyncResult()
    
    data class Error(val message: String) : SyncResult()
    
    /**
     * 空 Vault 保护阻止了同步
     */
    data class EmptyVaultBlocked(
        val localCount: Int,
        val serverCount: Int,
        val reason: String
    ) : SyncResult()
}

sealed class CipherSyncResult {
    object Added : CipherSyncResult()
    object Updated : CipherSyncResult()
    object Conflict : CipherSyncResult()
    data class Skipped(val reason: String) : CipherSyncResult()
    data class Error(val message: String) : CipherSyncResult()
}

sealed class UploadResult {
    data class Success(val uploaded: Int, val failed: Int) : UploadResult()
    data class Error(val message: String) : UploadResult()
}
