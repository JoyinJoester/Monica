package takagi.ru.monica.ui.screens

import android.content.Context
import android.util.Log
import app.keemobile.kotpass.constants.BasicField
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.decode
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.modifiers.modifyParentGroup
import app.keemobile.kotpass.models.Entry
import app.keemobile.kotpass.models.EntryFields
import app.keemobile.kotpass.models.EntryValue
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.models.Group
import com.thegrizzlylabs.sardineandroid.Sardine
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.PasswordRepository
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * KeePass WebDAV ViewModel
 * 
 * 负责处理 WebDAV 连接和 .kdbx 文件的上传/下载操作
 */
class KeePassWebDavViewModel {
    
    companion object {
        private const val TAG = "KeePassWebDavVM"
        private const val PREFS_NAME = "keepass_webdav_config"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_KDBX_PASSWORD = "kdbx_password"  // KeePass 数据库密码
        
        // KeePass 文件夹名称
        private const val KEEPASS_FOLDER = "KeePass_Monica"
    }
    
    private var sardine: Sardine? = null
    private var serverUrl: String = ""
    private var username: String = ""
    private var password: String = ""
    
    /**
     * WebDAV 配置信息
     */
    data class KeePassWebDavConfig(
        val serverUrl: String,
        val username: String,
        val kdbxPassword: String = ""  // KeePass 数据库密码
    )
    
    /**
     * 从 SharedPreferences 加载已保存的配置
     */
    fun loadSavedConfig(context: Context): KeePassWebDavConfig? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_SERVER_URL, "") ?: ""
        val user = prefs.getString(KEY_USERNAME, "") ?: ""
        val pass = prefs.getString(KEY_PASSWORD, "") ?: ""
        val kdbxPass = prefs.getString(KEY_KDBX_PASSWORD, "") ?: ""
        
        if (url.isNotEmpty() && user.isNotEmpty() && pass.isNotEmpty()) {
            serverUrl = url
            username = user
            password = pass
            
            // 初始化 Sardine
            sardine = OkHttpSardine().apply {
                setCredentials(username, password)
            }
            
            Log.d(TAG, "Loaded saved config: url=$serverUrl, user=$username")
            return KeePassWebDavConfig(serverUrl, username, kdbxPass)
        }
        return null
    }
    
    /**
     * 保存 KeePass 数据库密码
     */
    fun saveKdbxPassword(context: Context, kdbxPassword: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_KDBX_PASSWORD, kdbxPassword).apply()
        Log.d(TAG, "Saved kdbx password")
    }
    
    /**
     * 获取保存的 KeePass 数据库密码
     */
    fun getSavedKdbxPassword(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_KDBX_PASSWORD, "") ?: ""
    }
    
    /**
     * 保存配置到 SharedPreferences
     */
    private fun saveConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString(KEY_SERVER_URL, serverUrl)
            putString(KEY_USERNAME, username)
            putString(KEY_PASSWORD, password)
            apply()
        }
        Log.d(TAG, "Saved config: url=$serverUrl, user=$username")
    }
    
    /**
     * 清除配置
     */
    fun clearConfig(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        
        serverUrl = ""
        username = ""
        password = ""
        sardine = null
        
        Log.d(TAG, "Config cleared")
    }
    
    /**
     * 配置并测试 WebDAV 连接
     */
    suspend fun configureAndTest(
        context: Context,
        url: String,
        user: String,
        pass: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            serverUrl = url.trimEnd('/')
            username = user
            password = pass
            
            // 创建 Sardine 实例
            sardine = OkHttpSardine().apply {
                setCredentials(username, password)
            }
            
            Log.d(TAG, "Testing connection to: $serverUrl")
            
            // 测试连接
            var connectionOk = false
            
            // 方法1: 使用 exists()
            try {
                val exists = sardine?.exists(serverUrl) ?: false
                Log.d(TAG, "Method 1 (exists): path exists = $exists")
                connectionOk = true
            } catch (e: Exception) {
                Log.w(TAG, "Method 1 (exists) failed: ${e.message}")
                
                // 方法2: 使用 list()
                try {
                    sardine?.list(serverUrl)
                    Log.d(TAG, "Method 2 (list): success")
                    connectionOk = true
                } catch (e2: Exception) {
                    Log.w(TAG, "Method 2 (list) failed: ${e2.message}")
                    throw e2
                }
            }
            
            if (connectionOk) {
                // 确保 KeePass 文件夹存在
                ensureKeePassFolder()
                
                // 保存配置
                saveConfig(context)
                
                Log.d(TAG, "Connection test SUCCESSFUL")
                Result.success(true)
            } else {
                Result.failure(Exception("连接测试失败"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test FAILED", e)
            
            val detailedMessage = when {
                e.message?.contains("Network is unreachable") == true -> 
                    "网络不可达，请检查网络连接"
                e.message?.contains("Connection timed out") == true -> 
                    "连接超时，请检查服务器地址"
                e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true -> 
                    "认证失败，请检查用户名和密码"
                e.message?.contains("404") == true -> 
                    "路径未找到，请检查服务器地址"
                e.message?.contains("403") == true -> 
                    "访问被拒绝，请检查权限"
                else -> "连接失败: ${e.message}"
            }
            Result.failure(Exception(detailedMessage))
        }
    }
    
    /**
     * 确保 KeePass 文件夹存在
     */
    private suspend fun ensureKeePassFolder() = withContext(Dispatchers.IO) {
        try {
            val folderPath = "$serverUrl/$KEEPASS_FOLDER"
            if (sardine?.exists(folderPath) != true) {
                sardine?.createDirectory(folderPath)
                Log.d(TAG, "Created KeePass folder: $folderPath")
            }
            Unit
        } catch (e: Exception) {
            Log.w(TAG, "Could not create KeePass folder: ${e.message}")
            // 不抛出异常，可能是文件夹已存在
        }
    }
    
    /**
     * 列出所有 .kdbx 文件
     */
    suspend fun listKdbxFiles(context: Context): Result<List<KdbxFileInfo>> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV 未配置"))
            }
            
            val folderPath = "$serverUrl/$KEEPASS_FOLDER"
            
            // 检查文件夹是否存在
            if (sardine?.exists(folderPath) != true) {
                // 尝试创建文件夹
                try {
                    sardine?.createDirectory(folderPath)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not create folder: ${e.message}")
                }
                return@withContext Result.success(emptyList())
            }
            
            // 列出目录内容
            val resources = sardine!!.list(folderPath)
            
            val kdbxFiles = resources
                .filter { !it.isDirectory && it.name.endsWith(".kdbx", ignoreCase = true) }
                .map { resource ->
                    KdbxFileInfo(
                        name = resource.name,
                        path = resource.href.toString(),
                        size = resource.contentLength ?: 0,
                        modified = resource.modified ?: Date()
                    )
                }
                .sortedByDescending { it.modified }
            
            Log.d(TAG, "Found ${kdbxFiles.size} .kdbx files")
            Result.success(kdbxFiles)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list kdbx files", e)
            Result.failure(e)
        }
    }
    
    // ==================== 本地 KDBX 导出和导入（不需要 WebDAV）====================
    
    /**
     * 导出数据为 .kdbx 格式到本地 OutputStream
     * 供导出页面使用，不需要 WebDAV 配置
     * 
     * @param context Android Context
     * @param outputStream 输出流
     * @param kdbxPassword 用于加密 .kdbx 文件的密码
     */
    suspend fun exportToLocalKdbx(
        context: Context,
        outputStream: OutputStream,
        kdbxPassword: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting local KDBX export...")
            exportToKdbxStream(context, outputStream, kdbxPassword)
            
            // 返回导出的条目数
            val database = PasswordDatabase.getDatabase(context)
            val passwordCount = database.passwordEntryDao().getAllPasswordEntriesSync().size
            val totpCount = database.secureItemDao().getActiveItemsByTypeSync(ItemType.TOTP).size
            val totalCount = passwordCount + totpCount
            
            Log.d(TAG, "Local KDBX export completed: $passwordCount passwords, $totpCount TOTP")
            Result.success(totalCount)
        } catch (e: Exception) {
            Log.e(TAG, "Local KDBX export failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从本地 InputStream 导入 .kdbx 文件
     * 供导入页面使用，不需要 WebDAV 配置
     * 
     * @param context Android Context
     * @param inputStream KDBX 文件输入流
     * @param kdbxPassword 用于解密 .kdbx 文件的密码
     * @return 导入的条目数量
     */
    suspend fun importFromLocalKdbx(
        context: Context,
        inputStream: InputStream,
        kdbxPassword: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting local KDBX import...")
            val importedCount = parseKdbxAndInsertToDb(context, inputStream, kdbxPassword)
            Log.d(TAG, "Local KDBX import completed: $importedCount entries")
            Result.success(importedCount)
        } catch (e: Exception) {
            Log.e(TAG, "Local KDBX import failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 导出数据为 .kdbx 格式并上传到 WebDAV
     * 
     * @param context Android Context
     * @param kdbxPassword 用于加密 .kdbx 文件的密码
     * @return 上传成功的文件名
     */
    suspend fun exportToKdbx(
        context: Context,
        kdbxPassword: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV 未配置"))
            }
            
            Log.d(TAG, "Starting export to kdbx...")
            
            // 生成文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "monica_export_$timestamp.kdbx"
            
            // 创建临时文件
            val tempFile = File(context.cacheDir, fileName)
            
            try {
                // TODO: 实现实际的 KDBX 导出逻辑
                // 这里需要调用 exportToKdbx(outputStream, kdbxPassword) 来生成 .kdbx 文件
                // 目前先创建一个占位文件用于测试 WebDAV 上传流程
                
                tempFile.outputStream().use { outputStream ->
                    exportToKdbxStream(context, outputStream, kdbxPassword)
                }
                
                Log.d(TAG, "Temp file created: ${tempFile.length()} bytes")
                
                // 确保文件夹存在
                ensureKeePassFolder()
                
                // 上传到 WebDAV
                val remotePath = "$serverUrl/$KEEPASS_FOLDER/$fileName"
                val fileBytes = tempFile.readBytes()
                
                sardine!!.put(remotePath, fileBytes, "application/octet-stream")
                
                Log.d(TAG, "Uploaded successfully: $fileName")
                Result.success(fileName)
            } finally {
                // 清理临时文件
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Export failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 从 WebDAV 下载 .kdbx 文件并导入
     * 
     * @param context Android Context
     * @param file 要下载的文件信息
     * @param kdbxPassword 用于解密 .kdbx 文件的密码
     * @return 导入的条目数量
     */
    suspend fun importFromKdbx(
        context: Context,
        file: KdbxFileInfo,
        kdbxPassword: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV 未配置"))
            }
            
            Log.d(TAG, "Starting import from: ${file.name}")
            
            // 下载文件
            val remotePath = "$serverUrl/$KEEPASS_FOLDER/${file.name}"
            val tempFile = File(context.cacheDir, "import_${file.name}")
            
            try {
                sardine!!.get(remotePath).use { inputStream ->
                    tempFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                
                Log.d(TAG, "Downloaded: ${tempFile.length()} bytes")
                
                // TODO: 实现实际的 KDBX 解析和导入逻辑
                // 这里需要调用 parseKdbxAndInsertToDb(inputStream, kdbxPassword) 来解析和导入
                
                val importedCount = tempFile.inputStream().use { inputStream ->
                    parseKdbxAndInsertToDb(context, inputStream, kdbxPassword)
                }
                
                Log.d(TAG, "Imported $importedCount entries")
                Result.success(importedCount)
            } finally {
                // 清理临时文件
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * 下载 .kdbx 文件流
     * 
     * @param file 要下载的文件信息
     * @return 文件输入流
     */
    suspend fun downloadKdbxStream(file: KdbxFileInfo): Result<InputStream> = withContext(Dispatchers.IO) {
        try {
            if (sardine == null) {
                return@withContext Result.failure(Exception("WebDAV 未配置"))
            }
            
            val remotePath = "$serverUrl/$KEEPASS_FOLDER/${file.name}"
            val inputStream = sardine!!.get(remotePath)
            
            Log.d(TAG, "Downloaded stream for: ${file.name}")
            Result.success(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download stream", e)
            Result.failure(e)
        }
    }
    
    // ==================== KDBX 导出和导入实现 ====================
    
    /**
     * 将本地数据导出为 KDBX 格式
     * 使用 kotpass 库生成真正的 KeePass 数据库文件
     * 
     * @param context Android Context
     * @param outputStream 输出流
     * @param kdbxPassword 数据库密码
     */
    private suspend fun exportToKdbxStream(
        context: Context,
        outputStream: OutputStream,
        kdbxPassword: String
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "exportToKdbxStream: Starting KDBX export")
        
        // 1. 从数据库读取所有密码条目
        val database = PasswordDatabase.getDatabase(context)
        val passwordDao = database.passwordEntryDao()
        val secureItemDao = database.secureItemDao()
        val passwords = passwordDao.getAllPasswordEntriesSync()
        
        // 获取所有 TOTP 验证器条目
        val totpItems = secureItemDao.getActiveItemsByTypeSync(ItemType.TOTP)
        
        // 获取 SecurityManager 用于解密密码
        val securityManager = takagi.ru.monica.security.SecurityManager(context)
        
        Log.d(TAG, "Found ${passwords.size} password entries and ${totpItems.size} TOTP entries to export")
        
        // 2. 创建凭证
        val credentials = Credentials.from(EncryptedValue.fromString(kdbxPassword))
        
        // 3. 创建密码 KeePass 条目列表
        val passwordEntries = passwords.map { password ->
            // 解密密码 - 数据库中存储的是加密后的密码
            val decryptedPassword = try {
                securityManager.decryptData(password.password)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to decrypt password for ${password.title}: ${e.message}")
                password.password // 如果解密失败，使用原始值
            }
            
            Entry(
                uuid = UUID.randomUUID(),
                fields = EntryFields.of(
                    BasicField.Title() to EntryValue.Plain(password.title),
                    BasicField.UserName() to EntryValue.Plain(password.username),
                    BasicField.Password() to EntryValue.Encrypted(
                        EncryptedValue.fromString(decryptedPassword)
                    ),
                    BasicField.Url() to EntryValue.Plain(password.website),
                    BasicField.Notes() to EntryValue.Plain(buildString {
                        if (password.notes.isNotEmpty()) {
                            append(password.notes)
                        }
                        if (password.email.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("Email: ${password.email}")
                        }
                        if (password.phone.isNotEmpty()) {
                            if (isNotEmpty()) append("\n")
                            append("Phone: ${password.phone}")
                        }
                    })
                )
            )
        }
        
        // 4. 创建 TOTP KeePass 条目列表
        val totpEntries = totpItems.mapNotNull { item ->
            try {
                val totpData = Json.decodeFromString<TotpData>(item.itemData)
                
                // 解密 secret
                val decryptedSecret = try {
                    securityManager.decryptData(totpData.secret)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to decrypt TOTP secret for ${item.title}: ${e.message}")
                    totpData.secret
                }
                
                // 构建 otpauth:// URI (KeePass 标准 TOTP 格式)
                val otpUri = buildOtpAuthUri(
                    secret = decryptedSecret,
                    issuer = totpData.issuer.ifEmpty { item.title },
                    accountName = totpData.accountName,
                    algorithm = totpData.algorithm,
                    digits = totpData.digits,
                    period = totpData.period
                )
                
                Entry(
                    uuid = UUID.randomUUID(),
                    fields = EntryFields.of(
                        BasicField.Title() to EntryValue.Plain(item.title),
                        BasicField.UserName() to EntryValue.Plain(totpData.accountName),
                        BasicField.Password() to EntryValue.Encrypted(
                            EncryptedValue.fromString("")
                        ),
                        BasicField.Url() to EntryValue.Plain(totpData.link),
                        BasicField.Notes() to EntryValue.Plain(item.notes),
                        // KeePass TOTP 标准字段 - otp
                        "otp" to EntryValue.Plain(otpUri),
                        // 备用字段 - 一些 KeePass 插件使用这些
                        "TOTP Seed" to EntryValue.Plain(decryptedSecret),
                        "TOTP Settings" to EntryValue.Plain("${totpData.period};${totpData.digits}")
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse TOTP data for ${item.title}: ${e.message}")
                null
            }
        }
        
        // 5. 创建分组结构
        val passwordGroup = Group(
            uuid = UUID.randomUUID(),
            name = "密码",
            entries = passwordEntries
        )
        
        val totpGroup = Group(
            uuid = UUID.randomUUID(),
            name = "验证器",
            entries = totpEntries
        )
        
        // 6. 创建 KeePass 数据库
        val meta = Meta(
            generator = "Monica Password Manager",
            name = "Monica Export"
        )
        
        val keePassDatabase = KeePassDatabase.Ver4x.create(
            rootName = "Monica",
            meta = meta,
            credentials = credentials
        ).modifyParentGroup {
            copy(groups = listOf(passwordGroup, totpGroup))
        }
        
        // 7. 编码并写入输出流
        keePassDatabase.encode(outputStream)
        
        Log.d(TAG, "KDBX export completed successfully with ${passwords.size} passwords and ${totpItems.size} TOTP entries")
    }
    
    /**
     * 构建 otpauth:// URI
     */
    private fun buildOtpAuthUri(
        secret: String,
        issuer: String,
        accountName: String,
        algorithm: String,
        digits: Int,
        period: Int
    ): String {
        val label = if (issuer.isNotEmpty() && accountName.isNotEmpty()) {
            "${URLEncoder.encode(issuer, "UTF-8")}:${URLEncoder.encode(accountName, "UTF-8")}"
        } else if (accountName.isNotEmpty()) {
            URLEncoder.encode(accountName, "UTF-8")
        } else {
            URLEncoder.encode(issuer, "UTF-8")
        }
        
        return buildString {
            append("otpauth://totp/")
            append(label)
            append("?secret=")
            append(secret.replace(" ", "").uppercase())
            if (issuer.isNotEmpty()) {
                append("&issuer=")
                append(URLEncoder.encode(issuer, "UTF-8"))
            }
            if (algorithm != "SHA1") {
                append("&algorithm=")
                append(algorithm)
            }
            if (digits != 6) {
                append("&digits=")
                append(digits)
            }
            if (period != 30) {
                append("&period=")
                append(period)
            }
        }
    }
    
    /**
     * 解析 KDBX 文件并导入到数据库
     * 支持密码和 TOTP 验证器的导入
     * 
     * @param context Android Context
     * @param inputStream KDBX 文件输入流
     * @param kdbxPassword 数据库密码
     * @return 导入的条目数量（密码 + TOTP）
     */
    private suspend fun parseKdbxAndInsertToDb(
        context: Context,
        inputStream: InputStream,
        kdbxPassword: String
    ): Int = withContext(Dispatchers.IO) {
        Log.d(TAG, "parseKdbxAndInsertToDb: Starting KDBX import")
        
        try {
            // 1. 创建凭证
            val credentials = Credentials.from(EncryptedValue.fromString(kdbxPassword))
            
            // 2. 解码 KDBX 文件
            val keePassDatabase = KeePassDatabase.decode(inputStream, credentials)
            
            // 3. 获取所有条目
            val allEntries = mutableListOf<Entry>()
            collectEntries(keePassDatabase.content.group, allEntries)
            
            Log.d(TAG, "Found ${allEntries.size} entries in KDBX file")
            
            // 4. 准备数据库和安全管理器
            val database = PasswordDatabase.getDatabase(context)
            val passwordDao = database.passwordEntryDao()
            val secureItemDao = database.secureItemDao()
            val securityManager = takagi.ru.monica.security.SecurityManager(context)
            
            var passwordImportedCount = 0
            var totpImportedCount = 0
            
            allEntries.forEach { entry ->
                try {
                    // 安全获取字段值的辅助函数
                    fun getFieldValue(key: String): String {
                        return try {
                            entry.fields[key]?.content ?: ""
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to get field '$key': ${e.message}")
                            ""
                        }
                    }
                    
                    val title = getFieldValue("Title")
                    val username = getFieldValue("UserName")
                    val password = getFieldValue("Password")
                    val url = getFieldValue("URL")
                    val notes = getFieldValue("Notes")
                    
                    // 检查是否是 TOTP 条目（检查 otp 字段或 TOTP Seed 字段）
                    val otpField = getFieldValue("otp")
                    val totpSeed = getFieldValue("TOTP Seed")
                    val totpSettings = getFieldValue("TOTP Settings")
                    
                    if (otpField.isNotEmpty() || totpSeed.isNotEmpty()) {
                        // 这是一个 TOTP 条目
                        val totpData = if (otpField.startsWith("otpauth://")) {
                            parseOtpAuthUri(otpField, securityManager)
                        } else if (totpSeed.isNotEmpty()) {
                            // 使用 TOTP Seed 和 TOTP Settings
                            val (period, digits) = parseTotpSettings(totpSettings)
                            TotpData(
                                secret = securityManager.encryptData(totpSeed),
                                issuer = title,
                                accountName = username,
                                period = period,
                                digits = digits,
                                algorithm = "SHA1",
                                link = url
                            )
                        } else {
                            null
                        }
                        
                        if (totpData != null) {
                            // 检查是否重复
                            val existingItem = secureItemDao.findDuplicateItem(ItemType.TOTP, title)
                            if (existingItem != null) {
                                Log.d(TAG, "Skipping duplicate TOTP: $title")
                                return@forEach
                            }
                            
                            val secureItem = SecureItem(
                                itemType = ItemType.TOTP,
                                title = title.ifEmpty { totpData.issuer },
                                notes = notes,
                                itemData = Json.encodeToString(TotpData.serializer(), totpData),
                                createdAt = Date(),
                                updatedAt = Date()
                            )
                            
                            secureItemDao.insertItem(secureItem)
                            totpImportedCount++
                            Log.d(TAG, "Imported TOTP: $title")
                        }
                    } else if (title.isNotEmpty() || username.isNotEmpty() || password.isNotEmpty()) {
                        // 这是一个普通密码条目
                        // 检查是否重复
                        val isDuplicate = passwordDao.countByTitleUsernameWebsite(title, username, url) > 0
                        if (isDuplicate) {
                            Log.d(TAG, "Skipping duplicate password: $title")
                            return@forEach
                        }
                        
                        // 加密密码
                        val encryptedPassword = securityManager.encryptData(password)
                        
                        val passwordEntry = takagi.ru.monica.data.PasswordEntry(
                            title = title,
                            username = username,
                            password = encryptedPassword,
                            website = url,
                            notes = notes,
                            createdAt = Date(),
                            updatedAt = Date()
                        )
                        
                        passwordDao.insertPasswordEntry(passwordEntry)
                        passwordImportedCount++
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to import entry: ${e.message}")
                }
            }
            
            val totalImported = passwordImportedCount + totpImportedCount
            Log.d(TAG, "KDBX import completed: $passwordImportedCount passwords, $totpImportedCount TOTP entries")
            totalImported
        } catch (e: Exception) {
            Log.e(TAG, "KDBX parsing failed", e)
            when {
                e.message?.contains("Invalid credentials") == true ||
                e.message?.contains("Wrong password") == true ||
                e.message?.contains("InvalidKey") == true -> 
                    throw Exception("密码错误，无法解密数据库")
                e.message?.contains("Invalid database") == true ||
                e.message?.contains("Not a valid KDBX") == true ->
                    throw Exception("无效的 KeePass 数据库文件")
                else -> throw Exception("导入失败: ${e.message}")
            }
        }
    }
    
    /**
     * 递归收集所有组中的条目
     */
    private fun collectEntries(group: Group, entries: MutableList<Entry>) {
        entries.addAll(group.entries)
        group.groups.forEach { subGroup ->
            collectEntries(subGroup, entries)
        }
    }
    
    /**
     * 解析 otpauth:// URI 并返回 TotpData
     * 格式: otpauth://totp/LABEL?secret=SECRET&issuer=ISSUER&algorithm=SHA1&digits=6&period=30
     */
    private fun parseOtpAuthUri(
        uri: String,
        securityManager: takagi.ru.monica.security.SecurityManager
    ): TotpData? {
        try {
            if (!uri.startsWith("otpauth://")) {
                return null
            }
            
            val url = java.net.URI(uri)
            val type = url.host // totp 或 hotp
            if (type != "totp") {
                Log.w(TAG, "Only TOTP is supported, got: $type")
                // 仍然尝试解析
            }
            
            // 解析 label (path 部分)
            val path = java.net.URLDecoder.decode(url.path.trimStart('/'), "UTF-8")
            val (issuer, accountName) = if (path.contains(":")) {
                val parts = path.split(":", limit = 2)
                parts[0] to parts[1]
            } else {
                "" to path
            }
            
            // 解析查询参数
            val params = mutableMapOf<String, String>()
            url.query?.split("&")?.forEach { param ->
                val parts = param.split("=", limit = 2)
                if (parts.size == 2) {
                    params[parts[0].lowercase()] = java.net.URLDecoder.decode(parts[1], "UTF-8")
                }
            }
            
            val secret = params["secret"] ?: return null
            val finalIssuer = params["issuer"] ?: issuer
            val algorithm = params["algorithm"]?.uppercase() ?: "SHA1"
            val digits = params["digits"]?.toIntOrNull() ?: 6
            val period = params["period"]?.toIntOrNull() ?: 30
            
            // 加密 secret
            val encryptedSecret = securityManager.encryptData(secret)
            
            return TotpData(
                secret = encryptedSecret,
                issuer = finalIssuer,
                accountName = accountName,
                algorithm = algorithm,
                digits = digits,
                period = period
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse otpauth URI: $uri", e)
            return null
        }
    }
    
    /**
     * 解析 TOTP Settings 字段
     * 格式: "period;digits" 例如 "30;6"
     */
    private fun parseTotpSettings(settings: String): Pair<Int, Int> {
        return try {
            val parts = settings.split(";")
            val period = parts.getOrNull(0)?.toIntOrNull() ?: 30
            val digits = parts.getOrNull(1)?.toIntOrNull() ?: 6
            period to digits
        } catch (e: Exception) {
            30 to 6
        }
    }
}
