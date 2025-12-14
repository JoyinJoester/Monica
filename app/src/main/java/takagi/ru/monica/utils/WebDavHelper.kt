package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.widget.Toast
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.PasswordHistoryManager
import takagi.ru.monica.data.BackupPreferences
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.BackupReport
import takagi.ru.monica.data.RestoreReport
import takagi.ru.monica.data.ItemCounts
import takagi.ru.monica.data.FailedItem
import takagi.ru.monica.util.DataExportImportManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.decodeFromString
import kotlinx.coroutines.flow.first
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.io.BufferedWriter
import java.io.OutputStreamWriter

@Serializable
private data class PasswordBackupEntry(
    val id: Long = 0,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val categoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
private data class NoteBackupEntry(
    val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val itemData: String = "",
    val isFavorite: Boolean = false,
    val imagePaths: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * WebDAV 帮助类
 * 用于备份和恢复数据到 WebDAV 服务器
 */
class WebDavHelper(
    private val context: Context
) {
    private var sardine: Sardine? = null
    private var serverUrl: String = ""
    private var username: String = ""
    private var password: String = ""
    
    companion object {
        private const val PREFS_NAME = "webdav_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ENABLE_ENCRYPTION = "enable_encryption"
        private const val KEY_ENCRYPTION_PASSWORD = "encryption_password"
        private const val KEY_AUTO_BACKUP_ENABLED = "auto_backup_enabled"
        private const val KEY_LAST_BACKUP_TIME = "last_backup_time"
        private const val PASSWORD_META_MARKER = "[MonicaMeta]"
        
        // Backup preferences keys
        private const val KEY_BACKUP_INCLUDE_PASSWORDS = "backup_include_passwords"
        private const val KEY_BACKUP_INCLUDE_AUTHENTICATORS = "backup_include_authenticators"
        private const val KEY_BACKUP_INCLUDE_DOCUMENTS = "backup_include_documents"
        private const val KEY_BACKUP_INCLUDE_BANK_CARDS = "backup_include_bank_cards"
        private const val KEY_BACKUP_INCLUDE_GENERATOR_HISTORY = "backup_include_generator_history"
        private const val KEY_BACKUP_INCLUDE_IMAGES = "backup_include_images"
        private const val KEY_BACKUP_INCLUDE_NOTES = "backup_include_notes"
    }
    
    // 加密相关
    private var enableEncryption: Boolean = false
    private var encryptionPassword: String = ""
    
    init {
        // 启动时自动加载保存的配置
        loadConfig()
    }
    
    /**
     * 配置 WebDAV 连接
     */
    fun configure(url: String, user: String, pass: String) {
        serverUrl = url.trimEnd('/')
        username = user
        password = pass
        // 创建新的 Sardine 实例并立即设置凭证
        sardine = OkHttpSardine()
        sardine?.setCredentials(username, password)
        android.util.Log.d("WebDavHelper", "Configured WebDAV: url=$serverUrl, user=$username")
        // 自动保存配置
        saveConfig()
    }
    
    /**
     * 保存配置到SharedPreferences
     */
    private fun saveConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_SERVER_URL, serverUrl)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            putBoolean(KEY_ENABLE_ENCRYPTION, enableEncryption)
            putString(KEY_ENCRYPTION_PASSWORD, encryptionPassword)
            apply()
        }
    }
    
    /**
     * 从SharedPreferences加载配置
     */
    private fun loadConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_SERVER_URL, "") ?: ""
        val user = prefs.getString(KEY_USERNAME, "") ?: ""
        val pass = prefs.getString(KEY_PASSWORD, "") ?: ""
        enableEncryption = prefs.getBoolean(KEY_ENABLE_ENCRYPTION, false)
        encryptionPassword = prefs.getString(KEY_ENCRYPTION_PASSWORD, "") ?: ""
        
        if (url.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
            serverUrl = url
            username = user
            password = pass
            // 重新创建 sardine 实例并设置凭证
            sardine = OkHttpSardine()
            sardine?.setCredentials(username, password)
            android.util.Log.d("WebDavHelper", "Loaded WebDAV config: url=$serverUrl, user=$username, encryption=$enableEncryption")
        }
    }
    
    /**
     * 检查是否已配置
     */
    fun isConfigured(): Boolean {
        return serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
    }
    
    /**
     * 获取当前配置信息
     */
    data class WebDavConfig(
        val serverUrl: String,
        val username: String
    )
    
    fun getCurrentConfig(): WebDavConfig? {
        return if (isConfigured()) {
            WebDavConfig(serverUrl, username)
        } else {
            null
        }
    }
    
    /**
     * 清除配置
     */
    fun clearConfig() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        serverUrl = ""
        username = ""
        password = ""
        enableEncryption = false
        encryptionPassword = ""
        sardine = null
    }
    
    /**
     * 配置加密设置
     * @param enable 是否启用加密
     * @param encPassword 加密密码 (如果启用加密)
     */
    fun configureEncryption(enable: Boolean, encPassword: String = "") {
        enableEncryption = enable
        encryptionPassword = if (enable) encPassword else ""
        saveConfig()
        android.util.Log.d("WebDavHelper", "Encryption configured: enabled=$enable")
    }
    
    /**
     * 获取加密状态
     */
    fun isEncryptionEnabled(): Boolean = enableEncryption
    
    /**
     * 检查加密密码是否已设置
     */
    fun hasEncryptionPassword(): Boolean = enableEncryption && encryptionPassword.isNotEmpty()
    
    /**
     * 配置自动备份
     * @param enable 是否启用自动备份
     */
    fun configureAutoBackup(enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_BACKUP_ENABLED, enable).apply()
        android.util.Log.d("WebDavHelper", "Auto backup configured: enabled=$enable")
    }
    
    /**
     * 获取自动备份状态
     */
    fun isAutoBackupEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_BACKUP_ENABLED, false)
    }
    
    /**
     * 检查是否需要自动备份
     * 逻辑:
     * 1. 每天首次打开必定备份(即使距离上次备份不到12小时)
     * 2. 如果距离上次备份超过12小时,则备份(即使今天已经备份过)
     */
    fun shouldAutoBackup(): Boolean {
        if (!isAutoBackupEnabled()) {
            return false
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastBackupTime = prefs.getLong(KEY_LAST_BACKUP_TIME, 0)
        
        // 如果从未备份过,则需要备份
        if (lastBackupTime == 0L) {
            android.util.Log.d("WebDavHelper", "Never backed up before, need backup")
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        
        // 获取上次备份的日期
        calendar.timeInMillis = lastBackupTime
        val lastBackupDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val lastBackupYear = calendar.get(java.util.Calendar.YEAR)
        
        // 获取当前日期
        calendar.timeInMillis = currentTime
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        
        // 计算距离上次备份的小时数
        val hoursSinceLastBackup = (currentTime - lastBackupTime) / (1000 * 60 * 60)
        
        // 判断是否为新的一天
        val isNewDay = (currentYear > lastBackupYear) || 
                      (currentYear == lastBackupYear && currentDay > lastBackupDay)
        
        android.util.Log.d("WebDavHelper", 
            "Last backup: year=$lastBackupYear, day=$lastBackupDay, " +
            "Current: year=$currentYear, day=$currentDay, " +
            "Hours since: $hoursSinceLastBackup, " +
            "Is new day: $isNewDay")
        
        // 规则1: 如果是新的一天,必定备份
        if (isNewDay) {
            android.util.Log.d("WebDavHelper", "New day detected, need backup")
            return true
        }
        
        // 规则2: 如果距离上次备份超过12小时,则备份
        if (hoursSinceLastBackup >= 12) {
            android.util.Log.d("WebDavHelper", "More than 12 hours since last backup, need backup")
            return true
        }
        
        android.util.Log.d("WebDavHelper", "No backup needed")
        return false
    }
    
    /**
     * 更新最后备份时间
     */
    fun updateLastBackupTime() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_BACKUP_TIME, currentTime).apply()
        android.util.Log.d("WebDavHelper", "Updated last backup time: $currentTime")
    }
    
    /**
     * 获取最后备份时间
     */
    fun getLastBackupTime(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_BACKUP_TIME, 0)
    }
    
    /**
     * 保存备份偏好设置
     */
    fun saveBackupPreferences(preferences: BackupPreferences) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putBoolean(KEY_BACKUP_INCLUDE_PASSWORDS, preferences.includePasswords)
            putBoolean(KEY_BACKUP_INCLUDE_AUTHENTICATORS, preferences.includeAuthenticators)
            putBoolean(KEY_BACKUP_INCLUDE_DOCUMENTS, preferences.includeDocuments)
            putBoolean(KEY_BACKUP_INCLUDE_BANK_CARDS, preferences.includeBankCards)
            putBoolean(KEY_BACKUP_INCLUDE_GENERATOR_HISTORY, preferences.includeGeneratorHistory)
            putBoolean(KEY_BACKUP_INCLUDE_IMAGES, preferences.includeImages)
            putBoolean(KEY_BACKUP_INCLUDE_NOTES, preferences.includeNotes)
            apply()
        }
        android.util.Log.d("WebDavHelper", "Saved backup preferences: $preferences")
    }
    
    /**
     * 获取备份偏好设置
     * 默认所有类型都启用
     */
    fun getBackupPreferences(): BackupPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return BackupPreferences(
            includePasswords = prefs.getBoolean(KEY_BACKUP_INCLUDE_PASSWORDS, true),
            includeAuthenticators = prefs.getBoolean(KEY_BACKUP_INCLUDE_AUTHENTICATORS, true),
            includeDocuments = prefs.getBoolean(KEY_BACKUP_INCLUDE_DOCUMENTS, true),
            includeBankCards = prefs.getBoolean(KEY_BACKUP_INCLUDE_BANK_CARDS, true),
            includeGeneratorHistory = prefs.getBoolean(KEY_BACKUP_INCLUDE_GENERATOR_HISTORY, true),
            includeImages = prefs.getBoolean(KEY_BACKUP_INCLUDE_IMAGES, true),
            includeNotes = prefs.getBoolean(KEY_BACKUP_INCLUDE_NOTES, true)
        )
    }
    
    /**
     * 测试连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            // 检查网络和时间同步
            checkNetworkAndTimeSync(context)
            
            android.util.Log.d("WebDavHelper", "Testing connection to: $serverUrl")
            android.util.Log.d("WebDavHelper", "Username: $username")
            
            // 尝试多种方法测试连接,从最简单到最复杂
            var connectionOk = false
            var lastError: Exception? = null
            
            // 方法1: 使用 exists() - HEAD 请求
            try {
                val exists = sardine?.exists(serverUrl) ?: false
                android.util.Log.d("WebDavHelper", "Method 1 (exists): path exists = $exists")
                connectionOk = true
                
                // 如果路径不存在,尝试创建
                if (!exists) {
                    try {
                        sardine?.createDirectory(serverUrl)
                        android.util.Log.d("WebDavHelper", "Directory created successfully")
                    } catch (createError: Exception) {
                        android.util.Log.w("WebDavHelper", "Could not create directory (may already exist): ${createError.message}")
                    }
                }
            } catch (e1: Exception) {
                android.util.Log.w("WebDavHelper", "Method 1 (exists) failed: ${e1.message}")
                lastError = e1
                
                // 方法2: 尝试 list() - PROPFIND 请求
                try {
                    val resources = sardine?.list(serverUrl)
                    android.util.Log.d("WebDavHelper", "Method 2 (list): found ${resources?.size ?: 0} resources")
                    connectionOk = true
                    lastError = null
                } catch (e2: Exception) {
                    android.util.Log.w("WebDavHelper", "Method 2 (list) failed: ${e2.message}")
                    lastError = e2
                    
                    // 方法3: 尝试上传一个测试文件
                    try {
                        val testFileName = ".monica_test"
                        val testUrl = "$serverUrl/$testFileName".replace("//", "/")
                            .replace(":/", "://")
                        val testData = "test".toByteArray()
                        
                        sardine?.put(testUrl, testData, "text/plain")
                        android.util.Log.d("WebDavHelper", "Method 3 (put): test file uploaded")
                        
                        // 尝试删除测试文件
                        try {
                            sardine?.delete(testUrl)
                            android.util.Log.d("WebDavHelper", "Test file deleted")
                        } catch (delError: Exception) {
                            android.util.Log.w("WebDavHelper", "Could not delete test file: ${delError.message}")
                        }
                        
                        connectionOk = true
                        lastError = null
                    } catch (e3: Exception) {
                        android.util.Log.w("WebDavHelper", "Method 3 (put) failed: ${e3.message}")
                        lastError = e3
                    }
                }
            }
            
            if (connectionOk) {
                android.util.Log.d("WebDavHelper", "Connection test SUCCESSFUL")
                return@withContext Result.success(true)
            } else {
                throw lastError ?: Exception("All connection methods failed")
            }
            
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Connection test FAILED", e)
            android.util.Log.e("WebDavHelper", "Error type: ${e.javaClass.name}")
            android.util.Log.e("WebDavHelper", "Error message: ${e.message}")
            
            // 添加更详细的错误信息
            val detailedMessage = when {
                e.message?.contains("Network is unreachable") == true -> 
                    "网络不可达，请检查网络连接"
                e.message?.contains("Connection timed out") == true -> 
                    "连接超时，请检查服务器地址和网络连接"
                e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> 
                    "认证失败(401)，请检查用户名和密码"
                e.message?.contains("404") == true -> 
                    "路径未找到(404)，请检查服务器地址"
                e.message?.contains("403") == true -> 
                    "访问被拒绝(403)，请检查权限设置"
                e.message?.contains("405") == true || e.message?.contains("Method Not Allowed") == true -> 
                    "服务器限制了某些操作方法(405)，但这不影响备份功能。请直接尝试创建备份。"
                else -> "连接测试失败: ${e.message}。如果账号密码正确，可以忽略此错误直接创建备份。"
            }
            Result.failure(Exception(detailedMessage, e))
        }
    }
    
    /**
     * 创建并上传备份
     * @param passwords 所有密码条目
     * @param secureItems 所有其他安全数据项(TOTP、银行卡、证件)
     * @param preferences 备份偏好设置，控制包含哪些内容类型
     * @return 备份报告，包含成功/失败详情
     */
    suspend fun createAndUploadBackup(
        passwords: List<PasswordEntry>,
        secureItems: List<SecureItem>,
        preferences: BackupPreferences = getBackupPreferences()
    ): Result<BackupReport> = withContext(Dispatchers.IO) {
        try {
            // 验证：检查是否至少启用了一种内容类型
            if (!preferences.hasAnyEnabled()) {
                android.util.Log.w("WebDavHelper", "Backup cancelled: no content types selected")
                return@withContext Result.failure(Exception("请至少选择一种备份内容"))
            }
            
            // 记录备份偏好设置
            android.util.Log.d("WebDavHelper", "Creating backup with preferences: $preferences")
            
            // P0修复：错误跟踪
            val failedItems = mutableListOf<FailedItem>()
            val warnings = mutableListOf<String>()
            var successPasswordCount = 0
            var successNoteCount = 0
            var successImageCount = 0
            
            // 1. 创建临时导出文件/目录
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val cacheBackupDir = File(context.cacheDir, "Monica_${timestamp}_backup")
            if (!cacheBackupDir.exists()) cacheBackupDir.mkdirs()
            
            // 使用旧版本兼容的文件名
            val passwordsCsvFile = File(cacheBackupDir, "Monica_${timestamp}_password.csv")
            // 分离备份文件：验证(TOTP) 和 证件/银行卡
            val totpCsvFile = File(cacheBackupDir, "Monica_${timestamp}_totp.csv")
            val cardsDocsCsvFile = File(cacheBackupDir, "Monica_${timestamp}_cards_docs.csv")
            // 笔记：改为每条笔记独立JSON文件存放于 notes 目录
            val notesDir = File(cacheBackupDir, "notes")
            // 密码：改为每个密码独立JSON文件存放于 passwords 目录
            val passwordsDir = File(cacheBackupDir, "passwords")
            // 旧版本兼容：如果需要恢复旧版本，可能需要这个，但这里是创建新备份，所以不需要创建 other.csv
            
            val historyJsonFile = File(context.cacheDir, "Monica_${timestamp}_generated_history.json")
            val zipFile = File(context.cacheDir, "monica_backup_$timestamp.zip")
            val finalFile = if (enableEncryption) {
                File(context.cacheDir, "monica_backup_$timestamp.enc.zip")
            } else {
                zipFile
            }
            
            try {
                // 2. 根据偏好设置过滤密码数据
                val filteredPasswords = if (preferences.includePasswords) passwords else emptyList()
                android.util.Log.d("WebDavHelper", "Passwords: ${passwords.size} total, ${filteredPasswords.size} included")
                
                // 3. 根据偏好设置过滤安全项目
                val filteredSecureItems = secureItems.filter { item ->
                    when (item.itemType) {
                        ItemType.TOTP -> preferences.includeAuthenticators
                        ItemType.DOCUMENT -> preferences.includeDocuments
                        ItemType.BANK_CARD -> preferences.includeBankCards
                        ItemType.NOTE -> preferences.includeNotes
                        else -> true // 其他类型默认包含
                    }
                }
                
                // 分类过滤后的项目
                val totpItems = filteredSecureItems.filter { it.itemType == ItemType.TOTP }
                val cardsDocsItems = filteredSecureItems.filter { it.itemType == ItemType.BANK_CARD || it.itemType == ItemType.DOCUMENT }
                val noteItems = filteredSecureItems.filter { it.itemType == ItemType.NOTE }
                
                // 记录各类型的数量
                val totpCount = secureItems.count { it.itemType == ItemType.TOTP }
                val docCount = secureItems.count { it.itemType == ItemType.DOCUMENT }
                val cardCount = secureItems.count { it.itemType == ItemType.BANK_CARD }
                val noteCount = secureItems.count { it.itemType == ItemType.NOTE }
                val filteredTotpCount = totpItems.size
                val filteredDocAndCardCount = cardsDocsItems.size
                val filteredNoteCount = noteItems.size
                
                android.util.Log.d("WebDavHelper", "TOTP: $totpCount total, $filteredTotpCount included")
                android.util.Log.d("WebDavHelper", "Docs & Cards: ${docCount + cardCount} total, $filteredDocAndCardCount included")
                android.util.Log.d("WebDavHelper", "Notes: $noteCount total, $filteredNoteCount included")
                
                // 4. 导出密码数据到JSON（更可靠的格式）
                val passwordsDir = File(cacheBackupDir, "passwords")
                if (preferences.includePasswords && filteredPasswords.isNotEmpty()) {
                    if (!passwordsDir.exists()) passwordsDir.mkdirs()
                    val json = Json { prettyPrint = false }
                    filteredPasswords.forEach { password ->
                        try {
                            val backup = PasswordBackupEntry(
                                id = password.id,
                                title = password.title,
                                username = password.username,
                                password = password.password,
                                website = password.website,
                                notes = password.notes,
                                isFavorite = password.isFavorite,
                                categoryId = password.categoryId,
                                createdAt = password.createdAt.time,
                                updatedAt = password.updatedAt.time
                            )
                            val fileName = "password_${password.id}_${password.createdAt.time}.json"
                            val target = File(passwordsDir, fileName)
                            target.writeText(json.encodeToString(PasswordBackupEntry.serializer(), backup), Charsets.UTF_8)
                            successPasswordCount++
                        } catch (e: Exception) {
                            android.util.Log.e("WebDavHelper", "导出密码失败: ${password.id} - ${password.title}", e)
                            failedItems.add(FailedItem(
                                id = password.id,
                                type = "密码",
                                title = password.title,
                                reason = "序列化失败: ${e.message}"
                            ))
                        }
                    }
                    // 向后兼容: 也生成CSV文件供旧版本使用
                    try {
                        exportPasswordsToCSV(filteredPasswords, passwordsCsvFile)
                    } catch (e: Exception) {
                        android.util.Log.w("WebDavHelper", "CSV backup failed (non-critical): ${e.message}")
                    }
                }
                
                // 5. 导出验证(TOTP)数据到CSV
                if (totpItems.isNotEmpty()) {
                    val exportManager = DataExportImportManager(context)
                    val csvUri = Uri.fromFile(totpCsvFile)
                    val exportResult = exportManager.exportData(totpItems, csvUri)
                    
                    if (exportResult.isFailure) {
                        return@withContext Result.failure(exportResult.exceptionOrNull() 
                            ?: Exception("导出验证数据失败"))
                    }
                }
                
                // 6. 导出证件和银行卡数据到CSV
                if (cardsDocsItems.isNotEmpty()) {
                    val exportManager = DataExportImportManager(context)
                    val csvUri = Uri.fromFile(cardsDocsCsvFile)
                    val exportResult = exportManager.exportData(cardsDocsItems, csvUri)
                    
                    if (exportResult.isFailure) {
                        return@withContext Result.failure(exportResult.exceptionOrNull() 
                            ?: Exception("导出证件和银行卡数据失败"))
                    }
                }

                // 6.5 导出笔记数据到单文件 JSON（每条一个文件，放在 notes 目录）
                if (noteItems.isNotEmpty()) {
                    if (!notesDir.exists()) notesDir.mkdirs()
                    val json = Json { prettyPrint = false }
                    noteItems.forEach { item ->
                        try {
                            val backup = NoteBackupEntry(
                                id = item.id,
                                title = item.title,
                                notes = item.notes,
                                itemData = item.itemData,
                                isFavorite = item.isFavorite,
                                imagePaths = item.imagePaths,
                                createdAt = item.createdAt.time,
                                updatedAt = item.updatedAt.time
                            )
                            val fileName = "note_${item.id}_${item.createdAt.time}.json"
                            val target = File(notesDir, fileName)
                            target.writeText(json.encodeToString(NoteBackupEntry.serializer(), backup), Charsets.UTF_8)
                            successNoteCount++  // P0修复：记录成功
                        } catch (e: Exception) {
                            android.util.Log.e("WebDavHelper", "导出单条笔记失败: ${item.id}", e)
                            // P0修复：记录失败项而不是静默跳过
                            failedItems.add(FailedItem(
                                id = item.id,
                                type = "笔记",
                                title = item.title,
                                reason = "序列化失败: ${e.message}"
                            ))
                        }
                    }
                }
                
                // 7. 创建ZIP文件,根据偏好设置包含相应内容
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    // 添加passwords目录下的每个密码JSON文件
                    if (passwordsDir.exists()) {
                        passwordsDir.listFiles()?.forEach { passwordFile ->
                            addFileToZip(zipOut, passwordFile, "passwords/${passwordFile.name}")
                        }
                    }
                    // 向后兼容: 添加passwords.csv（如果存在）
                    if (preferences.includePasswords && passwordsCsvFile.exists()) {
                        addFileToZip(zipOut, passwordsCsvFile, passwordsCsvFile.name)
                    }
                    
                    // 添加totp.csv（如果文件存在）
                    if (totpCsvFile.exists()) {
                        addFileToZip(zipOut, totpCsvFile, totpCsvFile.name)
                    }
                    
                    // 添加cards_docs.csv（如果文件存在）
                    if (cardsDocsCsvFile.exists()) {
                        addFileToZip(zipOut, cardsDocsCsvFile, cardsDocsCsvFile.name)
                    }

                    // 添加 notes 目录下的每条笔记文件
                    if (notesDir.exists()) {
                        notesDir.listFiles()?.forEach { noteFile ->
                            addFileToZip(zipOut, noteFile, "notes/${noteFile.name}")
                        }
                    }
                    
                    // 添加密码生成历史（JSON）- 如果启用
                    if (preferences.includeGeneratorHistory) {
                        try {
                            val historyManager = PasswordHistoryManager(context)
                            val historyJson = historyManager.exportHistoryJson()
                            historyJsonFile.writeText(historyJson, Charsets.UTF_8)
                            addFileToZip(zipOut, historyJsonFile, historyJsonFile.name)
                        } catch (e: Exception) {
                            android.util.Log.w("WebDavHelper", "Failed to export password generation history: ${e.message}")
                        }
                    }
                    
                    // 添加图片文件（银行卡和证件的照片）- 如果启用
                    if (preferences.includeImages) {
                        try {
                            val imageFileNames = extractAllImageFileNames(filteredSecureItems)
                            android.util.Log.d("WebDavHelper", "Found ${imageFileNames.size} image files to backup")
                            val imageDir = File(context.filesDir, "secure_images")
                            imageFileNames.forEach { fileName ->
                                val imageFile = File(imageDir, fileName)
                                when {
                                    imageFile.exists() -> {
                                        addFileToZip(zipOut, imageFile, "images/$fileName")
                                        successImageCount++  // P0修复：记录成功
                                    }
                                    else -> {
                                        android.util.Log.w("WebDavHelper", "Image file not found: $fileName")
                                        // P0修复：记录缺失的图片
                                        warnings.add("图片文件缺失: $fileName")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("WebDavHelper", "Failed to backup images: ${e.message}")
                            warnings.add("图片备份失败: ${e.message}")
                        }
                    }
                }
                
                // 8. 如果启用加密，加密 ZIP 文件
                if (enableEncryption && encryptionPassword.isNotEmpty()) {
                    val encryptResult = EncryptionHelper.encryptFile(zipFile, finalFile, encryptionPassword)
                    if (encryptResult.isFailure) {
                        return@withContext Result.failure(encryptResult.exceptionOrNull() 
                            ?: Exception("加密失败"))
                    }
                    android.util.Log.d("WebDavHelper", "Backup encrypted successfully")
                }
                
                // 9. 上传到WebDAV
                val uploadResult = uploadBackup(finalFile)
                
                // 10. 如果上传成功，更新最后备份时间
                if (uploadResult.isSuccess) {
                    updateLastBackupTime()
                }
                
                // P0修复：生成详细报告
                val totalImageCount = if (preferences.includeImages) extractAllImageFileNames(filteredSecureItems).size else 0
                
                val totalCounts = ItemCounts(
                    passwords = if (preferences.includePasswords) passwords.size else 0,
                    notes = noteItems.size,
                    totp = totpItems.size,
                    bankCards = cardsDocsItems.count { it.itemType == ItemType.BANK_CARD },
                    documents = cardsDocsItems.count { it.itemType == ItemType.DOCUMENT },
                    images = totalImageCount
                )
                
                val successCounts = ItemCounts(
                    passwords = if (preferences.includePasswords) passwords.size else 0,
                    notes = successNoteCount,
                    totp = totpItems.size,
                    bankCards = cardsDocsItems.count { it.itemType == ItemType.BANK_CARD },
                    documents = cardsDocsItems.count { it.itemType == ItemType.DOCUMENT },
                    images = successImageCount
                )
                
                val report = BackupReport(
                    success = uploadResult.isSuccess && failedItems.isEmpty(),
                    totalItems = totalCounts,
                    successItems = successCounts,
                    failedItems = failedItems,
                    warnings = warnings
                )
                
                if (uploadResult.isFailure) {
                    Result.failure(uploadResult.exceptionOrNull() ?: Exception("上传失败"))
                } else {
                    Result.success(report)
                }
            } finally {
                // 11. 清理临时文件
                passwordsCsvFile.delete()
                totpCsvFile.delete()
                cardsDocsCsvFile.delete()
                notesDir.deleteRecursively()
                historyJsonFile.delete()
                zipFile.delete()
                if (finalFile != zipFile) {
                    finalFile.delete()
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("创建备份失败: ${e.message}"))
        }
    }
    
    /**
     * 导出密码到CSV文件
     */
    private fun exportPasswordsToCSV(passwords: List<PasswordEntry>, file: File) {
        file.outputStream().use { output ->
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                // 写入BOM标记
                writer.write("\uFEFF")
                
                // 写入列标题
                writer.write("name,url,username,password,note")
                writer.newLine()
                
                // 写入数据行
                passwords.forEach { entry ->
                    val displayName = entry.title.ifBlank { entry.website.ifBlank { entry.username } }
                    val row = listOf(
                        escapeCsvField(displayName),
                        escapeCsvField(entry.website),
                        escapeCsvField(entry.username),
                        escapeCsvField(entry.password),
                        escapeCsvField(buildPasswordNoteWithMetadata(entry))
                    )
                    writer.write(row.joinToString(","))
                    writer.newLine()
                }
            }
        }
    }
    
    /**
     * 转义CSV字段
     */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
    }
    
    /**
     * 从安全项目列表中提取所有图片文件名
     */
    private fun extractAllImageFileNames(secureItems: List<SecureItem>): Set<String> {
        val imageFileNames = mutableSetOf<String>()
        val json = Json { ignoreUnknownKeys = true }
        
        secureItems.forEach { item ->
            try {
                if (!item.imagePaths.isNullOrBlank()) {
                    val imagePathsArray = json.parseToJsonElement(item.imagePaths).jsonArray
                    imagePathsArray.forEach { element ->
                        val imagePath = element.jsonPrimitive.content
                        if (imagePath.endsWith(".enc")) {
                            imageFileNames.add(imagePath)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("WebDavHelper", "Failed to parse imagePaths for item ${item.id}: ${e.message}")
            }
        }
        
        return imageFileNames
    }
    
    /**
     * 异常：需要密码
     */
    class PasswordRequiredException : Exception("备份文件已加密，请提供解密密码")

    /**
     * 下载并恢复备份 - 返回密码、其他数据和恢复报告
     * @param backupFile 要恢复的备份文件
     * @param decryptPassword 解密密码 (如果文件已加密)
     */
    suspend fun downloadAndRestoreBackup(
        backupFile: BackupFile,
        decryptPassword: String? = null
    ): Result<RestoreResult> = 
        withContext(Dispatchers.IO) {
        try {
            // P0修复：错误跟踪
            val failedItems = mutableListOf<FailedItem>()
            val warnings = mutableListOf<String>()
            var backupPasswordCount = 0
            var backupNoteCount = 0
            var backupTotpCount = 0
            var backupCardCount = 0
            var backupDocCount = 0
            var backupImageCount = 0
            var restoredPasswordCount = 0
            var restoredNoteCount = 0
            var restoredImageCount = 0
            // 1. 下载备份文件
            val downloadedFile = File(context.cacheDir, "restore_${backupFile.name}")
            val downloadResult = downloadBackup(backupFile, downloadedFile)
            
            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() 
                    ?: Exception("下载备份失败"))
            }
            
            // 2. 检测是否加密
            val isEncrypted = EncryptionHelper.isEncryptedFile(downloadedFile)
            
            // 3. 解密文件 (如果需要)
            val zipFile = if (isEncrypted) {
                val password = decryptPassword ?: encryptionPassword
                if (password.isEmpty()) {
                    downloadedFile.delete()
                    return@withContext Result.failure(PasswordRequiredException())
                }
                
                val decryptedFile = File(context.cacheDir, "restore_decrypted_${System.nanoTime()}.zip")
                val decryptResult = EncryptionHelper.decryptFile(downloadedFile, decryptedFile, password)
                
                if (decryptResult.isFailure) {
                    downloadedFile.delete()
                    return@withContext Result.failure(decryptResult.exceptionOrNull() 
                        ?: Exception("解密失败"))
                }
                
                android.util.Log.d("WebDavHelper", "Backup decrypted successfully")
                decryptedFile
            } else {
                downloadedFile
            }
            
            try {
                val passwords = mutableListOf<PasswordEntry>()
                val secureItems = mutableListOf<DataExportImportManager.ExportItem>()
                
                // 临时存储CSV文件路径，延后处理
                var passwordsCsvFile: File? = null
                
                // 4. 解压ZIP文件并读取JSON/CSV、密码历史和图片
                ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val entryName = entry.name.substringAfterLast('/')
                        val tempFile = File(context.cacheDir, "restore_${System.nanoTime()}_${entryName}")
                        FileOutputStream(tempFile).use { fileOut ->
                            zipIn.copyTo(fileOut)
                        }
                        zipIn.closeEntry()
                        when {
                            // 优先收集JSON格式的密码文件
                            entry.name.contains("/passwords/") || entry.name.startsWith("passwords/") -> {
                                backupPasswordCount++
                                val passwordItem = restorePasswordFromJson(tempFile)
                                if (passwordItem != null) {
                                    passwords.add(passwordItem)
                                    restoredPasswordCount++
                                } else {
                                    failedItems.add(FailedItem(
                                        id = 0,
                                        type = "密码",
                                        title = entryName,
                                        reason = "JSON解析失败"
                                    ))
                                }
                                tempFile.delete()
                            }
                            // 保存CSV文件路径，稍后处理（向后兼容）
                            entryName.equals("passwords.csv", ignoreCase = true) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_password.csv", ignoreCase = true)) -> {
                                // 只在没有JSON密码时才使用CSV
                                if (passwordsCsvFile == null) {
                                    passwordsCsvFile = tempFile
                                } else {
                                    tempFile.delete()
                                }
                            }
                            entryName.equals("secure_items.csv", ignoreCase = true) ||
                                entryName.equals("backup.csv", ignoreCase = true) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_other.csv", ignoreCase = true)) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_totp.csv", ignoreCase = true)) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_cards_docs.csv", ignoreCase = true)) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_notes.csv", ignoreCase = true)) -> {
                                val exportManager = DataExportImportManager(context)
                                val csvUri = Uri.fromFile(tempFile)
                                val importResult = exportManager.importData(csvUri)
                                if (importResult.isSuccess) {
                                    secureItems.addAll(importResult.getOrNull() ?: emptyList())
                                }
                            }
                            entry.name.contains("/notes/") || entry.name.startsWith("notes/") -> {
                                backupNoteCount++  // P0修复：统计备份中的笔记数
                                val noteItem = restoreNoteFromJson(tempFile)
                                if (noteItem != null) {
                                    secureItems.add(noteItem)
                                    restoredNoteCount++  // P0修复：记录成功
                                } else {
                                    // P0修复：记录失败项
                                    failedItems.add(FailedItem(
                                        id = 0,
                                        type = "笔记",
                                        title = entryName,
                                        reason = "JSON解析失败"
                                    ))
                                }
                            }
                            entryName.endsWith("_generated_history.json", ignoreCase = true) -> {
                                // 恢复密码生成历史
                                try {
                                    val historyJson = tempFile.readText(Charsets.UTF_8)
                                    val json = Json { ignoreUnknownKeys = true }
                                    val history = json.decodeFromString<List<takagi.ru.monica.data.PasswordGenerationHistory>>(historyJson)
                                    val historyManager = PasswordHistoryManager(context)
                                    historyManager.importHistory(history)
                                    android.util.Log.d("WebDavHelper", "Restored ${history.size} password generation history entries")
                                } catch (e: Exception) {
                                    android.util.Log.w("WebDavHelper", "Failed to restore password generation history: ${e.message}")
                                }
                            }
                            entry.name.contains("/images/") || entryName.endsWith(".enc") -> {
                                backupImageCount++  // P0修复：统计备份中的图片数
                                // 恢复图片文件
                                try {
                                    val imageDir = File(context.filesDir, "secure_images")
                                    if (!imageDir.exists()) {
                                        imageDir.mkdirs()
                                    }
                                    val destFile = File(imageDir, entryName)
                                    tempFile.copyTo(destFile, overwrite = true)
                                    android.util.Log.d("WebDavHelper", "Restored image file: $entryName")
                                    restoredImageCount++  // P0修复：记录成功
                                } catch (e: Exception) {
                                    android.util.Log.w("WebDavHelper", "Failed to restore image file $entryName: ${e.message}")
                                    // P0修复：记录失败
                                    warnings.add("图片恢复失败: $entryName - ${e.message}")
                                }
                            }
                        }
                        if (!entry.name.contains("/passwords/") && !entry.name.startsWith("passwords/") &&
                            !(entryName.equals("passwords.csv", ignoreCase = true) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_password.csv", ignoreCase = true)))) {
                            tempFile.delete()
                        }
                        entry = zipIn.nextEntry
                    }
                }
                
                // 5. 向后兼容：如果没有JSON密码，使用CSV（支持旧版本备份）
                passwordsCsvFile?.let { csvFile ->
                    if (passwords.isEmpty() && csvFile.exists()) {
                        android.util.Log.d("WebDavHelper", "No JSON passwords found, using CSV for backward compatibility")
                        try {
                            val csvPasswords = importPasswordsFromCSV(csvFile)
                            backupPasswordCount = csvPasswords.size
                            passwords.addAll(csvPasswords)
                            restoredPasswordCount = csvPasswords.size
                            android.util.Log.d("WebDavHelper", "Restored ${csvPasswords.size} passwords from CSV")
                        } catch (e: Exception) {
                            android.util.Log.e("WebDavHelper", "Failed to import passwords from CSV: ${e.message}")
                            warnings.add("CSV密码导入失败: ${e.message}")
                        }
                    }
                    csvFile.delete()
                }
                
                // P0修复：生成详细报告
                if (backupPasswordCount == 0) {
                    backupPasswordCount = passwords.size
                }
                val totpItems = secureItems.count { it.itemType == "TOTP" }
                val cardItems = secureItems.count { it.itemType == "BANK_CARD" }
                val docItems = secureItems.count { it.itemType == "DOCUMENT" }
                
                val backupCounts = ItemCounts(
                    passwords = backupPasswordCount,
                    notes = backupNoteCount,
                    totp = totpItems,
                    bankCards = cardItems,
                    documents = docItems,
                    images = backupImageCount
                )
                
                val restoredCounts = ItemCounts(
                    passwords = passwords.size,
                    notes = restoredNoteCount,
                    totp = totpItems,
                    bankCards = cardItems,
                    documents = docItems,
                    images = restoredImageCount
                )
                
                val report = RestoreReport(
                    success = failedItems.isEmpty(),
                    backupContains = backupCounts,
                    restoredSuccessfully = restoredCounts,
                    failedItems = failedItems,
                    warnings = warnings
                )
                
                Result.success(RestoreResult(
                    content = BackupContent(passwords, secureItems),
                    report = report
                ))
            } finally {
                zipFile.delete()
                if (zipFile != downloadedFile) {
                    downloadedFile.delete()
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("恢复备份失败: ${e.message}"))
        }
    }
    
    /**
     * 从CSV文件导入密码
     */
    private fun importPasswordsFromCSV(file: File): List<PasswordEntry> {
        val passwords = mutableListOf<PasswordEntry>()
        
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var firstLine = reader.readLine()
            
            // 跳过BOM标记
            if (firstLine?.startsWith("\uFEFF") == true) {
                firstLine = firstLine.substring(1)
            }
            var format = PasswordCsvFormat.UNKNOWN
            var isHeader = false
            if (firstLine != null) {
                val fields = splitCsvLine(firstLine)
                format = detectPasswordCsvFormat(fields)
                isHeader = when (format) {
                    PasswordCsvFormat.CHROME -> fields.map { it.lowercase(Locale.getDefault()) }.let {
                        it.contains("name") && it.contains("password") && it.contains("username")
                    }
                    PasswordCsvFormat.LEGACY -> fields.map { it.lowercase(Locale.getDefault()) }.let {
                        it.contains("title") && it.contains("password")
                    }
                    PasswordCsvFormat.UNKNOWN -> false
                }
                if (!isHeader && firstLine.isNotBlank()) {
                    parsePasswordEntry(firstLine, format)?.let { passwords.add(it) }
                }
            }
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    parsePasswordEntry(line, format)?.let { passwords.add(it) }
                }
            }
        }
        
        return passwords
    }

    private fun restorePasswordFromJson(file: File): PasswordEntry? {
        return try {
            val content = file.readText(Charsets.UTF_8)
            val json = Json { ignoreUnknownKeys = true }
            val backup = json.decodeFromString<PasswordBackupEntry>(content)
            PasswordEntry(
                id = 0, // 重置id以避免冲突
                title = backup.title,
                username = backup.username,
                password = backup.password,
                website = backup.website,
                notes = backup.notes,
                isFavorite = backup.isFavorite,
                categoryId = backup.categoryId,
                createdAt = Date(backup.createdAt),
                updatedAt = Date(backup.updatedAt)
            )
        } catch (e: Exception) {
            android.util.Log.w("WebDavHelper", "Failed to parse password JSON from ${file.name}: ${e.message}")
            null
        }
    }

    private fun restoreNoteFromJson(file: File): DataExportImportManager.ExportItem? {
        return try {
            val json = Json { ignoreUnknownKeys = true }
            val text = file.readText(Charsets.UTF_8)
            val entry = json.decodeFromString(NoteBackupEntry.serializer(), text)
            DataExportImportManager.ExportItem(
                id = entry.id,
                itemType = ItemType.NOTE.name,
                title = entry.title,
                itemData = entry.itemData,
                notes = entry.notes,
                isFavorite = entry.isFavorite,
                imagePaths = entry.imagePaths,
                createdAt = entry.createdAt,
                updatedAt = entry.updatedAt
            )
        } catch (e: Exception) {
            android.util.Log.w("WebDavHelper", "Failed to restore note from ${file.name}: ${e.message}")
            null
        }
    }

    private fun detectPasswordCsvFormat(fields: List<String>): PasswordCsvFormat {
        val lowered = fields.map { it.lowercase(Locale.getDefault()) }
        return when {
            lowered.contains("name") && lowered.contains("url") &&
                lowered.contains("username") && lowered.contains("password") -> PasswordCsvFormat.CHROME
            lowered.contains("title") && lowered.contains("password") -> PasswordCsvFormat.LEGACY
            else -> PasswordCsvFormat.UNKNOWN
        }
    }

    private fun parsePasswordEntry(line: String, format: PasswordCsvFormat): PasswordEntry? {
        val fields = splitCsvLine(line)
        return when (format) {
            PasswordCsvFormat.CHROME -> parseChromePasswordFields(fields)
            PasswordCsvFormat.LEGACY -> parseLegacyPasswordFields(fields)
            PasswordCsvFormat.UNKNOWN -> parseLegacyPasswordFields(fields) ?: parseChromePasswordFields(fields)
        }
    }

    private fun parseLegacyPasswordFields(fields: List<String>): PasswordEntry? {
        return try {
            if (fields.size >= 11) {
                PasswordEntry(
                    id = 0,
                    title = fields[1],
                    website = fields[2],
                    username = fields[3],
                    password = fields[4],
                    notes = fields[5],
                    isFavorite = fields[6].toBoolean(),
                    createdAt = Date(fields[7].toLong()),
                    updatedAt = Date(fields[8].toLong()),
                    sortOrder = fields.getOrNull(9)?.toIntOrNull() ?: 0,
                    isGroupCover = fields.getOrNull(10)?.toBoolean() ?: false
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Failed to parse legacy password CSV line: ${e.message}")
            null
        }
    }

    private fun parseChromePasswordFields(fields: List<String>): PasswordEntry? {
        return try {
            if (fields.size >= 4) {
                val now = Date()
                val title = fields.getOrNull(0)?.trim().orEmpty()
                val website = fields.getOrNull(1)?.trim().orEmpty()
                val username = fields.getOrNull(2)?.trim().orEmpty()
                val password = fields.getOrNull(3)?.trim().orEmpty()
                val rawNote = fields.getOrNull(4) ?: ""
                val (note, metadata) = extractNoteAndMetadata(rawNote)
                val createdAt = metadata["createdAt"]?.toLongOrNull()?.let(::Date) ?: now
                val updatedAt = metadata["updatedAt"]?.toLongOrNull()?.let(::Date) ?: createdAt

                PasswordEntry(
                    id = 0,
                    title = if (title.isNotBlank()) title else website.ifBlank { username },
                    website = website,
                    username = username,
                    password = password,
                    notes = note,
                    isFavorite = metadata["isFavorite"]?.toBoolean() ?: false,
                    createdAt = createdAt,
                    updatedAt = updatedAt,
                    sortOrder = metadata["sortOrder"]?.toIntOrNull() ?: 0,
                    isGroupCover = metadata["isGroupCover"]?.toBoolean() ?: false
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Failed to parse Chrome password CSV line: ${e.message}")
            null
        }
    }

    private fun buildPasswordNoteWithMetadata(entry: PasswordEntry): String {
        val metaParts = listOf(
            "isFavorite=${entry.isFavorite}",
            "createdAt=${entry.createdAt.time}",
            "updatedAt=${entry.updatedAt.time}",
            "sortOrder=${entry.sortOrder}",
            "isGroupCover=${entry.isGroupCover}"
        )
        return buildString {
            if (entry.notes.isNotEmpty()) {
                append(entry.notes)
                append("\n\n")
            }
            append(PASSWORD_META_MARKER)
            append(metaParts.joinToString("|"))
        }
    }

    private fun extractNoteAndMetadata(noteRaw: String): Pair<String, Map<String, String>> {
        val normalised = noteRaw.replace("\r\n", "\n")
        val markerIndex = normalised.indexOf(PASSWORD_META_MARKER)
        if (markerIndex < 0) {
            return noteRaw to emptyMap()
        }
        val baseNote = normalised.substring(0, markerIndex).trimEnd('\n', '\r')
        val metaPart = normalised.substring(markerIndex + PASSWORD_META_MARKER.length)
        val metadata = metaPart.split('|')
            .mapNotNull {
                val trimmed = it.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val parts = trimmed.split('=', limit = 2)
                if (parts.size == 2) parts[0] to parts[1] else null
            }
            .toMap()
        return baseNote to metadata
    }

    private fun splitCsvLine(line: String): List<String> {
        return line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
            .map { it.trim().removeSurrounding("\"").replace("\"\"", "\"") }
    }

    private enum class PasswordCsvFormat {
        LEGACY,
        CHROME,
        UNKNOWN
    }

    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) return
        FileInputStream(file).use { fileIn ->
            val zipEntry = ZipEntry(entryName)
            zipOut.putNextEntry(zipEntry)
            fileIn.copyTo(zipOut)
            zipOut.closeEntry()
        }
    }
    
    /**
     * 上传备份文件
     */
    suspend fun uploadBackup(file: File): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            // 创建 Monica 备份目录
            val backupDir = "$serverUrl/Monica_Backups"
            if (!sardine!!.exists(backupDir)) {
                sardine!!.createDirectory(backupDir)
            }
            
            // 生成带时间戳的文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "monica_backup_$timestamp.zip"
            val remotePath = "$backupDir/$fileName"
            
            // 上传文件
            val fileBytes = file.readBytes()
            sardine!!.put(remotePath, fileBytes, "application/zip")
            
            Result.success(fileName)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 列出所有备份文件
     */
    suspend fun listBackups(): Result<List<BackupFile>> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            val backupDir = "$serverUrl/Monica_Backups"
            
            // 检查目录是否存在
            if (!sardine!!.exists(backupDir)) {
                return@withContext Result.success(emptyList())
            }
            
            // 列出目录内容
            val resources = sardine!!.list(backupDir)
            
            val backups = resources
                .filter { !it.isDirectory && it.name.endsWith(".zip") }
                .map { resource ->
                    BackupFile(
                        name = resource.name,
                        path = resource.href.toString(),
                        size = resource.contentLength ?: 0,
                        modified = resource.modified ?: Date()
                    )
                }
                .sortedByDescending { it.modified }
            
            Result.success(backups)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 下载备份文件
     */
    suspend fun downloadBackup(backupFile: BackupFile, destFile: File): Result<File> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            val remotePath = "$serverUrl/Monica_Backups/${backupFile.name}"
            
            // 下载文件
            sardine!!.get(remotePath).use { inputStream ->
                destFile.outputStream().use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            Result.success(destFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除备份文件
     */
    suspend fun deleteBackup(backupFile: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            val remotePath = "$serverUrl/Monica_Backups/${backupFile.name}"
            sardine!!.delete(remotePath)
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
    
    /**
     * 获取加密配置
     */
    data class EncryptionConfig(
        val enabled: Boolean,
        val password: String
    )

    fun getEncryptionConfig(): EncryptionConfig {
        return EncryptionConfig(enableEncryption, encryptionPassword)
    }

    /**
     * 设置加密配置
     */
    fun setEncryptionConfig(enabled: Boolean, password: String) {
        enableEncryption = enabled
        encryptionPassword = password
        saveConfig()
    }
}

/**
 * 备份文件信息
 */
data class BackupFile(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Date
) {
    /**
     * 判断是否为加密文件
     */
    fun isEncrypted(): Boolean {
        return name.endsWith(".enc.zip")
    }
}

data class BackupContent(
    val passwords: List<PasswordEntry>,
    val secureItems: List<DataExportImportManager.ExportItem>
)

/**
 * 恢复结果 - 包含恢复的内容和详细报告
 */
data class RestoreResult(
    val content: BackupContent,
    val report: RestoreReport
)


/**
 * 检查网络和时间同步状态
 */
private fun checkNetworkAndTimeSync(context: Context) {
    try {
        // 检查网络连接
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        
        if (activeNetworkInfo == null || !activeNetworkInfo.isConnected) {
            android.util.Log.w("WebDavHelper", "Network not available, some features may not work properly")
            // 显示网络不可用提示
            android.util.Log.w("WebDavHelper", "网络连接不可用，部分功能可能受限")
        }
        
        // 检查时间同步问题
        try {
            val currentTime = System.currentTimeMillis()
            // 检查时间是否合理 (2001年以后)
            if (currentTime < 1000000000000L) {
                android.util.Log.w("WebDavHelper", "System time appears incorrect, using default time")
                // 使用应用内的时间逻辑
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Error checking time", e)
        }
    } catch (e: Exception) {
        android.util.Log.e("WebDavHelper", "Error checking network and time sync", e)
    }
}

/**
 * 为用户获取系统服务
 */
private fun getSystemServiceForUser(context: Context, serviceName: String): Any? {
    try {
        // 确保在访问系统服务时提供正确的用户上下文
        return context.getSystemService(serviceName)
    } catch (e: Exception) {
        android.util.Log.e("WebDavHelper", "Error getting system service for user", e)
        // 降级到普通方式
        return context.getSystemService(serviceName)
    }
}


