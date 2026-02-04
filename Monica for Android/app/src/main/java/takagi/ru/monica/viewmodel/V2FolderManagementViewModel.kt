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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import takagi.ru.monica.bitwarden.api.BitwardenApiManager
import takagi.ru.monica.bitwarden.api.FolderCreateRequest
import takagi.ru.monica.bitwarden.api.FolderUpdateRequest
import takagi.ru.monica.bitwarden.crypto.BitwardenCrypto
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.bitwarden.BitwardenFolder
import takagi.ru.monica.data.bitwarden.BitwardenVault
import takagi.ru.monica.data.KeepassGroupSyncConfig
import takagi.ru.monica.data.KeepassGroupSyncConfigDao
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassKdbxService
import takagi.ru.monica.utils.SettingsManager

/**
 * 数据源类型
 */
enum class VaultType {
    MONICA_LOCAL,
    KEEPASS
}

/**
 * 操作结果
 */
data class OperationResult(
    val success: Boolean,
    val message: String
)

/**
 * V2 文件夹管理 ViewModel
 * 
 * 功能：
 * 1. 管理本地文件夹（Category）
 * 2. 管理 KeePass 组
 * 3. 管理 Bitwarden 文件夹（创建、编辑、删除）
 * 4. 关联本地/KeePass 到 Bitwarden
 */
class V2FolderManagementViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "V2FolderMgmtVM"
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

    val categories = categoryDao.getAllCategories().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    val keepassDatabases = keepassDao.getAllDatabases().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        emptyList()
    )

    private val _selectedVaultType = MutableStateFlow(VaultType.MONICA_LOCAL)
    val selectedVaultType: StateFlow<VaultType> = _selectedVaultType.asStateFlow()

    private val _selectedKeePassDatabaseId = MutableStateFlow<Long?>(null)
    val selectedKeePassDatabaseId: StateFlow<Long?> = _selectedKeePassDatabaseId.asStateFlow()

    private val _bitwardenVaults = MutableStateFlow<List<BitwardenVault>>(emptyList())
    val bitwardenVaults: StateFlow<List<BitwardenVault>> = _bitwardenVaults.asStateFlow()

    private val _selectedBitwardenVaultId = MutableStateFlow<Long?>(null)
    val selectedBitwardenVaultId: StateFlow<Long?> = _selectedBitwardenVaultId.asStateFlow()

    private val _bitwardenFolders = MutableStateFlow<List<BitwardenFolder>>(emptyList())
    val bitwardenFolders: StateFlow<List<BitwardenFolder>> = _bitwardenFolders.asStateFlow()

    private var bitwardenFolderJob: Job? = null

    private val _keepassGroups = MutableStateFlow<List<KeePassGroupInfo>>(emptyList())
    val keepassGroups: StateFlow<List<KeePassGroupInfo>> = _keepassGroups.asStateFlow()

    private val _keepassGroupMappings = MutableStateFlow<List<KeepassGroupSyncConfig>>(emptyList())
    val keepassGroupMappings: StateFlow<List<KeepassGroupSyncConfig>> = _keepassGroupMappings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _operationResult = MutableStateFlow<OperationResult?>(null)
    val operationResult: StateFlow<OperationResult?> = _operationResult.asStateFlow()

    init {
        loadBitwardenVaults()
    }

    // ========== 数据源选择 ==========

    fun selectVaultType(type: VaultType) {
        _selectedVaultType.value = type
        when (type) {
            VaultType.MONICA_LOCAL -> {
                _keepassGroups.value = emptyList()
            }
            VaultType.KEEPASS -> {
                // 自动选择第一个 KeePass 数据库
                viewModelScope.launch {
                    keepassDatabases.collect { databases ->
                        if (_selectedKeePassDatabaseId.value == null && databases.isNotEmpty()) {
                            selectKeePassDatabase(databases.first().id)
                        }
                        return@collect
                    }
                }
            }
        }
    }

    fun selectKeePassDatabase(databaseId: Long) {
        _selectedKeePassDatabaseId.value = databaseId
        loadKeePassGroups(databaseId)
        loadKeePassMappings(databaseId)
    }

    fun selectBitwardenVault(vaultId: Long) {
        _selectedBitwardenVaultId.value = vaultId
        bitwardenFolderJob?.cancel()
        bitwardenFolderJob = viewModelScope.launch(Dispatchers.IO) {
            bitwardenFolderDao.getFoldersByVaultFlow(vaultId)
                .collectLatest { folders ->
                    _bitwardenFolders.value = folders
                }
        }
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
                _operationResult.value = OperationResult(
                    success = true,
                    message = "已将所有数据标记为同步到 Bitwarden"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to apply upload all", e)
                _operationResult.value = OperationResult(
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
                _operationResult.value = OperationResult(
                    success = true,
                    message = "文件夹已关联到 Bitwarden"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link category", e)
                _operationResult.value = OperationResult(
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
                _operationResult.value = OperationResult(
                    success = true,
                    message = "已解除关联"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unlink category", e)
                _operationResult.value = OperationResult(
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
                loadKeePassMappings(databaseId)
                _operationResult.value = OperationResult(
                    success = true,
                    message = "KeePass 组已关联到 Bitwarden"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to link KeePass group", e)
                _operationResult.value = OperationResult(
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
                loadKeePassMappings(databaseId)
                _operationResult.value = OperationResult(
                    success = true,
                    message = "已解除关联"
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unlink KeePass group", e)
                _operationResult.value = OperationResult(
                    success = false,
                    message = "解除关联失败: ${e.message}"
                )
            }
        }
    }

    // ========== Bitwarden 文件夹操作 ==========

    /**
     * 创建 Bitwarden 文件夹
     */
    fun createBitwardenFolder(vaultId: Long, name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val vault = _bitwardenVaults.value.find { it.id == vaultId }
                    ?: throw Exception("Vault not found")
                val accessToken = getAccessToken(vault) ?: throw Exception("令牌不可用，请重新登录 Bitwarden")

                // 获取加密密钥
                val cryptoKey = getCryptoKey(vault)
                    ?: throw Exception("无法获取加密密钥")

                // 加密文件夹名称
                val encryptedName = BitwardenCrypto.encryptString(name, cryptoKey)

                // 创建 API 请求
                val request = FolderCreateRequest(name = encryptedName)

                // 调用 API
                val api = apiManager.getVaultApi(vault.apiUrl)
                val response = api.createFolder(
                    "Bearer $accessToken",
                    request
                )

                if (response.isSuccessful && response.body() != null) {
                    val created = response.body()!!
                    
                    // 保存到本地数据库
                    val folder = BitwardenFolder(
                        vaultId = vaultId,
                        bitwardenFolderId = created.id,
                        name = name,
                        encryptedName = encryptedName,
                        revisionDate = created.revisionDate ?: "",
                        lastSyncedAt = System.currentTimeMillis()
                    )
                    bitwardenFolderDao.insert(folder)
                    
                    // 刷新列表
                    loadBitwardenFolders(vaultId)
                    
                    _operationResult.value = OperationResult(
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
                _operationResult.value = OperationResult(
                    success = false,
                    message = "创建失败: ${e.message}"
                )
            } finally {
                _isLoading.value = false
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

                // 获取加密密钥
                val cryptoKey = getCryptoKey(vault)
                    ?: throw Exception("无法获取加密密钥")

                // 加密新名称
                val encryptedName = BitwardenCrypto.encryptString(newName, cryptoKey)

                // 创建 API 请求
                val request = FolderUpdateRequest(name = encryptedName)

                // 调用 API
                val api = apiManager.getVaultApi(vault.apiUrl)
                val response = api.updateFolder(
                    "Bearer $accessToken",
                    folderId,
                    request
                )

                if (response.isSuccessful) {
                    // 更新本地数据库
                    bitwardenFolderDao.updateName(folderId, newName, encryptedName)
                    
                    // 刷新列表
                    loadBitwardenFolders(vaultId)
                    
                    _operationResult.value = OperationResult(
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
                _operationResult.value = OperationResult(
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

                // 调用 API
                val api = apiManager.getVaultApi(vault.apiUrl)
                val response = api.deleteFolder(
                    "Bearer $accessToken",
                    folderId
                )

                if (response.isSuccessful) {
                    // 从本地数据库删除
                    bitwardenFolderDao.deleteByBitwardenId(folderId)
                    
                    // 解除所有关联到此文件夹的 Category
                    categoryDao.unlinkByFolderId(folderId)
                    
                    // 刷新列表
                    loadBitwardenFolders(vaultId)
                    
                    _operationResult.value = OperationResult(
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
                _operationResult.value = OperationResult(
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

    private fun loadKeePassGroups(databaseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = keePassService.listGroups(databaseId)
            _keepassGroups.value = result.getOrDefault(emptyList())
        }
    }

    private fun loadKeePassMappings(databaseId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            _keepassGroupMappings.value = keepassGroupDao.getByDatabaseSync(databaseId)
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
