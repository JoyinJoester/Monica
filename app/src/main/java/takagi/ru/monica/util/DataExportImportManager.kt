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
            context.contentResolver.openOutputStream(outputUri)?.use { output ->
                BufferedWriter(OutputStreamWriter(output, Charsets.UTF_8)).use { writer ->
                    // 写入BOM标记，让Excel能正确识别UTF-8
                    writer.write("\uFEFF")
                    
                    // 写入列标题
                    writer.write(CSV_HEADERS.joinToString(CSV_SEPARATOR))
                    writer.newLine()
                    
                    // 写入数据行
                    items.forEach { item ->
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
                    }
                }
            } ?: return@withContext Result.failure(Exception("无法打开输出流"))

            Result.success("成功导出 ${items.size} 条数据")
        } catch (e: Exception) {
            Result.failure(e)
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
            
            context.contentResolver.openInputStream(inputUri)?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { reader ->
                    var firstLine = reader.readLine()
                    android.util.Log.d("DataImport", "第一行: $firstLine")
                    
                    // 跳过BOM标记（如果存在）
                    if (firstLine?.startsWith("\uFEFF") == true) {
                        firstLine = firstLine.substring(1)
                        android.util.Log.d("DataImport", "跳过BOM后: $firstLine")
                    }
                    
                    // 检测CSV格式
                    csvFormat = detectCsvFormat(firstLine ?: "")
                    android.util.Log.d("DataImport", "检测到格式: $csvFormat")
                    
                    // 如果第一行是标题，跳过它
                    val isHeader = when (csvFormat) {
                        CsvFormat.APP_EXPORT -> firstLine?.contains("Type") == true && 
                                               firstLine.contains("Title") && 
                                               firstLine.contains("Data")
                        CsvFormat.CHROME_PASSWORD -> firstLine?.contains("name") == true && 
                                                    firstLine.contains("url") && 
                                                    firstLine.contains("username") &&
                                                    firstLine.contains("password")
                        else -> false
                    }
                    
                    android.util.Log.d("DataImport", "是否为标题行: $isHeader")
                    
                    if (!isHeader && firstLine != null && firstLine.isNotBlank()) {
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
                        lineCount++
                        android.util.Log.d("DataImport", "读取第${lineCount}行: $line")
                        if (line!!.isNotBlank()) {
                            try {
                                val fields = parseCsvLine(line!!)
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
            } ?: return@withContext Result.failure(Exception("无法读取文件"))

            Result.success(items)
        } catch (e: Exception) {
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
        UNKNOWN
    }
    
    /**
     * 检测CSV格式
     */
    private fun detectCsvFormat(firstLine: String): CsvFormat {
        val lowerLine = firstLine.lowercase()
        return when {
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
                    fields.add(currentField.toString())
                    currentField.clear()
                }
                else -> {
                    currentField.append(char)
                }
            }
            i++
        }
        fields.add(currentField.toString())
        
        return fields
    }

    /**
     * 获取建议的导出文件名
     */
    fun getSuggestedFileName(): String {
        val timestamp = System.currentTimeMillis()
        return "monica_backup_${timestamp}${EXPORT_FILE_EXTENSION}"
    }
}
