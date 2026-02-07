package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.NoteData
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

class NoteViewModel(
    private val repository: SecureItemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 笔记列表布局偏好 (true = 网格, false = 列表)
    private val _isGridLayout = MutableStateFlow(true)
    val isGridLayout: StateFlow<Boolean> = _isGridLayout.asStateFlow()
    
    fun setGridLayout(isGrid: Boolean) {
        _isGridLayout.value = isGrid
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
        isFavorite: Boolean = false
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
                createdAt = Date(),
                updatedAt = Date()
            )
            val newId = repository.insertItem(item)
            
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
        createdAt: Date
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
                createdAt = createdAt,
                updatedAt = Date()
            )
            repository.updateItem(item)
            
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
