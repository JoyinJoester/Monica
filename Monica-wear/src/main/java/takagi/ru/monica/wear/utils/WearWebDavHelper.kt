package takagi.ru.monica.wear.utils

import android.content.Context
import android.util.Log
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.text.Charsets
import takagi.ru.monica.wear.data.ItemType
import takagi.ru.monica.wear.data.PasswordDatabase
import takagi.ru.monica.wear.data.SecureItem
import takagi.ru.monica.wear.repository.TotpRepository
import takagi.ru.monica.wear.repository.TotpRepositoryImpl
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import java.util.zip.ZipInputStream

/**
 * Wear版WebDAV助手
 * 仅实现下载和解密功能，不上传
 */
class WearWebDavHelper(private val context: Context) {
    
    private var sardine: Sardine? = null
    private var serverUrl: String = ""
    private var username: String = ""
    private var password: String = ""
    private var encryptionPassword: String = ""
    private var enableEncryption: Boolean = false
    private val totpRepository: TotpRepository by lazy {
        val database = PasswordDatabase.getDatabase(context)
        TotpRepositoryImpl(database.secureItemDao())
    }
    
    companion object {
        private const val TAG = "WearWebDavHelper"
        private const val PREFS_NAME = "wear_webdav_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_ENABLE_ENCRYPTION = "enable_encryption"
        private const val KEY_ENCRYPTION_PASSWORD = "encryption_password"
        private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
        private const val KEY_LAST_SYNC_TIME = "last_sync_time"
    }
    
    init {
        loadConfig()
    }
    
    /**
     * 配置WebDAV连接
     */
    fun configure(url: String, user: String, pass: String) {
        serverUrl = url.trimEnd('/')
        username = user
        password = pass
        sardine = OkHttpSardine()
        sardine?.setCredentials(username, password)
        Log.d(TAG, "Configured WebDAV: url=$serverUrl, user=$username")
        saveConfig()
    }
    
    /**
     * 配置加密设置
     */
    fun configureEncryption(enable: Boolean, encPassword: String = "") {
        enableEncryption = enable
        encryptionPassword = if (enable) encPassword else ""
        saveConfig()
        Log.d(TAG, "Encryption configured: enabled=$enable")
    }
    
    /**
     * 保存配置
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
     * 加载配置
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
            sardine = OkHttpSardine()
            sardine?.setCredentials(username, password)
            Log.d(TAG, "Loaded WebDAV config: url=$serverUrl, user=$username")
        }
    }
    
    /**
     * 检查是否已配置
     */
    fun isConfigured(): Boolean {
        return serverUrl.isNotEmpty() && username.isNotEmpty() && password.isNotEmpty()
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
     * 配置自动同步
     */
    fun configureAutoSync(enable: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_AUTO_SYNC_ENABLED, enable).apply()
        Log.d(TAG, "Auto sync configured: enabled=$enable")
    }
    
    /**
     * 获取自动同步状态
     */
    fun isAutoSyncEnabled(): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }
    
    /**
     * 检查是否需要同步
     * 规则：
     * 1. 每天首次打开必定同步
     * 2. 距离上次同步超过12小时也同步
     */
    fun shouldAutoSync(): Boolean {
        if (!isAutoSyncEnabled()) {
            return false
        }
        
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastSyncTime = prefs.getLong(KEY_LAST_SYNC_TIME, 0)
        
        if (lastSyncTime == 0L) {
            Log.d(TAG, "Never synced before, need sync")
            return true
        }
        
        val currentTime = System.currentTimeMillis()
        val calendar = java.util.Calendar.getInstance()
        
        // 获取上次同步的日期
        calendar.timeInMillis = lastSyncTime
        val lastSyncDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val lastSyncYear = calendar.get(java.util.Calendar.YEAR)
        
        // 获取当前日期
        calendar.timeInMillis = currentTime
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        
        // 计算小时数
        val hoursSinceLastSync = (currentTime - lastSyncTime) / (1000 * 60 * 60)
        
        // 判断是否为新的一天
        val isNewDay = (currentYear > lastSyncYear) || 
                      (currentYear == lastSyncYear && currentDay > lastSyncDay)
        
        Log.d(TAG, "Last sync: year=$lastSyncYear, day=$lastSyncDay, " +
            "Current: year=$currentYear, day=$currentDay, " +
            "Hours since: $hoursSinceLastSync, Is new day: $isNewDay")
        
        if (isNewDay) {
            Log.d(TAG, "New day detected, need sync")
            return true
        }
        
        if (hoursSinceLastSync >= 12) {
            Log.d(TAG, "More than 12 hours since last sync, need sync")
            return true
        }
        
        Log.d(TAG, "No sync needed")
        return false
    }
    
    /**
     * 更新最后同步时间
     */
    fun updateLastSyncTime() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentTime = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_SYNC_TIME, currentTime).apply()
        Log.d(TAG, "Updated last sync time: $currentTime")
    }
    
    /**
     * 获取最后同步时间
     */
    fun getLastSyncTime(): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_LAST_SYNC_TIME, 0)
    }
    
    /**
     * 测试连接
     */
    suspend fun testConnection(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV未配置"))
            }
            
            Log.d(TAG, "Testing connection to: $serverUrl")
            
            var connectionOk = false
            var lastError: Exception? = null
            
            // 方法1: 使用exists()
            try {
                val exists = sardine?.exists(serverUrl) ?: false
                Log.d(TAG, "Connection test (exists): path exists = $exists")
                connectionOk = true
            } catch (e1: Exception) {
                Log.w(TAG, "exists() failed: ${e1.message}")
                lastError = e1
                
                // 方法2: 尝试list()
                try {
                    val resources = sardine?.list(serverUrl)
                    Log.d(TAG, "Connection test (list): found ${resources?.size ?: 0} resources")
                    connectionOk = true
                    lastError = null
                } catch (e2: Exception) {
                    Log.w(TAG, "list() failed: ${e2.message}")
                    lastError = e2
                }
            }
            
            if (connectionOk) {
                Log.d(TAG, "Connection test SUCCESSFUL")
                return@withContext Result.success(true)
            } else {
                throw lastError ?: Exception("所有连接方法都失败")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Connection test FAILED", e)
            val detailedMessage = when {
                e.message?.contains("401") == true -> "认证失败，请检查用户名和密码"
                e.message?.contains("404") == true -> "路径未找到，请检查服务器地址"
                e.message?.contains("403") == true -> "访问被拒绝，请检查权限"
                e.message?.contains("timeout") == true -> "连接超时，请检查网络"
                else -> "连接失败: ${e.message}"
            }
            Result.failure(Exception(detailedMessage, e))
        }
    }
    
    /**
     * 下载并导入最新的备份
     * 只下载，不上传
     * @return Result包含Pair<导入数量, 跳过数量>和消息
     */
    suspend fun downloadAndImportLatestBackup(): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                Log.e(TAG, "Sardine client is null")
                return@withContext Result.failure(Exception("WebDAV未配置"))
            }
            
            // Monica备份目录路径
            val backupDir = "$serverUrl/Monica_Backups"
            
            Log.d(TAG, "Checking backup directory: $backupDir")
            
            // 检查备份目录是否存在
            val dirExists = try {
                sardine?.exists(backupDir) ?: false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check if backup directory exists", e)
                return@withContext Result.failure(Exception("无法连接到服务器: ${e.message}"))
            }
            
            if (!dirExists) {
                Log.w(TAG, "Backup directory does not exist: $backupDir")
                return@withContext Result.failure(Exception("未找到备份目录 Monica_Backups，请先在手机版Monica中创建备份"))
            }
            
            // 1. 列出备份目录中的所有文件
            val resources = try {
                sardine?.list(backupDir) ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to list files from backup directory", e)
                return@withContext Result.failure(Exception("无法读取备份目录: ${e.message}"))
            }
            
            Log.d(TAG, "Found ${resources.size} items in backup directory")
            
            // 打印所有文件名用于调试
            resources.forEachIndexed { index, resource ->
                Log.d(TAG, "File $index: name='${resource.name}', isDirectory=${resource.isDirectory}, modified=${resource.modified}")
            }
            
            val backupFiles = resources.filter { resource ->
                if (resource.isDirectory) {
                    Log.d(TAG, "Skipping directory: ${resource.name}")
                    return@filter false
                }
                
                // 提取文件名（去掉路径）
                val fileName = resource.name.substringAfterLast('/')
                
                val matches = fileName.startsWith("monica_backup_") && 
                    (fileName.endsWith(".zip") || fileName.endsWith(".enc.zip"))
                
                Log.d(TAG, "Checking file: '$fileName', matches=$matches")
                matches
            }.sortedByDescending { it.modified }
            
            Log.d(TAG, "Found ${backupFiles.size} backup files after filtering")
            
            if (backupFiles.isEmpty()) {
                // 提供更详细的错误信息
                val allFileNames = resources.filter { !it.isDirectory }
                    .joinToString(", ") { it.name }
                Log.e(TAG, "No backup files found. All files on server: $allFileNames")
                return@withContext Result.failure(Exception("未找到备份文件。服务器上找到${resources.size - 1}个文件，但没有符合'monica_backup_*.zip'格式的备份文件"))
            }
            
            val latestBackup = backupFiles.first()
            Log.d(TAG, "Latest backup: ${latestBackup.name}, modified: ${latestBackup.modified}")
            
            // 2. 下载文件
            val downloadUrl = "$backupDir/${latestBackup.name}"
            val localFile = File(context.cacheDir, latestBackup.name)
            
            Log.d(TAG, "Downloading from: $downloadUrl")
            
            try {
                sardine?.get(downloadUrl)?.use { inputStream ->
                    FileOutputStream(localFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Log.d(TAG, "Downloaded ${localFile.length()} bytes to: ${localFile.absolutePath}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download file", e)
                return@withContext Result.failure(Exception("下载备份文件失败: ${e.message}"))
            }
            
            // 3. 如果是加密文件，先解密
            val zipFile = if (latestBackup.name.endsWith(".enc.zip")) {
                if (!enableEncryption || encryptionPassword.isEmpty()) {
                    localFile.delete()
                    return@withContext Result.failure(Exception("备份已加密但未配置解密密码"))
                }
                
                Log.d(TAG, "Decrypting backup file...")
                val decryptedFile = File(context.cacheDir, latestBackup.name.replace(".enc.zip", ".zip"))
                val decryptResult = EncryptionHelper.decryptFile(localFile, decryptedFile, encryptionPassword)
                
                localFile.delete()
                
                if (decryptResult.isFailure) {
                    val error = decryptResult.exceptionOrNull()?.message ?: "未知错误"
                    Log.e(TAG, "Decryption failed: $error")
                    return@withContext Result.failure(Exception("解密失败: $error"))
                }
                
                Log.d(TAG, "Decrypted backup successfully")
                decryptedFile
            } else {
                localFile
            }
            
            // 4. 解压并导入TOTP数据
            Log.d(TAG, "Importing TOTP data from zip...")
            val (importedCount, skippedCount) = importTotpFromZip(zipFile)
            
            // 5. 清理临时文件
            zipFile.delete()
            
            // 6. 更新同步时间
            updateLastSyncTime()
            
            // 7. 生成结果消息
            val message = when {
                importedCount > 0 && skippedCount > 0 -> 
                    "同步成功：导入 $importedCount 个新项目，跳过 $skippedCount 个重复项"
                importedCount > 0 && skippedCount == 0 -> 
                    "同步成功：导入 $importedCount 个新项目"
                importedCount == 0 && skippedCount > 0 -> 
                    "同步成功：所有项目已是最新"
                else -> 
                    "同步成功：备份中没有验证器数据"
            }
            
            Log.d(TAG, "Import completed: imported=$importedCount, skipped=$skippedCount")
            Result.success(message)
            
        } catch (e: Exception) {
            Log.e(TAG, "Download and import failed", e)
            val detailedMessage = when {
                e.message?.contains("401") == true -> "认证失败，请检查用户名和密码"
                e.message?.contains("404") == true -> "服务器路径未找到"
                e.message?.contains("403") == true -> "访问被拒绝，请检查权限"
                e.message?.contains("timeout") == true -> "连接超时，请检查网络"
                e.message?.contains("UnknownHost") == true -> "无法解析服务器地址"
                e.message?.contains("Connection refused") == true -> "连接被拒绝，请检查服务器地址和端口"
                else -> e.message ?: "未知错误"
            }
            Result.failure(Exception(detailedMessage))
        }
    }
    
    /**
     * 从ZIP文件导入TOTP数据
     * @return Pair<导入数量, 跳过数量>
     */
    private suspend fun importTotpFromZip(zipFile: File): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val allTotpItems = mutableListOf<SecureItem>()
        
        ZipInputStream(zipFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && isSecureItemsEntry(entry.name)) {
                    val csvFile = File(context.cacheDir, "temp_import_${System.nanoTime()}.csv")
                    try {
                        FileOutputStream(csvFile).use { output ->
                            zip.copyTo(output)
                        }
                        val parsedItems = parseTotpItemsFromCsv(csvFile)
                        if (parsedItems.isNotEmpty()) {
                            Log.d(TAG, "Parsed ${parsedItems.size} TOTP entries from ${entry.name}")
                            allTotpItems.addAll(parsedItems)
                        } else {
                            Log.w(TAG, "No TOTP entries detected in ${entry.name}")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to import CSV ${entry.name}", e)
                        throw e
                    } finally {
                        csvFile.delete()
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        
        if (allTotpItems.isEmpty()) {
            Log.w(TAG, "Zip file did not contain any TOTP data")
            return@withContext Pair(0, 0)
        }
        
        val (importedCount, skippedCount) = totpRepository.importTotpItems(allTotpItems)
        Log.d(TAG, "Imported $importedCount TOTP entries, skipped $skippedCount duplicates")
        Pair(importedCount, skippedCount)
    }
    
    private fun isSecureItemsEntry(entryName: String): Boolean {
        val normalized = entryName.substringAfterLast('/').lowercase(Locale.US)
        return normalized == "secure_items.csv" ||
            normalized == "backup.csv" ||
            (normalized.startsWith("monica_") && normalized.endsWith("_other.csv"))
    }
    
    private fun parseTotpItemsFromCsv(csvFile: File): List<SecureItem> {
        val parsedItems = mutableListOf<SecureItem>()
        csvFile.bufferedReader(Charsets.UTF_8).use { reader ->
            var firstLine = reader.readLine() ?: return emptyList()
            if (firstLine.startsWith("\uFEFF")) {
                firstLine = firstLine.substring(1)
            }
            val hasHeader = firstLine.contains("type", ignoreCase = true) &&
                firstLine.contains("title", ignoreCase = true)
            if (!hasHeader && firstLine.isNotBlank()) {
                parseSecureItem(firstLine)?.let(parsedItems::add)
            }
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    parseSecureItem(line)?.let(parsedItems::add)
                }
            }
        }
        return parsedItems.filter { it.itemType == ItemType.TOTP }
    }
    
    private fun parseSecureItem(line: String): SecureItem? {
        return try {
            val fields = parseCsvLine(line)
            if (fields.size < 9) {
                Log.w(TAG, "Skipping CSV line with insufficient fields: $line")
                return null
            }
            val itemType = runCatching { ItemType.valueOf(fields[1]) }.getOrNull()
            if (itemType != ItemType.TOTP) {
                return null
            }
            val createdAt = fields.getOrNull(7)?.toLongOrNull()?.let { Date(it) }
            val updatedAt = fields.getOrNull(8)?.toLongOrNull()?.let { Date(it) }
            SecureItem(
                id = fields[0].toLongOrNull() ?: 0L,
                itemType = itemType,
                title = fields.getOrNull(2).orEmpty(),
                itemData = fields.getOrNull(3).orEmpty(),
                notes = fields.getOrNull(4).orEmpty(),
                isFavorite = fields.getOrNull(5)?.equals("true", ignoreCase = true) == true,
                createdAt = createdAt ?: Date(),
                updatedAt = updatedAt ?: Date(),
                imagePaths = fields.getOrNull(6).orEmpty()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse CSV line", e)
            null
        }
    }
    
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
            i++
        }
        fields.add(current.toString())
        return fields.map { it.trim() }
    }
    
    fun getServerUrl(): String = serverUrl
    fun getUsername(): String = username
    fun isEncryptionEnabled(): Boolean = enableEncryption
}
