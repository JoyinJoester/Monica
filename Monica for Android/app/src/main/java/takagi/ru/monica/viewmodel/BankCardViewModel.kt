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
import takagi.ru.monica.data.model.BankCardData
import takagi.ru.monica.data.model.CardWalletDataCodec
import takagi.ru.monica.data.model.CardType
import takagi.ru.monica.data.model.StorageTarget
import takagi.ru.monica.data.model.toStorageTarget
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import java.util.Date
import java.util.UUID

class BankCardViewModel(
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

    fun syncAllKeePassCards() {
        viewModelScope.launch {
            val dao = localKeePassDatabaseDao ?: return@launch
            val dbs = withContext(Dispatchers.IO) { dao.getAllDatabasesSync() }
            dbs.forEach { syncKeePassCards(it.id) }
        }
    }

    fun syncKeePassCards(databaseId: Long) {
        viewModelScope.launch {
            val snapshots = keepassBridge
                ?.readLegacySecureItems(databaseId, setOf(ItemType.BANK_CARD))
                ?.getOrNull()
                ?: return@launch

            val existingCards = repository.getItemsByType(ItemType.BANK_CARD).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingByUuid = incoming.keepassEntryUuid
                    ?.takeIf { it.isNotBlank() }
                    ?.let { repository.getItemByKeePassUuid(databaseId, it) }
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.BANK_CARD }

                val existing = existingByUuid ?: existingBySource ?: existingCards.firstOrNull {
                    it.itemType == ItemType.BANK_CARD &&
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
        val item = repository.getItemById(id) ?: return null
        return repository.normalizeLegacyDetachedKeePassItem(item, ::hasKeePassDatabase)
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
                itemType = ItemType.BANK_CARD,
                title = title,
                itemData = CardWalletDataCodec.encodeBankCardData(cardData),
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
                    changes.add(FieldChange("银行", oldCardData?.bankName ?: "", cardData.bankName))
                }
                
                val updatedItem = existingItem.copy(
                    title = title,
                    itemData = CardWalletDataCodec.encodeBankCardData(cardData),
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
                    itemType = OperationLogItemType.BANK_CARD,
                    itemId = id,
                    itemTitle = title,
                    changes = if (changes.isEmpty()) listOf(FieldChange("更新", "编辑于", java.text.SimpleDateFormat("HH:mm").format(java.util.Date()))) else changes
                )
            }
        }
    }

    suspend fun moveCardToStorage(
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
                itemType = ItemType.BANK_CARD,
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

    fun saveCardAcrossTargets(
        id: Long?,
        title: String,
        cardData: BankCardData,
        notes: String = "",
        isFavorite: Boolean = false,
        imagePaths: String = "",
        targets: List<StorageTarget>
    ) {
        viewModelScope.launch {
            val distinctTargets = targets.distinctBy(StorageTarget::stableKey)
            if (distinctTargets.isEmpty()) return@launch

            val existingItem = id?.let { repository.getItemById(it) }?.takeIf { it.itemType == ItemType.BANK_CARD }
            val currentTarget = existingItem?.toStorageTarget() ?: distinctTargets.first()
            val replicaGroupId = existingItem?.replicaGroupId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val existingReplicaKeys = if (existingItem != null) {
                repository.getAllItems().first()
                    .asSequence()
                    .filter {
                        it.itemType == ItemType.BANK_CARD &&
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
                        addCard(
                            title = title,
                            cardData = cardData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            categoryId = currentTarget.categoryId,
                            replicaGroupId = replicaGroupId
                        )
                    } else {
                        updateCard(
                            id = existingItem.id,
                            title = title,
                            cardData = cardData,
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
                        addCard(
                            title = title,
                            cardData = cardData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            keepassDatabaseId = currentTarget.databaseId,
                            keepassGroupPath = currentTarget.groupPath,
                            replicaGroupId = replicaGroupId
                        )
                    } else {
                        updateCard(
                            id = existingItem.id,
                            title = title,
                            cardData = cardData,
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
                        addCard(
                            title = title,
                            cardData = cardData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            bitwardenVaultId = currentTarget.vaultId,
                            bitwardenFolderId = currentTarget.folderId,
                            replicaGroupId = replicaGroupId
                        )
                    } else {
                        updateCard(
                            id = existingItem.id,
                            title = title,
                            cardData = cardData,
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
                        is StorageTarget.MonicaLocal -> addCard(
                            title = title,
                            cardData = cardData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            categoryId = target.categoryId,
                            replicaGroupId = replicaGroupId
                        )
                        is StorageTarget.KeePass -> addCard(
                            title = title,
                            cardData = cardData,
                            notes = notes,
                            isFavorite = isFavorite,
                            imagePaths = imagePaths,
                            keepassDatabaseId = target.databaseId,
                            keepassGroupPath = target.groupPath,
                            replicaGroupId = replicaGroupId
                        )
                        is StorageTarget.Bitwarden -> addCard(
                            title = title,
                            cardData = cardData,
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

    suspend fun copyCardToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Long? {
        if (item.itemType != ItemType.BANK_CARD || item.hasOwnershipConflict()) return null
        val localCopy = item.asMonicaLocalCopy(categoryId).copy(
            createdAt = Date(),
            updatedAt = Date()
        )
        return repository.insertItem(localCopy)
    }

    suspend fun moveCardToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Result<Long> {
        if (item.itemType != ItemType.BANK_CARD) {
            return Result.failure(IllegalArgumentException("仅支持银行卡项目"))
        }
        if (item.hasOwnershipConflict()) {
            return Result.failure(IllegalStateException("银行卡来源冲突，无法移动到 Monica 本地"))
        }

        val newId = copyCardToMonicaLocal(item, categoryId)
            ?: return Result.failure(IllegalStateException("创建 Monica 本地银行卡副本失败"))

        val sourceDelete = when (val ownership = item.resolveOwnership()) {
            is SecureItemOwnership.Bitwarden -> {
                val vaultId = ownership.vaultId
                val cipherId = ownership.cipherId
                if (vaultId == null || cipherId.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Bitwarden 银行卡缺少同步标识"))
                } else {
                    bitwardenRepository?.queueCipherDelete(
                        vaultId = vaultId,
                        cipherId = cipherId,
                        entryId = item.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_CARD
                    ) ?: Result.failure(IllegalStateException("Bitwarden 仓库不可用"))
                }
            }
            is SecureItemOwnership.KeePass -> {
                if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = false)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("KeePass 银行卡源删除失败"))
                }
            }
            is SecureItemOwnership.MonicaLocal -> Result.success(Unit)
            is SecureItemOwnership.Conflict -> Result.failure(IllegalStateException("银行卡来源冲突，无法移动到 Monica 本地"))
        }

        if (sourceDelete.isFailure) {
            repository.deleteItemById(newId)
            return Result.failure(
                sourceDelete.exceptionOrNull() ?: IllegalStateException("删除银行卡源失败")
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
    
    // 删除银行卡
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteCard(id: Long, softDelete: Boolean = true) {
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
                        itemType = BitwardenPendingOperation.ITEM_TYPE_CARD
                    )
                    if (queueResult?.isFailure == true) {
                        Log.e("BankCardViewModel", "Queue Bitwarden delete failed: ${queueResult.exceptionOrNull()?.message}")
                        return@launch
                    }
                }

                if (!softDelete || isBitwardenCipher) {
                    if (!keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = softDelete || isBitwardenCipher)) {
                        Log.e("BankCardViewModel", "KeePass delete failed for card id=${item.id}")
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
                        itemType = OperationLogItemType.BANK_CARD,
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
                        itemType = OperationLogItemType.BANK_CARD,
                        itemId = id,
                        itemTitle = item.title,
                        detail = "移入回收站"
                    )

                    if (!isBitwardenCipher && item.keepassDatabaseId != null) {
                        viewModelScope.launch keepassDeleteSync@{
                            if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = true)) {
                                Log.i("BankCardViewModel", "KeePass trash delete synced for card id=${item.id}")
                                return@keepassDeleteSync
                            }

                            Log.e("BankCardViewModel", "KeePass trash delete failed, reverting local trash state for card id=${item.id}")
                            repository.updateItem(item.copy(updatedAt = Date()))
                        }
                    }
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
        return CardWalletDataCodec.parseBankCardData(jsonData)
    }

    private suspend fun repairLegacyDetachedKeePassItems() {
        repository.repairLegacyDetachedKeePassItems(::hasKeePassDatabase)
    }

    private suspend fun hasKeePassDatabase(databaseId: Long): Boolean {
        return localKeePassDatabaseDao?.getDatabaseById(databaseId) != null
    }
}
