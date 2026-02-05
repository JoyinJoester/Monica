package takagi.ru.monica.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import takagi.ru.monica.bitwarden.api.BitwardenApiManager
import takagi.ru.monica.bitwarden.api.FolderCreateRequest
import takagi.ru.monica.bitwarden.api.FolderUpdateRequest
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.KeepassGroupSyncConfig
import takagi.ru.monica.data.KeepassGroupSyncConfigDao
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.SettingsManager

/**
 * 操作结果
 */
data class FolderOperationResult(
    val success: Boolean,
    val message: String
)

/**
 * 文件夹管理 ViewModel
 * 
 * 统一管理所有数据源的文件夹与 Bitwarden 的映射关系：
 * - Monica 本地分类
 * - KeePass 数据库组
 * - Bitwarden 云端文件夹
 */
class FolderManagementViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "FolderMgmtVM"
    }

    private val database = PasswordDatabase.getDatabase(application)
    private val categoryDao = database.categoryDao()
    private val keepassDao = database.localKeePassDatabaseDao()
    private val keepassGroupDao: KeepassGroupSyncConfigDao = database.keepassGroupSyncConfigDao()
    private val bitwardenFolderDao = database.bitwardenFolderDao()
    private val passwordEntryDao = database.passwordEntryDao()
    private val secureItemDao = database.secureItemDao()
    private val passkeyDao = database.passkeyDao()
    private val settingsManager = SettingsManager(application)
    private val bitwardenRepository = BitwardenRepository.getInstance(application)
    private val apiManager = BitwardenApiManager()
    private val securityManager = SecurityManager(application.applicationContext)
    private val keePassService = KeePassKdbxService(
        application.applicationContext,
        keepassDao,
        securityManager
    )

    // ========== 状态 ==========

    val settings = settingsManager.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        takagi.ru.monica.data.AppSettings()
    )

    // Monica 本地分类
    val categories = categoryDao.getAllCategories().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // KeePass 数据库列表
    val keepassDatabases = keepassDao.getAllDatabases().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    // Bitwarden 账户
    private val _bitwardenVaults = MutableStateFlow<List<BitwardenVault>>(emptyList())
    val bitwardenVaults: StateFlow<List<BitwardenVault>> = _bitwardenVaults.asStateFlow()

    private val _selectedBitwardenVaultId = MutableStateFlow<Long?>(null)
    val selectedBitwardenVaultId: StateFlow<Long?> = _selectedBitwardenVaultId.asStateFlow()

    // Bitwarden 文件夹
    private val _bitwardenFolders = MutableStateFlow<List<BitwardenFolder>>(emptyList())
    val bitwardenFolders: StateFlow<List<BitwardenFolder>> = _bitwardenFolders.asStateFlow()

    private var bitwardenFolderJob: Job? = null

    // 所有 KeePass 数据库的组（合并显示）
    private val _allKeePassGroups = MutableStateFlow<List<KeePassGroupWithDatabase>>(emptyList())
    val allKeePassGroups: StateFlow<List<KeePassGroupWithDatabase>> = _allKeePassGroups.asStateFlow()

    // KeePass 组映射配置
    private val _keepassGroupMappings = MutableStateFlow<List<KeepassGroupSyncConfig>>(emptyList())
    val keepassGroupMappings: StateFlow<List<KeepassGroupSyncConfig>> = _keepassGroupMappings.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 同步状态消息（用于显示"Bitwarden 数据库同步中..."等）
    private val _syncStatusMessage = MutableStateFlow<String?>(null)
    val syncStatusMessage: StateFlow<String?> = _syncStatusMessage.asStateFlow()

    // 是否需要解锁
    private val _needsUnlock = MutableStateFlow(false)
    val needsUnlock: StateFlow<Boolean> = _needsUnlock.asStateFlow()

    // 待执行的操作（解锁后执行）
    private var pendingOperation: (() -> Unit)? = null

    // 操作结果
    private val _operationResult = MutableStateFlow<FolderOperationResult?>(null)
    val operationResult: StateFlow<FolderOperationResult?> = _operationResult.asStateFlow()

    init {
        loadBitwardenVaults()
        loadAllKeePassGroups()
    }

    // ========== Bitwarden 账户选择 ==========

    fun selectBitwardenVault(vaultId: Long) {
        _selectedBitwardenVaultId.value = vaultId
        bitwardenFolderJob?.cancel()
        bitwardenFolderJob = viewModelScope.launch(Dispatchers.IO) {
            bitwardenFolderDao.getFoldersByVaultFlow(vaultId)
                .collectLatest { folders ->
                    _bitwardenFolders.value = folders
                }
        }
        syncBitwardenFolders(vaultId)
    }

    // ========== 设置 ==========

    fun updateBitwardenUploadAll(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateBitwardenUploadAll(enabled)
        }
    }

    fun applyUploadAll(vaultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                passwordEntryDao.markAllForBitwarden(vaultId)
                secureItemDao.markAllForBitwarden(vaultId)
                passkeyDao.markAllForBitwarden(vaultId)
                _operationResult.value = FolderOperationResult(
                    success = true,
                    message = "已将所有数据标记为同步到 Bitwarden"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply upload all", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "操作失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========== 本地分类操作 ==========

    fun linkCategoryToBitwarden(
        categoryId: Long,
        vaultId: Long,
        folderId: String,
        syncTypes: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categoryDao.linkToBitwarden(
                    categoryId = categoryId,
                    vaultId = vaultId,
                    folderId = folderId,
                    syncTypes = toSyncTypesJson(syncTypes)
                )
                _operationResult.value = FolderOperationResult(
                    success = true,
                    message = "文件夹已关联到 Bitwarden"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link category", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "关联失败: ${e.message}"
                )
            }
        }
    }

    fun unlinkCategoryFromBitwarden(categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                categoryDao.unlinkFromBitwarden(categoryId)
                _operationResult.value = FolderOperationResult(
                    success = true,
                    message = "已解除关联"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unlink category", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "解除关联失败: ${e.message}"
                )
            }
        }
    }

    // ========== KeePass 组操作 ==========

    fun linkKeePassGroup(
        databaseId: Long,
        groupPath: String,
        groupUuid: String?,
        vaultId: Long,
        folderId: String,
        syncTypes: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                keepassGroupDao.upsert(
                    KeepassGroupSyncConfig(
                        keepassDatabaseId = databaseId,
                        groupPath = groupPath,
                        groupUuid = groupUuid,
                        bitwardenVaultId = vaultId,
                        bitwardenFolderId = folderId,
                        syncItemTypes = toSyncTypesJson(syncTypes),
                        updatedAt = System.currentTimeMillis()
                    )
                )
                loadKeePassMappings()
                _operationResult.value = FolderOperationResult(
                    success = true,
                    message = "KeePass 组已关联到 Bitwarden"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link KeePass group", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "关联失败: ${e.message}"
                )
            }
        }
    }

    fun unlinkKeePassGroup(databaseId: Long, groupPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                keepassGroupDao.unlink(databaseId, groupPath)
                loadKeePassMappings()
                _operationResult.value = FolderOperationResult(
                    success = true,
                    message = "已解除关联"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unlink KeePass group", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "解除关联失败: ${e.message}"
                )
            }
        }
    }

    // ========== Monica 本地文件夹操作 ==========

    fun createLocalFolder(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val nextOrder = categories.value.size
                categoryDao.insert(Category(name = name.trim(), sortOrder = nextOrder))
                _operationResult.value = FolderOperationResult(
                    success = true,
                    message = "本地文件夹 \"${name.trim()}\" 已创建"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create local folder", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "创建本地文件夹失败: ${e.message}"
                )
            }
        }
    }

    // ========== Bitwarden 文件夹操作 ==========

    /**
     * 同步 Bitwarden 文件夹到本地（作为客户端拉取最新文件夹）
     */
    fun syncBitwardenFolders(vaultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val vault = _bitwardenVaults.value.find { it.id == vaultId }
            if (vault == null) {
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "Vault 不存在"
                )
                return@launch
            }

            if (!bitwardenRepository.isVaultUnlocked(vaultId)) {
                pendingOperation = { syncBitwardenFoldersInternal(vaultId) }
                _needsUnlock.value = true
                return@launch
            }

            syncBitwardenFoldersInternal(vaultId)
        }
    }

    /**
     * 创建 Bitwarden 文件夹
     * 
     * 流程：
     * 1. 检查 Vault 是否已解锁
     * 2. 如果已锁定，触发解锁对话框
     * 3. 先同步 Bitwarden 数据库
     * 4. 同步完成后创建文件夹
     */
    fun createBitwardenFolder(vaultId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val vault = _bitwardenVaults.value.find { it.id == vaultId }
            if (vault == null) {
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "Vault 不存在"
                )
                return@launch
            }

            // 检查 Vault 是否已解锁
            if (!bitwardenRepository.isVaultUnlocked(vaultId)) {
                // 保存待执行的操作
                pendingOperation = { createBitwardenFolderInternal(vaultId, name) }
                _needsUnlock.value = true
                return@launch
            }

            // 已解锁，执行创建
            createBitwardenFolderInternal(vaultId, name)
        }
    }

    /**
     * 内部方法：执行 Bitwarden 文件夹创建
     * 先同步再创建
     */
    private fun createBitwardenFolderInternal(vaultId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val vault = _bitwardenVaults.value.find { it.id == vaultId }
                    ?: throw Exception("Vault not found")

                // Step 1: 先同步
                _syncStatusMessage.value = "正在同步 Bitwarden 数据库..."
                val syncResult = bitwardenRepository.sync(vaultId)
                when (syncResult) {
                    is BitwardenRepository.SyncResult.Success -> {
                        Log.d(TAG, "同步成功，已同步 ${syncResult.syncedCount} 条记录")
                    }
                    is BitwardenRepository.SyncResult.Error -> {
                        Log.w(TAG, "同步失败: ${syncResult.message}，继续尝试创建文件夹")
                    }
                    is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                        Log.w(TAG, "空 Vault 保护触发，继续尝试创建文件夹")
                    }
                }
                _syncStatusMessage.value = null

                // 重新加载文件夹列表
                loadBitwardenFolders(vaultId)

                // Step 2: 创建文件夹
                _syncStatusMessage.value = "正在创建文件夹..."

                val accessToken = getAccessToken(vault) ?: throw Exception("令牌不可用，请重新登录 Bitwarden")

                val cryptoKey = getCryptoKey(vault)
                    ?: throw Exception("无法获取加密密钥")

                val encryptedName = BitwardenCrypto.encryptString(name, cryptoKey)
                val request = FolderCreateRequest(name = encryptedName)

                val api = apiManager.getVaultApi(vault.apiUrl)
                val response = api.createFolder(
                    "Bearer $accessToken",
                    request
                )

                if (response.isSuccessful && response.body() != null) {
                    val created = response.body()!!
                    
                    val folder = BitwardenFolder(
                        vaultId = vaultId,
                        bitwardenFolderId = created.id,
                        name = name,
                        encryptedName = encryptedName,
                        revisionDate = created.revisionDate ?: "",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    bitwardenFolderDao.insert(folder)
                    loadBitwardenFolders(vaultId)
                    
                    _operationResult.value = FolderOperationResult(
                        success = true,
                        message = "文件夹 \"$name\" 创建成功"
                    )
                } else {
                    val error = response.errorBody()?.string() ?: "Unknown error"
                    if (response.code() == 401) {
                        throw Exception("令牌已失效，请重新登录 Bitwarden")
                    }
                    throw Exception("API 错误: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create Bitwarden folder", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "创建失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
                _syncStatusMessage.value = null
            }
        }
    }

    /**
     * 内部方法：执行 Bitwarden 同步（拉取文件夹）
     */
    private fun syncBitwardenFoldersInternal(vaultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                _syncStatusMessage.value = "正在同步 Bitwarden 数据库..."
                val syncResult = bitwardenRepository.sync(vaultId)
                when (syncResult) {
                    is BitwardenRepository.SyncResult.Success -> {
                        Log.d(TAG, "同步成功，已同步 ${syncResult.syncedCount} 条记录")
                    }
                    is BitwardenRepository.SyncResult.Error -> {
                        Log.w(TAG, "同步失败: ${syncResult.message}")
                    }
                    is BitwardenRepository.SyncResult.EmptyVaultBlocked -> {
                        Log.w(TAG, "空 Vault 保护触发，未拉取数据")
                    }
                }
                loadBitwardenFolders(vaultId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync Bitwarden folders", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "同步失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
                _syncStatusMessage.value = null
            }
        }
    }

    /**
     * 重命名 Bitwarden 文件夹
     */
    fun renameBitwardenFolder(folderId: String, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val vaultId = _selectedBitwardenVaultId.value
                    ?: throw Exception("未选择 Vault")
                val vault = _bitwardenVaults.value.find { it.id == vaultId }
                    ?: throw Exception("Vault not found")
                val accessToken = getAccessToken(vault) ?: throw Exception("令牌不可用，请重新登录 Bitwarden")

                val cryptoKey = getCryptoKey(vault)
                    ?: throw Exception("无法获取加密密钥")

                val encryptedName = BitwardenCrypto.encryptString(newName, cryptoKey)
                val request = FolderUpdateRequest(name = encryptedName)

                val api = apiManager.getVaultApi(vault.apiUrl)
                val response = api.updateFolder(
                    "Bearer $accessToken",
                    folderId,
                    request
                )

                if (response.isSuccessful) {
                    bitwardenFolderDao.updateName(folderId, newName, encryptedName)
                    loadBitwardenFolders(vaultId)
                    
                    _operationResult.value = FolderOperationResult(
                        success = true,
                        message = "文件夹已重命名为 \"$newName\""
                    )
                } else {
                    val error = response.errorBody()?.string() ?: "Unknown error"
                    if (response.code() == 401) {
                        throw Exception("令牌已失效，请重新登录 Bitwarden")
                    }
                    throw Exception("API 错误: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to rename Bitwarden folder", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "重命名失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 删除 Bitwarden 文件夹
     */
    fun deleteBitwardenFolder(folderId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val vaultId = _selectedBitwardenVaultId.value
                    ?: throw Exception("未选择 Vault")
                val vault = _bitwardenVaults.value.find { it.id == vaultId }
                    ?: throw Exception("Vault not found")
                val accessToken = getAccessToken(vault) ?: throw Exception("令牌不可用，请重新登录 Bitwarden")

                val api = apiManager.getVaultApi(vault.apiUrl)
                val response = api.deleteFolder(
                    "Bearer $accessToken",
                    folderId
                )

                if (response.isSuccessful) {
                    bitwardenFolderDao.deleteByBitwardenId(folderId)
                    categoryDao.unlinkByFolderId(folderId)
                    loadBitwardenFolders(vaultId)
                    
                    _operationResult.value = FolderOperationResult(
                        success = true,
                        message = "文件夹已删除"
                    )
                } else {
                    val error = response.errorBody()?.string() ?: "Unknown error"
                    if (response.code() == 401) {
                        throw Exception("令牌已失效，请重新登录 Bitwarden")
                    }
                    throw Exception("API 错误: $error")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete Bitwarden folder", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "删除失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ========== 工具方法 ==========

    fun clearOperationResult() {
        _operationResult.value = null
    }

    /**
     * 获取当前选中 Vault 的邮箱（用于解锁对话框显示）
     */
    fun getSelectedVaultEmail(): String? {
        val vaultId = _selectedBitwardenVaultId.value ?: return null
        return _bitwardenVaults.value.find { it.id == vaultId }?.email
    }

    /**
     * 解锁 Bitwarden Vault
     */
    fun unlockVault(masterPassword: String) {
        val vaultId = _selectedBitwardenVaultId.value ?: return
        
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _syncStatusMessage.value = "正在解锁 Bitwarden..."
            
            try {
                val result = bitwardenRepository.unlock(vaultId, masterPassword)
                when (result) {
                    is BitwardenRepository.UnlockResult.Success -> {
                        _needsUnlock.value = false
                        _operationResult.value = FolderOperationResult(
                            success = true,
                            message = "Vault 已解锁"
                        )
                        // 执行待操作
                        pendingOperation?.invoke()
                        pendingOperation = null
                    }
                    is BitwardenRepository.UnlockResult.Error -> {
                        _operationResult.value = FolderOperationResult(
                            success = false,
                            message = "解锁失败: ${result.message}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Unlock failed", e)
                _operationResult.value = FolderOperationResult(
                    success = false,
                    message = "解锁失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
                _syncStatusMessage.value = null
            }
        }
    }

    /**
     * 取消解锁
     */
    fun cancelUnlock() {
        _needsUnlock.value = false
        pendingOperation = null
    }

    private fun loadBitwardenVaults() {
        viewModelScope.launch(Dispatchers.IO) {
            val vaults = bitwardenRepository.getAllVaults()
            _bitwardenVaults.value = vaults
            if (_selectedBitwardenVaultId.value == null) {
                vaults.firstOrNull()?.let { selectBitwardenVault(it.id) }
            }
        }
    }

    private fun loadBitwardenFolders(vaultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _bitwardenFolders.value = bitwardenRepository.getFolders(vaultId)
        }
    }

    /**
     * 加载所有 KeePass 数据库的组
     */
    private fun loadAllKeePassGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            val allGroups = mutableListOf<KeePassGroupWithDatabase>()
            
            keepassDatabases.collect { databases ->
                databases.forEach { db ->
                    val result = keePassService.listGroups(db.id)
                    result.getOrNull()?.forEach { group ->
                        allGroups.add(KeePassGroupWithDatabase(db, group))
                    }
                }
                _allKeePassGroups.value = allGroups.toList()
                loadKeePassMappings()
            }
        }
    }

    private fun loadKeePassMappings() {
        viewModelScope.launch(Dispatchers.IO) {
            val allMappings = mutableListOf<KeepassGroupSyncConfig>()
            keepassDatabases.value.forEach { db ->
                allMappings.addAll(keepassGroupDao.getByDatabaseSync(db.id))
            }
            _keepassGroupMappings.value = allMappings
        }
    }

    private suspend fun getCryptoKey(vault: BitwardenVault): BitwardenCrypto.SymmetricCryptoKey? {
        return try {
            val encKeyBytes = decodeStoredKeyBytes(vault.encryptedEncKey) ?: return null
            val macKeyBytes = decodeStoredKeyBytes(vault.encryptedMacKey) ?: return null
            BitwardenCrypto.SymmetricCryptoKey(encKeyBytes, macKeyBytes)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get crypto key", e)
            null
        }
    }

    private fun decodeStoredKeyBytes(encrypted: String?): ByteArray? {
        if (encrypted.isNullOrBlank()) return null
        return try {
            // 与 BitwardenRepository.encryptForStorage/decryptFromStorage 保持一致
            val stored = String(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP), Charsets.UTF_8)
            android.util.Base64.decode(stored, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode stored key", e)
            null
        }
    }

    private fun getAccessToken(vault: BitwardenVault): String? {
        val encrypted = vault.encryptedAccessToken ?: return null
        return try {
            String(android.util.Base64.decode(encrypted, android.util.Base64.NO_WRAP), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode access token", e)
            null
        }
    }

    private fun toSyncTypesJson(types: List<String>): String? {
        if (types.isEmpty()) return null
        return types.joinToString(prefix = "[", postfix = "]") { "\"${it.uppercase()}\"" }
    }
}

/**
 * KeePass 组与其所属数据库的组合
 */
data class KeePassGroupWithDatabase(
    val database: LocalKeePassDatabase,
    val group: KeePassGroupInfo
)
