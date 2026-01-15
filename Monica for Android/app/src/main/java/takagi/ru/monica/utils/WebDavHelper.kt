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
import takagi.ru.monica.data.model.TotpData
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
    val categoryName: String? = null,  // ✅ 添加分类名称用于跨设备同步
    val email: String = "",
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val authenticatorKey: String = "",  // ✅ 直接存储验证器密钥
    // ✅ 第三方登录(SSO)字段
    val loginType: String = "PASSWORD",  // 登录类型: PASSWORD 或 SSO
    val ssoProvider: String = "",        // SSO提供商: GOOGLE, APPLE, FACEBOOK 等
    val ssoRefEntryId: Long? = null      // 引用的账号条目ID
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

@Serializable
private data class CategoryBackupEntry(
    val id: Long = 0,
    val name: String = "",
    val sortOrder: Int = 0
)

@Serializable
private data class OperationLogBackupEntry(
    val id: Long = 0,
    val itemType: String = "",
    val itemId: Long = 0,
    val itemTitle: String = "",
    val operationType: String = "",
    val changesJson: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val timestamp: Long = 0,
    val isReverted: Boolean = false
)

@Serializable
private data class TrashPasswordBackupEntry(
    val id: Long = 0,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val categoryId: Long? = null,
    val categoryName: String? = null,
    val email: String = "",
    val phone: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val authenticatorKey: String = "",
    val deletedAt: Long? = null,
    // ✅ 第三方登录(SSO)字段
    val loginType: String = "PASSWORD",
    val ssoProvider: String = "",
    val ssoRefEntryId: Long? = null
)

@Serializable
private data class TrashSecureItemBackupEntry(
    val id: Long = 0,
    val title: String = "",
    val itemType: String = "",
    val itemData: String = "",
    val notes: String = "",
    val isFavorite: Boolean = false,
    val imagePaths: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null
)

/**
 * ✅ 常用账号信息备份数据类
 * 用于备份设置中的常用账号信息
 */
@Serializable
private data class CommonAccountBackupEntry(
    val email: String = "",
    val phone: String = "",
    val username: String = "",
    val autoFillEnabled: Boolean = false
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
    
    // 备份操作锁，防止多次点击导致并发备份
    private val backupLock = java.util.concurrent.atomic.AtomicBoolean(false)
    
    /**
     * 检查是否正在备份
     */
    fun isBackupInProgress(): Boolean = backupLock.get()
    
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
        private const val PERMANENT_SUFFIX = "_permanent"        
        // Backup preferences keys
        private const val KEY_BACKUP_INCLUDE_PASSWORDS = "backup_include_passwords"
        private const val KEY_BACKUP_INCLUDE_AUTHENTICATORS = "backup_include_authenticators"
        private const val KEY_BACKUP_INCLUDE_DOCUMENTS = "backup_include_documents"
        private const val KEY_BACKUP_INCLUDE_BANK_CARDS = "backup_include_bank_cards"
        private const val KEY_BACKUP_INCLUDE_GENERATOR_HISTORY = "backup_include_generator_history"
        private const val KEY_BACKUP_INCLUDE_IMAGES = "backup_include_images"
        private const val KEY_BACKUP_INCLUDE_NOTES = "backup_include_notes"
        private const val KEY_BACKUP_INCLUDE_TIMELINE = "backup_include_timeline"
        private const val KEY_BACKUP_INCLUDE_TRASH = "backup_include_trash"
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
            putBoolean(KEY_BACKUP_INCLUDE_TIMELINE, preferences.includeTimeline)
            putBoolean(KEY_BACKUP_INCLUDE_TRASH, preferences.includeTrash)
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
            includeNotes = prefs.getBoolean(KEY_BACKUP_INCLUDE_NOTES, true),
            includeTimeline = prefs.getBoolean(KEY_BACKUP_INCLUDE_TIMELINE, true),
            includeTrash = prefs.getBoolean(KEY_BACKUP_INCLUDE_TRASH, true)
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
     * 创建备份文件 (通用方法，用于 WebDAV 上传和本地导出)
     * @param passwords 所有密码条目
     * @param secureItems 所有其他安全数据项
     * @param preferences 备份偏好设置
     * @return Result<Pair<File, BackupReport>> 包含生成的ZIP文件和备份报告
     */
    suspend fun createBackupZip(
        passwords: List<PasswordEntry>,
        secureItems: List<SecureItem>,
        preferences: BackupPreferences = getBackupPreferences()
    ): Result<Pair<File, BackupReport>> = withContext(Dispatchers.IO) {
        try {
            // 验证：检查是否至少启用了一种内容类型
            if (!preferences.hasAnyEnabled()) {
                android.util.Log.w("WebDavHelper", "Backup cancelled: no content types selected")
                return@withContext Result.failure(Exception("请至少选择一种备份内容"))
            }

            // 记录备份偏好设置
            android.util.Log.d("WebDavHelper", "Creating backup zip with preferences: $preferences")

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

            val passwordsCsvFile = File(cacheBackupDir, "Monica_${timestamp}_password.csv")
            val totpCsvFile = File(cacheBackupDir, "Monica_${timestamp}_totp.csv")
            val cardsDocsCsvFile = File(cacheBackupDir, "Monica_${timestamp}_cards_docs.csv")
            val notesDir = File(cacheBackupDir, "notes")
            val passwordsDir = File(cacheBackupDir, "passwords")
            
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
                
                // 3. 根据偏好设置过滤安全项目
                val filteredSecureItems = secureItems.filter { item ->
                    when (item.itemType) {
                        ItemType.TOTP -> preferences.includeAuthenticators
                        ItemType.DOCUMENT -> preferences.includeDocuments
                        ItemType.BANK_CARD -> preferences.includeBankCards
                        ItemType.NOTE -> preferences.includeNotes
                        else -> true
                    }
                }

                // 分类过滤后的项目
                val totpItems = filteredSecureItems.filter { it.itemType == ItemType.TOTP }
                val cardsDocsItems = filteredSecureItems.filter { it.itemType == ItemType.BANK_CARD || it.itemType == ItemType.DOCUMENT }
                val noteItems = filteredSecureItems.filter { it.itemType == ItemType.NOTE }

                // 4. 导出密码数据到JSON
                if (preferences.includePasswords && filteredPasswords.isNotEmpty()) {
                    if (!passwordsDir.exists()) passwordsDir.mkdirs()
                    val json = Json { prettyPrint = false }
                    
                    val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                    val categoryDao = database.categoryDao()
                    val allCategories = try { categoryDao.getAllCategories().first() } catch (e: Exception) { emptyList() }
                    val categoryMap = allCategories.associateBy { it.id }
                    
                    filteredPasswords.forEach { password ->
                        try {
                            val categoryName = password.categoryId?.let { id -> categoryMap[id]?.name }
                            
                            val backup = PasswordBackupEntry(
                                id = password.id,
                                title = password.title,
                                username = password.username,
                                password = password.password,
                                website = password.website,
                                notes = password.notes,
                                isFavorite = password.isFavorite,
                                categoryId = password.categoryId,
                                categoryName = categoryName,
                                email = password.email,
                                phone = password.phone,
                                createdAt = password.createdAt.time,
                                updatedAt = password.updatedAt.time,
                                authenticatorKey = password.authenticatorKey,  // ✅ 直接备份验证器密钥
                                // ✅ 第三方登录(SSO)字段
                                loginType = password.loginType,
                                ssoProvider = password.ssoProvider,
                                ssoRefEntryId = password.ssoRefEntryId
                            )
                            val fileName = "password_${password.id}_${password.createdAt.time}.json"
                            val target = File(passwordsDir, fileName)
                            target.writeText(json.encodeToString(PasswordBackupEntry.serializer(), backup), Charsets.UTF_8)
                            successPasswordCount++
                        } catch (e: Exception) {
                            android.util.Log.e("WebDavHelper", "导出密码失败: ${password.id}", e)
                            failedItems.add(FailedItem(
                                id = password.id,
                                type = "密码",
                                title = password.title,
                                reason = "序列化失败: ${e.message}"
                            ))
                        }
                    }
                    
                    try {
                        exportPasswordsToCSV(filteredPasswords, passwordsCsvFile)
                    } catch (e: Exception) {
                        android.util.Log.w("WebDavHelper", "CSV backup failed: ${e.message}")
                    }
                    
                    try {
                        if (allCategories.isNotEmpty()) {
                            val categoriesFile = File(cacheBackupDir, "categories.json")
                            val categoryBackups = allCategories.map { cat ->
                                CategoryBackupEntry(cat.id, cat.name, cat.sortOrder)
                            }
                            categoriesFile.writeText(
                                Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(CategoryBackupEntry.serializer()), categoryBackups),
                                Charsets.UTF_8
                            )
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WebDavHelper", "Category backup failed: ${e.message}")
                    }
                }

                // 5. 导出TOTP
                if (totpItems.isNotEmpty()) {
                    val exportManager = DataExportImportManager(context)
                    val exportResult = exportManager.exportData(totpItems, Uri.fromFile(totpCsvFile))
                    if (exportResult.isFailure) throw exportResult.exceptionOrNull()!!
                }

                // 6. 导出 Cards & Docs
                if (cardsDocsItems.isNotEmpty()) {
                    val exportManager = DataExportImportManager(context)
                    val exportResult = exportManager.exportData(cardsDocsItems, Uri.fromFile(cardsDocsCsvFile))
                    if (exportResult.isFailure) throw exportResult.exceptionOrNull()!!
                }

                // 6.5 导出笔记
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
                            successNoteCount++
                        } catch (e: Exception) {
                            failedItems.add(FailedItem(
                                id = item.id,
                                type = "笔记",
                                title = item.title,
                                reason = "序列化失败: ${e.message}"
                            ))
                        }
                    }
                }

                // 7. 创建 ZIP
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    if (passwordsDir.exists()) {
                        passwordsDir.listFiles()?.forEach { addFileToZip(zipOut, it, "passwords/${it.name}") }
                    }
                    if (preferences.includePasswords && passwordsCsvFile.exists()) {
                        addFileToZip(zipOut, passwordsCsvFile, passwordsCsvFile.name)
                    }
                    if (totpCsvFile.exists()) addFileToZip(zipOut, totpCsvFile, totpCsvFile.name)
                    if (cardsDocsCsvFile.exists()) addFileToZip(zipOut, cardsDocsCsvFile, cardsDocsCsvFile.name)
                    if (notesDir.exists()) {
                        notesDir.listFiles()?.forEach { addFileToZip(zipOut, it, "notes/${it.name}") }
                    }
                    if (preferences.includeGeneratorHistory) {
                        try {
                            val historyManager = PasswordHistoryManager(context)
                            val historyJson = historyManager.exportHistoryJson()
                            historyJsonFile.writeText(historyJson, Charsets.UTF_8)
                            addFileToZip(zipOut, historyJsonFile, historyJsonFile.name)
                        } catch (e: Exception) {
                            android.util.Log.w("WebDavHelper", "Failed to export history: ${e.message}")
                        }
                    }
                    if (preferences.includeImages) {
                        try {
                            val imageFileNames = extractAllImageFileNames(filteredSecureItems)
                            val imageDir = File(context.filesDir, "secure_images")
                            imageFileNames.forEach { fileName ->
                                val imageFile = File(imageDir, fileName)
                                if (imageFile.exists()) {
                                    addFileToZip(zipOut, imageFile, "images/$fileName")
                                    successImageCount++
                                } else {
                                    warnings.add("图片文件缺失: $fileName")
                                }
                            }
                        } catch (e: Exception) {
                            warnings.add("图片备份失败: ${e.message}")
                        }
                    }
                    
                    // 7.5 备份操作历史记录 (时间线)
                    if (preferences.includeTimeline) {
                        try {
                            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                            val operationLogDao = database.operationLogDao()
                            val allLogs = operationLogDao.getAllLogsSync()
                            
                            if (allLogs.isNotEmpty()) {
                                val logBackups = allLogs.map { log ->
                                    OperationLogBackupEntry(
                                        id = log.id,
                                        itemType = log.itemType,
                                        itemId = log.itemId,
                                        itemTitle = log.itemTitle,
                                        operationType = log.operationType,
                                        changesJson = log.changesJson,
                                        deviceId = log.deviceId,
                                        deviceName = log.deviceName,
                                        timestamp = log.timestamp,
                                        isReverted = log.isReverted
                                    )
                                }
                                val json = Json { prettyPrint = false }
                                val timelineJson = json.encodeToString(
                                    kotlinx.serialization.builtins.ListSerializer(OperationLogBackupEntry.serializer()),
                                    logBackups
                                )
                                val timelineFile = File(cacheBackupDir, "timeline_history.json")
                                timelineFile.writeText(timelineJson, Charsets.UTF_8)
                                addFileToZip(zipOut, timelineFile, timelineFile.name)
                                timelineFile.delete()
                                android.util.Log.d("WebDavHelper", "Backup ${allLogs.size} timeline entries")
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("WebDavHelper", "Failed to backup timeline: ${e.message}")
                            warnings.add("操作历史备份失败: ${e.message}")
                        }
                    }
                    
                    // 7.6 备份回收站数据
                    if (preferences.includeTrash) {
                        try {
                            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                            val passwordEntryDao = database.passwordEntryDao()
                            val secureItemDao = database.secureItemDao()
                            
                            // 获取已删除的密码
                            val deletedPasswords = passwordEntryDao.getDeletedEntriesSync()
                            // 获取已删除的安全项目
                            val deletedSecureItems = secureItemDao.getDeletedItemsSync()
                            
                            val json = Json { prettyPrint = false }
                            val trashDir = File(cacheBackupDir, "trash")
                            if (!trashDir.exists()) trashDir.mkdirs()
                            
                            // 备份已删除的密码
                            if (deletedPasswords.isNotEmpty()) {
                                val categoryDao = database.categoryDao()
                                val allCategories = try { categoryDao.getAllCategories().first() } catch (e: Exception) { emptyList() }
                                val categoryMap = allCategories.associateBy { it.id }
                                
                                val trashPasswordBackups = deletedPasswords.map { password ->
                                    val categoryName = password.categoryId?.let { id -> categoryMap[id]?.name }
                                    TrashPasswordBackupEntry(
                                        id = password.id,
                                        title = password.title,
                                        username = password.username,
                                        password = password.password,
                                        website = password.website,
                                        notes = password.notes,
                                        isFavorite = password.isFavorite,
                                        categoryId = password.categoryId,
                                        categoryName = categoryName,
                                        email = password.email,
                                        phone = password.phone,
                                        createdAt = password.createdAt.time,
                                        updatedAt = password.updatedAt.time,
                                        authenticatorKey = password.authenticatorKey,
                                        deletedAt = password.deletedAt?.time,
                                        // ✅ 第三方登录(SSO)字段
                                        loginType = password.loginType,
                                        ssoProvider = password.ssoProvider,
                                        ssoRefEntryId = password.ssoRefEntryId
                                    )
                                }
                                val trashPasswordsFile = File(trashDir, "trash_passwords.json")
                                trashPasswordsFile.writeText(
                                    json.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(TrashPasswordBackupEntry.serializer()),
                                        trashPasswordBackups
                                    ),
                                    Charsets.UTF_8
                                )
                                addFileToZip(zipOut, trashPasswordsFile, "trash/${trashPasswordsFile.name}")
                            }
                            
                            // 备份已删除的安全项目
                            if (deletedSecureItems.isNotEmpty()) {
                                val trashSecureItemBackups = deletedSecureItems.map { item ->
                                    TrashSecureItemBackupEntry(
                                        id = item.id,
                                        title = item.title,
                                        itemType = item.itemType.name,
                                        itemData = item.itemData,
                                        notes = item.notes,
                                        isFavorite = item.isFavorite,
                                        imagePaths = item.imagePaths,
                                        createdAt = item.createdAt.time,
                                        updatedAt = item.updatedAt.time,
                                        deletedAt = item.deletedAt?.time
                                    )
                                }
                                val trashSecureItemsFile = File(trashDir, "trash_secure_items.json")
                                trashSecureItemsFile.writeText(
                                    json.encodeToString(
                                        kotlinx.serialization.builtins.ListSerializer(TrashSecureItemBackupEntry.serializer()),
                                        trashSecureItemBackups
                                    ),
                                    Charsets.UTF_8
                                )
                                addFileToZip(zipOut, trashSecureItemsFile, "trash/${trashSecureItemsFile.name}")
                            }
                            
                            trashDir.deleteRecursively()
                            val totalTrashCount = deletedPasswords.size + deletedSecureItems.size
                            android.util.Log.d("WebDavHelper", "Backup $totalTrashCount trash items (${deletedPasswords.size} passwords, ${deletedSecureItems.size} secure items)")
                        } catch (e: Exception) {
                            android.util.Log.w("WebDavHelper", "Failed to backup trash: ${e.message}")
                            warnings.add("回收站备份失败: ${e.message}")
                        }
                    }
                    
                    // 7.7 ✅ 备份常用账号信息
                    try {
                        val commonAccountPreferences = takagi.ru.monica.data.CommonAccountPreferences(context)
                        val commonInfo = commonAccountPreferences.commonAccountInfo.first()
                        
                        if (commonInfo.hasAnyInfo() || commonInfo.autoFillEnabled) {
                            val commonAccountBackup = CommonAccountBackupEntry(
                                email = commonInfo.email,
                                phone = commonInfo.phone,
                                username = commonInfo.username,
                                autoFillEnabled = commonInfo.autoFillEnabled
                            )
                            val json = Json { prettyPrint = false }
                            val commonAccountFile = File(cacheBackupDir, "common_account.json")
                            commonAccountFile.writeText(
                                json.encodeToString(CommonAccountBackupEntry.serializer(), commonAccountBackup),
                                Charsets.UTF_8
                            )
                            addFileToZip(zipOut, commonAccountFile, commonAccountFile.name)
                            commonAccountFile.delete()
                            android.util.Log.d("WebDavHelper", "Backup common account info")
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("WebDavHelper", "Failed to backup common account info: ${e.message}")
                        warnings.add("常用账号信息备份失败: ${e.message}")
                    }
                }

                // 8. 加密
                if (enableEncryption && encryptionPassword.isNotEmpty()) {
                    val encryptResult = EncryptionHelper.encryptFile(zipFile, finalFile, encryptionPassword)
                    if (encryptResult.isFailure) throw encryptResult.exceptionOrNull()!!
                }

                // 生成报告
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
                    success = failedItems.isEmpty(),
                    totalItems = totalCounts,
                    successItems = successCounts,
                    failedItems = failedItems,
                    warnings = warnings
                )

                Result.success(Pair(finalFile, report))
            } finally {
                // 清理临时文件 (保留 finalFile 即 ZIP 文件)
                passwordsCsvFile.delete()
                totpCsvFile.delete()
                cardsDocsCsvFile.delete()
                notesDir.deleteRecursively()
                historyJsonFile.delete()
                if (finalFile != zipFile) zipFile.delete()
                passwordsDir.deleteRecursively()
                cacheBackupDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 创建并上传备份
     * 使用锁机制防止并发备份导致的内存溢出
     */
    suspend fun createAndUploadBackup(
        passwords: List<PasswordEntry>,
        secureItems: List<SecureItem>,
        preferences: BackupPreferences = getBackupPreferences(),
        isPermanent: Boolean = false,
        isManualTrigger: Boolean = true  // 默认为手动触发
    ): Result<BackupReport> = withContext(Dispatchers.IO) {
        // 检查是否已有备份正在进行
        if (!backupLock.compareAndSet(false, true)) {
            android.util.Log.w("WebDavHelper", "Backup already in progress, ignoring request")
            return@withContext Result.failure(Exception("备份正在进行中，请稍候再试"))
        }
        
        try {
            android.util.Log.d("WebDavHelper", "Starting backup with ${passwords.size} passwords and ${secureItems.size} secure items")
            
            // 调用重构后的创建方法
            val createResult = createBackupZip(passwords, secureItems, preferences)
            
            if (createResult.isFailure) {
                return@withContext Result.failure(createResult.exceptionOrNull() ?: Exception("创建备份失败"))
            }

            val (backupFile, report) = createResult.getOrThrow()
            
            android.util.Log.d("WebDavHelper", "Backup file created: ${backupFile.length() / 1024}KB")

            try {
                // 上传
                val uploadResult = uploadBackup(backupFile, isPermanent)
                
                if (uploadResult.isSuccess) {
                    updateLastBackupTime()

                    // Trigger cleanup after successful upload
                    cleanupBackups()
                    
                    // 记录 WebDAV 上传操作到时间线
                    val uploadDetails = mutableListOf<FieldChange>()
                    if (passwords.isNotEmpty()) {
                        uploadDetails.add(FieldChange("密码", "", "${passwords.size}项"))
                    }
                    val totpCount = secureItems.count { it.itemType == takagi.ru.monica.data.ItemType.TOTP }
                    if (totpCount > 0) {
                        uploadDetails.add(FieldChange("验证器", "", "${totpCount}项"))
                    }
                    val cardCount = secureItems.count { it.itemType == takagi.ru.monica.data.ItemType.BANK_CARD }
                    if (cardCount > 0) {
                        uploadDetails.add(FieldChange("卡片", "", "${cardCount}项"))
                    }
                    val noteCount = secureItems.count { it.itemType == takagi.ru.monica.data.ItemType.NOTE }
                    if (noteCount > 0) {
                        uploadDetails.add(FieldChange("笔记", "", "${noteCount}项"))
                    }
                    val docCount = secureItems.count { it.itemType == takagi.ru.monica.data.ItemType.DOCUMENT }
                    if (docCount > 0) {
                        uploadDetails.add(FieldChange("证件", "", "${docCount}项"))
                    }
                    OperationLogger.logWebDavUpload(
                        isAutomatic = !isManualTrigger,
                        isPermanent = isPermanent,
                        details = uploadDetails
                    )

                    // 更新报告状态为 true (如果之前没有失败项)
                    val finalReport = report.copy(success = report.success)
                    Result.success(finalReport)
                } else {
                    Result.failure(uploadResult.exceptionOrNull() ?: Exception("上传失败"))
                }
            } finally {
                // 上传完成后删除生成的 ZIP 文件
                backupFile.delete()
            }
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("WebDavHelper", "Out of memory during backup", e)
            System.gc()
            Result.failure(Exception("内存不足，请先压缩图片后再试"))
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Backup failed", e)
            Result.failure(Exception("备份过程失败: ${e.message}"))
        } finally {
            // 释放备份锁
            backupLock.set(false)
            android.util.Log.d("WebDavHelper", "Backup lock released")
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
                writer.write("name,url,username,password,note,email,phone")
                writer.newLine()
                
                // 写入数据行
                passwords.forEach { entry ->
                    val displayName = entry.title.ifBlank { entry.website.ifBlank { entry.username } }
                    val row = listOf(
                        escapeCsvField(displayName),
                        escapeCsvField(entry.website),
                        escapeCsvField(entry.username),
                        escapeCsvField(entry.password),
                        escapeCsvField(buildPasswordNoteWithMetadata(entry)),
                        escapeCsvField(entry.email),
                        escapeCsvField(entry.phone)
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
     * 从备份文件恢复数据 (通用方法，用于 WebDAV 下载后恢复和本地导入)
     * @param backupFile 本地备份文件（ZIP）
     * @param decryptPassword 解密密码 (如果文件已加密)
     * @return Result<RestoreResult> 包含恢复的数据和报告
     */
    suspend fun restoreFromBackupFile(
        backupFile: File,
        decryptPassword: String? = null
    ): Result<RestoreResult> = withContext(Dispatchers.IO) {
        try {
            // P0修复：错误跟踪
            val failedItems = mutableListOf<FailedItem>()
            val warnings = mutableListOf<String>()
            var backupPasswordCount = 0
            var backupNoteCount = 0
            var backupTotpCount = 0 // 目前没有统计CSV中的TOTP，可以在CSV导入时统计，这里先保留变量
            var backupCardCount = 0
            var backupDocCount = 0
            var backupImageCount = 0
            var restoredPasswordCount = 0
            var restoredNoteCount = 0
            var restoredImageCount = 0

            // 1. 检测是否加密
            val isEncrypted = EncryptionHelper.isEncryptedFile(backupFile)
            
            // 2. 解密文件 (如果需要)
            val zipFile = if (isEncrypted) {
                val password = decryptPassword ?: encryptionPassword
                if (password.isEmpty()) {
                    return@withContext Result.failure(PasswordRequiredException())
                }
                
                val decryptedFile = File(context.cacheDir, "restore_decrypted_${System.nanoTime()}.zip")
                val decryptResult = EncryptionHelper.decryptFile(backupFile, decryptedFile, password)
                
                if (decryptResult.isFailure) {
                    return@withContext Result.failure(decryptResult.exceptionOrNull() 
                        ?: Exception("解密失败"))
                }
                
                android.util.Log.d("WebDavHelper", "Backup decrypted successfully")
                decryptedFile
            } else {
                backupFile
            }
            
            try {
                val passwordsWithMetadata = mutableListOf<Pair<PasswordEntry, String?>>()  // ✅ 存储密码和分类名称
                val passwords = mutableListOf<PasswordEntry>()
                val secureItems = mutableListOf<DataExportImportManager.ExportItem>()
                
                // 临时存储CSV文件路径，延后处理
                var passwordsCsvFile: File? = null
                
                // 3. 解压ZIP文件并读取JSON/CSV、密码历史和图片
                ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        // 防止Zip Slip漏洞
                        val entryName = entry.name.substringAfterLast('/')
                        if (entryName.contains("..")) {
                             android.util.Log.w("WebDavHelper", "Skipping unsafe zip entry: ${entry.name}")
                             entry = zipIn.nextEntry
                             continue
                        }

                        val tempFile = File(context.cacheDir, "restore_${System.nanoTime()}_${entryName}")
                        FileOutputStream(tempFile).use { fileOut ->
                            zipIn.copyTo(fileOut)
                        }
                        zipIn.closeEntry()
                        
                        try {
                            // Normalize path separators for Windows compatibility
                            val normalizedEntryName = entry.name.replace('\\', '/')
                            
                            when {
                                // 优先收集JSON格式的密码文件
                                normalizedEntryName.contains("/passwords/") || normalizedEntryName.startsWith("passwords/") -> {
                                    backupPasswordCount++
                                    val result = restorePasswordFromJson(tempFile)
                                    if (result != null) {
                                        passwordsWithMetadata.add(result)  // ✅ 存储密码、分类名称和TOTP密钥
                                        restoredPasswordCount++
                                    } else {
                                        failedItems.add(FailedItem(
                                            id = 0,
                                            type = "密码",
                                            title = entryName,
                                            reason = "JSON解析失败"
                                        ))
                                    }
                                }
                                // 保存CSV文件路径，稍后处理（向后兼容）
                                entryName.equals("passwords.csv", ignoreCase = true) ||
                                    (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_password.csv", ignoreCase = true)) -> {
                                    // 只在没有JSON密码时才使用CSV
                                    if (passwordsCsvFile == null) {
                                        passwordsCsvFile = tempFile
                                        // 标记不需要删除，因为要留到后面处理
                                        // 但要注意在这个when块结束后 tempFile会被删除，所以这里需要复制一份或者不删除
                                        // 这里的逻辑稍微调整下：如果不立即处理，就不删除 tempFile
                                    } else {
                                        // 已经有一个了，这个忽略
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
                                    } else {
                                        warnings.add("导入CSV失败 $entryName: ${importResult.exceptionOrNull()?.message}")
                                    }
                                }
                                normalizedEntryName.contains("/notes/") || normalizedEntryName.startsWith("notes/") -> {
                                    backupNoteCount++
                                    val noteItem = restoreNoteFromJson(tempFile)
                                    if (noteItem != null) {
                                        secureItems.add(noteItem)
                                        restoredNoteCount++
                                    } else {
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
                                        warnings.add("密码生成历史恢复失败: ${e.message}")
                                    }
                                }
                                // ✅ 恢复分类数据
                                entryName.equals("categories.json", ignoreCase = true) -> {
                                    try {
                                        val categoriesJson = tempFile.readText(Charsets.UTF_8)
                                        val json = Json { ignoreUnknownKeys = true }
                                        val categoryBackups = json.decodeFromString<List<CategoryBackupEntry>>(categoriesJson)
                                        
                                        val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                                        val categoryDao = database.categoryDao()
                                        
                                        // 导入分类（保持原ID以保持与密码的关联）
                                        categoryBackups.forEach { backup ->
                                            try {
                                                // 检查分类是否已存在
                                                val existingCategories = categoryDao.getAllCategories().first()
                                                val exists = existingCategories.any { it.id == backup.id || it.name == backup.name }
                                                if (!exists) {
                                                    categoryDao.insert(takagi.ru.monica.data.Category(
                                                        id = backup.id,
                                                        name = backup.name,
                                                        sortOrder = backup.sortOrder
                                                    ))
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.w("WebDavHelper", "Failed to import category ${backup.name}: ${e.message}")
                                            }
                                        }
                                        android.util.Log.d("WebDavHelper", "Restored ${categoryBackups.size} categories")
                                    } catch (e: Exception) {
                                        android.util.Log.w("WebDavHelper", "Failed to restore categories: ${e.message}")
                                        warnings.add("分类恢复失败: ${e.message}")
                                    }
                                }
                                // ✅ 恢复操作历史记录 (时间线)
                                entryName.equals("timeline_history.json", ignoreCase = true) -> {
                                    try {
                                        val timelineJson = tempFile.readText(Charsets.UTF_8)
                                        val json = Json { ignoreUnknownKeys = true }
                                        val logBackups = json.decodeFromString<List<OperationLogBackupEntry>>(timelineJson)
                                        
                                        if (logBackups.isNotEmpty()) {
                                            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                                            val operationLogDao = database.operationLogDao()
                                            
                                            // 获取现有日志的时间戳用于去重
                                            val existingLogs = operationLogDao.getAllLogsSync()
                                            val existingTimestamps = existingLogs.map { it.timestamp }.toSet()
                                            
                                            // 只导入不存在的日志
                                            val newLogs = logBackups.filter { backup ->
                                                backup.timestamp !in existingTimestamps
                                            }.map { backup ->
                                                takagi.ru.monica.data.OperationLog(
                                                    id = 0, // 使用新ID，避免冲突
                                                    itemType = backup.itemType,
                                                    itemId = backup.itemId,
                                                    itemTitle = backup.itemTitle,
                                                    operationType = backup.operationType,
                                                    changesJson = backup.changesJson,
                                                    deviceId = backup.deviceId,
                                                    deviceName = backup.deviceName,
                                                    timestamp = backup.timestamp,
                                                    isReverted = backup.isReverted
                                                )
                                            }
                                            
                                            if (newLogs.isNotEmpty()) {
                                                operationLogDao.insertAll(newLogs)
                                                android.util.Log.d("WebDavHelper", "Restored ${newLogs.size} new timeline entries (${logBackups.size - newLogs.size} duplicates skipped)")
                                            } else {
                                                android.util.Log.d("WebDavHelper", "All ${logBackups.size} timeline entries already exist, skipped")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("WebDavHelper", "Failed to restore timeline: ${e.message}")
                                        warnings.add("操作历史恢复失败: ${e.message}")
                                    }
                                }
                                // ✅ 恢复回收站数据 - 密码
                                normalizedEntryName.contains("/trash/") && entryName.equals("trash_passwords.json", ignoreCase = true) -> {
                                    try {
                                        val trashJson = tempFile.readText(Charsets.UTF_8)
                                        val json = Json { ignoreUnknownKeys = true }
                                        val trashPasswordBackups = json.decodeFromString<List<TrashPasswordBackupEntry>>(trashJson)
                                        
                                        if (trashPasswordBackups.isNotEmpty()) {
                                            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                                            val passwordEntryDao = database.passwordEntryDao()
                                            
                                            // 获取现有的已删除密码用于去重
                                            val existingDeletedPasswords = passwordEntryDao.getDeletedEntriesSync()
                                            val existingTitles = existingDeletedPasswords.map { "${it.title}_${it.createdAt.time}" }.toSet()
                                            
                                            var importedCount = 0
                                            trashPasswordBackups.forEach { backup ->
                                                val key = "${backup.title}_${backup.createdAt}"
                                                if (key !in existingTitles) {
                                                    try {
                                                        val entry = PasswordEntry(
                                                            id = 0, // 使用新ID
                                                            title = backup.title,
                                                            username = backup.username,
                                                            password = backup.password,
                                                            website = backup.website,
                                                            notes = backup.notes,
                                                            isFavorite = backup.isFavorite,
                                                            categoryId = backup.categoryId,
                                                            email = backup.email,
                                                            phone = backup.phone,
                                                            createdAt = java.util.Date(backup.createdAt),
                                                            updatedAt = java.util.Date(backup.updatedAt),
                                                            authenticatorKey = backup.authenticatorKey,
                                                            isDeleted = true,
                                                            deletedAt = backup.deletedAt?.let { java.util.Date(it) },
                                                            // ✅ 第三方登录(SSO)字段
                                                            loginType = backup.loginType,
                                                            ssoProvider = backup.ssoProvider,
                                                            ssoRefEntryId = backup.ssoRefEntryId
                                                        )
                                                        passwordEntryDao.insertPasswordEntry(entry)
                                                        importedCount++
                                                    } catch (e: Exception) {
                                                        android.util.Log.w("WebDavHelper", "Failed to restore trash password ${backup.title}: ${e.message}")
                                                    }
                                                }
                                            }
                                            android.util.Log.d("WebDavHelper", "Restored $importedCount trash passwords (${trashPasswordBackups.size - importedCount} duplicates skipped)")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("WebDavHelper", "Failed to restore trash passwords: ${e.message}")
                                        warnings.add("回收站密码恢复失败: ${e.message}")
                                    }
                                }
                                // ✅ 恢复回收站数据 - 安全项目
                                normalizedEntryName.contains("/trash/") && entryName.equals("trash_secure_items.json", ignoreCase = true) -> {
                                    try {
                                        val trashJson = tempFile.readText(Charsets.UTF_8)
                                        val json = Json { ignoreUnknownKeys = true }
                                        val trashSecureItemBackups = json.decodeFromString<List<TrashSecureItemBackupEntry>>(trashJson)
                                        
                                        if (trashSecureItemBackups.isNotEmpty()) {
                                            val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                                            val secureItemDao = database.secureItemDao()
                                            
                                            // 获取现有的已删除安全项用于去重
                                            val existingDeletedItems = secureItemDao.getDeletedItemsSync()
                                            val existingKeys = existingDeletedItems.map { "${it.title}_${it.createdAt.time}" }.toSet()
                                            
                                            var importedCount = 0
                                            trashSecureItemBackups.forEach { backup ->
                                                val key = "${backup.title}_${backup.createdAt}"
                                                if (key !in existingKeys) {
                                                    try {
                                                        val itemType = try {
                                                            ItemType.valueOf(backup.itemType)
                                                        } catch (e: Exception) {
                                                            ItemType.NOTE // 默认类型
                                                        }
                                                        val item = SecureItem(
                                                            id = 0, // 使用新ID
                                                            title = backup.title,
                                                            itemType = itemType,
                                                            itemData = backup.itemData,
                                                            notes = backup.notes,
                                                            isFavorite = backup.isFavorite,
                                                            imagePaths = backup.imagePaths,
                                                            createdAt = java.util.Date(backup.createdAt),
                                                            updatedAt = java.util.Date(backup.updatedAt),
                                                            isDeleted = true,
                                                            deletedAt = backup.deletedAt?.let { java.util.Date(it) }
                                                        )
                                                        secureItemDao.insertItem(item)
                                                        importedCount++
                                                    } catch (e: Exception) {
                                                        android.util.Log.w("WebDavHelper", "Failed to restore trash item ${backup.title}: ${e.message}")
                                                    }
                                                }
                                            }
                                            android.util.Log.d("WebDavHelper", "Restored $importedCount trash secure items (${trashSecureItemBackups.size - importedCount} duplicates skipped)")
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.w("WebDavHelper", "Failed to restore trash secure items: ${e.message}")
                                        warnings.add("回收站项目恢复失败: ${e.message}")
                                    }
                                }
                                normalizedEntryName.contains("/images/") || entryName.endsWith(".enc") -> {
                                    backupImageCount++
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
                                // ✅ 恢复常用账号信息（仅当本地为空时恢复，避免覆盖用户更新的数据）
                                entryName.equals("common_account.json", ignoreCase = true) -> {
                                    try {
                                        val commonAccountJson = tempFile.readText(Charsets.UTF_8)
                                        val json = Json { ignoreUnknownKeys = true }
                                        val commonAccountBackup = json.decodeFromString<CommonAccountBackupEntry>(commonAccountJson)
                                        
                                        val commonAccountPreferences = takagi.ru.monica.data.CommonAccountPreferences(context)
                                        val currentInfo = commonAccountPreferences.commonAccountInfo.first()
                                        
                                        // 只有当本地对应字段为空时才恢复，保护用户本地更新的数据
                                        if (currentInfo.email.isEmpty() && commonAccountBackup.email.isNotEmpty()) {
                                            commonAccountPreferences.setDefaultEmail(commonAccountBackup.email)
                                        }
                                        if (currentInfo.phone.isEmpty() && commonAccountBackup.phone.isNotEmpty()) {
                                            commonAccountPreferences.setDefaultPhone(commonAccountBackup.phone)
                                        }
                                        if (currentInfo.username.isEmpty() && commonAccountBackup.username.isNotEmpty()) {
                                            commonAccountPreferences.setDefaultUsername(commonAccountBackup.username)
                                        }
                                        // autoFillEnabled 只有在当前未启用且备份启用时才恢复
                                        if (!currentInfo.autoFillEnabled && commonAccountBackup.autoFillEnabled) {
                                            commonAccountPreferences.setAutoFillEnabled(true)
                                        }
                                        
                                        android.util.Log.d("WebDavHelper", "Restored common account info (merge mode)")
                                    } catch (e: Exception) {
                                        android.util.Log.w("WebDavHelper", "Failed to restore common account info: ${e.message}")
                                        warnings.add("常用账号信息恢复失败: ${e.message}")
                                    }
                                }
                            }
                        } finally {
                            // 只有当 tempFile 不是 passwordsCsvFile 时才删除
                            if (tempFile != passwordsCsvFile) {
                                tempFile.delete()
                            }
                        }
                        
                        entry = zipIn.nextEntry
                    }
                }
                
                // ✅ 解析分类并创建缺失的分类，同时处理TOTP关联
                if (passwordsWithMetadata.isNotEmpty()) {
                    val database = takagi.ru.monica.data.PasswordDatabase.getDatabase(context)
                    val categoryDao = database.categoryDao()
                    
                    // 获取当前所有分类
                    val existingCategories = try { categoryDao.getAllCategories().first() } catch (e: Exception) { emptyList() }
                    val categoryByName = existingCategories.associateBy { it.name }.toMutableMap()
                    
                    // 收集需要创建的分类名称
                    val categoryNamesToCreate = passwordsWithMetadata
                        .mapNotNull { it.second }
                        .distinct()
                        .filter { it.isNotBlank() && !categoryByName.containsKey(it) }
                    
                    // 创建缺失的分类
                    categoryNamesToCreate.forEach { categoryName ->
                        try {
                            val maxSortOrder = (categoryByName.values.maxOfOrNull { it.sortOrder } ?: 0) + 1
                            val newCategory = takagi.ru.monica.data.Category(
                                name = categoryName,
                                sortOrder = maxSortOrder
                            )
                            val newId = categoryDao.insert(newCategory)
                            val createdCategory = newCategory.copy(id = newId)
                            categoryByName[categoryName] = createdCategory
                            android.util.Log.d("WebDavHelper", "Created category: $categoryName (id=$newId)")
                        } catch (e: Exception) {
                            android.util.Log.w("WebDavHelper", "Failed to create category $categoryName: ${e.message}")
                        }
                    }
                    
                    // ✅ 存储密码和分类名称（authenticatorKey已经在PasswordEntry中）
                    
                    // 将密码与分类关联
                    passwordsWithMetadata.forEach { (entry, categoryName) ->
                        val categoryId = categoryName?.let { categoryByName[it]?.id }
                        val passwordEntry = entry.copy(categoryId = categoryId)
                        passwords.add(passwordEntry)
                    }
                    
                    android.util.Log.d("WebDavHelper", "Resolved ${passwords.size} passwords with categories")
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
                    content = BackupContent(
                        passwords = passwords,
                        secureItems = secureItems
                    ),
                    report = report
                ))
            } finally {
                if (zipFile != backupFile) {
                    zipFile.delete()
                }
            }
        } catch (e: Exception) {
            Result.failure(Exception("恢复备份失败: ${e.message}"))
        }
    }

    /**
     * 下载并恢复备份 - 返回密码、其他数据和恢复报告
     * @param backupFile 要恢复的备份文件信息
     * @param decryptPassword 解密密码 (如果文件已加密)
     */
    suspend fun downloadAndRestoreBackup(
        backupFile: BackupFile,
        decryptPassword: String? = null
    ): Result<RestoreResult> = 
        withContext(Dispatchers.IO) {
        try {
            // 1. 下载备份文件
            val downloadedFile = File(context.cacheDir, "restore_${backupFile.name}")
            val downloadResult = downloadBackup(backupFile, downloadedFile)
            
            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() 
                    ?: Exception("下载备份失败"))
            }
            
            try {
                // 2. 调用的恢复方法
                val restoreResult = restoreFromBackupFile(downloadedFile, decryptPassword)
                if (restoreResult.isFailure) {
                    // 如果是密码错误，传递具体的异常
                    val ex = restoreResult.exceptionOrNull()
                    if (ex is PasswordRequiredException) {
                        return@withContext Result.failure(ex)
                    }
                    return@withContext Result.failure(ex ?: Exception("恢复失败"))
                }
                
                // 记录 WebDAV 下载/同步操作到时间线
                // 注意：此时数据还未真正写入数据库，result 包含的是备份文件中解析出的条目
                // 实际新增/修改的统计需要在合并逻辑中完成
                val result = restoreResult.getOrThrow()
                val downloadDetails = mutableListOf<FieldChange>()
                val newItemNames = mutableListOf<FieldChange>()
                
                // 密码统计
                if (result.content.passwords.isNotEmpty()) {
                    val passwordCount = result.content.passwords.size
                    downloadDetails.add(FieldChange("密码", "", "${passwordCount}项"))
                    // 收集密码名称用于git-branch风格展示（最多10个）
                    result.content.passwords.take(10).forEach { pwd ->
                        newItemNames.add(FieldChange("密码", "", pwd.title.ifBlank { pwd.username }))
                    }
                    if (passwordCount > 10) {
                        newItemNames.add(FieldChange("密码", "", "...还有${passwordCount - 10}项"))
                    }
                }
                
                // 安全项统计
                val secureItems = result.content.secureItems
                val totpItems = secureItems.filter { it.itemType == "TOTP" }
                if (totpItems.isNotEmpty()) {
                    downloadDetails.add(FieldChange("验证器", "", "${totpItems.size}项"))
                    totpItems.take(10).forEach { item ->
                        newItemNames.add(FieldChange("验证器", "", item.title))
                    }
                    if (totpItems.size > 10) {
                        newItemNames.add(FieldChange("验证器", "", "...还有${totpItems.size - 10}项"))
                    }
                }
                
                val cardItems = secureItems.filter { it.itemType == "BANK_CARD" }
                if (cardItems.isNotEmpty()) {
                    downloadDetails.add(FieldChange("卡片", "", "${cardItems.size}项"))
                    cardItems.take(10).forEach { item ->
                        newItemNames.add(FieldChange("卡片", "", item.title))
                    }
                    if (cardItems.size > 10) {
                        newItemNames.add(FieldChange("卡片", "", "...还有${cardItems.size - 10}项"))
                    }
                }
                
                val noteItems = secureItems.filter { it.itemType == "NOTE" }
                if (noteItems.isNotEmpty()) {
                    downloadDetails.add(FieldChange("笔记", "", "${noteItems.size}项"))
                    noteItems.take(10).forEach { item ->
                        newItemNames.add(FieldChange("笔记", "", item.title))
                    }
                    if (noteItems.size > 10) {
                        newItemNames.add(FieldChange("笔记", "", "...还有${noteItems.size - 10}项"))
                    }
                }
                
                val docItems = secureItems.filter { it.itemType == "DOCUMENT" }
                if (docItems.isNotEmpty()) {
                    downloadDetails.add(FieldChange("证件", "", "${docItems.size}项"))
                    docItems.take(10).forEach { item ->
                        newItemNames.add(FieldChange("证件", "", item.title))
                    }
                    if (docItems.size > 10) {
                        newItemNames.add(FieldChange("证件", "", "...还有${docItems.size - 10}项"))
                    }
                }
                
                if (downloadDetails.isNotEmpty()) {
                    // 合并统计和详细条目列表
                    val allDetails = downloadDetails + newItemNames
                    OperationLogger.logWebDavDownload(addedItems = allDetails)
                }
                
                Result.success(result)
            } finally {
                // 清理下载的文件
                downloadedFile.delete()
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
                    PasswordCsvFormat.APP_EXPORT -> fields.map { it.lowercase(Locale.getDefault()) }.let {
                        it.contains("type") && it.contains("data")
                    }
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

    /**
     * 从JSON文件恢复密码
     * @return Pair of (PasswordEntry, categoryName) - categoryName用于创建/查找分类
     */
    /**
     * 从JSON文件恢复密码条目
     * @return Pair<PasswordEntry, categoryName>
     */
    private fun restorePasswordFromJson(file: File): Pair<PasswordEntry, String?>? {
        return try {
            val content = file.readText(Charsets.UTF_8)
            val json = Json { ignoreUnknownKeys = true }
            val backup = json.decodeFromString<PasswordBackupEntry>(content)
            val entry = PasswordEntry(
                id = backup.id, // 暂存原始ID，用于后续TOTP关联映射
                title = backup.title,
                username = backup.username,
                password = backup.password,
                website = backup.website,
                notes = backup.notes,
                isFavorite = backup.isFavorite,
                categoryId = null, // ✅ 先设为null，稍后根据categoryName解析
                email = backup.email,
                phone = backup.phone,
                createdAt = Date(backup.createdAt),
                updatedAt = Date(backup.updatedAt),
                authenticatorKey = backup.authenticatorKey,  // ✅ 直接恢复验证器密钥
                // ✅ 第三方登录(SSO)字段
                loginType = backup.loginType,
                ssoProvider = backup.ssoProvider,
                ssoRefEntryId = backup.ssoRefEntryId
            )
            Pair(entry, backup.categoryName)
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
            lowered.contains("type") && lowered.contains("data") && 
            lowered.contains("id") -> PasswordCsvFormat.APP_EXPORT
            lowered.contains("name") && lowered.contains("url") &&
                lowered.contains("username") && lowered.contains("password") -> PasswordCsvFormat.CHROME
            lowered.contains("title") && lowered.contains("password") -> PasswordCsvFormat.LEGACY
            else -> PasswordCsvFormat.UNKNOWN
        }
    }

    private fun parsePasswordEntry(line: String, format: PasswordCsvFormat): PasswordEntry? {
        val fields = splitCsvLine(line)
        return when (format) {
            PasswordCsvFormat.APP_EXPORT -> parseAppPasswordFields(fields)
            PasswordCsvFormat.CHROME -> parseChromePasswordFields(fields)
            PasswordCsvFormat.LEGACY -> parseLegacyPasswordFields(fields)
            PasswordCsvFormat.UNKNOWN -> parseAppPasswordFields(fields) ?: parseLegacyPasswordFields(fields) ?: parseChromePasswordFields(fields)
        }
    }

    private fun parseAppPasswordFields(fields: List<String>): PasswordEntry? {
        return try {
            // ID, Type, Title, Data, Notes, IsFavorite, ImagePaths, CreatedAt, UpdatedAt
            if (fields.size >= 9 && fields.getOrNull(1) == "PASSWORD") {
                val id = fields.getOrNull(0)?.toLongOrNull() ?: 0L
                val title = fields.getOrNull(2) ?: ""
                val dataStr = fields.getOrNull(3) ?: ""
                val notes = fields.getOrNull(4) ?: ""
                val isFavorite = fields.getOrNull(5)?.toBoolean() ?: false
                val createdAt = fields.getOrNull(7)?.toLongOrNull()?.let { Date(it) } ?: Date()
                val updatedAt = fields.getOrNull(8)?.toLongOrNull()?.let { Date(it) } ?: Date()

                // Parse Data string (username:x;password:y;...)
                val dataMap = parsePasswordDataString(dataStr)
                
                PasswordEntry(
                    id = id, // Preserve ID!
                    title = title,
                    username = dataMap["username"] ?: "",
                    password = dataMap["password"] ?: "",
                    website = dataMap["website"] ?: "",
                    email = dataMap["email"] ?: "",
                    phone = dataMap["phone"] ?: "",
                    notes = notes,
                    isFavorite = isFavorite,
                    createdAt = createdAt,
                    updatedAt = updatedAt
                )
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Failed to parse APP_EXPORT password CSV line: ${e.message}")
            null
        }
    }

    private fun parsePasswordDataString(data: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        data.split(";").forEach { pair ->
            val parts = pair.split(":", limit = 2)
            if (parts.size == 2) {
                result[parts[0].trim()] = parts[1].trim()
            }
        }
        return result
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
                val email = fields.getOrNull(5)?.trim().orEmpty()
                val phone = fields.getOrNull(6)?.trim().orEmpty()

                PasswordEntry(
                    id = 0,
                    title = if (title.isNotBlank()) title else website.ifBlank { username },
                    website = website,
                    username = username,
                    password = password,
                    notes = note,
                    email = email,
                    phone = phone,
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
        APP_EXPORT,
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
     * 使用流式上传避免内存溢出
     */
    suspend fun uploadBackup(file: File, isPermanent: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            // 检查文件大小，如果文件过大（>50MB），给出警告日志
            val fileSizeMB = file.length() / (1024 * 1024)
            if (fileSizeMB > 50) {
                android.util.Log.w("WebDavHelper", "Large backup file detected: ${fileSizeMB}MB. Consider compressing images first.")
            }
            
            // 创建 Monica 备份目录
            val backupDir = "$serverUrl/Monica_Backups"
            if (!sardine!!.exists(backupDir)) {
                sardine!!.createDirectory(backupDir)
            }
            
            // 生成带时间戳的文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val suffix = if (isPermanent) PERMANENT_SUFFIX else ""
            val fileName = "monica_backup_$timestamp$suffix.zip"
            val remotePath = "$backupDir/$fileName"
            
            // 使用流式上传避免内存溢出
            // Sardine不直接支持InputStream，使用文件直接上传
            val fileSize = file.length()
            if (fileSize > 100 * 1024 * 1024) { // 大于100MB
                // 对于超大文件，分块读取
                android.util.Log.w("WebDavHelper", "Very large file (${fileSize / 1024 / 1024}MB), may take a while...")
            }
            
            // 使用readBytes但添加内存检查
            try {
                val fileBytes = file.readBytes()
                sardine!!.put(remotePath, fileBytes, "application/zip")
            } catch (e: OutOfMemoryError) {
                android.util.Log.e("WebDavHelper", "Out of memory reading file, trying alternative method", e)
                System.gc()
                throw e
            }
            
            android.util.Log.d("WebDavHelper", "Backup uploaded successfully: $fileName (${fileSizeMB}MB)")
            
            Result.success(fileName)
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("WebDavHelper", "Out of memory while uploading backup", e)
            // 显式请求垃圾回收
            System.gc()
            Result.failure(Exception("备份文件过大，内存不足。请先压缩图片后再试。"))
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Failed to upload backup", e)
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
     * Delete backups older than 60 days (only temporary ones)
     */
    suspend fun cleanupBackups(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }

            val result = listBackups()
            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val backups = result.getOrNull() ?: emptyList()
            var deletedCount = 0

            val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)

            backups.forEach { backup ->
                if (!backup.isPermanent && backup.modified.time < sixtyDaysAgo) {
                    android.util.Log.d("WebDavHelper", "Deleting expired backup: ${backup.name}")
                    try {
                        deleteBackup(backup)
                        deletedCount++
                    } catch (e: Exception) {
                        android.util.Log.w("WebDavHelper", "Failed to delete expired backup ${backup.name}: ${e.message}")
                    }
                }
            }

            Result.success(deletedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a backup as permanent by renaming it
     */
    suspend fun markBackupAsPermanent(backup: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }

            if (backup.isPermanent) {
                return@withContext Result.success(true)
            }

            val oldPath = "$serverUrl/Monica_Backups/${backup.name}"
            // Insert _permanent before .zip
            val newName = backup.name.replace(".zip", "${PERMANENT_SUFFIX}.zip")
            val newPath = "$serverUrl/Monica_Backups/$newName"

            sardine!!.move(oldPath, newPath)
            android.util.Log.d("WebDavHelper", "Marked backup as permanent: ${backup.name} -> $newName")
            
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unmark a permanent backup (revert to temporary)
     */
    suspend fun unmarkPermanent(backup: BackupFile): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }

            if (!backup.isPermanent) {
                return@withContext Result.success(true)
            }

            val oldPath = "$serverUrl/Monica_Backups/${backup.name}"
            // Remove _permanent suffix
            val newName = backup.name.replace(PERMANENT_SUFFIX, "")
            val newPath = "$serverUrl/Monica_Backups/$newName"

            sardine!!.move(oldPath, newPath)
            android.util.Log.d("WebDavHelper", "Unmarked permanent backup: ${backup.name} -> $newName")

            Result.success(true)
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
                android.util.Log.e("WebDavHelper", "Delete failed: WebDAV not configured")
                return@withContext Result.failure(Exception("WebDAV not configured"))
            }
            
            val remotePath = "$serverUrl/Monica_Backups/${backupFile.name}"
            android.util.Log.d("WebDavHelper", "Deleting backup: $remotePath")
            
            sardine!!.delete(remotePath)
            
            android.util.Log.d("WebDavHelper", "Backup deleted successfully: ${backupFile.name}")
            Result.success(true)
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Delete failed: ${e.message}", e)
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
 * Backup file info
 */
data class BackupFile(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Date
) {
    val isPermanent: Boolean
        get() = name.contains("_permanent")
    
    val isExpiring: Boolean
        get() {
            if (isPermanent) return false
            // Expiring if older than 50 days (10 days left until 60 days limit)
            val fiftyDaysAgo = System.currentTimeMillis() - (50L * 24 * 60 * 60 * 1000)
            return modified.time < fiftyDaysAgo
        }

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


