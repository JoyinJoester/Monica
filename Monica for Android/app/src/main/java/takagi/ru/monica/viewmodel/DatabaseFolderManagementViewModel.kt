package takagi.ru.monica.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
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

enum class DatabaseSourceType {
    LOCAL,
    KEEPASS
}

data class DatabaseSourceOption(
    val type: DatabaseSourceType,
    val databaseId: Long? = null,
    val name: String
) {
    companion object {
        fun local(): DatabaseSourceOption = DatabaseSourceOption(
            type = DatabaseSourceType.LOCAL,
            databaseId = null,
            name = "Monica 本地"
        )
    }
}

class DatabaseFolderManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PasswordDatabase.getDatabase(application)
    private val categoryDao = database.categoryDao()
    private val keepassDao = database.localKeePassDatabaseDao()
    private val keepassGroupDao: KeepassGroupSyncConfigDao = database.keepassGroupSyncConfigDao()
    private val passwordEntryDao = database.passwordEntryDao()
    private val settingsManager = SettingsManager(application)
    private val bitwardenRepository = BitwardenRepository.getInstance(application)
    private val keePassService = KeePassKdbxService(
        application.applicationContext,
        keepassDao,
        SecurityManager(application.applicationContext)
    )

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

    private val _selectedSource = MutableStateFlow(DatabaseSourceOption.local())
    val selectedSource: StateFlow<DatabaseSourceOption> = _selectedSource.asStateFlow()

    private val _bitwardenVaults = MutableStateFlow<List<BitwardenVault>>(emptyList())
    val bitwardenVaults: StateFlow<List<BitwardenVault>> = _bitwardenVaults.asStateFlow()

    private val _selectedBitwardenVaultId = MutableStateFlow<Long?>(null)
    val selectedBitwardenVaultId: StateFlow<Long?> = _selectedBitwardenVaultId.asStateFlow()

    private val _bitwardenFolders = MutableStateFlow<List<BitwardenFolder>>(emptyList())
    val bitwardenFolders: StateFlow<List<BitwardenFolder>> = _bitwardenFolders.asStateFlow()

    private val _keepassGroups = MutableStateFlow<List<KeePassGroupInfo>>(emptyList())
    val keepassGroups: StateFlow<List<KeePassGroupInfo>> = _keepassGroups.asStateFlow()

    private val _keepassGroupMappings = MutableStateFlow<List<KeepassGroupSyncConfig>>(emptyList())
    val keepassGroupMappings: StateFlow<List<KeepassGroupSyncConfig>> = _keepassGroupMappings.asStateFlow()

    init {
        loadBitwardenVaults()
        refreshSelectedSource()
    }

    fun selectSource(option: DatabaseSourceOption) {
        _selectedSource.value = option
        refreshSelectedSource()
    }

    fun selectBitwardenVault(vaultId: Long) {
        _selectedBitwardenVaultId.value = vaultId
        loadBitwardenFolders(vaultId)
    }

    fun refreshBitwardenFolders() {
        _selectedBitwardenVaultId.value?.let { loadBitwardenFolders(it) }
    }

    fun refreshKeePassGroups() {
        val source = _selectedSource.value
        if (source.type == DatabaseSourceType.KEEPASS && source.databaseId != null) {
            loadKeePassGroups(source.databaseId)
            loadKeePassMappings(source.databaseId)
        }
    }

    fun updateBitwardenUploadAll(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.updateBitwardenUploadAll(enabled)
        }
    }

    fun applyUploadAll(vaultId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkedCategories = categoryDao
                .getBitwardenLinkedCategoriesSync()
                .filter { it.bitwardenVaultId == vaultId && !it.bitwardenFolderId.isNullOrBlank() }

            linkedCategories.forEach { category ->
                val folderId = category.bitwardenFolderId ?: return@forEach
                passwordEntryDao.bindCategoryToBitwarden(
                    categoryId = category.id,
                    vaultId = vaultId,
                    folderId = folderId
                )
            }
        }
    }

    fun linkCategoryToBitwarden(
        categoryId: Long,
        vaultId: Long,
        folderId: String,
        syncTypes: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.linkToBitwarden(
                categoryId = categoryId,
                vaultId = vaultId,
                folderId = folderId,
                syncTypes = toSyncTypesJson(syncTypes)
            )
        }
    }

    fun unlinkCategoryFromBitwarden(categoryId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.unlinkFromBitwarden(categoryId)
        }
    }

    fun updateCategorySyncTypes(categoryId: Long, syncTypes: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            categoryDao.updateSyncTypes(categoryId, toSyncTypesJson(syncTypes))
        }
    }

    fun linkKeePassGroup(
        databaseId: Long,
        groupPath: String,
        groupUuid: String?,
        vaultId: Long,
        folderId: String,
        syncTypes: List<String>
    ) {
        viewModelScope.launch(Dispatchers.IO) {
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
        }
    }

    fun unlinkKeePassGroup(databaseId: Long, groupPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            keepassGroupDao.unlink(databaseId, groupPath)
            loadKeePassMappings(databaseId)
        }
    }

    fun updateKeePassGroupSyncTypes(databaseId: Long, groupPath: String, syncTypes: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            keepassGroupDao.updateSyncTypes(databaseId, groupPath, toSyncTypesJson(syncTypes))
            loadKeePassMappings(databaseId)
        }
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

    private fun refreshSelectedSource() {
        val source = _selectedSource.value
        if (source.type == DatabaseSourceType.KEEPASS && source.databaseId != null) {
            loadKeePassGroups(source.databaseId)
            loadKeePassMappings(source.databaseId)
        } else {
            _keepassGroups.value = emptyList()
            _keepassGroupMappings.value = emptyList()
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

    private fun toSyncTypesJson(types: List<String>): String? {
        if (types.isEmpty()) return null
        return types.joinToString(prefix = "[", postfix = "]") { "\"${it.uppercase()}\"" }
    }
}
