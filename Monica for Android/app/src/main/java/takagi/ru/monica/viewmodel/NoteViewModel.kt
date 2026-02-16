package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

data class NoteDraftStorageTarget(
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

class NoteViewModel(
    private val repository: SecureItemRepository,
    context: Context? = null,
    localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null,
    securityManager: SecurityManager? = null
) : ViewModel() {

    private val keepassService = if (context != null && localKeePassDatabaseDao != null && securityManager != null) {
        KeePassKdbxService(context.applicationContext, localKeePassDatabaseDao, securityManager)
    } else {
        null
    }
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 笔记列表布局偏好 (true = 网格, false = 列表)
    private val _isGridLayout = MutableStateFlow(true)
    val isGridLayout: StateFlow<Boolean> = _isGridLayout.asStateFlow()

    private val _draftStorageTarget = MutableStateFlow(NoteDraftStorageTarget())
    val draftStorageTarget: StateFlow<NoteDraftStorageTarget> = _draftStorageTarget.asStateFlow()
    
    fun setGridLayout(isGrid: Boolean) {
        _isGridLayout.value = isGrid
    }

    fun setDraftStorageTarget(target: NoteDraftStorageTarget) {
        _draftStorageTarget.value = target
    }

    fun syncKeePassNotes(databaseId: Long) {
        viewModelScope.launch {
            val snapshots = keepassService
                ?.readSecureItems(databaseId, setOf(ItemType.NOTE))
                ?.getOrNull()
                ?: return@launch

            val existingNotes = repository.getItemsByType(ItemType.NOTE).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.NOTE }

                val existing = existingBySource ?: existingNotes.firstOrNull {
                    it.itemType == ItemType.NOTE &&
                        it.keepassDatabaseId == databaseId &&
                        it.keepassGroupPath == incoming.keepassGroupPath &&
                        it.title == incoming.title
                }

                if (existing == null) {
                    repository.insertItem(incoming)
                } else {
                    repository.updateItem(
                        existing.copy(
                            title = incoming.title,
                            notes = incoming.notes,
                            itemData = incoming.itemData,
                            isFavorite = incoming.isFavorite,
                            imagePaths = incoming.imagePaths,
                            keepassDatabaseId = incoming.keepassDatabaseId,
                            keepassGroupPath = incoming.keepassGroupPath,
                            isDeleted = false,
                            deletedAt = null,
                            updatedAt = Date()
                        )
                    )
                }
            }
        }
    }
    
    // 获取所有笔记
    val allNotes: Flow<List<SecureItem>> = repository.getItemsByType(ItemType.NOTE)
        .onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 根据ID获取笔记
    suspend fun getNoteById(id: Long): SecureItem? {
        return repository.getItemById(id)
    }
    
    /**
     * 快速添加笔记（从底部导航栏快速添加）
     */
    fun quickAddNote(title: String, content: String) {
        if (title.isBlank() && content.isBlank()) return
        val fullContent = if (title.isNotBlank() && content.isNotBlank()) {
            "$title\n\n$content"
        } else if (title.isNotBlank()) {
            title
        } else {
            content
        }
        addNote(
            content = fullContent,
            title = title.takeIf { it.isNotBlank() }
        )
    }
    
    // 添加笔记
    fun addNote(
        content: String,
        title: String? = null,
        tags: List<String> = emptyList(),
        isFavorite: Boolean = false,
        categoryId: Long? = null,
        imagePaths: String = "",
        keepassDatabaseId: Long? = null,
        keepassGroupPath: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null
    ) {
        viewModelScope.launch {
            val noteData = NoteData(
                content = content,
                tags = tags,
                isMarkdown = false
            )
            
            // 显式标题优先；为空时保持旧逻辑自动生成标题
            val resolvedTitle = if (!title.isNullOrBlank()) {
                title.trim()
            } else {
                if (content.length > 20) {
                    content.take(20) + "..."
                } else {
                    content.ifEmpty { "New Note" }
                }
            }
            
            val item = SecureItem(
                id = 0,
                itemType = ItemType.NOTE,
                title = resolvedTitle,
                notes = content, // 将内容同时也保存在 notes 字段以便搜索
                itemData = Json.encodeToString(noteData),
                isFavorite = isFavorite,
                imagePaths = imagePaths,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId,
                syncStatus = if (bitwardenVaultId != null) "PENDING" else "NONE",
                createdAt = Date(),
                updatedAt = Date()
            )
            val newId = repository.insertItem(item)
            if (keepassDatabaseId != null) {
                val syncResult = keepassService?.updateSecureItem(keepassDatabaseId, item.copy(id = newId))
                if (syncResult?.isFailure == true) {
                    Log.e("NoteViewModel", "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
                }
            }
            
            // 记录创建操作
            OperationLogger.logCreate(
                itemType = OperationLogItemType.NOTE,
                itemId = newId,
                itemTitle = resolvedTitle
            )
        }
    }
    
    // 更新笔记
    fun updateNote(
        id: Long,
        content: String,
        title: String? = null,
        tags: List<String> = emptyList(),
        isFavorite: Boolean,
        createdAt: Date,
        categoryId: Long? = null,
        imagePaths: String = "",
        keepassDatabaseId: Long? = null,
        keepassGroupPath: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null
    ) {
        viewModelScope.launch {
            // 获取旧笔记以检测变化
            val existingItem = repository.getItemById(id)
            
            val noteData = NoteData(
                content = content,
                tags = tags,
                isMarkdown = false
            )
            
            // 显式标题优先；为空时保持旧逻辑自动生成标题
            val resolvedTitle = if (!title.isNullOrBlank()) {
                title.trim()
            } else {
                if (content.length > 20) {
                    content.take(20) + "..."
                } else {
                    content.ifEmpty { "New Note" }
                }
            }
            
            val item = SecureItem(
                id = id,
                itemType = ItemType.NOTE,
                title = resolvedTitle,
                notes = content,
                itemData = Json.encodeToString(noteData),
                isFavorite = isFavorite,
                imagePaths = imagePaths,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassGroupPath ?: existingItem?.keepassGroupPath,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenCipherId = existingItem?.bitwardenCipherId,
                bitwardenFolderId = bitwardenFolderId,
                bitwardenRevisionDate = existingItem?.bitwardenRevisionDate,
                bitwardenLocalModified = existingItem?.bitwardenCipherId != null && bitwardenVaultId != null,
                syncStatus = if (bitwardenVaultId != null) {
                    if (existingItem?.bitwardenCipherId != null) "PENDING" else "NONE"
                } else {
                    "NONE"
                },
                createdAt = createdAt,
                updatedAt = Date()
            )
            repository.updateItem(item)
            val oldKeepassId = existingItem?.keepassDatabaseId
            val newKeepassId = item.keepassDatabaseId
            if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                existingItem?.let { oldItem ->
                    val deleteResult = keepassService?.deleteSecureItems(oldKeepassId, listOf(oldItem))
                    if (deleteResult?.isFailure == true) {
                        Log.e("NoteViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
            }
            if (newKeepassId != null) {
                val updateResult = keepassService?.updateSecureItem(newKeepassId, item)
                if (updateResult?.isFailure == true) {
                    Log.e("NoteViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                }
            }
            
            // 记录更新操作 - 始终记录，即使没有检测到字段变更
            val changes = mutableListOf<FieldChange>()
            existingItem?.let { oldItem ->
                if (oldItem.notes != content) {
                    changes.add(FieldChange("内容", oldItem.notes, content))
                }
                // 检测标题变化
                if (oldItem.title != resolvedTitle) {
                    changes.add(FieldChange("标题", oldItem.title, resolvedTitle))
                }
            }
            // 即使没有变更也记录更新操作，以便追踪编辑行为
            OperationLogger.logUpdate(
                itemType = OperationLogItemType.NOTE,
                itemId = id,
                itemTitle = resolvedTitle,
                changes = if (changes.isEmpty()) listOf(FieldChange("更新", "编辑于", java.text.SimpleDateFormat("HH:mm").format(Date()))) else changes
            )
        }
    }
    
    // 删除笔记
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteNote(item: SecureItem, softDelete: Boolean = true) {
        viewModelScope.launch {
            if (softDelete) {
                if (item.keepassDatabaseId != null) {
                    val deleteResult = keepassService?.deleteSecureItems(item.keepassDatabaseId, listOf(item))
                    if (deleteResult?.isFailure == true) {
                        Log.e("NoteViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
                // 软删除：移动到回收站
                repository.softDeleteItem(item)
                // 记录移入回收站操作
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.NOTE,
                    itemId = item.id,
                    itemTitle = item.title,
                    detail = "移入回收站"
                )
            } else {
                // 永久删除
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.NOTE,
                    itemId = item.id,
                    itemTitle = item.title
                )
                repository.deleteItem(item)
            }
        }
    }

    // 批量删除笔记
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteNotes(items: List<SecureItem>, softDelete: Boolean = true) {
        viewModelScope.launch {
            items.forEach { item ->
                if (softDelete) {
                    if (item.keepassDatabaseId != null) {
                        val deleteResult = keepassService?.deleteSecureItems(item.keepassDatabaseId, listOf(item))
                        if (deleteResult?.isFailure == true) {
                            Log.e("NoteViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                        }
                    }
                    // 软删除：移动到回收站
                    repository.softDeleteItem(item)
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.NOTE,
                        itemId = item.id,
                        itemTitle = item.title,
                        detail = "移入回收站"
                    )
                } else {
                    // 永久删除
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.NOTE,
                        itemId = item.id,
                        itemTitle = item.title
                    )
                    repository.deleteItem(item)
                }
            }
        }
    }
}
