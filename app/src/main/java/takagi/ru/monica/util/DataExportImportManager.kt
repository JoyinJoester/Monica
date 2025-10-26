package takagi.ru.monica.util

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.SecureItem
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonArray
import takagi.ru.monica.util.AegisDecryptor

/**
 * 数据导入导出管理器
 * 负责将所有数据导出为CSV文件，以及从CSV文件导入数据
 */
class DataExportImportManager(private val context: Context) {

    /**
     * 导出数据项
     */
    data class ExportItem(
        val id: Long,
        val itemType: String,
        val title: String,
        val itemData: String,
        val notes: String,
        val isFavorite: Boolean,
        val imagePaths: String,
        val createdAt: Long,
        val updatedAt: Long
    )

    companion object {
        private const val EXPORT_FILE_EXTENSION = ".csv"
        private const val CSV_SEPARATOR = ","
        private const val CSV_QUOTE = "\""
        
        // CSV 列标题
        private val CSV_HEADERS = arrayOf(
            "ID", "Type", "Title", "Data", "Notes", "IsFavorite", 
            "ImagePaths", "CreatedAt", "UpdatedAt"
        )
    }

    /**
     * 导出数据到CSV文件
     * @param items 要导出的所有数据项
     * @param outputUri 输出文件的URI
     */
    suspend fun exportData(
        items: List<SecureItem>,
        outputUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: return@withContext Result.failure(Exception("无法创建输出文件，请检查存储权限"))
            
            outputStream.use { output ->
                BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                    // 写入BOM标记，让Excel能正确识别UTF-8
                    writer.write("\uFEFF")
                    
                    // 写入列标题
                    writer.write(CSV_HEADERS.joinToString(CSV_SEPARATOR))
                    writer.newLine()
                    
                    // 写入数据行
                    items.forEach { item ->
                        try {
                            val row = arrayOf(
                                item.id.toString(),
                                item.itemType.name,
                                escapeCsvField(item.title),
                                escapeCsvField(item.itemData),
                                escapeCsvField(item.notes),
                                item.isFavorite.toString(),
                                escapeCsvField(item.imagePaths),
                                item.createdAt.time.toString(),
                                item.updatedAt.time.toString()
                            )
                            writer.write(row.joinToString(CSV_SEPARATOR))
                            writer.newLine()
                        } catch (e: Exception) {
                            android.util.Log.e("DataExport", "写入数据项失败: ${item.id}", e)
                            // 继续处理下一项
                        }
                    }
                }
            }

            Result.success("成功导出 ${items.size} 条数据")
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出失败", e)
            Result.failure(Exception("导出失败：${e.message ?: "未知错误"}"))
        }
    }

    /**
     * 从CSV文件导入数据
     * @param inputUri 输入文件的URI
     * @return 导入的数据项列表
     */
    suspend fun importData(
        inputUri: Uri
    ): Result<List<ExportItem>> = withContext(Dispatchers.IO) {
        try {
            val items = mutableListOf<ExportItem>()
            var lineCount = 0
            var errorCount = 0
            var csvFormat: CsvFormat = CsvFormat.UNKNOWN
            
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("无法读取文件，请检查文件是否存在"))
            
            inputStream.use { input ->
                // 尝试UTF-8，如果失败则尝试GBK
                val reader = try {
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                } catch (e: Exception) {
                    android.util.Log.w("DataImport", "UTF-8解码失败，尝试GBK", e)
                    BufferedReader(InputStreamReader(input, Charset.forName("GBK")))
                }
                
                reader.use { 
                    var firstLine = reader.readLine()
                    if (firstLine == null) {
                        return@withContext Result.failure(Exception("文件为空"))
                    }
                    
                    android.util.Log.d("DataImport", "第一行: $firstLine")
                    
                    // 跳过BOM标记（如果存在）
                    if (firstLine.startsWith("\uFEFF")) {
                        firstLine = firstLine.substring(1)
                        android.util.Log.d("DataImport", "跳过BOM后: $firstLine")
                    }
                    
                    // 检测CSV格式
                    csvFormat = detectCsvFormat(firstLine)
                    android.util.Log.d("DataImport", "检测到格式: $csvFormat")
                    
                    // 如果第一行是标题，跳过它
                    val isHeader = when (csvFormat) {
                        CsvFormat.APP_EXPORT -> firstLine.contains("Type") && 
                                               firstLine.contains("Title") && 
                                               firstLine.contains("Data")
                        CsvFormat.CHROME_PASSWORD -> firstLine.contains("name") && 
                                                    firstLine.contains("url") && 
                                                    firstLine.contains("username") &&
                                                    firstLine.contains("password")
                        else -> false
                    }
                    
                    android.util.Log.d("DataImport", "是否为标题行: $isHeader")
                    
                    if (!isHeader && firstLine.isNotBlank()) {
                        // 第一行就是数据，处理它
                        lineCount++
                        try {
                            val fields = parseCsvLine(firstLine)
                            android.util.Log.d("DataImport", "第一行字段数: ${fields.size}, 内容: $fields")
                            val item = createExportItemFromFormat(fields, csvFormat)
                            if (item != null) {
                                items.add(item)
                                android.util.Log.d("DataImport", "成功添加第一行数据")
                            } else {
                                android.util.Log.w("DataImport", "第一行数据无效")
                                errorCount++
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("DataImport", "处理第一行失败: ${e.message}", e)
                            errorCount++
                        }
                    }
                    
                    // 读取剩余数据行
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: continue
                        lineCount++
                        android.util.Log.d("DataImport", "读取第${lineCount}行: $currentLine")
                        if (currentLine.isNotBlank()) {
                            try {
                                val fields = parseCsvLine(currentLine)
                                android.util.Log.d("DataImport", "第${lineCount}行字段数: ${fields.size}")
                                val item = createExportItemFromFormat(fields, csvFormat)
                                if (item != null) {
                                    items.add(item)
                                    android.util.Log.d("DataImport", "成功添加第${lineCount}行数据")
                                } else {
                                    android.util.Log.w("DataImport", "第${lineCount}行数据无效")
                                    errorCount++
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("DataImport", "处理第${lineCount}行失败: ${e.message}", e)
                                errorCount++
                            }
                        }
                    }
                    android.util.Log.d("DataImport", "总行数: $lineCount, 成功: ${items.size}, 错误: $errorCount")
                }
            }

            if (items.isEmpty()) {
                Result.failure(Exception("未能导入任何数据，请检查文件格式"))
            } else {
                Result.success(items)
            }
        } catch (e: Exception) {
            android.util.Log.e("DataImport", "导入异常", e)
            Result.failure(Exception("导入失败：${e.message ?: "文件格式错误"}"))
        }
    }

    /**
     * 创建导出项
     */
    private fun createExportItem(fields: List<String>): ExportItem {
        return ExportItem(
            id = fields[0].toLongOrNull() ?: 0,
            itemType = fields[1],
            title = fields[2],
            itemData = fields[3],
            notes = fields[4],
            isFavorite = fields[5].toBoolean(),
            imagePaths = fields[6],
            createdAt = fields[7].toLongOrNull() ?: System.currentTimeMillis(),
            updatedAt = fields[8].toLongOrNull() ?: System.currentTimeMillis()
        )
    }
    
    /**
     * CSV格式类型
     */
    private enum class CsvFormat {
        APP_EXPORT,        // 应用导出格式 (9个字段)
        CHROME_PASSWORD,   // Chrome密码格式 (name,url,username,password,note)
        ALIPAY_TRANSACTION, // 支付宝交易明细格式
        UNKNOWN
    }
    
    /**
     * 检测CSV格式
     */
    private fun detectCsvFormat(firstLine: String): CsvFormat {
        val lowerLine = firstLine.lowercase()
        return when {
            // 支付宝格式检测
            lowerLine.contains("交易时间") && lowerLine.contains("收/支") && 
            lowerLine.contains("金额") -> 
                CsvFormat.ALIPAY_TRANSACTION
            
            lowerLine.contains("name") && lowerLine.contains("url") && 
            lowerLine.contains("username") && lowerLine.contains("password") -> 
                CsvFormat.CHROME_PASSWORD
            
            lowerLine.contains("type") && lowerLine.contains("title") && 
            lowerLine.contains("data") -> 
                CsvFormat.APP_EXPORT
            
            else -> {
                // 根据字段数量推测
                val fields = parseCsvLine(firstLine)
                when {
                    fields.size >= 9 -> CsvFormat.APP_EXPORT
                    fields.size == 5 -> CsvFormat.CHROME_PASSWORD
                    fields.size >= 12 -> CsvFormat.ALIPAY_TRANSACTION
                    else -> CsvFormat.UNKNOWN
                }
            }
        }
    }
    
    /**
     * 根据格式创建导出项
     */
    private fun createExportItemFromFormat(fields: List<String>, format: CsvFormat): ExportItem? {
        return try {
            when (format) {
                CsvFormat.APP_EXPORT -> {
                    if (fields.size >= 9) {
                        createExportItem(fields)
                    } else null
                }
                
                CsvFormat.CHROME_PASSWORD -> {
                    if (fields.size >= 4) {
                        // Chrome格式: name,url,username,password,note
                        val name = fields.getOrNull(0)?.trim() ?: ""
                        val url = fields.getOrNull(1)?.trim() ?: ""
                        val username = fields.getOrNull(2)?.trim() ?: ""
                        val password = fields.getOrNull(3)?.trim() ?: ""
                        val note = fields.getOrNull(4)?.trim() ?: ""
                        
                        // 跳过空记录
                        if (name.isBlank() && username.isBlank() && password.isBlank()) {
                            return null
                        }
                        
                        // 转换为密码条目格式 (使用应用的格式: username:xxx;password:xxx;website:xxx)
                        val passwordData = buildString {
                            append("username:$username;")
                            append("password:$password")
                            if (url.isNotEmpty()) {
                                append(";website:$url")
                            }
                        }
                        
                        ExportItem(
                            id = 0,
                            itemType = "PASSWORD",
                            title = name.ifBlank { url.ifBlank { username } },
                            itemData = passwordData,
                            notes = note,
                            isFavorite = false,
                            imagePaths = "",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    } else null
                }
                
                CsvFormat.ALIPAY_TRANSACTION -> {
                    // 支付宝格式在专门的方法中处理,这里返回null
                    null
                }
                
                CsvFormat.UNKNOWN -> null
            }
        } catch (e: Exception) {
            android.util.Log.e("DataImport", "创建导出项失败: ${e.message}", e)
            null
        }
    }

    /**
     * 转义CSV字段（处理引号和逗号）
     */
    private fun escapeCsvField(field: String): String {
        return if (field.contains(CSV_SEPARATOR) || field.contains(CSV_QUOTE) || field.contains("\n")) {
            CSV_QUOTE + field.replace(CSV_QUOTE, "$CSV_QUOTE$CSV_QUOTE") + CSV_QUOTE
        } else {
            field
        }
    }

    /**
     * 解析CSV行（处理带引号的字段）
     */
    private fun parseCsvLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val currentField = StringBuilder()
        var inQuotes = false
        var i = 0
        
        try {
            while (i < line.length) {
                val char = line[i]
                
                when {
                    char == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                        // 转义的引号
                        currentField.append('"')
                        i++
                    }
                    char == '"' -> {
                        inQuotes = !inQuotes
                    }
                    char == ',' && !inQuotes -> {
                        fields.add(currentField.toString().trim())
                        currentField.clear()
                    }
                    else -> {
                        currentField.append(char)
                    }
                }
                i++
            }
            fields.add(currentField.toString().trim())
        } catch (e: Exception) {
            android.util.Log.e("DataImport", "解析CSV行失败: $line", e)
            // 返回当前已解析的字段
        }
        
        return fields
    }

    /**
     * 获取建议的导出文件名
     */
    fun getSuggestedFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "monica_backup_${timestamp}${EXPORT_FILE_EXTENSION}"
    }

/**
     * 从Aegis JSON文件导入TOTP数据
     * @param inputUri 输入文件的URI
     * @return 导入的数据项列表
     */
    suspend fun importAegisJson(
        inputUri: Uri
    ): Result<List<AegisEntry>> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("无法读取文件，请检查文件是否存在"))
        
            inputStream.use { input ->
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val content = reader.readText()
                
                // 解析JSON
                val json = Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(content).jsonObject
                
                // 检查是否为加密的vault
                val dbField = root["db"]
                if (dbField != null && dbField is kotlinx.serialization.json.JsonPrimitive) {
                    // 这是一个加密的vault，我们无法解密它
                    return@withContext Result.failure(Exception("无法导入加密的Aegis备份文件。请导出未加密的JSON文件。"))
                }
                
                // 尝试解析未加密的数据库格式
                val entriesArray = try {
                    // 首先尝试从db字段中获取entries
                    val dbObj = dbField?.jsonObject
                    val dbEntries = if (dbObj != null) {
                        dbObj["entries"]?.jsonArray
                    } else {
                        null
                    }
                    dbEntries ?: root["entries"]?.jsonArray
                } catch (e: Exception) {
                    // 如果上面的方法失败，尝试直接解析根对象中的entries数组
                    try {
                        root["entries"]?.jsonArray
                    } catch (ex: Exception) {
                        null
                    }
                }
                
                if (entriesArray == null) {
                    return@withContext Result.failure(Exception("无效的Aegis JSON格式：未找到entries数组"))
                }
                
                val entries = mutableListOf<AegisEntry>()
                var parsedCount = 0
                var errorCount = 0
                
                entriesArray.forEach { element ->
                    try {
                        // 确保element是JsonObject类型
                        if (element is kotlinx.serialization.json.JsonObject) {
                            val entryObj = element
                            val type = entryObj["type"]?.jsonPrimitive?.content ?: "totp"
                            
                            // 只处理TOTP条目
                            if (type.lowercase() == "totp") {
                                val uuid = entryObj["uuid"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString()
                                val name = entryObj["name"]?.jsonPrimitive?.content ?: ""
                                val issuer = entryObj["issuer"]?.jsonPrimitive?.content ?: ""
                                val note = entryObj["note"]?.jsonPrimitive?.content ?: ""
                                
                                // 获取info对象
                                val infoObj = entryObj["info"]?.jsonObject
                                if (infoObj != null) {
                                    val secret = infoObj["secret"]?.jsonPrimitive?.content ?: ""
                                    val algo = infoObj["algo"]?.jsonPrimitive?.content ?: "SHA1"
                                    val digits = infoObj["digits"]?.jsonPrimitive?.content?.toIntOrNull() ?: 6
                                    val period = infoObj["period"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30
                                    
                                    if (secret.isNotBlank()) {
                                        val entry = AegisEntry(
                                            uuid = uuid,
                                            name = name,
                                            issuer = issuer,
                                            note = note,
                                            secret = secret,
                                            algorithm = algo,
                                            digits = digits,
                                            period = period
                                        )
                                        entries.add(entry)
                                        parsedCount++
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errorCount++
                        android.util.Log.e("AegisImport", "解析条目失败", e)
                    }
                }
                
                android.util.Log.d("AegisImport", "成功解析 $parsedCount 条目，$errorCount 个错误")
                
                if (entries.isEmpty()) {
                    Result.failure(Exception("未能从Aegis文件中导入任何有效的TOTP条目"))
                } else {
                    Result.success(entries)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AegisImport", "导入Aegis文件失败", e)
            Result.failure(Exception("导入Aegis文件失败：${e.message ?: "未知错误"}"))
        }
    }

    // Aegis条目数据类
    data class AegisEntry(
        val uuid: String,
        val name: String,
        val issuer: String,
        val note: String,
        val secret: String,
        val algorithm: String,
        val digits: Int,
        val period: Int
    )
    
    /**
     * 检查文件是否为加密的Aegis文件
     * @param inputUri 输入文件的URI
     * @return 如果是加密文件返回true，否则返回false
     */
    suspend fun isEncryptedAegisFile(inputUri: Uri): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.success(false)
        
            inputStream.use { input ->
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val content = reader.readText()
                
                val decryptor = AegisDecryptor()
                Result.success(decryptor.isEncryptedAegisFile(content))
            }
        } catch (e: Exception) {
            android.util.Log.e("AegisCheck", "检查Aegis文件失败", e)
            Result.success(false)
        }
    }
    
    /**
     * 从加密的Aegis JSON文件导入TOTP数据
     * @param inputUri 输入文件的URI
     * @param password 解密密码
     * @return 导入的数据项列表
     */
    suspend fun importEncryptedAegisJson(
        inputUri: Uri,
        password: String
    ): Result<List<AegisEntry>> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(inputUri)
                ?: return@withContext Result.failure(Exception("无法读取文件，请检查文件是否存在"))
        
            inputStream.use { input ->
                val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8))
                val content = reader.readText()
                
                // 解析JSON
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val root = json.parseToJsonElement(content).jsonObject
                
                // 获取header信息
                val header = root["header"]?.jsonObject
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少header"))
                
                // 获取slots信息
                val slots = header["slots"]?.jsonArray
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少slots"))
                
                if (slots.isEmpty()) {
                    return@withContext Result.failure(Exception("无效的Aegis文件格式：slots为空"))
                }
                
                // 获取第一个slot
                val slot = slots[0].jsonObject
                val slotType = slot["type"]?.jsonPrimitive?.content?.toIntOrNull()
                if (slotType != 1) {
                    return@withContext Result.failure(Exception("不支持的slot类型: $slotType"))
                }
                
                val salt = slot["salt"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少salt"))
                
                val key = slot["key"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少key"))
                
                val keyParams = slot["key_params"]?.jsonObject
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少key_params"))
                
                val nonce = keyParams["nonce"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少nonce"))
                
                val tag = keyParams["tag"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少tag"))
                
                // 获取加密的db数据
                val encryptedDb = root["db"]?.jsonPrimitive?.content
                    ?: return@withContext Result.failure(Exception("无效的Aegis文件格式：缺少db"))
                
                // 解密数据
                val decryptor = AegisDecryptor()
                val keyParamsObj = AegisDecryptor.KeyParams(nonce, tag)
                
                // 首先解密主密钥
                val decryptedKey = try {
                    decryptor.decryptMasterKey(password, salt, keyParamsObj, key)
                } catch (e: Exception) {
                    android.util.Log.e("EncryptedAegisImport", "解密主密钥失败", e)
                    return@withContext Result.failure(Exception("解密主密钥失败：密码错误或文件损坏"))
                }
                
                // 验证解密后的主密钥长度
                if (decryptedKey.size != 32) {
                    return@withContext Result.failure(Exception("解密主密钥失败：密钥长度不正确"))
                }
                
                // 然后使用主密钥解密db字段
                val dbNonce = header["params"]?.jsonObject?.get("nonce")?.jsonPrimitive?.content ?: nonce
                val dbTag = header["params"]?.jsonObject?.get("tag")?.jsonPrimitive?.content ?: tag
                val dbKeyParams = AegisDecryptor.KeyParams(dbNonce, dbTag)
                
                val decryptedDbData = try {
                    // 注意：db字段可能是Base64编码的，而不是十六进制字符串
                    decryptor.decryptWithKeyBase64(decryptedKey, dbKeyParams, encryptedDb)
                } catch (e: Exception) {
                    android.util.Log.e("EncryptedAegisImport", "解密db数据失败", e)
                    return@withContext Result.failure(Exception("解密db数据失败：密码错误或文件损坏"))
                }
                
                // 解析解密后的JSON
                val decryptedContent = String(decryptedDbData, Charsets.UTF_8)
                val decryptedRoot = json.parseToJsonElement(decryptedContent).jsonObject
                val entriesArray = decryptedRoot["entries"]?.jsonArray
                    ?: return@withContext Result.failure(Exception("无效的解密数据：未找到entries数组"))
                
                val entries = mutableListOf<AegisEntry>()
                var parsedCount = 0
                var errorCount = 0
                
                entriesArray.forEach { element ->
                    try {
                        val entryObj = element.jsonObject
                        val type = entryObj["type"]?.jsonPrimitive?.content ?: "totp"
                        
                        // 只处理TOTP条目
                        if (type.lowercase() == "totp") {
                            val uuid = entryObj["uuid"]?.jsonPrimitive?.content ?: java.util.UUID.randomUUID().toString()
                            val name = entryObj["name"]?.jsonPrimitive?.content ?: ""
                            val issuer = entryObj["issuer"]?.jsonPrimitive?.content ?: ""
                            val note = entryObj["note"]?.jsonPrimitive?.content ?: ""
                            
                            // 获取info对象
                            val infoObj = entryObj["info"]?.jsonObject
                            if (infoObj != null) {
                                val secret = infoObj["secret"]?.jsonPrimitive?.content ?: ""
                                val algo = infoObj["algo"]?.jsonPrimitive?.content ?: "SHA1"
                                val digits = infoObj["digits"]?.jsonPrimitive?.content?.toIntOrNull() ?: 6
                                val period = infoObj["period"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30
                                
                                if (secret.isNotBlank()) {
                                    val entry = AegisEntry(
                                        uuid = uuid,
                                        name = name,
                                        issuer = issuer,
                                        note = note,
                                        secret = secret,
                                        algorithm = algo,
                                        digits = digits,
                                        period = period
                                    )
                                    entries.add(entry)
                                    parsedCount++
                                }
                            }
                        }
                    } catch (e: Exception) {
                        errorCount++
                        android.util.Log.e("EncryptedAegisImport", "解析条目失败", e)
                    }
                }
                
                android.util.Log.d("EncryptedAegisImport", "成功解析 $parsedCount 条目，$errorCount 个错误")
                
                if (entries.isEmpty()) {
                    Result.failure(Exception("未能从Aegis文件中导入任何有效的TOTP条目"))
                } else {
                    Result.success(entries)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("EncryptedAegisImport", "导入加密Aegis文件失败", e)
            Result.failure(Exception("导入加密Aegis文件失败：${e.message ?: "未知错误"}"))
        }
    }
    
    /**
     * 导出密码数据到CSV
     */
    suspend fun exportPasswords(
        items: List<SecureItem>,
        outputUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: return@withContext Result.failure(Exception("无法创建输出文件"))
            
            outputStream.use { output ->
                BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                    writer.write("\uFEFF")
                    writer.write(CSV_HEADERS.joinToString(CSV_SEPARATOR))
                    writer.newLine()
                    
                    items.forEach { item ->
                        try {
                            val row = arrayOf(
                                item.id.toString(),
                                item.itemType.name,
                                escapeCsvField(item.title),
                                escapeCsvField(item.itemData),
                                escapeCsvField(item.notes),
                                item.isFavorite.toString(),
                                escapeCsvField(item.imagePaths),
                                item.createdAt.time.toString(),
                                item.updatedAt.time.toString()
                            )
                            writer.write(row.joinToString(CSV_SEPARATOR))
                            writer.newLine()
                        } catch (e: Exception) {
                            android.util.Log.e("DataExport", "写入密码项失败: ${item.id}", e)
                        }
                    }
                }
            }
            Result.success("成功导出 ${items.size} 条密码")
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出密码失败", e)
            Result.failure(Exception("导出密码失败：${e.message ?: "未知错误"}"))
        }
    }
    
    /**
     * 导出银行卡和证件到CSV
     */
    suspend fun exportBankCardsAndDocuments(
        items: List<SecureItem>,
        outputUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val outputStream = context.contentResolver.openOutputStream(outputUri)
                ?: return@withContext Result.failure(Exception("无法创建输出文件"))
            
            outputStream.use { output ->
                BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                    writer.write("\uFEFF")
                    writer.write(CSV_HEADERS.joinToString(CSV_SEPARATOR))
                    writer.newLine()
                    
                    items.forEach { item ->
                        try {
                            val row = arrayOf(
                                item.id.toString(),
                                item.itemType.name,
                                escapeCsvField(item.title),
                                escapeCsvField(item.itemData),
                                escapeCsvField(item.notes),
                                item.isFavorite.toString(),
                                escapeCsvField(item.imagePaths),
                                item.createdAt.time.toString(),
                                item.updatedAt.time.toString()
                            )
                            writer.write(row.joinToString(CSV_SEPARATOR))
                            writer.newLine()
                        } catch (e: Exception) {
                            android.util.Log.e("DataExport", "写入项失败: ${item.id}", e)
                        }
                    }
                }
            }
            Result.success("成功导出 ${items.size} 条数据")
        } catch (e: Exception) {
            android.util.Log.e("DataExport", "导出失败", e)
            Result.failure(Exception("导出失败：${e.message ?: "未知错误"}"))
        }
    }
}
