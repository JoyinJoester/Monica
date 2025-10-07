package takagi.ru.monica.utils

import android.content.Context
import android.net.Uri
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryType
import takagi.ru.monica.data.ledger.LedgerEntryWithRelations
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
        secureItems: List<SecureItem>,
        ledgerEntries: List<LedgerEntryWithRelations>
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 创建临时CSV文件
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val passwordsCsvFile = File(context.cacheDir, "Monica_${timestamp}_password.csv")
            val secureItemsCsvFile = File(context.cacheDir, "Monica_${timestamp}_other.csv")
            val ledgerCsvFile = File(context.cacheDir, "Monica_${timestamp}_bill.csv")
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

                // 4. 导出账单数据到CSV
                exportLedgerToCSV(ledgerEntries, ledgerCsvFile)
                
                // 5. 创建ZIP文件,包含三个CSV
                ZipOutputStream(FileOutputStream(zipFile)).use { zipOut ->
                    // 添加passwords.csv
                    addFileToZip(zipOut, passwordsCsvFile, passwordsCsvFile.name)
                    
                    // 添加secure_items.csv
                    addFileToZip(zipOut, secureItemsCsvFile, secureItemsCsvFile.name)

                    // 添加账单数据
                    addFileToZip(zipOut, ledgerCsvFile, ledgerCsvFile.name)
                }
                
                // 6. 上传到WebDAV
                val uploadResult = uploadBackup(zipFile)
                
                uploadResult
            } finally {
                // 7. 清理临时文件
                passwordsCsvFile.delete()
                secureItemsCsvFile.delete()
                ledgerCsvFile.delete()
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
                val ledgerEntries = mutableListOf<LedgerBackupEntry>()
                
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
                            entryName.startsWith("Monica_", ignoreCase = true) && entryName.endsWith("_bill.csv", ignoreCase = true) -> {
                                ledgerEntries.addAll(importLedgerFromCSV(tempFile))
                            }
                        }
                        tempFile.delete()
                        entry = zipIn.nextEntry
                    }
                }
                
                Result.success(BackupContent(passwords, secureItems, ledgerEntries))
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

    private fun exportLedgerToCSV(entries: List<LedgerEntryWithRelations>, file: File) {
        file.outputStream().use { output ->
            BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                writer.write("\uFEFF")
                writer.write("Title,Type,AmountInCents,CurrencyCode,CategoryName,PaymentMethod,OccurredAt,Note,CreatedAt,UpdatedAt")
                writer.newLine()

                entries.forEach { item ->
                    val entry = item.entry
                    val row = listOf(
                        escapeCsvField(entry.title),
                        escapeCsvField(entry.type.name),
                        entry.amountInCents.toString(),
                        escapeCsvField(entry.currencyCode),
                        escapeCsvField(item.category?.name ?: ""),
                        escapeCsvField(entry.paymentMethod),
                        entry.occurredAt.time.toString(),
                        escapeCsvField(entry.note),
                        entry.createdAt.time.toString(),
                        entry.updatedAt.time.toString()
                    )
                    writer.write(row.joinToString(","))
                    writer.newLine()
                }
            }
        }
    }

    private fun importLedgerFromCSV(file: File): List<LedgerBackupEntry> {
        val ledgerEntries = mutableListOf<LedgerBackupEntry>()
        file.bufferedReader(Charsets.UTF_8).use { reader ->
            var firstLine = reader.readLine()
            if (firstLine?.startsWith("\uFEFF") == true) {
                firstLine = firstLine.substring(1)
            }
            if (firstLine != null) {
                val fields = splitCsvLine(firstLine)
                val isHeader = fields.any { it.equals("Title", ignoreCase = true) } &&
                    fields.any { it.equals("Type", ignoreCase = true) }
                if (!isHeader && firstLine.isNotBlank()) {
                    parseLedgerCsvLine(firstLine)?.let { ledgerEntries.add(it) }
                }
            }
            reader.forEachLine { line ->
                if (line.isNotBlank()) {
                    parseLedgerCsvLine(line)?.let { ledgerEntries.add(it) }
                }
            }
        }
        return ledgerEntries
    }

    private fun parseLedgerCsvLine(line: String): LedgerBackupEntry? {
        return try {
            val fields = splitCsvLine(line)
            if (fields.size < 10) {
                return null
            }
            val type = fields.getOrNull(1)?.let {
                runCatching { LedgerEntryType.valueOf(it) }.getOrNull()
            } ?: LedgerEntryType.EXPENSE
            val occurredAt = fields.getOrNull(6)?.toLongOrNull()?.let(::Date) ?: Date()
            val createdAt = fields.getOrNull(8)?.toLongOrNull()?.let(::Date) ?: occurredAt
            val updatedAt = fields.getOrNull(9)?.toLongOrNull()?.let(::Date) ?: createdAt

            LedgerBackupEntry(
                entry = LedgerEntry(
                    id = 0,
                    title = fields[0],
                    amountInCents = fields.getOrNull(2)?.toLongOrNull() ?: 0L,
                    currencyCode = fields.getOrNull(3).takeUnless { it.isNullOrBlank() } ?: Currency.getInstance(Locale.getDefault()).currencyCode,
                    type = type,
                    categoryId = null,
                    linkedItemId = null,
                    occurredAt = occurredAt,
                    note = fields.getOrNull(7) ?: "",
                    paymentMethod = fields.getOrNull(5) ?: "",
                    createdAt = createdAt,
                    updatedAt = updatedAt
                ),
                categoryName = fields.getOrNull(4)?.takeUnless { it.isBlank() }
            )
        } catch (e: Exception) {
            android.util.Log.e("WebDavHelper", "Failed to parse ledger CSV line: ${e.message}")
            null
        }
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
    val secureItems: List<DataExportImportManager.ExportItem>,
    val ledgerEntries: List<LedgerBackupEntry>
)

data class LedgerBackupEntry(
    val entry: LedgerEntry,
    val categoryName: String?
)
