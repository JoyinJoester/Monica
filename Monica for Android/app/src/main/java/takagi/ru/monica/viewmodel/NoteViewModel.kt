package takagi.ru.monica.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.repository.KeePassCompatibilityBridge
import takagi.ru.monica.repository.KeePassWorkspaceRepository
import takagi.ru.monica.repository.SecureItemRepository
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.util.ImageManager
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.KeePassKdbxService
import java.util.Date
import java.util.UUID

data class NoteDraftStorageTarget(
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val keepassGroupPath: String? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null
)

class NoteViewModel(
    private val repository: SecureItemRepository,
    context: Context? = null,
    private val localKeePassDatabaseDao: LocalKeePassDatabaseDao? = null,
    securityManager: SecurityManager? = null
) : ViewModel() {

    companion object {
        private const val TAG = "NoteViewModel"
    }

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
    private val imageManager = context?.let { ImageManager(it.applicationContext) }

    private val bitwardenRepository = context?.let { BitwardenRepository.getInstance(it.applicationContext) }

    private data class BitwardenTransition(
        val cipherId: String?,
        val revisionDate: String?,
        val localModified: Boolean,
        val syncStatus: String
    )

    private data class KeePassMutationIdentity(
        val groupPath: String?,
        val entryUuid: String?,
        val groupUuid: String?
    )
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 笔记列表布局偏好 (true = 网格, false = 列表)
    private val _isGridLayout = MutableStateFlow(true)
    val isGridLayout: StateFlow<Boolean> = _isGridLayout.asStateFlow()

    private val _draftStorageTarget = MutableStateFlow(NoteDraftStorageTarget())
    val draftStorageTarget: StateFlow<NoteDraftStorageTarget> = _draftStorageTarget.asStateFlow()

    init {
        viewModelScope.launch {
            repairLegacyDetachedKeePassItems()
        }
    }
    
    fun setGridLayout(isGrid: Boolean) {
        _isGridLayout.value = isGrid
    }

    fun setDraftStorageTarget(target: NoteDraftStorageTarget) {
        _draftStorageTarget.value = target
    }

    fun syncKeePassNotes(databaseId: Long) {
        viewModelScope.launch {
            val snapshots = keepassBridge
                ?.readLegacySecureItems(databaseId, setOf(ItemType.NOTE))
                ?.getOrNull()
                ?: return@launch

            val existingNotes = repository.getItemsByType(ItemType.NOTE).first()
            snapshots.forEach { snapshot ->
                val incoming = snapshot.item
                val existingByUuid = incoming.keepassEntryUuid
                    ?.takeIf { it.isNotBlank() }
                    ?.let { repository.getItemByKeePassUuid(databaseId, it) }
                val existingBySource = snapshot.sourceMonicaId
                    ?.takeIf { it > 0 }
                    ?.let { sourceId -> repository.getItemById(sourceId) }
                    ?.takeIf { it.itemType == ItemType.NOTE }

                val existing = existingByUuid ?: existingBySource ?: existingNotes.firstOrNull {
                    it.itemType == ItemType.NOTE &&
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
        val item = repository.getItemById(id) ?: return null
        return repository.normalizeLegacyDetachedKeePassItem(item, ::hasKeePassDatabase)
    }

    fun observeNoteById(id: Long): Flow<SecureItem?> {
        return repository.observeItemById(id)
            .map { item ->
                item?.takeIf { it.itemType == ItemType.NOTE }
            }
    }

    private suspend fun repairLegacyDetachedKeePassItems() {
        repository.repairLegacyDetachedKeePassItems(::hasKeePassDatabase)
    }

    private suspend fun hasKeePassDatabase(databaseId: Long): Boolean {
        return localKeePassDatabaseDao?.getDatabaseById(databaseId) != null
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
        isMarkdown: Boolean = false,
        isFavorite: Boolean = false,
        categoryId: Long? = null,
        imagePaths: String = "",
        keepassDatabaseId: Long? = null,
        keepassGroupPath: String? = null,
        bitwardenVaultId: Long? = null,
        bitwardenFolderId: String? = null
    ) {
        viewModelScope.launch {
            val (itemData, notesCache) = NoteContentCodec.encode(
                content = content,
                tags = tags,
                isMarkdown = isMarkdown
            )
            val resolvedTitle = NoteContentCodec.resolveTitle(title = title, content = content)
            val keepassIdentity = resolveKeePassMutationIdentity(
                existingItem = null,
                targetDatabaseId = keepassDatabaseId,
                requestedGroupPath = keepassGroupPath
            )
            
            val item = SecureItem(
                id = 0,
                itemType = ItemType.NOTE,
                title = resolvedTitle,
                notes = notesCache, // 保留 notes 搜索兼容
                itemData = itemData,
                isFavorite = isFavorite,
                imagePaths = imagePaths,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassIdentity.groupPath,
                keepassEntryUuid = keepassIdentity.entryUuid,
                keepassGroupUuid = keepassIdentity.groupUuid,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenFolderId = bitwardenFolderId,
                syncStatus = if (bitwardenVaultId != null) "PENDING" else "NONE",
                createdAt = Date(),
                updatedAt = Date()
            )
            val newId = keepassSecureItemCreateExecutor.create(
                item = item,
                insertItem = repository::insertItem,
                rollbackItem = repository::deleteItemById
            ) ?: return@launch
            
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
        isMarkdown: Boolean = false,
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
            val transition = resolveBitwardenTransition(
                existingItem = existingItem,
                targetVaultId = bitwardenVaultId,
                targetFolderId = bitwardenFolderId,
                forcePendingWhenKeepingCipher = bitwardenVaultId != null &&
                    !existingItem?.bitwardenCipherId.isNullOrBlank(),
                abortOnQueueFailure = true
            ) ?: run {
                Log.e(TAG, "Skip note update $id: failed to queue Bitwarden delete")
                return@launch
            }
            
            val (itemData, notesCache) = NoteContentCodec.encode(
                content = content,
                tags = tags,
                isMarkdown = isMarkdown
            )
            val resolvedTitle = NoteContentCodec.resolveTitle(title = title, content = content)
            val keepassIdentity = resolveKeePassMutationIdentity(
                existingItem = existingItem,
                targetDatabaseId = keepassDatabaseId,
                requestedGroupPath = keepassGroupPath
            )
            
            val item = SecureItem(
                id = id,
                itemType = ItemType.NOTE,
                title = resolvedTitle,
                notes = notesCache,
                itemData = itemData,
                isFavorite = isFavorite,
                imagePaths = imagePaths,
                categoryId = categoryId,
                keepassDatabaseId = keepassDatabaseId,
                keepassGroupPath = keepassIdentity.groupPath,
                keepassEntryUuid = keepassIdentity.entryUuid,
                keepassGroupUuid = keepassIdentity.groupUuid,
                bitwardenVaultId = bitwardenVaultId,
                bitwardenCipherId = transition.cipherId,
                bitwardenFolderId = bitwardenFolderId,
                bitwardenRevisionDate = transition.revisionDate,
                bitwardenLocalModified = transition.localModified,
                syncStatus = transition.syncStatus,
                createdAt = createdAt,
                updatedAt = Date()
            )
            repository.updateItem(item)
            val removedImageIds = existingItem
                ?.let { extractImageRefs(it) - extractImageRefs(item) }
                .orEmpty()
            cleanupUnreferencedNoteImagesInternal(removedImageIds)
            keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = existingItem, updatedItem = item)
            
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

    suspend fun moveNoteToStorage(
        item: SecureItem,
        categoryId: Long? = item.categoryId,
        keepassDatabaseId: Long? = item.keepassDatabaseId,
        keepassGroupPath: String? = item.keepassGroupPath,
        bitwardenVaultId: Long? = item.bitwardenVaultId,
        bitwardenFolderId: String? = item.bitwardenFolderId
    ): Boolean {
        if (item.itemType != ItemType.NOTE) return false

        val transition = resolveBitwardenTransition(
            existingItem = item,
            targetVaultId = bitwardenVaultId,
            targetFolderId = bitwardenFolderId,
            forcePendingWhenKeepingCipher = item.bitwardenVaultId == bitwardenVaultId &&
                item.bitwardenFolderId != bitwardenFolderId &&
                bitwardenVaultId != null &&
                !item.bitwardenCipherId.isNullOrBlank(),
            abortOnQueueFailure = true
        ) ?: return false

        val keepassIdentity = resolveKeePassMutationIdentity(
            existingItem = item,
            targetDatabaseId = keepassDatabaseId,
            requestedGroupPath = keepassGroupPath
        )

        val updated = item.copy(
            categoryId = categoryId,
            keepassDatabaseId = keepassDatabaseId,
            keepassGroupPath = keepassIdentity.groupPath,
            keepassEntryUuid = keepassIdentity.entryUuid,
            keepassGroupUuid = keepassIdentity.groupUuid,
            bitwardenVaultId = bitwardenVaultId,
            bitwardenFolderId = bitwardenFolderId,
            bitwardenCipherId = transition.cipherId,
            bitwardenRevisionDate = transition.revisionDate,
            bitwardenLocalModified = transition.localModified,
            syncStatus = transition.syncStatus,
            updatedAt = Date()
        )
        repository.updateItem(updated)
        keepassSecureItemUpdateExecutor.syncUpdatedItem(existingItem = item, updatedItem = updated)
        return true
    }

    suspend fun copyNoteToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Long? {
        if (item.itemType != ItemType.NOTE || item.hasOwnershipConflict()) return null
        val localCopy = item.asMonicaLocalCopy(categoryId).copy(
            createdAt = Date(),
            updatedAt = Date()
        )
        return repository.insertItem(localCopy)
    }

    suspend fun moveNoteToMonicaLocal(
        item: SecureItem,
        categoryId: Long?
    ): Result<Long> {
        if (item.itemType != ItemType.NOTE) {
            return Result.failure(IllegalArgumentException("仅支持笔记项目"))
        }
        if (item.hasOwnershipConflict()) {
            return Result.failure(IllegalStateException("笔记来源冲突，无法移动到 Monica 本地"))
        }

        val newId = copyNoteToMonicaLocal(item, categoryId)
            ?: return Result.failure(IllegalStateException("创建 Monica 本地笔记副本失败"))

        val sourceDelete = when (val ownership = item.resolveOwnership()) {
            is SecureItemOwnership.Bitwarden -> {
                val vaultId = ownership.vaultId
                val cipherId = ownership.cipherId
                if (vaultId == null || cipherId.isNullOrBlank()) {
                    Result.failure(IllegalStateException("Bitwarden 笔记缺少同步标识"))
                } else {
                    bitwardenRepository?.queueCipherDelete(
                        vaultId = vaultId,
                        cipherId = cipherId,
                        entryId = item.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_NOTE
                    ) ?: Result.failure(IllegalStateException("Bitwarden 仓库不可用"))
                }
            }
            is SecureItemOwnership.KeePass -> {
                if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = false)) {
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("KeePass 笔记源删除失败"))
                }
            }
            is SecureItemOwnership.MonicaLocal -> Result.success(Unit)
            is SecureItemOwnership.Conflict -> Result.failure(IllegalStateException("笔记来源冲突，无法移动到 Monica 本地"))
        }

        if (sourceDelete.isFailure) {
            repository.deleteItemById(newId)
            return Result.failure(
                sourceDelete.exceptionOrNull() ?: IllegalStateException("删除源笔记失败")
            )
        }

        repository.deleteItem(item)
        return Result.success(newId)
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
    
    // 删除笔记
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteNote(item: SecureItem, softDelete: Boolean = true) {
        viewModelScope.launch {
            val vaultId = item.bitwardenVaultId
            val cipherId = item.bitwardenCipherId
            val isBitwardenCipher = vaultId != null && !cipherId.isNullOrBlank()

            if (isBitwardenCipher) {
                val queueResult = bitwardenRepository?.queueCipherDelete(
                    vaultId = vaultId!!,
                    cipherId = cipherId!!,
                    entryId = item.id,
                    itemType = BitwardenPendingOperation.ITEM_TYPE_NOTE
                )
                if (queueResult?.isFailure == true) {
                    Log.e(TAG, "Queue Bitwarden note delete failed: ${queueResult.exceptionOrNull()?.message}")
                    return@launch
                }
            }

            if (!softDelete || isBitwardenCipher) {
                if (!keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = softDelete || isBitwardenCipher)) {
                    Log.e("NoteViewModel", "KeePass delete failed for note id=${item.id}")
                    return@launch
                }
            }

            if (isBitwardenCipher) {
                repository.updateItem(
                    item.copy(
                        isDeleted = true,
                        deletedAt = Date(),
                        updatedAt = Date(),
                        bitwardenLocalModified = true
                    )
                )
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.NOTE,
                    itemId = item.id,
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
                    itemType = OperationLogItemType.NOTE,
                    itemId = item.id,
                    itemTitle = item.title,
                    detail = "移入回收站"
                )

                if (!isBitwardenCipher && item.keepassDatabaseId != null) {
                    viewModelScope.launch keepassDeleteSync@{
                        if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = true)) {
                            Log.i(TAG, "KeePass trash delete synced for note id=${item.id}")
                            return@keepassDeleteSync
                        }

                        Log.e(TAG, "KeePass trash delete failed, reverting local trash state for note id=${item.id}")
                        repository.updateItem(item.copy(updatedAt = Date()))
                    }
                }
            } else {
                // 永久删除
                OperationLogger.logDelete(
                    itemType = OperationLogItemType.NOTE,
                    itemId = item.id,
                    itemTitle = item.title
                )
                repository.deleteItem(item)
                cleanupUnreferencedNoteImagesInternal(extractImageRefs(item))
            }
        }
    }

    // 批量删除笔记
    // @param softDelete 是否软删除（移入回收站），默认为 true
    fun deleteNotes(items: List<SecureItem>, softDelete: Boolean = true) {
        viewModelScope.launch {
            items.forEach { item ->
                val vaultId = item.bitwardenVaultId
                val cipherId = item.bitwardenCipherId
                val isBitwardenCipher = vaultId != null && !cipherId.isNullOrBlank()

                if (isBitwardenCipher) {
                    val queueResult = bitwardenRepository?.queueCipherDelete(
                        vaultId = vaultId!!,
                        cipherId = cipherId!!,
                        entryId = item.id,
                        itemType = BitwardenPendingOperation.ITEM_TYPE_NOTE
                    )
                    if (queueResult?.isFailure == true) {
                        Log.e(TAG, "Queue Bitwarden note delete failed: ${queueResult.exceptionOrNull()?.message}")
                        return@forEach
                    }
                }

                if (!softDelete || isBitwardenCipher) {
                    if (!keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = softDelete || isBitwardenCipher)) {
                        Log.e("NoteViewModel", "KeePass delete failed for note id=${item.id}")
                        return@forEach
                    }
                }

                if (isBitwardenCipher) {
                    repository.updateItem(
                        item.copy(
                            isDeleted = true,
                            deletedAt = Date(),
                            updatedAt = Date(),
                            bitwardenLocalModified = true
                        )
                    )
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.NOTE,
                        itemId = item.id,
                        itemTitle = item.title,
                        detail = "移入回收站（待同步删除）"
                    )
                    return@forEach
                }

                if (softDelete) {
                    // 软删除：移动到回收站
                    repository.softDeleteItem(item)
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.NOTE,
                        itemId = item.id,
                        itemTitle = item.title,
                        detail = "移入回收站"
                    )

                    if (!isBitwardenCipher && item.keepassDatabaseId != null) {
                        viewModelScope.launch keepassDeleteSync@{
                            if (keepassSecureItemDeleteExecutor.delete(item, useRecycleBin = true)) {
                                Log.i(TAG, "KeePass trash delete synced for note id=${item.id}")
                                return@keepassDeleteSync
                            }

                            Log.e(TAG, "KeePass trash delete failed, reverting local trash state for note id=${item.id}")
                            repository.updateItem(item.copy(updatedAt = Date()))
                        }
                    }
                } else {
                    // 永久删除
                    OperationLogger.logDelete(
                        itemType = OperationLogItemType.NOTE,
                        itemId = item.id,
                        itemTitle = item.title
                    )
                    repository.deleteItem(item)
                    cleanupUnreferencedNoteImagesInternal(extractImageRefs(item))
                }
            }
        }
    }

    fun cleanupUnreferencedNoteImages(candidateImageIds: List<String>) {
        viewModelScope.launch {
            cleanupUnreferencedNoteImagesInternal(
                candidateImageIds
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .toSet()
            )
        }
    }

    private suspend fun cleanupUnreferencedNoteImagesInternal(candidateImageIds: Set<String>) {
        if (candidateImageIds.isEmpty()) return
        val manager = imageManager ?: return

        val activeNotes = repository.getItemsByType(ItemType.NOTE).first()
        val deletedNotes = repository.getDeletedItems().first().filter { it.itemType == ItemType.NOTE }
        val allNotes = activeNotes + deletedNotes
        val referencedImageIds = allNotes.flatMap { extractImageRefs(it) }.toSet()
        val shouldDelete = candidateImageIds.filterNot { referencedImageIds.contains(it) }
        if (shouldDelete.isNotEmpty()) {
            manager.deleteImages(shouldDelete)
        }
    }

    private fun extractImageRefs(item: SecureItem): Set<String> {
        val decoded = NoteContentCodec.decodeFromItem(item)
        val fromContent = NoteContentCodec.extractInlineImageIds(decoded.content)
        val fromLegacyPaths = NoteContentCodec.decodeImagePaths(item.imagePaths)
        return (fromContent + fromLegacyPaths).toSet()
    }

    private suspend fun resolveBitwardenTransition(
        existingItem: SecureItem?,
        targetVaultId: Long?,
        targetFolderId: String?,
        forcePendingWhenKeepingCipher: Boolean,
        abortOnQueueFailure: Boolean
    ): BitwardenTransition? {
        val previousVaultId = existingItem?.bitwardenVaultId
        val previousCipherId = existingItem?.bitwardenCipherId
        val hasExistingCipher = previousVaultId != null && !previousCipherId.isNullOrBlank()

        val leavingExistingCipher = hasExistingCipher && previousVaultId != targetVaultId
        if (leavingExistingCipher) {
            val queueResult = bitwardenRepository?.queueCipherDelete(
                vaultId = previousVaultId!!,
                cipherId = previousCipherId!!,
                entryId = existingItem.id,
                itemType = BitwardenPendingOperation.ITEM_TYPE_NOTE
            )
            if (queueResult?.isSuccess != true) {
                val err = queueResult?.exceptionOrNull()?.message ?: "Bitwarden repository unavailable"
                Log.e(TAG, "Queue Bitwarden note delete failed: $err")
                if (abortOnQueueFailure) return null
            }
        }

        val keepExistingCipher = hasExistingCipher && previousVaultId == targetVaultId
        if (targetVaultId == null) {
            return BitwardenTransition(
                cipherId = null,
                revisionDate = null,
                localModified = false,
                syncStatus = "NONE"
            )
        }

        if (!keepExistingCipher) {
            return BitwardenTransition(
                cipherId = null,
                revisionDate = null,
                localModified = false,
                syncStatus = "PENDING"
            )
        }

        val folderChanged = existingItem?.bitwardenFolderId != targetFolderId
        val localModified = forcePendingWhenKeepingCipher || folderChanged || (existingItem?.bitwardenLocalModified == true)
        return BitwardenTransition(
            cipherId = previousCipherId,
            revisionDate = existingItem?.bitwardenRevisionDate,
            localModified = localModified,
            syncStatus = if (localModified) "PENDING" else "SYNCED"
        )
    }
}
