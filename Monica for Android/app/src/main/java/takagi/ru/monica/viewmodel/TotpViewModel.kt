package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.repository.SecureItemRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

/**
 * TOTP验证器ViewModel
 */
class TotpViewModel(
    private val repository: SecureItemRepository
) : ViewModel() {
    
    // 搜索查询
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    // TOTP项目列表
    val totpItems: StateFlow<List<SecureItem>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) {
                repository.getItemsByType(ItemType.TOTP)
            } else {
                repository.searchItemsByType(ItemType.TOTP, query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * 更新搜索查询
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * 根据密钥查找现有的TOTP项目
     */
    fun findTotpBySecret(secret: String): SecureItem? {
        return totpItems.value.find { item ->
            try {
                val data = Json.decodeFromString<TotpData>(item.itemData)
                data.secret == secret
            } catch (e: Exception) {
                false
            }
        }
    }

    /**
     * 根据ID获取TOTP项目
     */
    suspend fun getTotpItemById(id: Long): SecureItem? {
        return repository.getItemById(id)
    }
    
    /**
     * 保存TOTP项目
     */
    fun saveTotpItem(
        id: Long?,
        title: String,
        notes: String,
        totpData: TotpData,
        isFavorite: Boolean = false
    ) {
        viewModelScope.launch {
            try {
                val itemDataJson = Json.encodeToString(totpData)
                
                val item = if (id != null && id > 0) {
                    // 更新现有项目
                    val existing = repository.getItemById(id)
                    existing?.copy(
                        title = title,
                        notes = notes,
                        itemData = itemDataJson,
                        updatedAt = Date()
                    ) ?: return@launch
                } else {
                    // 创建新项目
                    SecureItem(
                        itemType = ItemType.TOTP,
                        title = title,
                        notes = notes,
                        itemData = itemDataJson,
                        isFavorite = isFavorite,
                        createdAt = Date(),
                        updatedAt = Date(),
                        imagePaths = ""
                    )
                }
                
                if (id != null && id > 0) {
                    repository.updateItem(item)
                } else {
                    repository.insertItem(item)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // TODO: 处理错误
            }
        }
    }
    
    /**
     * 删除TOTP项目
     */
    fun deleteTotpItem(item: SecureItem) {
        viewModelScope.launch {
            repository.deleteItem(item)
        }
    }
    
    /**
     * 切换收藏状态
     */
    fun toggleFavorite(id: Long, isFavorite: Boolean) {
        viewModelScope.launch {
            repository.updateFavoriteStatus(id, isFavorite)
        }
    }
    
    /**
     * 更新排序顺序
     */
    fun updateSortOrders(items: List<Pair<Long, Int>>) {
        viewModelScope.launch {
            repository.updateSortOrders(items)
        }
    }
    
    /**
     * HOTP专用: 增加计数器并重新生成验证码
     * @param itemId HOTP项目ID
     */
    fun incrementHotpCounter(itemId: Long) {
        viewModelScope.launch {
            try {
                val item = repository.getItemById(itemId) ?: return@launch
                
                // 解析TOTP数据
                val totpData = Json.decodeFromString<TotpData>(item.itemData)
                
                // 只处理HOTP类型
                if (totpData.otpType != takagi.ru.monica.data.model.OtpType.HOTP) {
                    return@launch
                }
                
                // 增加计数器
                val updatedTotpData = totpData.copy(counter = totpData.counter + 1)
                val updatedItemData = Json.encodeToString(updatedTotpData)
                
                // 更新数据库
                val updatedItem = item.copy(
                    itemData = updatedItemData,
                    updatedAt = Date()
                )
                
                repository.updateItem(updatedItem)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
