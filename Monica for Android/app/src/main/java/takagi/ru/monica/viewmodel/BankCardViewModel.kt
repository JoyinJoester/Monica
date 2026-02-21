package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

class BankCardViewModel(
    private val repository: SecureItemRepository,
    context: Context? = null,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null,
    securityManager: SecurityManager? = null
) : ViewModel() {

    private val keepassService = if (context != null && localKeePassDatabaseDao != null && securityManager != null) {
        KeePassKdbxService(context.applicationContext, localKeePassDatabaseDao, securityManager)
    } else {
        null
    }

    fun syncAllKeePassCards() {
        viewModelScope.launch {
            val dao = localKeePassDatabaseDao ?: return@launch
            val dbs = withContext(Dispatchers.IO) { dao.getAllDatabasesSync() }
            dbs.forEach { syncKeePassCards(it.id) }
        }
    }

    fun syncKeePassCards(databaseId: Long) {
        viewModelScope.launch {
            val snapshots = keepassService
                ?.readSecureItems(databaseId, setOf(ItemType.BANK_CARD))
                ?.getOrNull()
                ?: return@launch

            val existingCards = repository.getItemsByType(ItemType.BANK_CARD).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.BANK_CARD }

                val existing = existingBySource ?: existingCards.firstOrNull {
                    it.itemType == ItemType.BANK_CARD &&
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
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null
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
                bitwardenFolderId = bitwardenFolderId,
                syncStatus = if (bitwardenVaultId != null) "PENDING" else "NONE",
                createdAt = Date(),
                updatedAt = Date(),
                imagePaths = imagePaths
            )
            val newId = repository.insertItem(item)
            if (keepassDatabaseId != null) {
                val syncResult = keepassService?.updateSecureItem(keepassDatabaseId, item.copy(id = newId))
                if (syncResult?.isFailure == true) {
                    Log.e("BankCardViewModel", "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
                }
            }
            
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
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null
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
                    bitwardenFolderId = bitwardenFolderId,
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
                val oldKeepassId = existingItem.keepassDatabaseId
                val newKeepassId = updatedItem.keepassDatabaseId
                if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                    val deleteResult = keepassService?.deleteSecureItems(oldKeepassId, listOf(existingItem))
                    if (deleteResult?.isFailure == true) {
                        Log.e("BankCardViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
                if (newKeepassId != null) {
                    val updateResult = keepassService?.updateSecureItem(newKeepassId, updatedItem)
                    if (updateResult?.isFailure == true) {
                        Log.e("BankCardViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                    }
                }
                
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

    fun moveCardToStorage(
        id: Long,
        categoryId: Long?,
        keepassDatabaseId: Long?,
        keepassGroupPath: String?,
        bitwardenVaultId: Long?,
        bitwardenFolderId: String?
    ) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existingItem ->
                val updatedItem = existingItem.copy(
                    categoryId = categoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    keepassGroupPath = keepassGroupPath,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenFolderId = bitwardenFolderId,
                    bitwardenLocalModified = existingItem.bitwardenCipherId != null && bitwardenVaultId != null,
                    syncStatus = if (bitwardenVaultId != null) {
                        if (existingItem.bitwardenCipherId != null) "PENDING" else existingItem.syncStatus
                    } else {
                        "NONE"
                    },
                    updatedAt = Date()
                )
                repository.updateItem(updatedItem)

                val oldKeepassId = existingItem.keepassDatabaseId
                val newKeepassId = updatedItem.keepassDatabaseId
                if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                    val deleteResult = keepassService?.deleteSecureItems(oldKeepassId, listOf(existingItem))
                    if (deleteResult?.isFailure == true) {
                        Log.e("BankCardViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
                if (newKeepassId != null) {
                    val updateResult = keepassService?.updateSecureItem(newKeepassId, updatedItem)
                    if (updateResult?.isFailure == true) {
                        Log.e("BankCardViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                    }
                }
            }
        }
    }
    
    // 删除银行卡
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteCard(id: Long, softDelete: Boolean = true) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                if (item.keepassDatabaseId != null) {
                    val deleteResult = keepassService?.deleteSecureItems(item.keepassDatabaseId, listOf(item))
                    if (deleteResult?.isFailure == true) {
                        Log.e("BankCardViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
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
