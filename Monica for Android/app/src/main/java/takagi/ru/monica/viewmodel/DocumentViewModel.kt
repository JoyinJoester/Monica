package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.DocumentData
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

class DocumentViewModel(
    private val repository: SecureItemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 获取所有证件
    val allDocuments: Flow<List<SecureItem>> = repository.getItemsByType(ItemType.DOCUMENT)
        .onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 根据ID获取证件
    suspend fun getDocumentById(id: Long): SecureItem? {
        return repository.getItemById(id)
    }
    
    // 添加证件
    fun addDocument(
        title: String,
        documentData: DocumentData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = ""
    ) {
        viewModelScope.launch {
            val item = SecureItem(
                id = 0,
                itemType = ItemType.DOCUMENT,
                title = title,
                itemData = Json.encodeToString(documentData),
                notes = notes,
                isFavorite = isFavorite,
                createdAt = Date(),
                updatedAt = Date(),
                imagePaths = imagePaths
            )
            repository.insertItem(item)
        }
    }
    
    // 更新证件
    fun updateDocument(
        id: Long,
        title: String,
        documentData: DocumentData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = ""
    ) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existingItem ->
                val updatedItem = existingItem.copy(
                    title = title,
                    itemData = Json.encodeToString(documentData),
                    notes = notes,
                    isFavorite = isFavorite,
                    updatedAt = Date(),
                    imagePaths = imagePaths
                )
                repository.updateItem(updatedItem)
            }
        }
    }
    
    // 删除证件
    fun deleteDocument(id: Long) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                repository.deleteItem(item)
            }
        }
    }
    
    // 切换收藏状态
    fun toggleFavorite(id: Long) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                repository.updateItem(item.copy(
                    isFavorite = !item.isFavorite,
                    updatedAt = Date()
                ))
            }
        }
    }
    
    // 更新排序顺序
    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }
    
    // 搜索证件
    fun searchDocuments(query: String): Flow<List<SecureItem>> {
        return repository.searchItems(query)
    }
    
    // 解析证件数据
    fun parseDocumentData(jsonData: String): DocumentData? {
        return try {
            Json.decodeFromString<DocumentData>(jsonData)
        } catch (e: Exception) {
            null
        }
    }
}
