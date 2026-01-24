package takagi.ru.monica.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.PasswordEntry
import java.util.UUID
import takagi.ru.monica.security.SecurityManager
import java.io.File
import java.io.FileOutputStream
import java.time.Instant

/**
 * 本地 KeePass 数据库管理 ViewModel
 */
class LocalKeePassViewModel(
    application: Application,
    private val dao: LocalKeePassDatabaseDao,
    private val securityManager: SecurityManager
) : AndroidViewModel(application) {
    
    private val context: Context get() = getApplication()
    
    /** 所有数据库列表 */
    val allDatabases: StateFlow<List<LocalKeePassDatabase>> = dao.getAllDatabases()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /** 内部数据库列表 */
    val internalDatabases: StateFlow<List<LocalKeePassDatabase>> = 
        dao.getDatabasesByLocation(KeePassStorageLocation.INTERNAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /** 外部数据库列表 */
    val externalDatabases: StateFlow<List<LocalKeePassDatabase>> = 
        dao.getDatabasesByLocation(KeePassStorageLocation.EXTERNAL)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    
    /** 操作状态 */
    private val _operationState = MutableStateFlow<OperationState>(OperationState.Idle)
    val operationState: StateFlow<OperationState> = _operationState.asStateFlow()
    
    /** 当前选中的数据库 */
    private val _selectedDatabase = MutableStateFlow<LocalKeePassDatabase?>(null)
    val selectedDatabase: StateFlow<LocalKeePassDatabase?> = _selectedDatabase.asStateFlow()
    
    /**
     * 创建新的 KeePass 数据库
     */
    fun createDatabase(
        name: String,
        password: String,
        storageLocation: KeePassStorageLocation,
        externalUri: Uri? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在创建数据库...")
            
            try {
                withContext(Dispatchers.IO) {
                    // 加密密码
                    val encryptedPassword = securityManager.encryptData(password)
                    
                    // 生成文件名
                    val fileName = "${name.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")}.kdbx"
                    
                    val filePath: String
                    
                    if (storageLocation == KeePassStorageLocation.INTERNAL) {
                        // 创建内部存储目录
                        val keepassDir = File(context.filesDir, "keepass")
                        if (!keepassDir.exists()) {
                            keepassDir.mkdirs()
                        }
                        
                        // 创建空的 kdbx 文件（实际应该用 KeePass 库创建）
                        val dbFile = File(keepassDir, fileName)
                        createEmptyKdbxFile(dbFile, password)
                        
                        filePath = "keepass/$fileName"
                    } else {
                        // 外部存储
                        if (externalUri == null) {
                            throw IllegalArgumentException("外部存储需要指定保存位置")
                        }
                        
                        // 使用 DocumentFile 创建文件
                        val docFile = DocumentFile.fromTreeUri(context, externalUri)
                        val newFile = docFile?.createFile("application/octet-stream", fileName)
                        
                        if (newFile?.uri != null) {
                            context.contentResolver.openOutputStream(newFile.uri)?.use { output ->
                                createEmptyKdbxContent(password).let { content ->
                                    output.write(content)
                                }
                            }
                            filePath = newFile.uri.toString()
                        } else {
                            throw Exception("无法在指定位置创建文件")
                        }
                    }
                    
                    // 保存数据库信息
                    val database = LocalKeePassDatabase(
                        name = name,
                        filePath = filePath,
                        storageLocation = storageLocation,
                        encryptedPassword = encryptedPassword,
                        description = description,
                        isDefault = allDatabases.value.isEmpty()
                    )
                    
                    dao.insertDatabase(database)
                }
                
                _operationState.value = OperationState.Success("数据库创建成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("创建失败: ${e.message}")
            }
        }
    }
    
    /**
     * 导入外部 KeePass 数据库（添加引用，不复制文件）
     */
    fun importExternalDatabase(
        name: String,
        uri: Uri,
        password: String,
        description: String? = null
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在添加数据库...")
            
            try {
                withContext(Dispatchers.IO) {
                    // 验证文件是否可访问
                    context.contentResolver.openInputStream(uri)?.close()
                        ?: throw Exception("无法访问文件")
                    
                    // 加密密码
                    val encryptedPassword = securityManager.encryptData(password)
                    
                    // 获取持久化 URI 权限
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                    
                    val database = LocalKeePassDatabase(
                        name = name,
                        filePath = uri.toString(),
                        storageLocation = KeePassStorageLocation.EXTERNAL,
                        encryptedPassword = encryptedPassword,
                        description = description,
                        isDefault = allDatabases.value.isEmpty()
                    )
                    
                    dao.insertDatabase(database)
                }
                
                _operationState.value = OperationState.Success("数据库添加成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("添加失败: ${e.message}")
            }
        }
    }
    
    /**
     * 复制外部数据库到内部存储
     */
    fun copyToInternal(databaseId: Long) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在复制到内部存储...")
            
            try {
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    
                    if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                        throw Exception("数据库已在内部存储")
                    }
                    
                    val externalUri = Uri.parse(database.filePath)
                    
                    // 创建内部目录
                    val keepassDir = File(context.filesDir, "keepass")
                    if (!keepassDir.exists()) {
                        keepassDir.mkdirs()
                    }
                    
                    // 复制文件
                    val fileName = "${database.name.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")}.kdbx"
                    val internalFile = File(keepassDir, fileName)
                    
                    context.contentResolver.openInputStream(externalUri)?.use { input ->
                        FileOutputStream(internalFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    // 创建新的内部数据库记录
                    val newDatabase = database.copy(
                        id = 0,
                        name = "${database.name} (内部)",
                        filePath = "keepass/$fileName",
                        storageLocation = KeePassStorageLocation.INTERNAL,
                        createdAt = System.currentTimeMillis()
                    )
                    
                    dao.insertDatabase(newDatabase)
                }
                
                _operationState.value = OperationState.Success("已复制到内部存储")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("复制失败: ${e.message}")
            }
        }
    }
    
    /**
     * 导出内部数据库到外部存储
     */
    fun exportToExternal(databaseId: Long, destinationUri: Uri) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在导出...")
            
            try {
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    
                    if (database.storageLocation != KeePassStorageLocation.INTERNAL) {
                        throw Exception("只能导出内部数据库")
                    }
                    
                    val internalFile = File(context.filesDir, database.filePath)
                    if (!internalFile.exists()) {
                        throw Exception("数据库文件不存在")
                    }
                    
                    // 导出到目标位置
                    context.contentResolver.openOutputStream(destinationUri)?.use { output ->
                        internalFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                }
                
                _operationState.value = OperationState.Success("导出成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("导出失败: ${e.message}")
            }
        }
    }
    
    /**
     * 转移数据库位置（内部 <-> 外部）
     * 与导入/导出不同，这会改变数据库的实际存储位置
     */
    fun transferDatabase(
        databaseId: Long,
        targetLocation: KeePassStorageLocation,
        targetUri: Uri? = null
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading(
                if (targetLocation == KeePassStorageLocation.EXTERNAL) 
                    "正在转移到外部存储..." 
                else 
                    "正在转移到内部存储..."
            )
            
            try {
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    
                    if (database.storageLocation == targetLocation) {
                        throw Exception("数据库已在目标位置")
                    }
                    
                    val newPath: String
                    
                    if (targetLocation == KeePassStorageLocation.EXTERNAL) {
                        // 内部 -> 外部
                        if (targetUri == null) {
                            throw Exception("需要指定目标位置")
                        }
                        
                        val internalFile = File(context.filesDir, database.filePath)
                        if (!internalFile.exists()) {
                            throw Exception("源文件不存在")
                        }
                        
                        // 复制到外部
                        context.contentResolver.openOutputStream(targetUri)?.use { output ->
                            internalFile.inputStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        
                        // 获取持久化权限
                        context.contentResolver.takePersistableUriPermission(
                            targetUri,
                            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        
                        // 删除内部文件
                        internalFile.delete()
                        
                        newPath = targetUri.toString()
                    } else {
                        // 外部 -> 内部
                        val externalUri = Uri.parse(database.filePath)
                        
                        // 创建内部目录
                        val keepassDir = File(context.filesDir, "keepass")
                        if (!keepassDir.exists()) {
                            keepassDir.mkdirs()
                        }
                        
                        val fileName = "${database.name.replace(Regex("[^a-zA-Z0-9\\u4e00-\\u9fa5]"), "_")}.kdbx"
                        val internalFile = File(keepassDir, fileName)
                        
                        // 复制到内部
                        context.contentResolver.openInputStream(externalUri)?.use { input ->
                            FileOutputStream(internalFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        
                        newPath = "keepass/$fileName"
                    }
                    
                    // 更新数据库记录
                    dao.updateStorageLocation(databaseId, targetLocation, newPath)
                }
                
                _operationState.value = OperationState.Success("转移成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("转移失败: ${e.message}")
            }
        }
    }
    
    /**
     * 删除数据库
     */
    fun deleteDatabase(databaseId: Long, deleteFile: Boolean = false) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在删除...")
            
            try {
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    
                    if (deleteFile) {
                        if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                            val file = File(context.filesDir, database.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                        // 外部文件不删除，只移除引用
                    }
                    
                    dao.deleteDatabaseById(databaseId)
                }
                
                _operationState.value = OperationState.Success("已删除")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("删除失败: ${e.message}")
            }
        }
    }
    
    /**
     * 更新数据库密码
     */
    fun updatePassword(databaseId: Long, newPassword: String) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    
                    val encryptedPassword = securityManager.encryptData(newPassword)
                    dao.updateDatabase(database.copy(encryptedPassword = encryptedPassword))
                }
                
                _operationState.value = OperationState.Success("密码已更新")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("更新失败: ${e.message}")
            }
        }
    }
    
    /**
     * 设为默认数据库
     */
    fun setAsDefault(databaseId: Long) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    dao.clearDefaultDatabase()
                    dao.setDefaultDatabase(databaseId)
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("设置失败: ${e.message}")
            }
        }
    }
    
    /**
     * 将密码条目添加到 KeePass 数据库的 .kdbx 文件中
     * @param databaseId 目标 KeePass 数据库 ID
     * @param entries 要添加的密码条目列表（已解密的密码）
     * @return Result 表示操作结果
     */
    suspend fun addPasswordEntriesToKdbx(
        databaseId: Long,
        entries: List<PasswordEntry>,
        decryptPassword: (String) -> String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = dao.getDatabaseById(databaseId)
                ?: return@withContext Result.failure(Exception("数据库不存在"))
            
            // 检查数据库密码是否存在
            val encryptedDbPassword = database.encryptedPassword
                ?: return@withContext Result.failure(Exception("数据库密码未设置"))
            
            // 解密数据库密码
            val kdbxPassword = securityManager.decryptData(encryptedDbPassword)
                ?: return@withContext Result.failure(Exception("无法解密数据库密码"))
            val credentials = Credentials.from(EncryptedValue.fromString(kdbxPassword))
            
            // 读取现有的 kdbx 文件
            val keePassDatabase = if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                val file = File(context.filesDir, database.filePath)
                if (!file.exists()) {
                    return@withContext Result.failure(Exception("数据库文件不存在"))
                }
                file.inputStream().use { input ->
                    KeePassDatabase.decode(input, credentials)
                }
            } else {
                val uri = Uri.parse(database.filePath)
                
                // 检查并尝试获取持久化权限
                try {
                    // 尝试重新获取持久化权限（如果之前已获取则不会报错）
                    val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                   android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (e: SecurityException) {
                        // 权限已存在或无法获取，继续尝试打开
                    }
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        KeePassDatabase.decode(input, credentials)
                    } ?: return@withContext Result.failure(Exception("无法打开数据库文件"))
                } catch (e: SecurityException) {
                    return@withContext Result.failure(Exception("没有文件访问权限，请重新打开数据库"))
                }
            }
            
            // 将密码条目转换为 KeePass Entry
            val newEntries = entries.map { entry ->
                val decryptedPassword = try {
                    decryptPassword(entry.password)
                } catch (e: Exception) {
                    entry.password // 如果解密失败，使用原始值
                }
                
                Entry(
                    uuid = UUID.randomUUID(),
                    fields = EntryFields.of(
                        BasicField.Title() to EntryValue.Plain(entry.title),
                        BasicField.UserName() to EntryValue.Plain(entry.username),
                        BasicField.Password() to EntryValue.Encrypted(
                            EncryptedValue.fromString(decryptedPassword)
                        ),
                        BasicField.Url() to EntryValue.Plain(entry.website),
                        BasicField.Notes() to EntryValue.Plain(buildString {
                            if (entry.notes.isNotEmpty()) {
                                append(entry.notes)
                            }
                            if (entry.email.isNotEmpty()) {
                                if (isNotEmpty()) append("\n")
                                append("Email: ${entry.email}")
                            }
                            if (entry.phone.isNotEmpty()) {
                                if (isNotEmpty()) append("\n")
                                append("Phone: ${entry.phone}")
                            }
                        })
                    )
                )
            }
            
            // 将新条目添加到根组
            val existingEntries = keePassDatabase.content.group.entries
            val updatedDatabase = keePassDatabase.modifyParentGroup {
                copy(entries = existingEntries + newEntries)
            }
            
            // 保存更新后的数据库
            if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                val file = File(context.filesDir, database.filePath)
                FileOutputStream(file).use { output ->
                    updatedDatabase.encode(output)
                }
            } else {
                val uri = Uri.parse(database.filePath)
                try {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        updatedDatabase.encode(output)
                    } ?: return@withContext Result.failure(Exception("无法写入数据库文件"))
                } catch (e: SecurityException) {
                    return@withContext Result.failure(Exception("没有文件写入权限，请重新打开数据库"))
                }
            }
            
            // 更新条目计数
            val newCount = (database.entryCount ?: 0) + entries.size
            dao.updateEntryCount(databaseId, newCount)
            
            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 清除操作状态
     */
    fun clearOperationState() {
        _operationState.value = OperationState.Idle
    }
    
    // === 私有辅助方法 ===
    
    /**
     * 使用 kotpass 库创建真正的 KDBX 格式数据库文件
     */
    private fun createEmptyKdbxFile(file: File, password: String) {
        // 创建凭据
        val credentials = Credentials.from(EncryptedValue.fromString(password))
        
        // 创建元数据
        val meta = Meta(
            generator = "Monica Password Manager",
            name = file.nameWithoutExtension
        )
        
        // 创建空的 KeePass 数据库
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = meta,
            credentials = credentials
        )
        
        // 写入文件
        FileOutputStream(file).use { output ->
            database.encode(output)
        }
    }
    
    /**
     * 使用 kotpass 库创建真正的 KDBX 格式数据库内容
     */
    private fun createEmptyKdbxContent(password: String): ByteArray {
        // 创建凭据
        val credentials = Credentials.from(EncryptedValue.fromString(password))
        
        // 创建元数据
        val meta = Meta(
            generator = "Monica Password Manager",
            name = "Monica Database"
        )
        
        // 创建空的 KeePass 数据库
        val database = KeePassDatabase.Ver4x.create(
            rootName = "Root",
            meta = meta,
            credentials = credentials
        )
        
        // 返回字节数组
        return java.io.ByteArrayOutputStream().use { output ->
            database.encode(output)
            output.toByteArray()
        }
    }
    
    /**
     * 操作状态
     */
    sealed class OperationState {
        object Idle : OperationState()
        data class Loading(val message: String) : OperationState()
        data class Success(val message: String) : OperationState()
        data class Error(val message: String) : OperationState()
    }
}
