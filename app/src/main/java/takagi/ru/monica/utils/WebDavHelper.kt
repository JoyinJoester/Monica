package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
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
import java.io.InputStream
import java.text.SimpleDateFormat
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
        sardine = OkHttpSardine().apply {
            setCredentials(username, password)
        }
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
            sardine = OkHttpSardine().apply {
                setCredentials(username, password)
            }
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
            
            // 尝试列出根目录
            sardine?.list(serverUrl)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
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
            val passwordsCsvFile = File(context.cacheDir, "passwords_$timestamp.csv")
            val secureItemsCsvFile = File(context.cacheDir, "secure_items_$timestamp.csv")
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
                    FileInputStream(passwordsCsvFile).use { fileIn ->
                        val entry = ZipEntry("passwords.csv")
                        zipOut.putNextEntry(entry)
                        fileIn.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
                    
                    // 添加secure_items.csv
                    FileInputStream(secureItemsCsvFile).use { fileIn ->
                        val entry = ZipEntry("secure_items.csv")
                        zipOut.putNextEntry(entry)
                        fileIn.copyTo(zipOut)
                        zipOut.closeEntry()
                    }
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
                writer.write("ID,Title,Website,Username,Password,Notes,IsFavorite,CreatedAt,UpdatedAt,SortOrder,IsGroupCover")
                writer.newLine()
                
                // 写入数据行
                passwords.forEach { entry ->
                    val row = listOf(
                        entry.id.toString(),
                        escapeCsvField(entry.title),
                        escapeCsvField(entry.website),
                        escapeCsvField(entry.username),
                        escapeCsvField(entry.password),
                        escapeCsvField(entry.notes),
                        entry.isFavorite.toString(),
                        entry.createdAt.time.toString(),
                        entry.updatedAt.time.toString(),
                        entry.sortOrder.toString(),
                        entry.isGroupCover.toString()
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
     * 下载并恢复备份 - 返回密码和其他数据
     * @param backupFile 要恢复的备份文件
     * @return Pair<密码列表, 其他数据列表>
     */
    suspend fun downloadAndRestoreBackup(backupFile: BackupFile): 
        Result<Pair<List<PasswordEntry>, List<DataExportImportManager.ExportItem>>> = 
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
                
                // 2. 解压ZIP文件并读取两个CSV
                ZipInputStream(FileInputStream(zipFile)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "passwords.csv" -> {
                                // 读取密码数据
                                val csvFile = File(context.cacheDir, "restore_passwords.csv")
                                FileOutputStream(csvFile).use { fileOut ->
                                    zipIn.copyTo(fileOut)
                                }
                                passwords.addAll(importPasswordsFromCSV(csvFile))
                                csvFile.delete()
                            }
                            "secure_items.csv" -> {
                                // 读取其他数据
                                val csvFile = File(context.cacheDir, "restore_secure_items.csv")
                                FileOutputStream(csvFile).use { fileOut ->
                                    zipIn.copyTo(fileOut)
                                }
                                val exportManager = DataExportImportManager(context)
                                val csvUri = Uri.fromFile(csvFile)
                                val importResult = exportManager.importData(csvUri)
                                if (importResult.isSuccess) {
                                    secureItems.addAll(importResult.getOrNull() ?: emptyList())
                                }
                                csvFile.delete()
                            }
                            // 兼容旧版本备份(只有backup.csv)
                            "backup.csv" -> {
                                val csvFile = File(context.cacheDir, "restore_backup.csv")
                                FileOutputStream(csvFile).use { fileOut ->
                                    zipIn.copyTo(fileOut)
                                }
                                val exportManager = DataExportImportManager(context)
                                val csvUri = Uri.fromFile(csvFile)
                                val importResult = exportManager.importData(csvUri)
                                if (importResult.isSuccess) {
                                    secureItems.addAll(importResult.getOrNull() ?: emptyList())
                                }
                                csvFile.delete()
                            }
                        }
                        entry = zipIn.nextEntry
                    }
                }
                
                Result.success(Pair(passwords, secureItems))
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
            
            // 跳过标题行
            if (firstLine?.contains("Title") == true && firstLine.contains("Password")) {
                // 是标题行,继续读取
            } else if (firstLine != null) {
                // 第一行就是数据
                parseCsvLine(firstLine)?.let { passwords.add(it) }
            }
            
            // 读取剩余数据
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    parseCsvLine(line)?.let { passwords.add(it) }
                }
            }
        }
        
        return passwords
    }
    
    /**
     * 解析CSV行为PasswordEntry
     */
    private fun parseCsvLine(line: String): PasswordEntry? {
        return try {
            val fields = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                .map { it.trim().removeSurrounding("\"").replace("\"\"", "\"") }
            
            if (fields.size >= 11) {
                PasswordEntry(
                    id = 0, // 让数据库自动生成新ID
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
            android.util.Log.e("WebDavHelper", "Failed to parse password CSV line: ${e.message}")
            null
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
