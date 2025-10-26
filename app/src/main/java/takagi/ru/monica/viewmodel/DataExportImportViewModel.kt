package takagi.ru.monica.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.repository.PasswordRepository
import takagi.ru.monica.util.DataExportImportManager
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
                    updatedAt = entry.updatedAt
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
    suspend fun importData(inputUri: Uri): Result<Int> {
        return try {
            // 导入数据
            val result = exportManager.importData(inputUri)
            
            result.fold(
                onSuccess = { items ->
                    android.util.Log.d("DataImport", "ViewModel收到 ${items.size} 条导入数据")
                    var count = 0
                    var errorCount = 0
                    var skippedCount = 0
                    // 将导入的数据添加到数据库
                    items.forEach { exportItem ->
                        try {
                            android.util.Log.d("DataImport", "处理项: ${exportItem.title}, 类型: ${exportItem.itemType}")
                            val itemType = ItemType.valueOf(exportItem.itemType)
                            
                            // 根据类型选择不同的存储方式
                            if (itemType == ItemType.PASSWORD) {
                                // PASSWORD类型存入PasswordEntry表
                                val passwordData = parsePasswordData(exportItem.itemData)
                                val website = passwordData["website"] ?: ""
                                val username = passwordData["username"] ?: ""
                                
                                // 检查是否重复
                                val isDuplicate = passwordRepository.isDuplicateEntry(
                                    exportItem.title,
                                    username,
                                    website
                                )
                                
                                if (!isDuplicate) {
                                    val passwordEntry = PasswordEntry(
                                        id = 0, // 让数据库自动生成新ID
                                        title = exportItem.title,
                                        website = website,
                                        username = username,
                                        password = passwordData["password"] ?: "",
                                        notes = exportItem.notes,
                                        isFavorite = exportItem.isFavorite,
                                        createdAt = Date(exportItem.createdAt),
                                        updatedAt = Date(exportItem.updatedAt)
                                    )
                                    passwordRepository.insertPasswordEntry(passwordEntry)
                                    android.util.Log.d("DataImport", "成功插入到PasswordEntry表: ${exportItem.title}")
                                    count++
                                } else {
                                    android.util.Log.d("DataImport", "跳过重复密码: ${exportItem.title}")
                                    skippedCount++
                                }
                            } else {
                                // 其他类型存入SecureItem表
                                // 检查是否重复
                                val isDuplicate = secureItemRepository.isDuplicateItem(
                                    itemType,
                                    exportItem.title
                                )
                                
                                if (!isDuplicate) {
                                    val secureItem = SecureItem(
                                        id = 0, // 让数据库自动生成新ID
                                        itemType = itemType,
                                        title = exportItem.title,
                                        itemData = exportItem.itemData,
                                        notes = exportItem.notes,
                                        isFavorite = exportItem.isFavorite,
                                        imagePaths = exportItem.imagePaths,
                                        createdAt = Date(exportItem.createdAt),
                                        updatedAt = Date(exportItem.updatedAt)
                                    )
                                    secureItemRepository.insertItem(secureItem)
                                    android.util.Log.d("DataImport", "成功插入到SecureItem表: ${exportItem.title}")
                                    count++
                                } else {
                                    android.util.Log.d("DataImport", "跳过重复项: ${exportItem.title}")
                                    skippedCount++
                                }
                            }
                        } catch (e: Exception) {
                            errorCount++
                            android.util.Log.e("DataImport", "插入数据库失败: ${exportItem.title}, 错误: ${e.message}", e)
                        }
                    }
                    android.util.Log.d("DataImport", "导入完成: 成功=$count, 跳过=$skippedCount, 失败=$errorCount")
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
                            takagi.ru.monica.util.AegisExporter.AegisEntry(
                                uuid = java.util.UUID.randomUUID().toString(),
                                name = totpData.accountName,
                                issuer = totpData.issuer,
                                note = item.notes,
                                secret = totpData.secret,
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
}
