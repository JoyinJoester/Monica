package takagi.ru.monica.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.keemobile.kotpass.models.Meta
import app.keemobile.kotpass.cryptography.EncryptedValue
import app.keemobile.kotpass.database.Credentials
import app.keemobile.kotpass.database.KeePassDatabase
import app.keemobile.kotpass.database.encode
import app.keemobile.kotpass.database.header.KdfParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.KeePassCipherAlgorithm
import takagi.ru.monica.data.KeePassDatabaseCreationOptions
import takagi.ru.monica.data.KeePassFormatVersion
import takagi.ru.monica.data.KeePassKdfAlgorithm
import takagi.ru.monica.data.KeePassStorageLocation
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassCodecSupport
import takagi.ru.monica.utils.KeePassOperationException
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.OperationLogger
import java.io.File
import java.io.FileOutputStream

/**
 * 本地 KeePass 数据库管理 ViewModel
 */
class LocalKeePassViewModel(
    application: Application,
    private val dao: LocalKeePassDatabaseDao,
    private val securityManager: SecurityManager
) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "LocalKeePassViewModel"
    }
    
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
    
    /** KeePass 分组缓存，按数据库 ID 组织 */
    private val _groupsByDatabase = MutableStateFlow<Map<Long, List<KeePassGroupInfo>>>(emptyMap())
    private val _verificationStates = MutableStateFlow<Map<Long, VerificationState>>(emptyMap())
    val verificationStates: StateFlow<Map<Long, VerificationState>> = _verificationStates.asStateFlow()

    private val kdbxService = KeePassKdbxService(context, dao, securityManager)
    private val workspaceRepository = KeePassWorkspaceRepository(kdbxService)
    private val compatibilityBridge = KeePassCompatibilityBridge(workspaceRepository)

    fun getGroups(databaseId: Long): Flow<List<KeePassGroupInfo>> {
        return _groupsByDatabase
            .map { cache -> cache[databaseId].orEmpty() }
            .onStart { refreshGroups(databaseId) }
    }

    fun refreshGroups(databaseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val groups = workspaceRepository.listGroups(databaseId).getOrDefault(emptyList())
            _groupsByDatabase.update { current -> current + (databaseId to groups) }
        }
    }

    fun ensureVerificationForDatabases(databaseIds: List<Long>) {
        val idSet = databaseIds.toSet()
        _verificationStates.update { current -> current.filterKeys { it in idSet } }
        databaseIds.forEach { databaseId ->
            val existing = _verificationStates.value[databaseId]
            if (existing == null || existing is VerificationState.Unknown) {
                verifyDatabaseCredentials(databaseId, force = false)
            }
        }
    }

    fun verifyDatabaseCredentials(databaseId: Long, force: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = _verificationStates.value[databaseId]
            if (!force && existing is VerificationState.Verified) {
                return@launch
            }

            _verificationStates.update { current ->
                current + (databaseId to VerificationState.Verifying)
            }

            val startedAt = SystemClock.elapsedRealtime()
            val verifyResult = workspaceRepository.verifyDatabase(databaseId)
            val elapsedMs = SystemClock.elapsedRealtime() - startedAt
            _verificationStates.update { current ->
                current + (
                    databaseId to if (verifyResult.isSuccess) {
                        VerificationState.Verified(
                            entryCount = verifyResult.getOrDefault(0),
                            decryptTimeMs = elapsedMs
                        )
                    } else {
                        VerificationState.Failed(verifyResult.exceptionOrNull()?.message ?: "验证失败")
                    }
                )
            }
            if (verifyResult.isSuccess) {
                Log.d(TAG, "KeePass verify success db=$databaseId elapsed=${elapsedMs}ms")
            } else {
                Log.w(TAG, "KeePass verify failed db=$databaseId elapsed=${elapsedMs}ms")
            }
        }
    }

    fun reverifyDatabasePassword(databaseId: Long, password: String, keyFileUri: Uri? = null) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在验证数据库密码...")
            _verificationStates.update { current ->
                current + (databaseId to VerificationState.Verifying)
            }
            try {
                var verifyElapsedMs = 0L
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId) ?: throw Exception("数据库不存在")
                    val passwordToUse = if (password.isNotBlank()) {
                        password
                    } else {
                        database.encryptedPassword?.let { securityManager.decryptData(it) } ?: ""
                    }
                    val verifyStart = SystemClock.elapsedRealtime()
                    val verifyResult = workspaceRepository.inspectDatabase(
                        databaseId = databaseId,
                        passwordOverride = passwordToUse,
                        keyFileUriOverride = keyFileUri
                    )
                    verifyElapsedMs = SystemClock.elapsedRealtime() - verifyStart
                    val diagnostics = verifyResult.getOrElse { throw it }
                    val count = diagnostics.entryCount
                    val options = diagnostics.creationOptions
                    val encryptedPassword = securityManager.encryptData(passwordToUse)
                    if (keyFileUri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                keyFileUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                    }
                    dao.updateDatabase(
                        database.copy(
                            encryptedPassword = encryptedPassword,
                            keyFileUri = keyFileUri?.toString() ?: database.keyFileUri,
                            entryCount = count,
                            kdbxMajorVersion = options.formatVersion.majorVersion,
                            cipherAlgorithm = options.cipherAlgorithm.name,
                            kdfAlgorithm = options.kdfAlgorithm.name,
                            kdfTransformRounds = options.transformRounds,
                            kdfMemoryBytes = options.memoryBytes,
                            kdfParallelism = options.parallelism,
                            lastAccessedAt = System.currentTimeMillis()
                        )
                    )
                    _verificationStates.update { current ->
                        current + (
                            databaseId to VerificationState.Verified(
                                entryCount = count,
                                decryptTimeMs = verifyElapsedMs
                            )
                        )
                    }
                }
                _operationState.value = OperationState.Success("密码验证成功（${verifyElapsedMs}ms）")
            } catch (e: Exception) {
                _verificationStates.update { current ->
                    current + (databaseId to VerificationState.Failed(e.message ?: "验证失败"))
                }
                _operationState.value = OperationState.Error("验证失败: ${formatOperationError(e)}")
            }
        }
    }

    fun createGroup(
        databaseId: Long,
        groupName: String,
        parentPath: String? = null,
        onResult: (Result<KeePassGroupInfo>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = workspaceRepository.createGroup(
                databaseId = databaseId,
                groupName = groupName,
                parentPath = parentPath
            )
            if (result.isSuccess) {
                refreshGroups(databaseId)
                val databaseName = dao.getDatabaseById(databaseId)?.name ?: "KeePass DB #$databaseId"
                result.getOrNull()?.let { groupInfo ->
                    logKeepassGroupCreate(
                        databaseId = databaseId,
                        databaseName = databaseName,
                        group = groupInfo,
                        parentPath = parentPath
                    )
                }
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun renameGroup(
        databaseId: Long,
        groupPath: String,
        newName: String,
        onResult: (Result<KeePassGroupInfo>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = workspaceRepository.renameGroup(
                databaseId = databaseId,
                groupPath = groupPath,
                newName = newName
            )
            if (result.isSuccess) {
                refreshGroups(databaseId)
                val databaseName = dao.getDatabaseById(databaseId)?.name ?: "KeePass DB #$databaseId"
                result.getOrNull()?.let { groupInfo ->
                    logKeepassGroupRename(
                        databaseId = databaseId,
                        databaseName = databaseName,
                        oldPath = groupPath,
                        newGroup = groupInfo
                    )
                }
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun deleteGroup(
        databaseId: Long,
        groupPath: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = workspaceRepository.deleteGroup(
                databaseId = databaseId,
                groupPath = groupPath
            )
            if (result.isSuccess) {
                refreshGroups(databaseId)
                val databaseName = dao.getDatabaseById(databaseId)?.name ?: "KeePass DB #$databaseId"
                logKeepassGroupDelete(
                    databaseId = databaseId,
                    databaseName = databaseName,
                    groupPath = groupPath
                )
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun moveGroup(
        sourceDatabaseId: Long,
        groupPath: String,
        targetDatabaseId: Long,
        targetParentPath: String? = null,
        onResult: (Result<KeePassGroupInfo>) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = workspaceRepository.moveGroup(
                sourceDatabaseId = sourceDatabaseId,
                groupPath = groupPath,
                targetDatabaseId = targetDatabaseId,
                targetParentPath = targetParentPath
            )
            if (result.isSuccess) {
                refreshGroups(sourceDatabaseId)
                if (targetDatabaseId != sourceDatabaseId) {
                    refreshGroups(targetDatabaseId)
                }
                val sourceDatabaseName = dao.getDatabaseById(sourceDatabaseId)?.name ?: "KeePass DB #$sourceDatabaseId"
                val targetDatabaseName = dao.getDatabaseById(targetDatabaseId)?.name ?: "KeePass DB #$targetDatabaseId"
                result.getOrNull()?.let { groupInfo ->
                    logKeepassGroupMove(
                        sourceDatabaseId = sourceDatabaseId,
                        sourceDatabaseName = sourceDatabaseName,
                        sourcePath = groupPath,
                        targetDatabaseId = targetDatabaseId,
                        targetDatabaseName = targetDatabaseName,
                        movedGroup = groupInfo
                    )
                }
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }
    
    /**
     * 创建新的 KeePass 数据库
     */
    fun createDatabase(
        name: String,
        password: String,
        storageLocation: KeePassStorageLocation,
        externalUri: Uri? = null,
        keyFileUri: Uri? = null,
        creationOptions: KeePassDatabaseCreationOptions = KeePassDatabaseCreationOptions(),
        description: String? = null
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在创建数据库...")
            
            try {
                var createdDatabaseId: Long? = null
                var createLogDetails: List<FieldChange> = emptyList()
                withContext(Dispatchers.IO) {
                    val encryptedPassword = if (password.isNotBlank()) securityManager.encryptData(password) else null
                    
                    // 读取密钥文件
                    val keyFileBytes = keyFileUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw Exception("无法读取密钥文件")
                    }
                    
                    if (keyFileUri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                keyFileUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                            )
                        }
                    }
                    
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
                        createEmptyKdbxFile(
                            file = dbFile,
                            password = password,
                            keyFileBytes = keyFileBytes,
                            options = creationOptions,
                            databaseName = name
                        )
                        
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
                                createEmptyKdbxContent(
                                    password = password,
                                    keyFileBytes = keyFileBytes,
                                    options = creationOptions,
                                    databaseName = name
                                ).let { content ->
                                    output.write(content)
                                }
                            }
                            filePath = newFile.uri.toString()
                        } else {
                            throw Exception("无法在指定位置创建文件")
                        }
                    }

                    val normalizedOptions = creationOptions.normalized()
                    // 保存数据库信息
                    val database = LocalKeePassDatabase(
                        name = name,
                        filePath = filePath,
                        keyFileUri = keyFileUri?.toString(),
                        storageLocation = storageLocation,
                        encryptedPassword = encryptedPassword,
                        description = description,
                        isDefault = allDatabases.value.isEmpty(),
                        kdbxMajorVersion = normalizedOptions.formatVersion.majorVersion,
                        cipherAlgorithm = normalizedOptions.cipherAlgorithm.name,
                        kdfAlgorithm = normalizedOptions.kdfAlgorithm.name,
                        kdfTransformRounds = normalizedOptions.transformRounds,
                        kdfMemoryBytes = normalizedOptions.memoryBytes,
                        kdfParallelism = normalizedOptions.parallelism
                    )
                    
                    createdDatabaseId = dao.insertDatabase(database)
                    createLogDetails = listOf(
                        FieldChange("存储位置", "", storageLocationLabel(storageLocation)),
                        FieldChange("格式版本", "", normalizedOptions.formatVersion.name),
                        FieldChange("加密算法", "", normalizedOptions.cipherAlgorithm.name),
                        FieldChange("KDF", "", normalizedOptions.kdfAlgorithm.name)
                    )
                }
                
                _operationState.value = OperationState.Success("数据库创建成功")
                createdDatabaseId?.let { databaseId ->
                    logKeepassDatabaseCreate(
                        databaseId = databaseId,
                        databaseName = name,
                        details = createLogDetails
                    )
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("创建失败: ${formatOperationError(e)}")
            }
        }
    }
    
    /**
     * 生成新的密钥文件 (XML 格式)
     */
    fun generateKeyFile(uri: Uri) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在生成密钥文件...")
            
            try {
                withContext(Dispatchers.IO) {
                    // 1. 生成 32 字节随机数据
                    val randomBytes = ByteArray(32)
                    java.security.SecureRandom().nextBytes(randomBytes)
                    
                    // 2. Base64 编码
                    val base64Key = android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
                    
                    // 3. 构建 XML 内容 (KeePass 2.x 格式)
                    val xmlContent = """
                        <?xml version="1.0" encoding="utf-8"?>
                        <KeyFile>
                        	<Meta>
                        		<Version>1.00</Version>
                        	</Meta>
                        	<Key>
                        		<Data>$base64Key</Data>
                        	</Key>
                        </KeyFile>
                    """.trimIndent()
                    
                    // 4. 写入文件
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(xmlContent.toByteArray())
                    } ?: throw Exception("无法写入文件")
                    
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                }
                
                _operationState.value = OperationState.Success("密钥文件生成成功")
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("生成密钥文件失败: ${formatOperationError(e)}")
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
        keyFileUri: Uri? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            _operationState.value = OperationState.Loading("正在添加数据库...")
            
            try {
                var verifyElapsedMs = 0L
                var importLogAction: String? = null
                var importLogDatabaseId = 0L
                var importLogDatabaseName = name
                var importLogChanges: List<FieldChange> = emptyList()
                withContext(Dispatchers.IO) {
                    // 验证文件是否可访问
                    context.contentResolver.openInputStream(uri)?.close()
                        ?: throw Exception("无法访问文件")

                    val verifyStart = SystemClock.elapsedRealtime()
                    val verifyResult = workspaceRepository.inspectExternalDatabase(
                        fileUri = uri,
                        password = password,
                        keyFileUri = keyFileUri
                    )
                    verifyElapsedMs = SystemClock.elapsedRealtime() - verifyStart
                    val diagnostics = verifyResult.getOrElse { throw it }
                    val entryCount = diagnostics.entryCount
                    val options = diagnostics.creationOptions
                    
                    val encryptedPassword = if (password.isNotBlank()) securityManager.encryptData(password) else null
                    
                    // 获取持久化 URI 权限
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }.onFailure { error ->
                        Log.w(TAG, "Persistable READ permission not granted for imported DB uri=$uri", error)
                    }
                    
                    if (keyFileUri != null) {
                        runCatching {
                            context.contentResolver.takePersistableUriPermission(
                                keyFileUri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }
                        context.contentResolver.openInputStream(keyFileUri)?.close()
                            ?: throw Exception("无法访问密钥文件")
                    }
                    
                    val uriPath = uri.toString()
                    val existing = dao.getAllDatabasesSync().firstOrNull { it.filePath == uriPath }
                    if (existing != null) {
                        val updated = existing.copy(
                            name = name,
                            keyFileUri = keyFileUri?.toString() ?: existing.keyFileUri,
                            storageLocation = KeePassStorageLocation.EXTERNAL,
                            encryptedPassword = encryptedPassword,
                            description = description ?: existing.description,
                            entryCount = entryCount,
                            kdbxMajorVersion = options.formatVersion.majorVersion,
                            cipherAlgorithm = options.cipherAlgorithm.name,
                            kdfAlgorithm = options.kdfAlgorithm.name,
                            kdfTransformRounds = options.transformRounds,
                            kdfMemoryBytes = options.memoryBytes,
                            kdfParallelism = options.parallelism,
                            lastAccessedAt = System.currentTimeMillis()
                        )
                        dao.updateDatabase(updated)
                        KeePassKdbxService.invalidateProcessCache(existing.id)

                        importLogAction = "update"
                        importLogDatabaseId = updated.id
                        importLogDatabaseName = updated.name
                        importLogChanges = buildList {
                            if (existing.name != updated.name) {
                                add(FieldChange("名称", existing.name, updated.name))
                            }
                            if (existing.description.orEmpty() != updated.description.orEmpty()) {
                                add(FieldChange("描述", existing.description.orEmpty(), updated.description.orEmpty()))
                            }
                            if (existing.entryCount != updated.entryCount) {
                                add(FieldChange("条目数量", existing.entryCount.toString(), updated.entryCount.toString()))
                            }
                            if (existing.keyFileUri != updated.keyFileUri) {
                                add(
                                    FieldChange(
                                        "密钥文件",
                                        if (existing.keyFileUri.isNullOrBlank()) "未设置" else "已设置",
                                        if (updated.keyFileUri.isNullOrBlank()) "未设置" else "已设置"
                                    )
                                )
                            }
                        }
                    } else {
                        val database = LocalKeePassDatabase(
                            name = name,
                            filePath = uriPath,
                            keyFileUri = keyFileUri?.toString(),
                            storageLocation = KeePassStorageLocation.EXTERNAL,
                            encryptedPassword = encryptedPassword,
                            description = description,
                            entryCount = entryCount,
                            kdbxMajorVersion = options.formatVersion.majorVersion,
                            cipherAlgorithm = options.cipherAlgorithm.name,
                            kdfAlgorithm = options.kdfAlgorithm.name,
                            kdfTransformRounds = options.transformRounds,
                            kdfMemoryBytes = options.memoryBytes,
                            kdfParallelism = options.parallelism,
                            isDefault = allDatabases.value.isEmpty()
                        )
                        val newId = dao.insertDatabase(database)
                        KeePassKdbxService.invalidateProcessCache(newId)

                        importLogAction = "create"
                        importLogDatabaseId = newId
                        importLogDatabaseName = database.name
                        importLogChanges = listOf(
                            FieldChange("来源", "", "外部导入"),
                            FieldChange("存储位置", "", storageLocationLabel(KeePassStorageLocation.EXTERNAL)),
                            FieldChange("条目数量", "", entryCount.toString())
                        )
                    }
                }
                
                _operationState.value = OperationState.Success("数据库添加成功（验证${verifyElapsedMs}ms）")
                when (importLogAction) {
                    "create" -> {
                        logKeepassDatabaseCreate(
                            databaseId = importLogDatabaseId,
                            databaseName = importLogDatabaseName,
                            details = importLogChanges
                        )
                    }
                    "update" -> {
                        logKeepassDatabaseUpdate(
                            databaseId = importLogDatabaseId,
                            databaseName = importLogDatabaseName,
                            changes = importLogChanges.ifEmpty {
                                listOf(FieldChange("外部引用", "已存在", "已刷新"))
                            }
                        )
                    }
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("添加失败: ${formatOperationError(e)}")
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
                var copiedDatabaseId: Long? = null
                var copiedDatabaseName = ""
                var sourceDatabaseName = ""
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    sourceDatabaseName = database.name
                    
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
                    
                    copiedDatabaseId = dao.insertDatabase(newDatabase)
                    copiedDatabaseName = newDatabase.name
                }
                
                _operationState.value = OperationState.Success("已复制到内部存储")
                copiedDatabaseId?.let { newDatabaseId ->
                    logKeepassDatabaseCreate(
                        databaseId = newDatabaseId,
                        databaseName = copiedDatabaseName,
                        details = listOf(
                            FieldChange("来源", "", sourceDatabaseName),
                            FieldChange("存储位置", "", storageLocationLabel(KeePassStorageLocation.INTERNAL))
                        )
                    )
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("复制失败: ${formatOperationError(e)}")
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
                _operationState.value = OperationState.Error("导出失败: ${formatOperationError(e)}")
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
                var transferDatabaseName = ""
                var transferChanges: List<FieldChange> = emptyList()
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    transferDatabaseName = database.name
                    
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
                    transferChanges = listOf(
                        FieldChange(
                            "存储位置",
                            storageLocationLabel(database.storageLocation),
                            storageLocationLabel(targetLocation)
                        ),
                        FieldChange(
                            "存储路径",
                            storagePathLabel(database.filePath),
                            storagePathLabel(newPath)
                        )
                    )
                }
                
                _operationState.value = OperationState.Success("转移成功")
                logKeepassDatabaseUpdate(
                    databaseId = databaseId,
                    databaseName = transferDatabaseName,
                    changes = transferChanges
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("转移失败: ${formatOperationError(e)}")
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
                var deletedDatabaseName = ""
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    deletedDatabaseName = database.name
                    val appDatabase = PasswordDatabase.getDatabase(context)
                    
                    if (deleteFile) {
                        if (database.storageLocation == KeePassStorageLocation.INTERNAL) {
                            val file = File(context.filesDir, database.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                        // 外部文件不删除，只移除引用
                    }

                    appDatabase.passwordEntryDao().clearKeePassBindingForDatabase(databaseId)
                    appDatabase.secureItemDao().clearKeePassBindingForDatabase(databaseId)
                    appDatabase.passkeyDao().clearKeePassBindingForDatabase(databaseId)
                    appDatabase.keepassGroupSyncConfigDao().deleteByDatabaseId(databaseId)
                    KeePassKdbxService.invalidateProcessCache(databaseId)
                    
                    dao.deleteDatabaseById(databaseId)
                }

                _groupsByDatabase.update { current -> current - databaseId }
                _verificationStates.update { current -> current - databaseId }
                _selectedDatabase.update { current -> current?.takeUnless { it.id == databaseId } }
                
                _operationState.value = OperationState.Success("已删除")
                logKeepassDatabaseDelete(
                    databaseId = databaseId,
                    databaseName = deletedDatabaseName,
                    detail = if (deleteFile) "删除记录与本地文件" else "删除记录"
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("删除失败: ${formatOperationError(e)}")
            }
        }
    }
    
    /**
     * 更新数据库密码
     */
    fun updatePassword(databaseId: Long, newPassword: String) {
        viewModelScope.launch {
            try {
                var verifyElapsedMs = 0L
                var databaseName = "KeePass DB #$databaseId"
                withContext(Dispatchers.IO) {
                    val database = dao.getDatabaseById(databaseId)
                        ?: throw Exception("数据库不存在")
                    databaseName = database.name
                    val verifyStart = SystemClock.elapsedRealtime()
                    val verifyResult = workspaceRepository.inspectDatabase(
                        databaseId = databaseId,
                        passwordOverride = newPassword
                    )
                    verifyElapsedMs = SystemClock.elapsedRealtime() - verifyStart
                    val diagnostics = verifyResult.getOrElse { throw it }
                    val entryCount = diagnostics.entryCount
                    val options = diagnostics.creationOptions
                    val encryptedPassword = securityManager.encryptData(newPassword)
                    dao.updateDatabase(
                        database.copy(
                            encryptedPassword = encryptedPassword,
                            entryCount = entryCount,
                            kdbxMajorVersion = options.formatVersion.majorVersion,
                            cipherAlgorithm = options.cipherAlgorithm.name,
                            kdfAlgorithm = options.kdfAlgorithm.name,
                            kdfTransformRounds = options.transformRounds,
                            kdfMemoryBytes = options.memoryBytes,
                            kdfParallelism = options.parallelism,
                            lastAccessedAt = System.currentTimeMillis()
                        )
                    )
                    _verificationStates.update { current ->
                        current + (
                            databaseId to VerificationState.Verified(
                                entryCount = entryCount,
                                decryptTimeMs = verifyElapsedMs
                            )
                        )
                    }
                }
                
                _operationState.value = OperationState.Success("密码已更新（验证${verifyElapsedMs}ms）")
                logKeepassDatabaseUpdate(
                    databaseId = databaseId,
                    databaseName = databaseName,
                    changes = listOf(
                        FieldChange("主密码", "已设置", "已更新")
                    )
                )
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("更新失败: ${formatOperationError(e)}")
            }
        }
    }
    
    /**
     * 设为默认数据库
     */
    fun setAsDefault(databaseId: Long) {
        viewModelScope.launch {
            try {
                var defaultDatabaseName: String? = null
                withContext(Dispatchers.IO) {
                    defaultDatabaseName = dao.getDatabaseById(databaseId)?.name
                    dao.clearDefaultDatabase()
                    dao.setDefaultDatabase(databaseId)
                }
                defaultDatabaseName?.let { databaseName ->
                    logKeepassDatabaseUpdate(
                        databaseId = databaseId,
                        databaseName = databaseName,
                        changes = listOf(
                            FieldChange("默认数据库", "否", "是")
                        )
                    )
                }
            } catch (e: Exception) {
                _operationState.value = OperationState.Error("设置失败: ${formatOperationError(e)}")
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
        compatibilityBridge.upsertLegacyPasswordEntries(
            databaseId = databaseId,
            entries = entries,
            resolvePassword = { entry ->
                try {
                    decryptPassword(entry.password)
                } catch (e: Exception) {
                    entry.password
                }
            }
        )
    }

    suspend fun movePasswordEntriesToKdbx(
        databaseId: Long,
        groupPath: String?,
        entries: List<PasswordEntry>,
        decryptPassword: (String) -> String
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            entries.forEach { entry ->
                val sourceDatabaseId = entry.keepassDatabaseId
                val targetEntry = entry.copy(
                    keepassDatabaseId = databaseId,
                    keepassGroupPath = groupPath,
                    bitwardenVaultId = null,
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
                    bitwardenLocalModified = false
                )

                when {
                    sourceDatabaseId == null -> {
                        compatibilityBridge.upsertLegacyPasswordEntries(
                            databaseId = databaseId,
                            entries = listOf(targetEntry),
                            resolvePassword = { item ->
                                try {
                                    decryptPassword(item.password)
                                } catch (_: Exception) {
                                    item.password
                                }
                            },
                            forceSyncWrite = true
                        ).getOrThrow()
                    }
                    sourceDatabaseId == databaseId -> {
                        compatibilityBridge.updateLegacyPasswordEntry(
                            databaseId = databaseId,
                            entry = targetEntry,
                            resolvePassword = { item ->
                                try {
                                    decryptPassword(item.password)
                                } catch (_: Exception) {
                                    item.password
                                }
                            }
                        ).getOrThrow()
                    }
                    else -> {
                        compatibilityBridge.upsertLegacyPasswordEntries(
                            databaseId = databaseId,
                            entries = listOf(targetEntry.copy(
                                keepassEntryUuid = null,
                                keepassGroupUuid = null
                            )),
                            resolvePassword = { item ->
                                try {
                                    decryptPassword(item.password)
                                } catch (_: Exception) {
                                    item.password
                                }
                            },
                            forceSyncWrite = true
                        ).getOrThrow()
                        compatibilityBridge.deleteLegacyPasswordEntries(
                            databaseId = sourceDatabaseId,
                            entries = listOf(entry)
                        ).getOrThrow()
                    }
                }
            }
            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun movePasswordEntriesToMonicaLocal(
        entries: List<PasswordEntry>
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val keepassEntries = entries.filter { it.keepassDatabaseId != null }
            if (keepassEntries.isEmpty()) {
                return@withContext Result.success(0)
            }

            keepassEntries
                .groupBy { it.keepassDatabaseId }
                .forEach { (databaseId, databaseEntries) ->
                    val resolvedDatabaseId = databaseId ?: return@forEach
                    compatibilityBridge.deleteLegacyPasswordEntries(
                        databaseId = resolvedDatabaseId,
                        entries = databaseEntries
                    ).getOrThrow()
                }

            Result.success(keepassEntries.size)
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

    private fun logKeepassDatabaseCreate(
        databaseId: Long,
        databaseName: String,
        details: List<FieldChange> = emptyList()
    ) {
        OperationLogger.logCreate(
            itemType = OperationLogItemType.KEEPASS_DATABASE,
            itemId = databaseId,
            itemTitle = databaseName,
            details = details
        )
    }

    private fun logKeepassDatabaseUpdate(
        databaseId: Long,
        databaseName: String,
        changes: List<FieldChange>
    ) {
        OperationLogger.logUpdate(
            itemType = OperationLogItemType.KEEPASS_DATABASE,
            itemId = databaseId,
            itemTitle = databaseName,
            changes = changes
        )
    }

    private fun logKeepassDatabaseDelete(
        databaseId: Long,
        databaseName: String,
        detail: String? = null
    ) {
        OperationLogger.logDelete(
            itemType = OperationLogItemType.KEEPASS_DATABASE,
            itemId = databaseId,
            itemTitle = databaseName,
            detail = detail
        )
    }

    private fun logKeepassGroupCreate(
        databaseId: Long,
        databaseName: String,
        group: KeePassGroupInfo,
        parentPath: String?
    ) {
        OperationLogger.logCreate(
            itemType = OperationLogItemType.KEEPASS_GROUP,
            itemId = buildKeepassGroupItemId(databaseId, group.path),
            itemTitle = "$databaseName · ${group.displayPath}",
            details = listOf(
                FieldChange("数据库", "", databaseName),
                FieldChange("父级分组", "", parentPath?.takeIf { it.isNotBlank() } ?: "根目录")
            )
        )
    }

    private fun logKeepassGroupRename(
        databaseId: Long,
        databaseName: String,
        oldPath: String,
        newGroup: KeePassGroupInfo
    ) {
        val oldName = oldPath.substringAfterLast('/')
        OperationLogger.logUpdate(
            itemType = OperationLogItemType.KEEPASS_GROUP,
            itemId = buildKeepassGroupItemId(databaseId, newGroup.path),
            itemTitle = "$databaseName · ${newGroup.displayPath}",
            changes = buildList {
                add(FieldChange("名称", oldName, newGroup.name))
                if (oldPath != newGroup.path) {
                    add(FieldChange("路径", oldPath, newGroup.path))
                }
            }
        )
    }

    private fun logKeepassGroupDelete(
        databaseId: Long,
        databaseName: String,
        groupPath: String
    ) {
        OperationLogger.logDelete(
            itemType = OperationLogItemType.KEEPASS_GROUP,
            itemId = buildKeepassGroupItemId(databaseId, groupPath),
            itemTitle = "$databaseName · $groupPath",
            detail = "删除分组"
        )
    }

    private fun logKeepassGroupMove(
        sourceDatabaseId: Long,
        sourceDatabaseName: String,
        sourcePath: String,
        targetDatabaseId: Long,
        targetDatabaseName: String,
        movedGroup: KeePassGroupInfo
    ) {
        OperationLogger.logUpdate(
            itemType = OperationLogItemType.KEEPASS_GROUP,
            itemId = buildKeepassGroupItemId(targetDatabaseId, movedGroup.path),
            itemTitle = "$targetDatabaseName · ${movedGroup.displayPath}",
            changes = buildList {
                if (sourceDatabaseId != targetDatabaseId) {
                    add(FieldChange("数据库", sourceDatabaseName, targetDatabaseName))
                }
                add(FieldChange("路径", sourcePath, movedGroup.path))
            }
        )
    }

    private fun buildKeepassGroupItemId(databaseId: Long, groupPath: String): Long {
        return "${databaseId}:$groupPath".hashCode().toLong() and 0x7FFFFFFFL
    }

    private fun storageLocationLabel(location: KeePassStorageLocation): String {
        return when (location) {
            KeePassStorageLocation.INTERNAL -> "内部"
            KeePassStorageLocation.EXTERNAL -> "外部"
        }
    }

    private fun storagePathLabel(path: String): String {
        return if (path.startsWith("content://")) {
            "外部 URI"
        } else {
            path
        }
    }

    private fun formatOperationError(error: Throwable): String {
        return if (error is KeePassOperationException) {
            "[${error.code.name}] ${error.message}"
        } else {
            error.message ?: "未知错误"
        }
    }
    
    // === 私有辅助方法 ===
    
    /**
     * 使用 kotpass 库创建真正的 KDBX 格式数据库文件
     */
    private fun createEmptyKdbxFile(
        file: File,
        password: String,
        keyFileBytes: ByteArray? = null,
        options: KeePassDatabaseCreationOptions,
        databaseName: String
    ) {
        // 创建凭据：空密码 + 密钥文件时优先使用 key-only，兼容 KeePassXC 习惯
        val credentials = buildKdbxCredentials(password, keyFileBytes)

        // 创建元数据
        val meta = Meta(
            generator = "Monica Password Manager",
            name = databaseName.ifBlank { file.nameWithoutExtension }
        )

        val database = createConfiguredDatabase(
            credentials = credentials,
            meta = meta,
            options = options
        )

        // 写入文件
        FileOutputStream(file).use { output ->
            database.encode(output, cipherProviders = KeePassCodecSupport.cipherProviders)
        }
    }
    
    /**
     * 使用 kotpass 库创建真正的 KDBX 格式数据库内容
     */
    private fun createEmptyKdbxContent(
        password: String,
        keyFileBytes: ByteArray? = null,
        options: KeePassDatabaseCreationOptions,
        databaseName: String
    ): ByteArray {
        // 创建凭据：空密码 + 密钥文件时优先使用 key-only，兼容 KeePassXC 习惯
        val credentials = buildKdbxCredentials(password, keyFileBytes)

        // 创建元数据
        val meta = Meta(
            generator = "Monica Password Manager",
            name = databaseName.ifBlank { "Monica Database" }
        )

        val database = createConfiguredDatabase(
            credentials = credentials,
            meta = meta,
            options = options
        )

        // 返回字节数组
        return java.io.ByteArrayOutputStream().use { output ->
            database.encode(output, cipherProviders = KeePassCodecSupport.cipherProviders)
            output.toByteArray()
        }
    }

    private fun createConfiguredDatabase(
        credentials: Credentials,
        meta: Meta,
        options: KeePassDatabaseCreationOptions
    ): KeePassDatabase {
        val normalized = options.normalized()
        return when (normalized.formatVersion) {
            KeePassFormatVersion.KDBX3 -> {
                val base = KeePassDatabase.Ver3x.create(
                    rootName = "Root",
                    meta = meta,
                    credentials = credentials
                )
                base.copy(
                    header = base.header.copy(
                        cipherId = KeePassCodecSupport.resolveCipherUuid(normalized.cipherAlgorithm),
                        transformRounds = normalized.transformRounds.toULong()
                    )
                )
            }
            KeePassFormatVersion.KDBX4 -> {
                val base = KeePassDatabase.Ver4x.create(
                    rootName = "Root",
                    meta = meta,
                    credentials = credentials
                )
                val saltOrSeed = when (val existing = base.header.kdfParameters) {
                    is KdfParameters.Aes -> existing.seed
                    is KdfParameters.Argon2 -> existing.salt
                }
                val kdfParameters = when (normalized.kdfAlgorithm) {
                    KeePassKdfAlgorithm.AES_KDF -> KdfParameters.Aes(
                        rounds = normalized.transformRounds.toULong(),
                        seed = saltOrSeed
                    )
                    KeePassKdfAlgorithm.ARGON2D -> KdfParameters.Argon2(
                        variant = KdfParameters.Argon2.Variant.Argon2d,
                        salt = saltOrSeed,
                        parallelism = normalized.parallelism.toUInt(),
                        memory = normalized.memoryBytes.toULong(),
                        iterations = normalized.transformRounds.toULong(),
                        version = 0x13U,
                        secretKey = null,
                        associatedData = null
                    )
                    KeePassKdfAlgorithm.ARGON2ID -> KdfParameters.Argon2(
                        variant = KdfParameters.Argon2.Variant.Argon2id,
                        salt = saltOrSeed,
                        parallelism = normalized.parallelism.toUInt(),
                        memory = normalized.memoryBytes.toULong(),
                        iterations = normalized.transformRounds.toULong(),
                        version = 0x13U,
                        secretKey = null,
                        associatedData = null
                    )
                }
                base.copy(
                    header = base.header.copy(
                        cipherId = KeePassCodecSupport.resolveCipherUuid(normalized.cipherAlgorithm),
                        kdfParameters = kdfParameters
                    )
                )
            }
        }
    }

    private fun buildKdbxCredentials(password: String, keyFileBytes: ByteArray?): Credentials {
        if (keyFileBytes == null) {
            return Credentials.from(EncryptedValue.fromString(password))
        }
        return if (password.isBlank()) {
            Credentials.from(keyFileBytes)
        } else {
            Credentials.from(EncryptedValue.fromString(password), keyFileBytes)
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

    sealed class VerificationState {
        object Unknown : VerificationState()
        object Verifying : VerificationState()
        data class Verified(
            val entryCount: Int,
            val decryptTimeMs: Long
        ) : VerificationState()
        data class Failed(val message: String) : VerificationState()
    }
}
