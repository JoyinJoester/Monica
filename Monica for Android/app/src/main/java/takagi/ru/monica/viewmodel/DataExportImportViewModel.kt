package takagi.ru.monica.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.BackupRestoreApplier
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.util.DataExportImportManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.steam.service.SteamLoginImportService
import java.util.Date

/**
 * 数据导入导出ViewModel
 */
class DataExportImportViewModel(
    private val secureItemRepository: SecureItemRepository,
    private val passwordRepository: PasswordRepository,
    private val context: Context
) : ViewModel() {

    private val exportManager = DataExportImportManager(context)
    private val steamLoginImportService = SteamLoginImportService()
    private val securityManager by lazy { SecurityManager(context) }

    sealed class SteamLoginImportState {
        data class ChallengeRequired(
            val pendingSessionId: String,
            val steamId: String,
            val challenges: List<SteamLoginImportService.SteamGuardChallenge>,
            val message: String? = null
        ) : SteamLoginImportState()

        data class Imported(
            val count: Int
        ) : SteamLoginImportState()

        data class Failure(
            val message: String
        ) : SteamLoginImportState()
    }

    /**
     * 导出所有数据
     */
    suspend fun exportData(outputUri: Uri): Result<String> {
        return try {
            // 获取SecureItem数据
            val secureItems = secureItemRepository.getAllItems().first()
            
            // 获取PasswordEntry数据并转换为SecureItem格式
            val passwordEntries = passwordRepository.getAllPasswordEntries().first()
            val passwordItems = passwordEntries.map { entry ->
                // 将PasswordEntry转换为数据字符串
                val passwordData = buildString {
                    append("username:${entry.username};")
                    append("password:${entry.password}")
                    if (entry.website.isNotEmpty()) {
                        append(";website:${entry.website}")
                    }
                    if (entry.email.isNotEmpty()) {
                        append(";email:${entry.email}")
                    }
                    if (entry.phone.isNotEmpty()) {
                        append(";phone:${entry.phone}")
                    }
                }
                
                SecureItem(
                    id = entry.id,
                    itemType = ItemType.PASSWORD,
                    title = entry.title,
                    itemData = passwordData,
                    notes = entry.notes,
                    isFavorite = entry.isFavorite,
                    imagePaths = "", // PasswordEntry没有iconPath字段
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt,
                    categoryId = entry.categoryId,
                    keepassDatabaseId = entry.keepassDatabaseId,
                    keepassGroupPath = entry.keepassGroupPath,
                    bitwardenVaultId = entry.bitwardenVaultId,
                    bitwardenFolderId = entry.bitwardenFolderId
                )
            }
            
            // 合并所有数据
            val allItems = secureItems + passwordItems
            
            // 导出数据
            exportManager.exportData(allItems, outputUri)
        } catch (e: Exception) {
            android.util.Log.e("DataImport", "导出失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 导入数据
     */
    suspend fun importData(inputUri: Uri, formatHint: DataExportImportManager.CsvFormat? = null): Result<Int> {
        return try {
            // 导入数据
            val result = exportManager.importData(inputUri, formatHint)
            
            result.fold(
                onSuccess = { items ->
                    android.util.Log.d("DataImport", "ViewModel收到 ${items.size} 条导入数据")
                    var count = 0
                    var errorCount = 0
                    var skippedCount = 0
                    
                    // Separate items into Passwords and Others
                    val passwordItems = items.filter { ItemType.valueOf(it.itemType) == ItemType.PASSWORD }
                    val otherItems = items.filter { ItemType.valueOf(it.itemType) != ItemType.PASSWORD }
                    
                    val passwordIdMap = mutableMapOf<Long, Long>() // Old ID -> New ID (or existing ID)
                    
                    // 1. Process Passwords First
                    passwordItems.forEach { exportItem ->
                        try {
                            // PASSWORD类型存入PasswordEntry表
                            val passwordData = parsePasswordData(exportItem.itemData)
                            val website = passwordData["website"] ?: ""
                            val username = passwordData["username"] ?: ""
                            val originalId = exportItem.id
                            
                            // 检查是否重复
                            val existingEntry = passwordRepository.getDuplicateEntry(
                                exportItem.title,
                                username,
                                website
                            )
                            
                            if (existingEntry == null) {
                                val passwordEntry = PasswordEntry(
                                    id = 0, // 让数据库自动生成新ID
                                    title = exportItem.title,
                                    website = website,
                                    username = username,
                                    password = passwordData["password"] ?: "",
                                    notes = exportItem.notes,
                                    email = passwordData["email"] ?: "",
                                    phone = passwordData["phone"] ?: "",
                                    categoryId = exportItem.categoryId,
                                    isFavorite = exportItem.isFavorite,
                                    createdAt = Date(exportItem.createdAt),
                                    updatedAt = Date(exportItem.updatedAt),
                                    keepassDatabaseId = exportItem.keepassDatabaseId,
                                    keepassGroupPath = exportItem.keepassGroupPath,
                                    bitwardenVaultId = exportItem.bitwardenVaultId,
                                    bitwardenFolderId = exportItem.bitwardenFolderId
                                )
                                val newId = passwordRepository.insertPasswordEntry(passwordEntry)
                                if (originalId > 0 && newId > 0) {
                                    passwordIdMap[originalId] = newId
                                }
                                android.util.Log.d("DataImport", "成功插入到PasswordEntry表: ${exportItem.title}")
                                count++
                            } else {
                                android.util.Log.d("DataImport", "跳过重复密码: ${exportItem.title}")
                                if (originalId > 0) {
                                    passwordIdMap[originalId] = existingEntry.id
                                }
                                skippedCount++
                            }
                        } catch (e: Exception) {
                            errorCount++
                            android.util.Log.e("DataImport", "插入密码失败: ${exportItem.title}, 错误: ${e.message}", e)
                        }
                    }
                    
                    // 2. Process Other Items (SecureItems) and update bindings
                    otherItems.forEach { exportItem ->
                        try {
                            android.util.Log.d("DataImport", "处理项: ${exportItem.title}, 类型: ${exportItem.itemType}")
                            val itemType = ItemType.valueOf(exportItem.itemType)
                            
                            // 其他类型存入SecureItem表
                            // 使用智能重复检测：根据类型比较不同的唯一标识字段
                            val existingItem = secureItemRepository.findDuplicateSecureItem(
                                itemType,
                                exportItem.itemData,
                                exportItem.title
                            )
                            val isDuplicate = existingItem != null
                            
                            if (!isDuplicate) {
                                var itemData = exportItem.itemData
                                
                                // Update TOTP binding if applicable
                                if (itemType == ItemType.TOTP) {
                                    try {
                                        val totpData = Json.decodeFromString<TotpData>(itemData)
                                        val originalBoundId = totpData.boundPasswordId
                                        if (originalBoundId != null && originalBoundId > 0) {
                                            val newBoundId = passwordIdMap[originalBoundId]
                                            if (newBoundId != null) {
                                                val updatedTotpData = totpData.copy(boundPasswordId = newBoundId)
                                                itemData = Json.encodeToString(updatedTotpData)
                                                android.util.Log.d("DataImport", "Updated TOTP boundPasswordId from $originalBoundId to $newBoundId")
                                            } else {
                                                android.util.Log.w("DataImport", "Could not map password ID for TOTP: ${exportItem.title} (Old: $originalBoundId)")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("DataImport", "Failed to parse/update TOTP data: ${e.message}")
                                    }
                                }
                                
                                val secureItem = SecureItem(
                                    id = 0, // 让数据库自动生成新ID
                                    itemType = itemType,
                                    title = exportItem.title,
                                    itemData = itemData,
                                    notes = exportItem.notes,
                                    isFavorite = exportItem.isFavorite,
                                    imagePaths = exportItem.imagePaths,
                                    createdAt = Date(exportItem.createdAt),
                                    updatedAt = Date(exportItem.updatedAt),
                                    categoryId = exportItem.categoryId,
                                    keepassDatabaseId = exportItem.keepassDatabaseId,
                                    keepassGroupPath = exportItem.keepassGroupPath,
                                    bitwardenVaultId = exportItem.bitwardenVaultId,
                                    bitwardenFolderId = exportItem.bitwardenFolderId
                                )
                                secureItemRepository.insertItem(secureItem)
                                android.util.Log.d("DataImport", "成功插入到SecureItem表: ${exportItem.title}")
                                count++
                            } else {
                                android.util.Log.d("DataImport", "跳过重复项: ${exportItem.title}")
                                skippedCount++
                            }
                        } catch (e: Exception) {
                            errorCount++
                            android.util.Log.e("DataImport", "插入数据库失败: ${exportItem.title}, 错误: ${e.message}", e)
                        }
                    }
                    
                    android.util.Log.d("DataImport", "导入完成: 成功=$count, 跳过=$skippedCount, 失败=$errorCount")
                    logImportSummary(
                        source = formatHint?.name ?: "CSV_AUTO",
                        importedCount = count,
                        skippedCount = skippedCount,
                        failedCount = errorCount
                    )
                    Result.success(count)
                },
                onFailure = { error ->
                    android.util.Log.e("DataImport", "导入失败: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("DataImport", "导入异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 导入KeePass CSV文件
     */
    suspend fun importKeePassCsv(inputUri: Uri): Result<Int> {
        return importData(inputUri, DataExportImportManager.CsvFormat.KEEPASS_PASSWORD)
    }

    /**
     * 导入Bitwarden CSV文件
     */
    suspend fun importBitwardenCsv(inputUri: Uri): Result<Int> {
        return importData(inputUri, DataExportImportManager.CsvFormat.BITWARDEN_PASSWORD)
    }
    
    /**
     * 解析密码数据字符串
     * 格式: username:xxx;password:xxx;email:xxx;url:xxx
     */
    private fun parsePasswordData(data: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        data.split(";").forEach { pair ->
            val parts = pair.split(":", limit = 2)
            if (parts.size == 2) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
    }

    /**
     * 获取建议的文件名
     */
    fun getSuggestedFileName(): String {
        return exportManager.getSuggestedFileName()
    }

    /**
     * 导入Aegis JSON文件
     */
    suspend fun importAegisJson(inputUri: Uri): Result<Int> {
        return try {
            // 首先检查是否为加密文件
            val isEncryptedResult = exportManager.isEncryptedAegisFile(inputUri)
            if (isEncryptedResult.getOrDefault(false)) {
                // 如果是加密文件，返回错误提示
                Result.failure(Exception("不支持导入加密的Aegis文件，请选择未加密的JSON文件"))
            } else {
                // 处理未加密的Aegis文件
                val result = exportManager.importAegisJson(inputUri)
                
                result.fold(
                    onSuccess = { entries ->
                        android.util.Log.d("AegisImport", "ViewModel收到 ${entries.size} 条Aegis条目")
                        var count = 0
                        var errorCount = 0
                        var skippedCount = 0
                        
                        entries.forEach { aegisEntry ->
                            try {
                                // 检查是否已存在相同的条目（基于issuer和name）
                                val existingItems = secureItemRepository.getItemsByType(ItemType.TOTP).first()
                                val isDuplicate = existingItems.any { item ->
                                    try {
                                        val totpData = Json.decodeFromString<TotpData>(item.itemData)
                                        totpData.issuer == aegisEntry.issuer && totpData.accountName == aegisEntry.name
                                    } catch (e: Exception) {
                                        false
                                    }
                                }
                                
                                if (isDuplicate) {
                                    android.util.Log.d("AegisImport", "跳过重复条目: ${aegisEntry.name}")
                                    skippedCount++
                                } else {
                                    // 创建新的TOTP条目
                                    val totpData = TotpData(
                                        secret = aegisEntry.secret,
                                        issuer = aegisEntry.issuer,
                                        accountName = aegisEntry.name,
                                        period = aegisEntry.period,
                                        digits = aegisEntry.digits,
                                        algorithm = aegisEntry.algorithm
                                    )
                                    
                                    val itemData = Json.encodeToString(totpData)
                                    val title = if (aegisEntry.issuer.isNotBlank()) {
                                        "${aegisEntry.issuer}: ${aegisEntry.name}"
                                    } else {
                                        aegisEntry.name
                                    }
                                    
                                    val secureItem = SecureItem(
                                        id = 0,
                                        itemType = ItemType.TOTP,
                                        title = title,
                                        itemData = itemData,
                                        notes = aegisEntry.note,
                                        isFavorite = false,
                                        imagePaths = "",
                                        createdAt = Date(),
                                        updatedAt = Date()
                                    )
                                    
                                    secureItemRepository.insertItem(secureItem)
                                    count++
                                    android.util.Log.d("AegisImport", "成功插入TOTP条目: $title")
                                }
                            } catch (e: Exception) {
                                errorCount++
                                android.util.Log.e("AegisImport", "插入数据库失败: ${aegisEntry.name}, 错误: ${e.message}", e)
                            }
                        }
                        
                        android.util.Log.d("AegisImport", "导入完成: 成功=$count, 跳过=$skippedCount, 失败=$errorCount")
                        logImportSummary(
                            source = "AEGIS_JSON",
                            importedCount = count,
                            skippedCount = skippedCount,
                            failedCount = errorCount
                        )
                        Result.success(count)
                    },
                    onFailure = { error ->
                        android.util.Log.e("AegisImport", "导入失败: ${error.message}", error)
                        Result.failure(error)
                    }
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AegisImport", "导入异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 导入加密的Aegis JSON文件
     */
    suspend fun importEncryptedAegisJson(inputUri: Uri, password: String): Result<Int> {
        return try {
            val result = exportManager.importEncryptedAegisJson(inputUri, password)
            
            result.fold(
                onSuccess = { entries ->
                    android.util.Log.d("EncryptedAegisImport", "ViewModel收到 ${entries.size} 条Aegis条目")
                    var count = 0
                    var errorCount = 0
                    var skippedCount = 0
                    
                    entries.forEach { aegisEntry ->
                        try {
                            // 检查是否已存在相同的条目（基于issuer和name）
                            val existingItems = secureItemRepository.getItemsByType(ItemType.TOTP).first()
                            val isDuplicate = existingItems.any { item ->
                                try {
                                    val totpData = Json.decodeFromString<TotpData>(item.itemData)
                                    totpData.issuer == aegisEntry.issuer && totpData.accountName == aegisEntry.name
                                } catch (e: Exception) {
                                    false
                                }
                            }
                            
                            if (isDuplicate) {
                                android.util.Log.d("EncryptedAegisImport", "跳过重复条目: ${aegisEntry.name}")
                                skippedCount++
                            } else {
                                // 创建新的TOTP条目
                                val totpData = TotpData(
                                    secret = aegisEntry.secret,
                                    issuer = aegisEntry.issuer,
                                    accountName = aegisEntry.name,
                                    period = aegisEntry.period,
                                    digits = aegisEntry.digits,
                                    algorithm = aegisEntry.algorithm
                                )
                                
                                val itemData = Json.encodeToString(totpData)
                                val title = if (aegisEntry.issuer.isNotBlank()) {
                                    "${aegisEntry.issuer}: ${aegisEntry.name}"
                                } else {
                                    aegisEntry.name
                                }
                                
                                val secureItem = SecureItem(
                                    id = 0,
                                    itemType = ItemType.TOTP,
                                    title = title,
                                    itemData = itemData,
                                    notes = aegisEntry.note,
                                    isFavorite = false,
                                    imagePaths = "",
                                    createdAt = Date(),
                                    updatedAt = Date()
                                )
                                
                                secureItemRepository.insertItem(secureItem)
                                count++
                                android.util.Log.d("EncryptedAegisImport", "成功插入TOTP条目: $title")
                            }
                        } catch (e: Exception) {
                            errorCount++
                            android.util.Log.e("EncryptedAegisImport", "插入数据库失败: ${aegisEntry.name}, 错误: ${e.message}", e)
                        }
                    }
                    
                    android.util.Log.d("EncryptedAegisImport", "导入完成: 成功=$count, 跳过=$skippedCount, 失败=$errorCount")
                    logImportSummary(
                        source = "AEGIS_JSON_ENCRYPTED",
                        importedCount = count,
                        skippedCount = skippedCount,
                        failedCount = errorCount
                    )
                    Result.success(count)
                },
                onFailure = { error ->
                    android.util.Log.e("EncryptedAegisImport", "导入失败: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("EncryptedAegisImport", "导入异常: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出密码数据
     */
    suspend fun exportPasswords(outputUri: Uri): Result<String> {
        return try {
            // 获取PasswordEntry数据
            val passwordEntries = passwordRepository.getAllPasswordEntries().first()
            val passwordItems = passwordEntries.map { entry ->
                val passwordData = buildString {
                    append("username:${entry.username};")
                    append("password:${entry.password}")
                    if (entry.website.isNotEmpty()) {
                        append(";website:${entry.website}")
                    }
                    if (entry.email.isNotEmpty()) {
                        append(";email:${entry.email}")
                    }
                    if (entry.phone.isNotEmpty()) {
                        append(";phone:${entry.phone}")
                    }
                }
                
                takagi.ru.monica.data.SecureItem(
                    id = entry.id,
                    itemType = ItemType.PASSWORD,
                    title = entry.title,
                    itemData = passwordData,
                    notes = entry.notes,
                    isFavorite = entry.isFavorite,
                    imagePaths = "",
                    createdAt = entry.createdAt,
                    updatedAt = entry.updatedAt
                )
            }
            
            exportManager.exportPasswords(passwordItems, outputUri)
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出密码失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出TOTP数据
     */
    suspend fun exportTotp(
        outputUri: Uri,
        format: takagi.ru.monica.ui.screens.TotpExportFormat,
        password: String?
    ): Result<String> {
        return try {
            val totpItems = secureItemRepository.getItemsByType(ItemType.TOTP).first()
            
            when (format) {
                takagi.ru.monica.ui.screens.TotpExportFormat.CSV -> {
                    // CSV格式导出
                    exportManager.exportData(totpItems, outputUri)
                }
                takagi.ru.monica.ui.screens.TotpExportFormat.AEGIS -> {
                    // Aegis格式导出
                    val aegisExporter = takagi.ru.monica.util.AegisExporter()
                    val aegisEntries = totpItems.mapNotNull { item ->
                        try {
                            val totpData = Json.decodeFromString<TotpData>(item.itemData)
                            // 移除secret中的所有空格和特殊字符，确保是纯Base32字符串
                            val cleanSecret = totpData.secret.replace(Regex("[\\s\\-]"), "").uppercase()
                            takagi.ru.monica.util.AegisExporter.AegisEntry(
                                uuid = java.util.UUID.randomUUID().toString(),
                                name = totpData.accountName,
                                issuer = totpData.issuer,
                                note = item.notes,
                                secret = cleanSecret,
                                algorithm = totpData.algorithm,
                                digits = totpData.digits,
                                period = totpData.period
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("TotpExport", "解析TOTP数据失败: ${item.title}", e)
                            null
                        }
                    }
                    
                    val jsonContent = if (password != null && password.isNotEmpty()) {
                        aegisExporter.exportToEncryptedAegisJson(aegisEntries, password)
                    } else {
                        aegisExporter.exportToUnencryptedAegisJson(aegisEntries)
                    }
                    
                    // 写入JSON文件
                    context.contentResolver.openOutputStream(outputUri)?.use { output ->
                        output.write(jsonContent.toByteArray(Charsets.UTF_8))
                    }
                    
                    Result.success("成功导出 ${aegisEntries.size} 条TOTP数据")
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出TOTP失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出银行卡和证件数据
     */
    suspend fun exportBankCardsAndDocuments(outputUri: Uri): Result<String> {
        return try {
            val bankCards = secureItemRepository.getItemsByType(ItemType.BANK_CARD).first()
            val documents = secureItemRepository.getItemsByType(ItemType.DOCUMENT).first()
            val allItems = bankCards + documents
            
            exportManager.exportBankCardsAndDocuments(allItems, outputUri)
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出银行卡和证件失败: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导入Steam maFile
     */
    suspend fun importSteamMaFile(inputUri: Uri): Result<Int> {
        return try {
            exportManager.importSteamMaFileWithMetadata(inputUri).fold(
                onSuccess = { steamEntry ->
                    insertSteamGuardEntry(steamEntry)
                },
                onFailure = { error ->
                    android.util.Log.e("SteamImport", "导入失败: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("SteamImport", "导入异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 导入 Steam App 共存令牌（设备ID + SteamGuard JSON）
     */
    suspend fun importSteamAppCoexist(
        deviceIdInput: String,
        steamGuardJson: String,
        customName: String?
    ): Result<Int> {
        return try {
            exportManager.importSteamAppCoexist(deviceIdInput, steamGuardJson, customName).fold(
                onSuccess = { steamEntry ->
                    insertSteamGuardEntry(steamEntry)
                },
                onFailure = { error ->
                    android.util.Log.e("SteamImport", "共存导入失败: ${error.message}", error)
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("SteamImport", "共存导入异常: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Steam 登录导入（第一阶段）：账号密码登录并返回挑战状态或 token
     */
    suspend fun beginSteamLoginImport(
        userName: String,
        password: String,
        customName: String? = null
    ): SteamLoginImportState {
        val result = steamLoginImportService.beginLogin(userName, password)
        return consumeSteamLoginResult(result, customName)
    }

    /**
     * Steam 登录导入（第一阶段）：提交挑战验证码
     */
    suspend fun submitSteamLoginImportCode(
        pendingSessionId: String,
        code: String,
        confirmationType: Int,
        customName: String? = null
    ): SteamLoginImportState {
        val result = steamLoginImportService.submitSteamGuardCode(
            pendingSessionId = pendingSessionId,
            code = code,
            confirmationType = confirmationType
        )
        return consumeSteamLoginResult(result, customName)
    }

    fun clearSteamLoginImportSession(sessionId: String) {
        steamLoginImportService.clearPendingSession(sessionId)
    }

    private suspend fun consumeSteamLoginResult(
        result: SteamLoginImportService.LoginResult,
        customName: String?
    ): SteamLoginImportState {
        return when (result) {
            is SteamLoginImportService.LoginResult.ChallengeRequired -> {
                SteamLoginImportState.ChallengeRequired(
                    pendingSessionId = result.pendingSessionId,
                    steamId = result.steamId,
                    challenges = result.challenges,
                    message = result.message
                )
            }

            is SteamLoginImportService.LoginResult.ReadyForImport -> {
                val importResult = importSteamAppCoexist(
                    deviceIdInput = result.payload.deviceId,
                    steamGuardJson = result.payload.steamGuardJson,
                    customName = customName
                )
                importResult.fold(
                    onSuccess = { count ->
                        SteamLoginImportState.Imported(count = count)
                    },
                    onFailure = { error ->
                        SteamLoginImportState.Failure(error.message ?: "导入失败")
                    }
                )
            }

            is SteamLoginImportService.LoginResult.Failure -> {
                SteamLoginImportState.Failure(result.message)
            }
        }
    }

    private suspend fun insertSteamGuardEntry(
        steamEntry: DataExportImportManager.SteamGuardImportEntry
    ): Result<Int> {
        val existingItems = secureItemRepository.getItemsByType(ItemType.TOTP).first()
        val normalizedName = steamEntry.name.trim()
        val isDuplicate = existingItems.any { item ->
            try {
                val totpData = Json.decodeFromString<TotpData>(item.itemData)
                if (totpData.otpType != takagi.ru.monica.data.model.OtpType.STEAM) {
                    return@any false
                }

                if (totpData.steamFingerprint.isNotBlank() &&
                    totpData.steamFingerprint == steamEntry.fingerprint
                ) {
                    return@any true
                }

                totpData.secret == steamEntry.secretBase32 &&
                    totpData.issuer.equals(steamEntry.issuer, ignoreCase = true) &&
                    totpData.accountName.trim().equals(normalizedName, ignoreCase = true)
            } catch (_: Exception) {
                false
            }
        }

        if (isDuplicate) {
            android.util.Log.d("SteamImport", "跳过重复条目: ${steamEntry.name}")
            return Result.failure(Exception("该Steam Guard验证器已存在"))
        }

        val totpData = TotpData(
            secret = steamEntry.secretBase32,
            issuer = steamEntry.issuer,
            accountName = steamEntry.name,
            period = 30,
            digits = 5,
            algorithm = "SHA1",
            otpType = takagi.ru.monica.data.model.OtpType.STEAM,
            steamFingerprint = steamEntry.fingerprint,
            steamDeviceId = steamEntry.deviceId,
            steamSerialNumber = steamEntry.serialNumber,
            steamSharedSecretBase64 = steamEntry.sharedSecretBase64,
            steamRevocationCode = steamEntry.revocationCode,
            steamIdentitySecret = steamEntry.identitySecret,
            steamTokenGid = steamEntry.tokenGid,
            steamRawJson = steamEntry.rawSteamGuardJson
        )

        val itemData = Json.encodeToString(totpData)
        val title = if (steamEntry.name.isNotBlank()) {
            "Steam: ${steamEntry.name}"
        } else {
            "Steam Guard"
        }

        val secureItem = SecureItem(
            id = 0,
            itemType = ItemType.TOTP,
            title = title,
            itemData = itemData,
            notes = "",
            isFavorite = false,
            imagePaths = "",
            createdAt = Date(),
            updatedAt = Date()
        )

        secureItemRepository.insertItem(secureItem)
        android.util.Log.d("SteamImport", "成功插入Steam Guard: $title")
        logImportSummary(
            source = "STEAM_GUARD",
            importedCount = 1
        )
        return Result.success(1)
    }

    /**
     * 导出笔记数据
     */
    suspend fun exportNotes(outputUri: Uri): Result<String> {
        return try {
            val notes = secureItemRepository.getItemsByType(ItemType.NOTE).first()
            exportManager.exportData(notes, outputUri)
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出笔记失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 导出完整备份 (ZIP格式，WebDAV兼容)
     * @param outputUri 导出文件的URI
     * @param preferences 备份偏好设置（选择要导出的内容类型）
     */
    suspend fun exportZipBackup(outputUri: Uri, preferences: takagi.ru.monica.data.BackupPreferences = takagi.ru.monica.data.BackupPreferences()): Result<String> {
        return try {
            val webDavHelper = takagi.ru.monica.utils.WebDavHelper(context)
            
            // 获取所有数据
            val passwordEntries = passwordRepository.getAllPasswordEntries().first()
            val secureItems = secureItemRepository.getAllItems().first()
            val exportedPasswords = passwordEntries.map { entry ->
                val exportedPassword = runCatching { securityManager.decryptData(entry.password) }
                    .getOrElse { error ->
                        android.util.Log.w(
                            "DataExport",
                            "Failed to decrypt password for ZIP export: ${entry.title} (${error.message})"
                        )
                        entry.password
                    }
                entry.copy(password = exportedPassword)
            }
            
            // 创建ZIP备份，使用传入的偏好设置
            val result = webDavHelper.createBackupZip(
                passwords = exportedPasswords,
                secureItems = secureItems,
                preferences = preferences
            )
            
            result.fold(
                onSuccess = { pair ->
                    val (zipFile, report) = pair
                    
                    try {
                        if (!report.success) {
                            // 如果有失败项，但还是生成了文件，可能需要警告用户
                            // 这里我们记录警告但继续导出
                            android.util.Log.w("DataExport", "Backup report has failures: ${report.failedItems.size}")
                        }
                        
                        // 将生成的ZIP文件复制到用户选择的 outputUri
                        context.contentResolver.openOutputStream(outputUri)?.use { output ->
                            zipFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        
                        Result.success("成功导出备份，包含 ${report.successItems.passwords} 个密码和 ${report.successItems.images} 张图片")
                    } finally {
                        // 清理临时文件
                        zipFile.delete()
                    }
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出ZIP失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * 导入完整备份 (ZIP格式)
     * @param inputUri 用户选择的ZIP文件URI
     */
    suspend fun importZipBackup(inputUri: Uri, decryptPassword: String? = null): Result<Int> {
        return try {
            val webDavHelper = takagi.ru.monica.utils.WebDavHelper(context)
            
            // 1. 将Uri内容复制到临时文件
            val tempFile = java.io.File(context.cacheDir, "import_temp_${System.nanoTime()}.zip")
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return Result.failure(Exception("无法读取选定的文件"))
            
            try {
                // 2. 调用 restoreFromBackupFile 解析备份
                val result = webDavHelper.restoreFromBackupFile(tempFile, decryptPassword)
                
                result.fold(
                    onSuccess = { restoreResult ->
                        val stats = BackupRestoreApplier.applyRestoreResult(
                            context = context,
                            restoreResult = restoreResult,
                            passwordRepository = passwordRepository,
                            secureItemRepository = secureItemRepository,
                            localOnlyDedup = true,
                            logTag = "DataImport"
                        )
                        logImportSummary(
                            source = "ZIP_BACKUP",
                            importedCount = stats.totalImported()
                        )
                        Result.success(stats.totalImported())
                    },
                    onFailure = { error ->
                        // 如果是密码错误，抛出特定的异常以便UI处理
                        if (error is takagi.ru.monica.utils.WebDavHelper.PasswordRequiredException) {
                            return Result.failure(error)
                        }
                        Result.failure(error)
                    }
                )
            } finally {
                tempFile.delete()
            }
        } catch (e: Exception) {
            android.util.Log.e("DataImport", "导入ZIP失败: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ==================== Stratum Auth Import ====================
    
    suspend fun isStratumFileEncrypted(inputUri: Uri): Boolean {
        return exportManager.isStratumFileEncrypted(inputUri).getOrDefault(false)
    }
    
    suspend fun importStratum(inputUri: Uri, password: String? = null): Result<Int> {
        return try {
            val fileType = exportManager.detectStratumFileType(inputUri).getOrNull()
                ?: return Result.failure(Exception("Cannot detect file type"))
            val entriesResult = when (fileType) {
                takagi.ru.monica.util.StratumDecryptor.StratumFileType.MODERN_ENCRYPTED,
                takagi.ru.monica.util.StratumDecryptor.StratumFileType.LEGACY_ENCRYPTED -> {
                    if (password.isNullOrEmpty()) return Result.failure(Exception("Password required"))
                    exportManager.importEncryptedStratum(inputUri, password)
                }
                takagi.ru.monica.util.StratumDecryptor.StratumFileType.UNENCRYPTED -> exportManager.importStratumJson(inputUri)
                takagi.ru.monica.util.StratumDecryptor.StratumFileType.NOT_STRATUM -> {
                    val txtResult = exportManager.importStratumTxt(inputUri)
                    if (txtResult.isSuccess) {
                        txtResult
                    } else {
                        exportManager.importStratumHtml(inputUri)
                    }
                }
            }
            entriesResult.fold(
                onSuccess = { list -> insertTotpEntries(list) },
                onFailure = { Result.failure(it) }
            )
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun importStratumTxt(inputUri: Uri): Result<Int> {
        return try {
            exportManager.importStratumTxt(inputUri).fold(onSuccess = { insertTotpEntries(it) }, onFailure = { Result.failure(it) })
        } catch (e: Exception) { Result.failure(e) }
    }
    
    suspend fun importStratumHtml(inputUri: Uri): Result<Int> {
        return try {
            exportManager.importStratumHtml(inputUri).fold(onSuccess = { insertTotpEntries(it) }, onFailure = { Result.failure(it) })
        } catch (e: Exception) { Result.failure(e) }
    }
    
    private suspend fun insertTotpEntries(entries: List<DataExportImportManager.AegisEntry>): Result<Int> {
        var count = 0
        var skippedCount = 0
        var failedCount = 0
        val existingItems = secureItemRepository.getAllItems().first().filter { it.itemType == ItemType.TOTP }
        val existingSecrets = existingItems.mapNotNull { try { Json.decodeFromString<TotpData>(it.itemData).secret } catch (e: Exception) { null } }.toSet()
        for (entry in entries) {
            try {
                if (entry.secret in existingSecrets) {
                    skippedCount++
                    continue
                }
                val totpData = TotpData(secret = entry.secret, issuer = entry.issuer, accountName = entry.name, digits = entry.digits, period = entry.period, algorithm = entry.algorithm)
                val item = SecureItem(id = 0, itemType = ItemType.TOTP, title = entry.issuer.ifBlank { entry.name }, itemData = Json.encodeToString(totpData), notes = entry.note, isFavorite = false, imagePaths = "", createdAt = Date(), updatedAt = Date(), categoryId = null, keepassDatabaseId = null, keepassGroupPath = null, bitwardenVaultId = null, bitwardenFolderId = null)
                secureItemRepository.insertItem(item)
                count++
            } catch (e: Exception) {
                failedCount++
            }
        }
        logImportSummary(
            source = "STRATUM_TOTP",
            importedCount = count,
            skippedCount = skippedCount,
            failedCount = failedCount
        )
        return Result.success(count)
    }

    private fun logImportSummary(
        source: String,
        importedCount: Int,
        skippedCount: Int = 0,
        failedCount: Int = 0
    ) {
        if (importedCount <= 0 && skippedCount <= 0 && failedCount <= 0) return

        val details = mutableListOf(
            FieldChange(
                fieldName = context.getString(R.string.import_data),
                oldValue = source,
                newValue = source
            ),
            FieldChange(
                fieldName = "Imported",
                oldValue = "0",
                newValue = importedCount.toString()
            )
        )
        if (skippedCount > 0) {
            details += FieldChange(
                fieldName = "Skipped",
                oldValue = "0",
                newValue = skippedCount.toString()
            )
        }
        if (failedCount > 0) {
            details += FieldChange(
                fieldName = "Failed",
                oldValue = "0",
                newValue = failedCount.toString()
            )
        }

        OperationLogger.logCreate(
            itemType = OperationLogItemType.CATEGORY,
            itemId = System.currentTimeMillis(),
            itemTitle = "${context.getString(R.string.import_data)} · $source",
            details = details
        )
    }

}
