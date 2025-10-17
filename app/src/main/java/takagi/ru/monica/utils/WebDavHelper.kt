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
import takagi.ru.monica.util.DataExportImportManager
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
        private const val PASSWORD_META_MARKER = "[MonicaMeta]"
    }
    
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
        
        if (url.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
            serverUrl = url
            username = user
            password = pass
            // 重新创建 sardine 实例并设置凭证
            sardine = OkHttpSardine()
            sardine?.setCredentials(username, password)
            android.util.Log.d("WebDavHelper", "Loaded WebDAV config: url=$serverUrl, user=$username")
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
        sardine = null
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
     * @return 备份文件名
     */
    suspend fun createAndUploadBackup(
        passwords: List<PasswordEntry>,
        secureItems: List<SecureItem>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 创建临时CSV文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val passwordsCsvFile = File(context.cacheDir, "Monica_${timestamp}_password.csv")
            val secureItemsCsvFile = File(context.cacheDir, "Monica_${timestamp}_other.csv")
            val zipFile = File(context.cacheDir, "monica_backup_$timestamp.zip")
            
            try {
                // 2. 导出密码数据到CSV
                exportPasswordsToCSV(passwords, passwordsCsvFile)
                
                // 3. 导出其他数据到CSV
                val exportManager = DataExportImportManager(context)
                val csvUri = Uri.fromFile(secureItemsCsvFile)
                val exportResult = exportManager.exportData(secureItems, csvUri)
                
                if (exportResult.isFailure) {
                    return@withContext Result.failure(exportResult.exceptionOrNull() 
                        ?: Exception("导出数据失败"))
                }
                
                // 4. 创建ZIP文件,包含两个CSV
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    // 添加passwords.csv
                    addFileToZip(zipOut, passwordsCsvFile, passwordsCsvFile.name)
                    
                    // 添加secure_items.csv
                    addFileToZip(zipOut, secureItemsCsvFile, secureItemsCsvFile.name)
                }
                
                // 5. 上传到WebDAV
                val uploadResult = uploadBackup(zipFile)
                
                uploadResult
            } finally {
                // 6. 清理临时文件
                passwordsCsvFile.delete()
                secureItemsCsvFile.delete()
                zipFile.delete()
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
     * 下载并恢复备份 - 返回密码、其他数据和账单
     * @param backupFile 要恢复的备份文件
     */
    suspend fun downloadAndRestoreBackup(backupFile: BackupFile): 
        Result<BackupContent> = 
        withContext(Dispatchers.IO) {
        try {
            // 1. 下载备份文件
            val zipFile = File(context.cacheDir, "restore_${backupFile.name}")
            val downloadResult = downloadBackup(backupFile, zipFile)
            
            if (downloadResult.isFailure) {
                return@withContext Result.failure(downloadResult.exceptionOrNull() 
                    ?: Exception("下载备份失败"))
            }
            
            try {
                val passwords = mutableListOf<PasswordEntry>()
                val secureItems = mutableListOf<DataExportImportManager.ExportItem>()
                
                // 2. 解压ZIP文件并读取CSV
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
                            entryName.equals("passwords.csv", ignoreCase = true) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_password.csv", ignoreCase = true)) -> {
                                passwords.addAll(importPasswordsFromCSV(tempFile))
                            }
                            entryName.equals("secure_items.csv", ignoreCase = true) ||
                                entryName.equals("backup.csv", ignoreCase = true) ||
                                (entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_other.csv", ignoreCase = true)) -> {
                                val exportManager = DataExportImportManager(context)
                                val csvUri = Uri.fromFile(tempFile)
                                val importResult = exportManager.importData(csvUri)
                                if (importResult.isSuccess) {
                                    secureItems.addAll(importResult.getOrNull() ?: emptyList())
                                }
                            }
                        }
                        tempFile.delete()
                        entry = zipIn.nextEntry
                    }
                }
                
                Result.success(BackupContent(passwords, secureItems))
            } finally {
                zipFile.delete()
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
}

/**
 * 备份文件信息
 */
data class BackupFile(
    val name: String,
    val path: String,
    val size: Long,
    val modified: Date
)

data class BackupContent(
    val passwords: List<PasswordEntry>,
    val secureItems: List<DataExportImportManager.ExportItem>
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
