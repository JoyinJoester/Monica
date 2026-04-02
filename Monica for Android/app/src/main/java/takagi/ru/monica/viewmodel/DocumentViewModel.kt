package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import takagi.ru.monica.keepass.KeePassSecureItemCreateExecutor
import takagi.ru.monica.keepass.KeePassSecureItemDeleteExecutor
import takagi.ru.monica.keepass.KeePassSecureItemUpdateExecutor
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabaseDao
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.SecureItemOwnership
import takagi.ru.monica.data.asMonicaLocalCopy
import takagi.ru.monica.data.hasOwnershipConflict
import takagi.ru.monica.data.resolveOwnership
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.data.model.DocumentData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import java.util.Date
import java.util.UUID

class DocumentViewModel(
    private val repository: SecureItemRepository,
    context: Context? = null,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null,
    securityManager: SecurityManager? = null
) : ViewModel() {
    private data class KeePassMutationIdentity(
        val groupPath: String?,
        val entryUuid: String?,
        val groupUuid: String?
    )

    private val bitwardenRepository = context?.let { BitwardenRepository.getInstance(it.applicationContext) }

    private val keepassBridge = if (context != null && localKeePassDatabaseDao != null && securityManager != null) {
        KeePassCompatibilityBridge(
            KeePassWorkspaceRepository(
                context = context.applicationContext,
                dao = localKeePassDatabaseDao,
                securityManager = securityManager
            )
        )
    } else {
        null
    }
    private val keepassSecureItemCreateExecutor = KeePassSecureItemCreateExecutor(keepassBridge)
    private val keepassSecureItemDeleteExecutor = KeePassSecureItemDeleteExecutor(keepassBridge)
    private val keepassSecureItemUpdateExecutor = KeePassSecureItemUpdateExecutor(keepassBridge)

    init {
        viewModelScope.launch {
            repairLegacyDetachedKeePassItems()
        }
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
        val item = repository.getItemById(id) ?: return null
        return repository.normalizeLegacyDetachedKeePassItem(item, ::hasKeePassDatabase)
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
            val snapshots = keepassBridge
                ?.readLegacySecureItems(databaseId, setOf(ItemType.DOCUMENT))
                ?.getOrNull()
                ?: return@launch

            val existingDocs = repository.getItemsByType(ItemType.DOCUMENT).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingByUuid = incoming.keepassEntryUuid
                    ?.takeIf { it.isNotBlank() }
                    ?.let { repository.getItemByKeePassUuid(databaseId, it) }
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.DOCUMENT }

                val existing = existingByUuid ?: existingBySource ?: existingDocs.firstOrNull {
                    it.itemType == ItemType.DOCUMENT &&
                        it.keepassDatabaseId == databaseId &&
                        it.keepassGroupPath == incoming.keepassGroupPath &&
                        it.title == incoming.title
                }

                if (existing == null) {
                    repository.insertItem(incoming)
                } else {
                    val isInRecycleBin = snapshot.isInRecycleBin
                    repository.updateItem(
                        existing.copy(
                            title = incoming.title,
                            notes = incoming.notes,
                            itemData = incoming.itemData,
                            isFavorite = incoming.isFavorite,
                            imagePaths = incoming.imagePaths,
                            keepassDatabaseId = incoming.keepassDatabaseId,
                            keepassGroupPath = incoming.keepassGroupPath,
                            keepassEntryUuid = incoming.keepassEntryUuid,
                            keepassGroupUuid = incoming.keepassGroupUuid,
                            isDeleted = isInRecycleBin,
                            deletedAt = if (isInRecycleBin) (existing.deletedAt ?: Date()) else null,
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
        categoryId: Long? = null,
        keepassDatabaseId: Long? = null,
        keepassGroupPath: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null,
        replicaGroupId: String? = null
    ) {
        viewModelScope.launch {
            val keepassIdentity = resolveKeePassMutationIdentity(
                existingItem = null,
                targetDatabaseId = keepassDatabaseId,
                requestedGroupPath = keepassGroupPath
            )
            val item = SecureItem(
                id = 0,
                itemType = ItemType.DOCUMENT,
                title = title,
                itemData = CardWalletDataCodec.encodeDocumentData(documentData),
                notes = notes,
                isFavorite = isFavorite,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassIdentity.groupPath,
                keepassEntryUuid = keepassIdentity.entryUuid,
                keepassGroupUuid = keepassIdentity.groupUuid,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId,
                syncStatus = if (bitwardenVaultId != null) "PENDING" else "NONE",
                replicaGroupId = replicaGroupId,
                createdAt = Date(),
                updatedAt = Date(),
                imagePaths = imagePaths
            )
            val newId = keepassSecureItemCreateExecutor.create(
                item = item,
                insertItem = repository::insertItem,
                rollbackItem = repository::deleteItemById
            ) ?: return@launch
            
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
        categoryId: Long? = null,
        keepassDatabaseId: Long? = null,
        keepassGroupPath: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null,
        replicaGroupId: String? = null
    ) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { existingItem ->
                val keepassIdentity = resolveKeePassMutationIdentity(
                    existingItem = existingItem,
                    targetDatabaseId = keepassDatabaseId,
                    requestedGroupPath = keepassGroupPath
                )
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
                    itemData = CardWalletDataCodec.encodeDocumentData(documentData),
                    notes = notes,
                    isFavorite = isFavorite,
                    categoryId = categoryId,
                    keepassDatabaseId = keepassDatabaseId,
                    keepassGroupPath = keepassIdentity.groupPath,
                    keepassEntryUuid = keepassIdentity.entryUuid,
                    keepassGroupUuid = keepassIdentity.groupUuid,
                    bitwardenVaultId = bitwardenVaultId,
                    bitwardenFolderId = bitwardenFolderId,
                    bitwardenLocalModified = existingItem.bitwardenCipherId != null && bitwardenVaultId != null,
                    syncStatus = if (bitwardenVaultId != null) {
                        if (existingItem.bitwardenCipherId != null) "PENDING" else existingItem.syncStatus
                    } else {
                        "NONE"
                    },
                    replicaGroupId = replicaGroupId ?: existingItem.replicaGroupId,
                    updatedAt = Date(),
                    imagePaths = imagePaths
                )
                repository.updateItem(updatedItem)
                keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = existingItem, updatedItem = updatedItem)
                
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

    suspend fun moveDocumentToStorage(
        id: Long,
        categoryId: Long?,
        keepassDatabaseId: Long?,
        keepassGroupPath: String?,
        bitwardenVaultId: Long?,
        bitwardenFolderId: String?
    ): Boolean {
        val existingItem = repository.getItemById(id) ?: return false
        val target = when {
            bitwardenVaultId != null -> StorageTarget.Bitwarden(bitwardenVaultId, bitwardenFolderId)
            keepassDatabaseId != null -> StorageTarget.KeePass(keepassDatabaseId, keepassGroupPath)
            else -> StorageTarget.MonicaLocal(categoryId)
        }
        if (hasReplicaTargetConflict(
                itemType = ItemType.DOCUMENT,
                itemId = existingItem.id,
                replicaGroupId = existingItem.replicaGroupId,
                target = target
            )
        ) {
            return false
        }
        val keepassIdentity = resolveKeePassMutationIdentity(
            existingItem = existingItem,
            targetDatabaseId = keepassDatabaseId,
            requestedGroupPath = keepassGroupPath
        )
        val updatedItem = existingItem.copy(
            categoryId = categoryId,
            keepassDatabaseId = keepassDatabaseId,
            keepassGroupPath = keepassIdentity.groupPath,
            keepassEntryUuid = keepassIdentity.entryUuid,
            keepassGroupUuid = keepassIdentity.groupUuid,
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
        keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = existingItem, updatedItem = updatedItem)
        return true
    }

    fun saveDocumentAcrossTargets(
        id: Long?,
        title: String,
        documentData: DocumentData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        targets: List<StorageTarget>
    ) {
        viewModelScope.launch {
            val distinctTargets = targets.distinctBy(StorageTarget::stableKey)
            if (distinctTargets.isEmpty()) return@launch

            val existingItem = id?.let { repository.getItemById(it) }?.takeIf { it.itemType == ItemType.DOCUMENT }
            val currentTarget = existingItem?.toStorageTarget() ?: distinctTargets.first()
            val replicaGroupId = existingItem?.replicaGroupId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val existingReplicaKeys = if (existingItem != null) {
                repository.getAllItems().first()
                    .asSequence()
                    .filter {
                        it.itemType == ItemType.DOCUMENT &&
                            it.replicaGroupId == replicaGroupId &&
                            it.id != existingItem.id &&
                            !it.isDeleted
                    }
                    .map { it.toStorageTarget().stableKey }
                    .toSet()
            } else {
                emptySet()
            }

            when (currentTarget) {
                is StorageTarget.MonicaLocal -> {
                    if (existingItem == null) {
                        addDocument(
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            categoryId = currentTarget.categoryId,
                            replicaGroupId = replicaGroupId
                        )
                    } else {
                        updateDocument(
                            id = existingItem.id,
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            categoryId = currentTarget.categoryId,
                            replicaGroupId = replicaGroupId
                        )
                    }
                }
                is StorageTarget.KeePass -> {
                    if (existingItem == null) {
                        addDocument(
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            keepassDatabaseId = currentTarget.databaseId,
                            keepassGroupPath = currentTarget.groupPath,
                            replicaGroupId = replicaGroupId
                        )
                    } else {
                        updateDocument(
                            id = existingItem.id,
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            keepassDatabaseId = currentTarget.databaseId,
                            keepassGroupPath = currentTarget.groupPath,
                            replicaGroupId = replicaGroupId
                        )
                    }
                }
                is StorageTarget.Bitwarden -> {
                    if (existingItem == null) {
                        addDocument(
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            bitwardenVaultId = currentTarget.vaultId,
                            bitwardenFolderId = currentTarget.folderId,
                            replicaGroupId = replicaGroupId
                        )
                    } else {
                        updateDocument(
                            id = existingItem.id,
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            bitwardenVaultId = currentTarget.vaultId,
                            bitwardenFolderId = currentTarget.folderId,
                            replicaGroupId = replicaGroupId
                        )
                    }
                }
            }

            distinctTargets
                .filter { it.stableKey != currentTarget.stableKey && it.stableKey !in existingReplicaKeys }
                .forEach { target ->
                    when (target) {
                        is StorageTarget.MonicaLocal -> addDocument(
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            categoryId = target.categoryId,
                            replicaGroupId = replicaGroupId
                        )
                        is StorageTarget.KeePass -> addDocument(
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            keepassDatabaseId = target.databaseId,
                            keepassGroupPath = target.groupPath,
                            replicaGroupId = replicaGroupId
                        )
                        is StorageTarget.Bitwarden -> addDocument(
                            title = title,
                            documentData = documentData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            bitwardenVaultId = target.vaultId,
                            bitwardenFolderId = target.folderId,
                            replicaGroupId = replicaGroupId
                        )
                    }
                }
        }
    }

    suspend fun copyDocumentToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Long? {
        if (item.itemType != ItemType.DOCUMENT || item.hasOwnershipConflict()) return null
        val localCopy = item.asMonicaLocalCopy(categoryId).copy(
            createdAt = Date(),
            updatedAt = Date()
        )
        return repository.insertItem(localCopy)
    }

    suspend fun moveDocumentToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Result<Long> {
        if (item.itemType != ItemType.DOCUMENT) {
            return Result.failure(IllegalArgumentException("仅支持证件项目"))
        }
        if (item.hasOwnershipConflict()) {
            return Result.failure(IllegalStateException("证件来源冲突，无法移动到 Monica 本地"))
        }

        val newId = copyDocumentToMonicaLocal(item, categoryId)
            ?: return Result.failure(IllegalStateException("创建 Monica 本地证件副本失败"))

        val sourceDelete = when (val ownership = item.resolveOwnership()) {
            is SecureItemOwnership.Bitwarden -> {
                val vaultId = ownership.vaultId
                val cipherId = ownership.cipherId
                if (vaultId == null || cipherId.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Bitwarden 证件缺少同步标识"))
                } else {
                    bitwardenRepository?.queueCipherDelete(
                        vaultId = vaultId,
                        cipherId = cipherId,
                        entryId = item.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_DOCUMENT
                    ) ?: Result.failure(IllegalStateException("Bitwarden 仓库不可用"))
                }
            }
            is SecureItemOwnership.KeePass -> {
                if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = false)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("KeePass 证件源删除失败"))
                }
            }
            is SecureItemOwnership.MonicaLocal -> Result.success(Unit)
            is SecureItemOwnership.Conflict -> Result.failure(IllegalStateException("证件来源冲突，无法移动到 Monica 本地"))
        }

        if (sourceDelete.isFailure) {
            repository.deleteItemById(newId)
            return Result.failure(
                sourceDelete.exceptionOrNull() ?: IllegalStateException("删除证件源失败")
            )
        }

        repository.deleteItem(item)
        return Result.success(newId)
    }

    private suspend fun hasReplicaTargetConflict(
        itemType: ItemType,
        itemId: Long,
        replicaGroupId: String?,
        target: StorageTarget
    ): Boolean {
        if (replicaGroupId.isNullOrBlank()) return false
        return repository.getAllItems().first()
            .asSequence()
            .filter { candidate ->
                candidate.itemType == itemType &&
                    candidate.id != itemId &&
                    candidate.replicaGroupId == replicaGroupId &&
                    !candidate.isDeleted
            }
            .any { candidate -> candidate.toStorageTarget().stableKey == target.stableKey }
    }

    private fun resolveKeePassMutationIdentity(
        existingItem: SecureItem?,
        targetDatabaseId: Long?,
        requestedGroupPath: String?
    ): KeePassMutationIdentity {
        if (targetDatabaseId == null) {
            return KeePassMutationIdentity(
                groupPath = null,
                entryUuid = null,
                groupUuid = null
            )
        }

        val sameDatabase = existingItem?.keepassDatabaseId == targetDatabaseId
        val resolvedGroupPath = requestedGroupPath ?: if (sameDatabase) existingItem?.keepassGroupPath else null
        val groupUnchanged = sameDatabase && resolvedGroupPath == existingItem?.keepassGroupPath

        return KeePassMutationIdentity(
            groupPath = resolvedGroupPath,
            entryUuid = if (sameDatabase) {
                existingItem?.keepassEntryUuid ?: UUID.randomUUID().toString()
            } else {
                UUID.randomUUID().toString()
            },
            groupUuid = if (groupUnchanged) existingItem?.keepassGroupUuid else null
        )
    }
    
    // 删除证件
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteDocument(id: Long, softDelete: Boolean = true) {
        viewModelScope.launch {
            repository.getItemById(id)?.let { item ->
                val vaultId = item.bitwardenVaultId
                val cipherId = item.bitwardenCipherId
                val isBitwardenCipher = vaultId != null && !cipherId.isNullOrBlank()

                if (isBitwardenCipher) {
                    val queueResult = bitwardenRepository?.queueCipherDelete(
                        vaultId = vaultId!!,
                        cipherId = cipherId!!,
                        entryId = item.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_DOCUMENT
                    )
                    if (queueResult?.isFailure == true) {
                        Log.e("DocumentViewModel", "Queue Bitwarden delete failed: ${queueResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }

                if (!softDelete || isBitwardenCipher) {
                    if (!keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = softDelete || isBitwardenCipher)) {
                        Log.e("DocumentViewModel", "KeePass delete failed for document id=${item.id}")
                        return@launch
                    }
                }

                if (isBitwardenCipher) {
                    val softDeletedItem = item.copy(
                        isDeleted = true,
                        deletedAt = Date(),
                        updatedAt = Date(),
                        bitwardenLocalModified = true
                    )
                    repository.updateItem(softDeletedItem)
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.DOCUMENT,
                        itemId = id,
                        itemTitle = item.title,
                        detail = "移入回收站（待同步删除）"
                    )
                    return@launch
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

                    if (!isBitwardenCipher && item.keepassDatabaseId != null) {
                        viewModelScope.launch keepassDeleteSync@{
                            if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = true)) {
                                Log.i("DocumentViewModel", "KeePass trash delete synced for document id=${item.id}")
                                return@keepassDeleteSync
                            }

                            Log.e("DocumentViewModel", "KeePass trash delete failed, reverting local trash state for document id=${item.id}")
                            repository.updateItem(item.copy(updatedAt = Date()))
                        }
                    }
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
        return CardWalletDataCodec.parseDocumentData(jsonData)
    }

    private suspend fun repairLegacyDetachedKeePassItems() {
        repository.repairLegacyDetachedKeePassItems(::hasKeePassDatabase)
    }

    private suspend fun hasKeePassDatabase(databaseId: Long): Boolean {
        return localKeePassDatabaseDao?.getDatabaseById(databaseId) != null
    }
}
