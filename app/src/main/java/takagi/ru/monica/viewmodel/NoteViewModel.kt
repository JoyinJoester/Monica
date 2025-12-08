package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.NoteData
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

class NoteViewModel(
    private val repository: SecureItemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
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
    
    // 添加笔记
    fun addNote(
        content: String,
        tags: List<String> = emptyList(),
        isFavorite: Boolean = false
    ) {
        viewModelScope.launch {
            val noteData = NoteData(
                content = content,
                tags = tags,
                isMarkdown = false
            )
            
            // 自动生成标题：取前20个字符，如果内容超过20个字符则添加省略号
            val title = if (content.length > 20) {
                content.take(20) + "..."
            } else {
                content.ifEmpty { "New Note" }
            }
            
            val item = SecureItem(
                id = 0,
                itemType = ItemType.NOTE,
                title = title,
                notes = content, // 将内容同时也保存在 notes 字段以便搜索
                itemData = Json.encodeToString(noteData),
                isFavorite = isFavorite,
                createdAt = Date(),
                updatedAt = Date()
            )
            repository.insertItem(item)
        }
    }
    
    // 更新笔记
    fun updateNote(
        id: Long,
        content: String,
        tags: List<String> = emptyList(),
        isFavorite: Boolean,
        createdAt: Date
    ) {
        viewModelScope.launch {
            val noteData = NoteData(
                content = content,
                tags = tags,
                isMarkdown = false
            )
            
            // 自动生成标题
            val title = if (content.length > 20) {
                content.take(20) + "..."
            } else {
                content.ifEmpty { "New Note" }
            }
            
            val item = SecureItem(
                id = id,
                itemType = ItemType.NOTE,
                title = title,
                notes = content,
                itemData = Json.encodeToString(noteData),
                isFavorite = isFavorite,
                createdAt = createdAt,
                updatedAt = Date()
            )
            repository.updateItem(item)
        }
    }
    
    // 删除笔记
    fun deleteNote(item: SecureItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }

    // 批量删除笔记
    fun deleteNotes(items: List<SecureItem>) {
        viewModelScope.launch {
            items.forEach { repository.deleteItem(it) }
        }
    }
}
