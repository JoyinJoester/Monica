package takagi.ru.monica.utils

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordHistoryEntry
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.repository.SecureItemRepository

data class RestoreApplyStats(
    val passwordImported: Int,
    val passwordSkipped: Int,
    val passwordFailed: Int,
    val secureItemImported: Int,
    val secureItemSkipped: Int,
    val secureItemFailed: Int,
    val passkeyImported: Int,
    val passkeySkipped: Int,
    val passkeyFailed: Int,
    val failedPasswordDetails: List<String>,
    val failedSecureItemDetails: List<String>
) {
    fun totalImported(): Int = passwordImported + secureItemImported + passkeyImported
}

object BackupRestoreApplier {
    suspend fun applyRestoreResult(
        context: Context,
        restoreResult: RestoreResult,
        passwordRepository: PasswordRepository,
        secureItemRepository: SecureItemRepository,
        localOnlyDedup: Boolean,
        logTag: String
    ): RestoreApplyStats {
        val content = restoreResult.content
        val passwords = content.passwords
        val passwordHistory = content.passwordHistory
        val secureItems = content.secureItems
        val passkeys = content.passkeys
        val securityManager = SecurityManager(context)

        android.util.Log.d(logTag, "===== 开始恢复 =====")
        android.util.Log.d(logTag, "备份中密码数量: ${passwords.size}")
        android.util.Log.d(logTag, "备份中安全项数量: ${secureItems.size}")
        android.util.Log.d(logTag, "备份中通行密钥数量: ${passkeys.size}")
        android.util.Log.d(logTag, "报告: ${restoreResult.report.getSummary()}")

        val passwordIdMap = mutableMapOf<Long, Long>()
        var passwordCount = 0
        var passwordSkipped = 0
        var passwordFailed = 0
        val failedPasswordDetails = mutableListOf<String>()

        passwords.forEach { password ->
            try {
                val originalId = password.id
                val existingEntry = PasswordImportDuplicateResolver.findMatchingEntry(
                    passwordRepository = passwordRepository,
                    securityManager = securityManager,
                    snapshot = ImportedPasswordSnapshot(
                        title = password.title,
                        username = password.username,
                        website = password.website,
                        password = password.password,
                        notes = password.notes,
                        email = password.email,
                        phone = password.phone,
                        authenticatorKey = password.authenticatorKey
                    ),
                    localOnly = localOnlyDedup
                )
                val encryptedImportedPassword = encryptImportedPasswordForDisplay(password.password, securityManager, logTag)

                if (existingEntry == null) {
                    val newId = passwordRepository.insertPasswordEntry(
                        password.copy(
                            id = 0,
                            password = encryptedImportedPassword
                        )
                    )
                    if (newId > 0) {
                        passwordIdMap[originalId] = newId
                        passwordCount++
                    } else {
                        passwordFailed++
                        android.util.Log.e(logTag, "Failed to insert password, returned ID <= 0")
                    }
                } else {
                    passwordIdMap[originalId] = existingEntry.id
                    if (
                        !password.customIconType.equals("NONE", ignoreCase = true) &&
                        existingEntry.customIconType.equals("NONE", ignoreCase = true)
                    ) {
                        val patchedEntry = existingEntry.copy(
                            customIconType = password.customIconType,
                            customIconValue = password.customIconValue?.let { java.io.File(it).name },
                            customIconUpdatedAt = password.customIconUpdatedAt
                        )
                        passwordRepository.updatePasswordEntry(patchedEntry)
                    }
                    passwordSkipped++
                }
            } catch (e: Exception) {
                passwordFailed++
                val detail = "${password.title} (${password.username}): ${e.message}"
                failedPasswordDetails.add(detail)
                android.util.Log.e(logTag, "Failed to import password: $detail")
            }
        }

        passwords.forEach { password ->
            if (password.ssoRefEntryId != null && password.ssoRefEntryId > 0) {
                try {
                    val originalRefId = password.ssoRefEntryId
                    val originalId = password.id
                    val currentId = passwordIdMap[originalId]

                    if (currentId != null) {
                        val newRefId = passwordIdMap[originalRefId]
                        val existingEntry = passwordRepository.getPasswordEntryById(currentId)

                        if (existingEntry != null) {
                            if (newRefId != null) {
                                if (newRefId != existingEntry.ssoRefEntryId) {
                                    val updatedEntry = existingEntry.copy(ssoRefEntryId = newRefId)
                                    passwordRepository.updatePasswordEntry(updatedEntry)
                                    android.util.Log.d(
                                        logTag,
                                        "Updated ssoRefEntryId from $originalRefId to $newRefId for password: ${password.title}"
                                    )
                                }
                            } else if (existingEntry.ssoRefEntryId != null) {
                                val updatedEntry = existingEntry.copy(ssoRefEntryId = null)
                                passwordRepository.updatePasswordEntry(updatedEntry)
                                android.util.Log.w(
                                    logTag,
                                    "Cleared invalid ssoRefEntryId $originalRefId for password: ${password.title} (referenced password not found)"
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(logTag, "Failed to update ssoRefEntryId for ${password.title}: ${e.message}")
                }
            }
        }

        if (content.customFieldsMap.isNotEmpty()) {
            val customFieldDao = PasswordDatabase.getDatabase(context).customFieldDao()
            var customFieldCount = 0

            content.customFieldsMap.forEach { (originalId, fields) ->
                val newId = passwordIdMap[originalId]
                if (newId != null && fields.isNotEmpty()) {
                    try {
                        fields.forEachIndexed { index, fieldBackup ->
                            val customField = CustomField(
                                id = 0,
                                entryId = newId,
                                title = fieldBackup.title,
                                value = fieldBackup.value,
                                isProtected = fieldBackup.isProtected,
                                sortOrder = index
                            )
                            customFieldDao.insert(customField)
                            customFieldCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.w(logTag, "Failed to restore custom fields for password $originalId -> $newId: ${e.message}")
                    }
                }
            }

            if (customFieldCount > 0) {
                android.util.Log.d(logTag, "Restored $customFieldCount custom fields")
            }
        }

        if (passwordHistory.isNotEmpty()) {
            var historyCount = 0
            passwordHistory.forEach { historyEntry ->
                val mappedEntryId = passwordIdMap[historyEntry.entryId] ?: return@forEach
                try {
                    passwordRepository.insertPasswordHistory(
                        PasswordHistoryEntry(
                            entryId = mappedEntryId,
                            password = encryptImportedPasswordForDisplay(
                                historyEntry.password,
                                securityManager,
                                logTag
                            ),
                            lastUsedAt = java.util.Date(historyEntry.lastUsedAt)
                        )
                    )
                    historyCount++
                } catch (e: Exception) {
                    android.util.Log.w(
                        logTag,
                        "Failed to restore password history for password ${historyEntry.entryId} -> $mappedEntryId: ${e.message}"
                    )
                }
            }

            if (historyCount > 0) {
                android.util.Log.d(logTag, "Restored $historyCount password history entries")
            }
        }

        var secureItemCount = 0
        var secureItemSkipped = 0
        var secureItemFailed = 0
        val failedSecureItemDetails = mutableListOf<String>()
        var passkeyCountImported = 0
        var passkeySkipped = 0
        var passkeyFailed = 0
        val json = Json { ignoreUnknownKeys = true }

        secureItems.forEach { exportItem ->
            try {
                val itemType = ItemType.valueOf(exportItem.itemType)
                val existingItem = secureItemRepository.findDuplicateSecureItem(
                    itemType,
                    exportItem.itemData,
                    exportItem.title,
                    localOnly = localOnlyDedup
                )

                if (existingItem == null) {
                    var finalItemData = exportItem.itemData
                    if (itemType == ItemType.TOTP) {
                        try {
                            val totpData = json.decodeFromString<TotpData>(exportItem.itemData)
                            if (totpData.boundPasswordId != null && totpData.boundPasswordId > 0) {
                                val newBoundId = passwordIdMap[totpData.boundPasswordId]
                                if (newBoundId != null) {
                                    val updatedTotpData = totpData.copy(boundPasswordId = newBoundId)
                                    finalItemData = json.encodeToString(updatedTotpData)
                                    android.util.Log.d(logTag, "Updated TOTP binding: ${exportItem.title} -> Password ID $newBoundId")
                                } else {
                                    android.util.Log.w(logTag, "Could not find new password ID for TOTP binding: ${exportItem.title} (Old ID: ${totpData.boundPasswordId})")
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w(logTag, "Failed to parse/update TOTP data for ${exportItem.title}: ${e.message}")
                        }
                    }

                    val secureItem = takagi.ru.monica.data.SecureItem(
                        id = 0,
                        itemType = itemType,
                        title = exportItem.title,
                        itemData = finalItemData,
                        notes = exportItem.notes,
                        isFavorite = exportItem.isFavorite,
                        imagePaths = exportItem.imagePaths,
                        createdAt = java.util.Date(exportItem.createdAt),
                        updatedAt = java.util.Date(exportItem.updatedAt),
                        categoryId = exportItem.categoryId
                    )
                    secureItemRepository.insertItem(secureItem)
                    secureItemCount++
                } else {
                    secureItemSkipped++
                }
            } catch (e: Exception) {
                secureItemFailed++
                val detail = "${exportItem.title} (${exportItem.itemType}): ${e.message}"
                failedSecureItemDetails.add(detail)
                android.util.Log.e(logTag, "Failed to import secure item: $detail")
            }
        }

        if (passkeys.isNotEmpty()) {
            val passkeyDao = PasswordDatabase.getDatabase(context).passkeyDao()
            passkeys.forEach { passkey ->
                try {
                    val existing = if (localOnlyDedup) {
                        passkeyDao.getLocalPasskeyById(passkey.credentialId)
                    } else {
                        passkeyDao.getPasskeyById(passkey.credentialId)
                    }
                    if (existing == null) {
                        val mappedBoundPasswordId = passkey.boundPasswordId?.let { oldId ->
                            passwordIdMap[oldId]
                        }
                        passkeyDao.insert(
                            passkey.copy(boundPasswordId = mappedBoundPasswordId)
                        )
                        passkeyCountImported++
                    } else {
                        passkeySkipped++
                    }
                } catch (e: Exception) {
                    passkeyFailed++
                    android.util.Log.e(
                        logTag,
                        "Failed to import passkey ${passkey.credentialId}: ${e.message}",
                        e
                    )
                }
            }
        }

        android.util.Log.d(logTag, "===== 导入统计 =====")
        android.util.Log.d(logTag, "成功导入密码: $passwordCount")
        android.util.Log.d(logTag, "跳过重复密码: $passwordSkipped")
        android.util.Log.d(logTag, "导入失败密码: $passwordFailed")
        android.util.Log.d(logTag, "成功导入安全项: $secureItemCount")
        android.util.Log.d(logTag, "跳过重复安全项: $secureItemSkipped")
        android.util.Log.d(logTag, "导入失败安全项: $secureItemFailed")
        android.util.Log.d(logTag, "成功导入通行密钥: $passkeyCountImported")
        android.util.Log.d(logTag, "跳过重复通行密钥: $passkeySkipped")
        android.util.Log.d(logTag, "导入失败通行密钥: $passkeyFailed")
        android.util.Log.d(logTag, "总计: ${passwordCount + passwordSkipped + passwordFailed} vs 备份中: ${passwords.size}")

        return RestoreApplyStats(
            passwordImported = passwordCount,
            passwordSkipped = passwordSkipped,
            passwordFailed = passwordFailed,
            secureItemImported = secureItemCount,
            secureItemSkipped = secureItemSkipped,
            secureItemFailed = secureItemFailed,
            passkeyImported = passkeyCountImported,
            passkeySkipped = passkeySkipped,
            passkeyFailed = passkeyFailed,
            failedPasswordDetails = failedPasswordDetails,
            failedSecureItemDetails = failedSecureItemDetails
        )
    }
}

private fun encryptImportedPasswordForDisplay(
    plainPassword: String,
    securityManager: SecurityManager,
    logTag: String
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
        logTag,
        "Imported password encrypted payload is not immediately readable; fallback to legacy V1"
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
            logTag,
            "Legacy fallback is still unreadable; keep primary encrypted payload"
        )
        primaryEncrypted
    }
}
