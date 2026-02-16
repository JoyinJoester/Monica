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
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.util.Date

class DocumentViewModel(
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

    fun syncAllKeePassDocuments() {
        viewModelScope.launch {
            val dao = localKeePassDatabaseDao ?: return@launch
            val dbs = withContext(Dispatchers.IO) { dao.getAllDatabasesSync() }
            dbs.forEach { syncKeePassDocuments(it.id) }
        }
    }

    fun syncKeePassDocuments(databaseId: Long) {
        viewModelScope.launch {
            val snapshots = keepassService
                ?.readSecureItems(databaseId, setOf(ItemType.DOCUMENT))
                ?.getOrNull()
                ?: return@launch

            val existingDocs = repository.getItemsByType(ItemType.DOCUMENT).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.DOCUMENT }

                val existing = existingBySource ?: existingDocs.firstOrNull {
                    it.itemType == ItemType.DOCUMENT &&
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
    
    // 添加证件
    fun addDocument(
        title: String,
        documentData: DocumentData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        keepassDatabaseId: Long? = null
    ) {
        viewModelScope.launch {
            val item = SecureItem(
                id = 0,
                itemType = ItemType.DOCUMENT,
                title = title,
                itemData = Json.encodeToString(documentData),
                notes = notes,
                isFavorite = isFavorite,
                keepassDatabaseId = keepassDatabaseId,
                createdAt = Date(),
                updatedAt = Date(),
                imagePaths = imagePaths
            )
            val newId = repository.insertItem(item)
            if (keepassDatabaseId != null) {
                val syncResult = keepassService?.updateSecureItem(keepassDatabaseId, item.copy(id = newId))
                if (syncResult?.isFailure == true) {
                    Log.e("DocumentViewModel", "KeePass write failed: ${syncResult.exceptionOrNull()?.message}")
                }
            }
            
            // 记录创建操作
            OperationLogger.logCreate(
                itemType = OperationLogItemType.DOCUMENT,
                itemId = newId,
                itemTitle = title
            )
        }
    }
    
    // 更新证件
    fun updateDocument(
        id: Long,
        title: String,
        documentData: DocumentData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        keepassDatabaseId: Long? = null
    ) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existingItem ->
                val oldDocData = parseDocumentData(existingItem.itemData)
                val changes = mutableListOf<FieldChange>()
                
                // 检测标题变化
                if (existingItem.title != title) {
                    changes.add(FieldChange("标题", existingItem.title, title))
                }
                // 检测备注变化
                if (existingItem.notes != notes) {
                    changes.add(FieldChange("备注", existingItem.notes, notes))
                }
                // 检测证件号变化
                if (oldDocData?.documentNumber != documentData.documentNumber) {
                    changes.add(FieldChange("证件号", oldDocData?.documentNumber ?: "", documentData.documentNumber))
                }
                // 检测姓名变化
                if (oldDocData?.fullName != documentData.fullName) {
                    changes.add(FieldChange("姓名", oldDocData?.fullName ?: "", documentData.fullName))
                }
                
                val updatedItem = existingItem.copy(
                    title = title,
                    itemData = Json.encodeToString(documentData),
                    notes = notes,
                    isFavorite = isFavorite,
                    keepassDatabaseId = keepassDatabaseId,
                    updatedAt = Date(),
                    imagePaths = imagePaths
                )
                repository.updateItem(updatedItem)
                val oldKeepassId = existingItem.keepassDatabaseId
                val newKeepassId = updatedItem.keepassDatabaseId
                if (oldKeepassId != null && oldKeepassId != newKeepassId) {
                    val deleteResult = keepassService?.deleteSecureItems(oldKeepassId, listOf(existingItem))
                    if (deleteResult?.isFailure == true) {
                        Log.e("DocumentViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
                if (newKeepassId != null) {
                    val updateResult = keepassService?.updateSecureItem(newKeepassId, updatedItem)
                    if (updateResult?.isFailure == true) {
                        Log.e("DocumentViewModel", "KeePass update failed: ${updateResult.exceptionOrNull()?.message}")
                    }
                }
                
                // 记录更新操作 - 始终记录，即使没有检测到字段变更
                OperationLogger.logUpdate(
                    itemType = OperationLogItemType.DOCUMENT,
                    itemId = id,
                    itemTitle = title,
                    changes = if (changes.isEmpty()) listOf(FieldChange("更新", "编辑于", java.text.SimpleDateFormat("HH:mm").format(java.util.Date()))) else changes
                )
            }
        }
    }
    
    // 删除证件
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteDocument(id: Long, softDelete: Boolean = true) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                if (item.keepassDatabaseId != null) {
                    val deleteResult = keepassService?.deleteSecureItems(item.keepassDatabaseId, listOf(item))
                    if (deleteResult?.isFailure == true) {
                        Log.e("DocumentViewModel", "KeePass delete failed: ${deleteResult.exceptionOrNull()?.message}")
                    }
                }
                if (softDelete) {
                    // 软删除：移动到回收站
                    repository.softDeleteItem(item)
                    // 记录移入回收站操作
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.DOCUMENT,
                        itemId = id,
                        itemTitle = item.title,
                        detail = "移入回收站"
                    )
                } else {
                    // 永久删除
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.DOCUMENT,
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
