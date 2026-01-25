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
import app.keemobile.kotpass.models.Group
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date
import kotlin.math.abs

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

    private val _syncVersion = MutableStateFlow(0L)
    val syncVersion: StateFlow<Long> = _syncVersion.asStateFlow()
    
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
    ): Result<Int> = upsertPasswordEntriesToKdbx(databaseId, entries, decryptPassword)

    suspend fun readPasswordEntriesFromKdbx(databaseId: Long): Result<List<PasswordEntry>> = withContext(Dispatchers.IO) {
        try {
            val database = dao.getDatabaseById(databaseId)
                ?: return@withContext Result.failure(Exception("数据库不存在"))

            val credentials = getCredentials(database)
                ?: return@withContext Result.failure(Exception("数据库密码未设置"))

            val keePassDatabase = loadKeePassDatabase(database, credentials)
            val allEntries = mutableListOf<Entry>()
            collectEntries(keePassDatabase.content.group, allEntries)

            val mappedEntries = allEntries.mapNotNull { entry ->
                val title = getEntryField(entry, "Title")
                val username = getEntryField(entry, "UserName")
                val password = getEntryField(entry, "Password")
                val url = getEntryField(entry, "URL")
                val notes = getEntryField(entry, "Notes")

                if (title.isBlank() && username.isBlank() && password.isBlank()) {
                    return@mapNotNull null
                }

                PasswordEntry(
                    id = buildExternalEntryId(entry),
                    title = title,
                    website = url,
                    username = username,
                    password = password,
                    notes = notes,
                    keepassDatabaseId = databaseId,
                    createdAt = Date(),
                    updatedAt = Date()
                )
            }

            dao.updateLastAccessedTime(databaseId)
            dao.updateEntryCount(databaseId, mappedEntries.size)

            Result.success(mappedEntries)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun upsertPasswordEntriesToKdbx(
        databaseId: Long,
        entries: List<PasswordEntry>,
        decryptPassword: (String) -> String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = dao.getDatabaseById(databaseId)
                ?: return@withContext Result.failure(Exception("数据库不存在"))

            val credentials = getCredentials(database)
                ?: return@withContext Result.failure(Exception("数据库密码未设置"))

            val keePassDatabase = loadKeePassDatabase(database, credentials)
            val pendingEntries = entries.toMutableList()

            val updatedRoot = updateEntriesInGroup(keePassDatabase.content.group) { existing ->
                val matchIndex = pendingEntries.indexOfFirst { matchesEntry(existing, it) }
                if (matchIndex >= 0) {
                    val localEntry = pendingEntries.removeAt(matchIndex)
                    val decryptedPassword = try {
                        decryptPassword(localEntry.password)
                    } catch (e: Exception) {
                        localEntry.password
                    }
                    val newEntry = buildKeePassEntry(localEntry, decryptedPassword)
                    existing.copy(fields = newEntry.fields)
                } else {
                    existing
                }
            }

            val additionalEntries = pendingEntries.map { entry ->
                val decryptedPassword = try {
                    decryptPassword(entry.password)
                } catch (e: Exception) {
                    entry.password
                }
                buildKeePassEntry(entry, decryptedPassword)
            }

            val finalRoot = updatedRoot.copy(entries = updatedRoot.entries + additionalEntries)
            val updatedDatabase = keePassDatabase.modifyParentGroup {
                finalRoot
            }

            val writeResult = writeKeePassDatabase(database, updatedDatabase, credentials)
            if (writeResult.isFailure) {
                return@withContext Result.failure(writeResult.exceptionOrNull() ?: Exception("写入失败"))
            }

            dao.updateEntryCount(databaseId, countEntries(updatedDatabase.content.group))
            dao.updateLastSyncedTime(databaseId)
            bumpSyncVersion()

            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePasswordEntriesFromKdbx(
        databaseId: Long,
        entries: List<PasswordEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = dao.getDatabaseById(databaseId)
                ?: return@withContext Result.failure(Exception("数据库不存在"))

            val credentials = getCredentials(database)
                ?: return@withContext Result.failure(Exception("数据库密码未设置"))

            val keePassDatabase = loadKeePassDatabase(database, credentials)
            val pendingEntries = entries.toMutableList()
            var deletedCount = 0

            val updatedRoot = updateEntriesInGroup(keePassDatabase.content.group) { existing ->
                val matchIndex = pendingEntries.indexOfFirst { matchesEntry(existing, it) }
                if (matchIndex >= 0) {
                    pendingEntries.removeAt(matchIndex)
                    deletedCount += 1
                    null
                } else {
                    existing
                }
            }

            val updatedDatabase = keePassDatabase.modifyParentGroup {
                updatedRoot
            }

            val writeResult = writeKeePassDatabase(database, updatedDatabase, credentials)
            if (writeResult.isFailure) {
                return@withContext Result.failure(writeResult.exceptionOrNull() ?: Exception("写入失败"))
            }

            dao.updateEntryCount(databaseId, countEntries(updatedDatabase.content.group))
            dao.updateLastSyncedTime(databaseId)
            bumpSyncVersion()

            Result.success(deletedCount)
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

    private fun bumpSyncVersion() {
        _syncVersion.value = _syncVersion.value + 1
    }

    private fun getCredentials(database: LocalKeePassDatabase): Credentials? {
        val encryptedDbPassword = database.encryptedPassword ?: return null
        val kdbxPassword = securityManager.decryptData(encryptedDbPassword) ?: return null
        return Credentials.from(EncryptedValue.fromString(kdbxPassword))
    }

    private fun loadKeePassDatabase(
        database: LocalKeePassDatabase,
        credentials: Credentials
    ): KeePassDatabase {
        return if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
            val file = File(context.filesDir, database.filePath)
            if (!file.exists()) {
                throw Exception("数据库文件不存在")
            }
            file.inputStream().use { input ->
                KeePassDatabase.decode(input, credentials)
            }
        } else {
            val uri = Uri.parse(database.filePath)
            val takeFlags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                try {
                    context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                } catch (e: SecurityException) {
                }
                context.contentResolver.openInputStream(uri)?.use { input ->
                    KeePassDatabase.decode(input, credentials)
                } ?: throw Exception("无法打开数据库文件")
            } catch (e: SecurityException) {
                throw Exception("没有文件访问权限，请重新打开数据库")
            }
        }
    }

    private fun writeKeePassDatabase(
        database: LocalKeePassDatabase,
        updatedDatabase: KeePassDatabase,
        credentials: Credentials
    ): Result<Unit> {
        var externalBackup: ByteArray? = null
        var internalBackup: File? = null
        var internalTarget: File? = null
        return try {
            if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                val file = File(context.filesDir, database.filePath)
                internalTarget = file
                val backupFile = File(file.parentFile, "${file.name}.bak")
                internalBackup = backupFile
                if (file.exists()) {
                    file.inputStream().use { input ->
                        FileOutputStream(backupFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                val tempFile = File(file.parentFile, "${file.name}.tmp")
                FileOutputStream(tempFile).use { output ->
                    updatedDatabase.encode(output)
                }
                if (file.exists()) {
                    file.delete()
                }
                if (!tempFile.renameTo(file)) {
                    if (backupFile.exists()) {
                        backupFile.copyTo(file, overwrite = true)
                    }
                    return Result.failure(Exception("无法保存数据库文件"))
                }
                file.inputStream().use { input ->
                    KeePassDatabase.decode(input, credentials)
                }
            } else {
                val uri = Uri.parse(database.filePath)
                externalBackup = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                }
                val encodedBytes = ByteArrayOutputStream().use { output ->
                    updatedDatabase.encode(output)
                    output.toByteArray()
                }
                context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                    output.write(encodedBytes)
                } ?: return Result.failure(Exception("无法写入数据库文件"))

                context.contentResolver.openInputStream(uri)?.use { input ->
                    KeePassDatabase.decode(input, credentials)
                } ?: return Result.failure(Exception("无法重新验证数据库文件"))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                if (internalBackup != null && internalBackup!!.exists() && internalTarget != null) {
                    try {
                        internalBackup!!.copyTo(internalTarget!!, overwrite = true)
                    } catch (restoreError: Exception) {
                    }
                }
            }
            if (database.storageLocation == KeePassStorageLocation.EXTERNAL) {
                val uri = Uri.parse(database.filePath)
                if (externalBackup != null) {
                    try {
                        context.contentResolver.openOutputStream(uri, "rwt")?.use { output ->
                            output.write(externalBackup)
                        }
                    } catch (restoreError: Exception) {
                    }
                }
            }
            Result.failure(e)
        }
    }

    private fun updateEntriesInGroup(
        group: Group,
        updater: (Entry) -> Entry?
    ): Group {
        val newEntries = group.entries.mapNotNull { entry ->
            updater(entry)
        }
        val newGroups = group.groups.map { updateEntriesInGroup(it, updater) }
        return group.copy(entries = newEntries, groups = newGroups)
    }

    private fun collectEntries(group: Group, entries: MutableList<Entry>) {
        entries.addAll(group.entries)
        group.groups.forEach { subGroup ->
            collectEntries(subGroup, entries)
        }
    }

    private fun countEntries(group: Group): Int {
        var count = group.entries.size
        group.groups.forEach { subGroup ->
            count += countEntries(subGroup)
        }
        return count
    }

    private fun getEntryField(entry: Entry, key: String): String {
        return try {
            entry.fields[key]?.content ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun buildMatchKey(title: String, username: String, url: String): String {
        return "${title.trim().lowercase()}|${username.trim().lowercase()}|${url.trim().lowercase()}"
    }

    private fun matchesEntry(existing: Entry, target: PasswordEntry): Boolean {
        val monicaId = getEntryField(existing, "MonicaId")
        if (monicaId.isNotBlank() && monicaId == target.id.toString()) {
            return true
        }
        val existingKey = buildMatchKey(
            getEntryField(existing, "Title"),
            getEntryField(existing, "UserName"),
            getEntryField(existing, "URL")
        )
        val targetKey = buildMatchKey(target.title, target.username, target.website)
        return existingKey == targetKey
    }

    private fun buildKeePassEntry(entry: PasswordEntry, decryptedPassword: String): Entry {
        return Entry(
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
                }),
                "MonicaId" to EntryValue.Plain(entry.id.toString())
            )
        )
    }

    private fun buildExternalEntryId(entry: Entry): Long {
        val raw = entry.uuid.mostSignificantBits
        val safe = if (raw == Long.MIN_VALUE) Long.MAX_VALUE else abs(raw)
        return -(safe + 1)
    }
    
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
