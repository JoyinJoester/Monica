package takagi.ru.monica.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.repository.KeePassWorkspaceSnapshot
import takagi.ru.monica.utils.KeePassEntryData
import takagi.ru.monica.utils.KeePassGroupInfo
import takagi.ru.monica.utils.KeePassSecureItemData

data class KeePassWorkspaceUiState(
    val databaseId: Long? = null,
    val isLoading: Boolean = false,
    val passwords: List<KeePassEntryData> = emptyList(),
    val secureItems: List<KeePassSecureItemData> = emptyList(),
    val groups: List<KeePassGroupInfo> = emptyList(),
    val errorMessage: String? = null,
    val lastLoadedAt: Long? = null
)

class KeePassWorkspaceViewModel(
    application: Application,
    private val repository: KeePassWorkspaceRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(KeePassWorkspaceUiState())
    val uiState: StateFlow<KeePassWorkspaceUiState> = _uiState.asStateFlow()

    fun openWorkspace(databaseId: Long) {
        if (_uiState.value.databaseId == databaseId && _uiState.value.passwords.isNotEmpty()) {
            return
        }
        refreshWorkspace(databaseId)
    }

    fun refreshWorkspace(databaseId: Long? = null) {
        val resolvedDatabaseId = databaseId ?: _uiState.value.databaseId ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    databaseId = resolvedDatabaseId,
                    isLoading = true,
                    errorMessage = null
                )
            }
            val snapshotResult = repository.loadWorkspace(resolvedDatabaseId)
            snapshotResult
                .onSuccess { snapshot ->
                    _uiState.value = snapshot.toUiState(resolvedDatabaseId)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            databaseId = resolvedDatabaseId,
                            isLoading = false,
                            errorMessage = error.message ?: "KeePass 工作区加载失败"
                        )
                    }
                }
        }
    }

    fun createGroup(
        databaseId: Long? = null,
        groupName: String,
        parentPath: String? = null,
        onResult: (Result<KeePassGroupInfo>) -> Unit = {}
    ) {
        val resolvedDatabaseId = databaseId ?: _uiState.value.databaseId ?: return
        viewModelScope.launch {
            val result = repository.createGroup(resolvedDatabaseId, groupName, parentPath)
            if (result.isSuccess) {
                refreshWorkspace(resolvedDatabaseId)
            }
            onResult(result)
        }
    }

    fun renameGroup(
        databaseId: Long? = null,
        groupPath: String,
        newName: String,
        onResult: (Result<KeePassGroupInfo>) -> Unit = {}
    ) {
        val resolvedDatabaseId = databaseId ?: _uiState.value.databaseId ?: return
        viewModelScope.launch {
            val result = repository.renameGroup(resolvedDatabaseId, groupPath, newName)
            if (result.isSuccess) {
                refreshWorkspace(resolvedDatabaseId)
            }
            onResult(result)
        }
    }

    fun deleteGroup(
        databaseId: Long? = null,
        groupPath: String,
        onResult: (Result<Unit>) -> Unit = {}
    ) {
        val resolvedDatabaseId = databaseId ?: _uiState.value.databaseId ?: return
        viewModelScope.launch {
            val result = repository.deleteGroup(resolvedDatabaseId, groupPath)
            if (result.isSuccess) {
                refreshWorkspace(resolvedDatabaseId)
            }
            onResult(result)
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun KeePassWorkspaceSnapshot.toUiState(databaseId: Long): KeePassWorkspaceUiState {
        return KeePassWorkspaceUiState(
            databaseId = databaseId,
            isLoading = false,
            passwords = passwords,
            secureItems = secureItems,
            groups = groups,
            errorMessage = null,
            lastLoadedAt = System.currentTimeMillis()
        )
    }
}