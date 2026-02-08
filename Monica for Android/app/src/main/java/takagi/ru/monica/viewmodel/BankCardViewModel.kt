package takagi.ru.monica.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

class BankCardViewModel(
    private val repository: SecureItemRepository
) : ViewModel() {
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 获取所有银行卡
    val allCards: Flow<List<SecureItem>> = repository.getItemsByType(ItemType.BANK_CARD)
        .onStart { _isLoading.value = true }
        .onEach { _isLoading.value = false }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    // 根据ID获取银行卡
    suspend fun getCardById(id: Long): SecureItem? {
        return repository.getItemById(id)
    }
    
    /**
     * 快速添加银行卡（从底部导航栏快速添加）
     */
    fun quickAddBankCard(name: String, cardNumber: String) {
        if (name.isBlank()) return
        val cardData = BankCardData(
            cardNumber = cardNumber,
            cardholderName = "",
            expiryMonth = "",
            expiryYear = "",
            cvv = "",
            bankName = name,
            cardType = CardType.CREDIT
        )
        addCard(title = name, cardData = cardData)
    }
    
    // 添加银行卡
    fun addCard(
        title: String,
        cardData: BankCardData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        categoryId: Long? = null,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null
    ) {
        viewModelScope.launch {
            val item = SecureItem(
                id = 0,
                itemType = ItemType.BANK_CARD,
                title = title,
                itemData = Json.encodeToString(cardData),
                notes = notes,
                isFavorite = isFavorite,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                bitwardenVaultId = bitwardenVaultId,
                syncStatus = if (bitwardenVaultId != null) "PENDING" else "NONE",
                createdAt = Date(),
                updatedAt = Date(),
                imagePaths = imagePaths
            )
            val newId = repository.insertItem(item)
            
            // 记录创建操作
            OperationLogger.logCreate(
                itemType = OperationLogItemType.BANK_CARD,
                itemId = newId,
                itemTitle = title
            )
        }
    }
    
    // 更新银行卡
    fun updateCard(
        id: Long,
        title: String,
        cardData: BankCardData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        categoryId: Long? = null,
        keepassDatabaseId: Long? = null,
        bitwardenVaultId: Long? = null
    ) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existingItem ->
                val oldCardData = parseCardData(existingItem.itemData)
                val changes = mutableListOf<FieldChange>()
                
                // 检测标题变化
                if (existingItem.title != title) {
                    changes.add(FieldChange("标题", existingItem.title, title))
                }
                // 检测备注变化
                if (existingItem.notes != notes) {
                    changes.add(FieldChange("备注", existingItem.notes, notes))
                }
                // 检测卡号变化
                if (oldCardData?.cardNumber != cardData.cardNumber) {
                    changes.add(FieldChange("卡号", oldCardData?.cardNumber ?: "", cardData.cardNumber))
                }
                // 检测持卡人变化
                if (oldCardData?.cardholderName != cardData.cardholderName) {
                    changes.add(FieldChange("持卡人", oldCardData?.cardholderName ?: "", cardData.cardholderName))
                }
                // 检测银行名称变化
                if (oldCardData?.bankName != cardData.bankName) {
                    changes.add(FieldChange("银行", oldCardData?.bankName ?: "", cardData.bankName ?: ""))
                }
                
                val updatedItem = existingItem.copy(
                    title = title,
                    itemData = Json.encodeToString(cardData),
                    notes = notes,
                    isFavorite = isFavorite,
                    categoryId = categoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenLocalModified = existingItem.bitwardenCipherId != null && bitwardenVaultId != null,
                    syncStatus = if (bitwardenVaultId != null) {
                        if (existingItem.bitwardenCipherId != null) "PENDING" else existingItem.syncStatus
                    } else {
                        "NONE"
                    },
                    updatedAt = Date(),
                    imagePaths = imagePaths
                )
                repository.updateItem(updatedItem)
                
                // 记录更新操作 - 始终记录，即使没有检测到字段变更
                OperationLogger.logUpdate(
                    itemType = OperationLogItemType.BANK_CARD,
                    itemId = id,
                    itemTitle = title,
                    changes = if (changes.isEmpty()) listOf(FieldChange("更新", "编辑于", java.text.SimpleDateFormat("HH:mm").format(java.util.Date()))) else changes
                )
            }
        }
    }
    
    // 删除银行卡
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteCard(id: Long, softDelete: Boolean = true) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                if (softDelete) {
                    // 软删除：移动到回收站
                    repository.softDeleteItem(item)
                    // 记录移入回收站操作
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.BANK_CARD,
                        itemId = id,
                        itemTitle = item.title,
                        detail = "移入回收站"
                    )
                } else {
                    // 永久删除
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.BANK_CARD,
                        itemId = id,
                        itemTitle = item.title
                    )
                    repository.deleteItem(item)
                }
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
    
    // 搜索银行卡
    fun searchCards(query: String): Flow<List<SecureItem>> {
        return repository.searchItems(query)
    }
    
    // 解析银行卡数据
    fun parseCardData(jsonData: String): BankCardData? {
        return try {
            Json.decodeFromString<BankCardData>(jsonData)
        } catch (e: Exception) {
            null
        }
    }
}
