package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.ItemType
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.PasskeyEntry
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordPageContentType
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.bitwarden.BitwardenPendingOperation
import takagi.ru.monica.data.isKeePassOwned
import takagi.ru.monica.data.isLocalOnlyItem
import takagi.ru.monica.data.model.TimelinePasswordRecreatedEntry
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.notes.domain.NoteContentCodec
import takagi.ru.monica.passkey.PasskeyPrivateKeySupport
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.components.UnifiedMoveAction
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.viewmodel.PasswordViewModel
import takagi.ru.monica.ui.password.PasswordAggregateListItemUi

internal data class PasswordBatchAggregateSelection(
    val bankCards: List<SecureItem> = emptyList(),
    val documents: List<SecureItem> = emptyList(),
    val notes: List<SecureItem> = emptyList(),
    val totpItems: List<SecureItem> = emptyList(),
    val passkeys: List<PasskeyEntry> = emptyList()
) {
    val hasKeePassOwned: Boolean
        get() = bankCards.any { it.isKeePassOwned() } ||
            documents.any { it.isKeePassOwned() } ||
            notes.any { it.isKeePassOwned() } ||
            totpItems.any { it.isKeePassOwned() } ||
            passkeys.any { it.isKeePassOwned() }

    val hasItems: Boolean
        get() = bankCards.isNotEmpty() ||
            documents.isNotEmpty() ||
            notes.isNotEmpty() ||
            totpItems.isNotEmpty() ||
            passkeys.isNotEmpty()
}

internal data class MixedPasswordBatchMoveResult(
    val successCount: Int,
    val failedCount: Int,
    val blockedPasskeyCount: Int,
    val copiedPasswordIds: List<Long>
)

internal fun PasswordListAggregateUiState.resolveBatchAggregateSelection(
    selectedSupplementaryItems: List<PasswordAggregateListItemUi>
): PasswordBatchAggregateSelection {
    if (selectedSupplementaryItems.isEmpty()) {
        return PasswordBatchAggregateSelection()
    }

    val bankCardIds = selectedSupplementaryItems
        .filter { it.type == PasswordPageContentType.CARD_WALLET && !it.isDocument }
        .mapNotNullTo(linkedSetOf()) { it.secureItemId }
    val documentIds = selectedSupplementaryItems
        .filter { it.type == PasswordPageContentType.CARD_WALLET && it.isDocument }
        .mapNotNullTo(linkedSetOf()) { it.secureItemId }
    val noteIds = selectedSupplementaryItems
        .filter { it.type == PasswordPageContentType.NOTE }
        .mapNotNullTo(linkedSetOf()) { it.secureItemId }
    val totpIds = selectedSupplementaryItems
        .filter { it.type == PasswordPageContentType.AUTHENTICATOR }
        .mapNotNullTo(linkedSetOf()) { it.secureItemId }
    val passkeyIds = selectedSupplementaryItems
        .filter { it.type == PasswordPageContentType.PASSKEY }
        .mapNotNullTo(linkedSetOf()) { it.passkeyRecordId }

    return PasswordBatchAggregateSelection(
        bankCards = bankCards.filter { it.id in bankCardIds },
        documents = documents.filter { it.id in documentIds },
        notes = notes.filter { it.id in noteIds },
        totpItems = totpItems.filter { it.id in totpIds },
        passkeys = passkeys.filter { it.id in passkeyIds }
    )
}

internal suspend fun executeMixedPasswordBatchMove(
    context: Context,
    action: UnifiedMoveAction,
    target: UnifiedMoveCategoryTarget,
    selectedEntries: List<PasswordEntry>,
    aggregateSelection: PasswordBatchAggregateSelection,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    viewModel: PasswordViewModel,
    aggregateUiState: PasswordListAggregateUiState,
    bitwardenRepository: BitwardenRepository,
    onProgress: ((Int, Int) -> Unit)? = null
): MixedPasswordBatchMoveResult {
    val passwordActionResolution = resolvePasswordBatchMoveAction(
        requestedAction = action,
        selectedEntries = selectedEntries
    )
    val forceSupplementaryCopy =
        action == UnifiedMoveAction.MOVE && aggregateSelection.hasKeePassOwned
    if (passwordActionResolution.showKeepassCopyOnlyHint || forceSupplementaryCopy) {
        Toast.makeText(
            context,
            context.getString(R.string.keepass_copy_only_hint),
            Toast.LENGTH_SHORT
        ).show()
    }

    val effectiveAction = if (
        passwordActionResolution.effectiveAction == UnifiedMoveAction.COPY ||
        forceSupplementaryCopy
    ) {
        UnifiedMoveAction.COPY
    } else {
        action
    }

    if (
        effectiveAction == UnifiedMoveAction.COPY &&
        action == UnifiedMoveAction.COPY &&
        aggregateSelection.passkeys.isNotEmpty()
    ) {
        Toast.makeText(
            context,
            context.getString(R.string.passkey_copy_uses_move_hint),
            Toast.LENGTH_SHORT
        ).show()
    }

    val targetRouting = resolvePasswordBatchMoveTargetRouting(target)
    val selectedIds = selectedEntries.map(PasswordEntry::id)
    val targetCategoryId = targetRouting.monicaCategoryId
    val targetKeepassDatabaseId = when (target) {
        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> target.databaseId
        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.databaseId
        else -> null
    }
    val targetKeepassGroupPath = when (target) {
        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.groupPath
        else -> null
    }
    val targetBitwardenVaultId = when (target) {
        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> target.vaultId
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.vaultId
        else -> null
    }
    val targetBitwardenFolderId = when (target) {
        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> ""
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.folderId
        else -> null
    }
    val isMonicaLocalTarget = targetRouting.isMonicaCopyTarget

    var successCount = 0
    var failedCount = 0
    var blockedPasskeyCount = 0
    val copiedPasswordIds = mutableListOf<Long>()

    val totalCount = selectedEntries.size +
        aggregateSelection.bankCards.size +
        aggregateSelection.documents.size +
        aggregateSelection.notes.size +
        aggregateSelection.totpItems.size +
        aggregateSelection.passkeys.size
    var processedCount = 0

    fun reportProgress(step: Int = 1) {
        if (totalCount <= 0 || step <= 0) return
        processedCount = (processedCount + step).coerceAtMost(totalCount)
        onProgress?.invoke(processedCount, totalCount)
    }

    if (totalCount > 0) {
        onProgress?.invoke(0, totalCount)
    }

    if (effectiveAction == UnifiedMoveAction.COPY) {
        if (targetRouting.isMonicaCopyTarget) {
            selectedEntries.forEach { entry ->
                val createdId = viewModel.copyPasswordToMonicaLocal(entry, targetCategoryId)
                if (createdId != null && createdId > 0) {
                    copiedPasswordIds += createdId
                    successCount++
                } else {
                    failedCount++
                }
                reportProgress()
            }
        } else {
            selectedEntries.forEach { entry ->
                val createdId = viewModel.addPasswordEntryWithResultAwait(
                    buildCopiedEntryForTarget(entry, target)
                )
                if (createdId != null && createdId > 0) {
                    copiedPasswordIds += createdId
                    successCount++
                } else {
                    failedCount++
                }
                reportProgress()
            }
        }

        aggregateSelection.totpItems.forEach { item ->
            val totpViewModel = aggregateUiState.totpViewModel
            if (totpViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                if (totpViewModel.copyTotpToMonicaLocal(item, targetCategoryId) != null) {
                    successCount++
                } else {
                    failedCount++
                }
                reportProgress()
                return@forEach
            }

            val totpData = runCatching { Json.decodeFromString<TotpData>(item.itemData) }
                .getOrNull()
            if (totpData == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            val detachedTotpData = totpData.copy(
                boundPasswordId = null,
                categoryId = null,
                keepassDatabaseId = null
            )
            totpViewModel.saveTotpItem(
                id = null,
                title = item.title,
                notes = item.notes,
                totpData = detachedTotpData,
                isFavorite = item.isFavorite,
                categoryId = targetCategoryId,
                keepassDatabaseId = targetKeepassDatabaseId,
                bitwardenVaultId = targetBitwardenVaultId,
                bitwardenFolderId = targetBitwardenFolderId
            )
            successCount++
            reportProgress()
        }

        aggregateSelection.notes.forEach { item ->
            val noteViewModel = aggregateUiState.noteViewModel
            if (noteViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                if (noteViewModel.copyNoteToMonicaLocal(item, targetCategoryId) != null) {
                    successCount++
                } else {
                    failedCount++
                }
                reportProgress()
                return@forEach
            }

            val decodedNote = NoteContentCodec.decodeFromItem(item)
            noteViewModel.addNote(
                content = decodedNote.content,
                title = item.title,
                tags = decodedNote.tags,
                isMarkdown = decodedNote.isMarkdown,
                isFavorite = item.isFavorite,
                categoryId = targetCategoryId,
                imagePaths = item.imagePaths,
                keepassDatabaseId = targetKeepassDatabaseId,
                keepassGroupPath = targetKeepassGroupPath,
                bitwardenVaultId = targetBitwardenVaultId,
                bitwardenFolderId = targetBitwardenFolderId
            )
            successCount++
            reportProgress()
        }

        aggregateSelection.bankCards.forEach { item ->
            val bankCardViewModel = aggregateUiState.bankCardViewModel
            if (bankCardViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                if (bankCardViewModel.copyCardToMonicaLocal(item, targetCategoryId) != null) {
                    successCount++
                } else {
                    failedCount++
                }
                reportProgress()
                return@forEach
            }

            val cardData = bankCardViewModel.parseCardData(item.itemData)
            if (cardData == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            bankCardViewModel.addCard(
                title = item.title,
                cardData = cardData,
                notes = item.notes,
                isFavorite = item.isFavorite,
                imagePaths = item.imagePaths,
                categoryId = targetCategoryId,
                keepassDatabaseId = targetKeepassDatabaseId,
                keepassGroupPath = targetKeepassGroupPath,
                bitwardenVaultId = targetBitwardenVaultId,
                bitwardenFolderId = targetBitwardenFolderId
            )
            successCount++
            reportProgress()
        }

        aggregateSelection.documents.forEach { item ->
            val documentViewModel = aggregateUiState.documentViewModel
            if (documentViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                if (documentViewModel.copyDocumentToMonicaLocal(item, targetCategoryId) != null) {
                    successCount++
                } else {
                    failedCount++
                }
                reportProgress()
                return@forEach
            }

            val documentData = documentViewModel.parseDocumentData(item.itemData)
            if (documentData == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            documentViewModel.addDocument(
                title = item.title,
                documentData = documentData,
                notes = item.notes,
                isFavorite = item.isFavorite,
                imagePaths = item.imagePaths,
                categoryId = targetCategoryId,
                keepassDatabaseId = targetKeepassDatabaseId,
                keepassGroupPath = targetKeepassGroupPath,
                bitwardenVaultId = targetBitwardenVaultId,
                bitwardenFolderId = targetBitwardenFolderId
            )
            successCount++
            reportProgress()
        }
    } else {
        val oldStates = selectedEntries.map(::toLocationState)
        val newStates = selectedEntries.map { toMovedLocationState(it, target) }
        val recreatedEntries = mutableListOf<TimelinePasswordRecreatedEntry>()
        val decryptedPasswordSnapshot = selectedEntries
            .mapNotNull { entry ->
                runCatching { securityManager.decryptData(entry.password) }
                    .getOrNull()
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { plain -> entry.password to plain }
            }
            .toMap()
        var passwordProgressHandledByCallback = false
        when {
            target == UnifiedMoveCategoryTarget.Uncategorized -> {
                try {
                    val keepassEntries = selectedEntries.filter { it.isKeePassEntry() }
                    val bitwardenEntries = selectedEntries.filter { it.isBitwardenEntry() }
                    val localIds = selectedEntries.filter { it.isLocalOnlyEntry() }.map { it.id }

                    if (keepassEntries.isNotEmpty()) {
                        val result = localKeePassViewModel.movePasswordEntriesToMonicaLocal(keepassEntries)
                        if (result.isSuccess) {
                            val keepassIds = keepassEntries.map { it.id }
                            viewModel.unarchivePasswordsAwait(keepassIds)
                            viewModel.movePasswordsToCategoryAwait(keepassIds, null)
                            successCount += keepassIds.size
                        } else {
                            failedCount += keepassEntries.size
                        }
                    }

                    bitwardenEntries.forEach { entry ->
                        val result = viewModel.moveBitwardenPasswordToMonicaLocal(entry, null)
                        if (result.isSuccess) {
                            recreatedEntries += TimelinePasswordRecreatedEntry(
                                sourceEntryId = entry.id,
                                recreatedEntryId = result.getOrThrow()
                            )
                            successCount++
                        } else {
                            failedCount++
                        }
                    }

                    if (localIds.isNotEmpty()) {
                        viewModel.unarchivePasswordsAwait(localIds)
                        viewModel.movePasswordsToCategoryAwait(localIds, null)
                        successCount += localIds.size
                    }
                } catch (_: Exception) {
                    failedCount += selectedEntries.size
                }
            }

            target is UnifiedMoveCategoryTarget.MonicaCategory -> {
                try {
                    val keepassEntries = selectedEntries.filter { it.isKeePassEntry() }
                    val bitwardenEntries = selectedEntries.filter { it.isBitwardenEntry() }
                    val localIds = selectedEntries.filter { it.isLocalOnlyEntry() }.map { it.id }

                    if (keepassEntries.isNotEmpty()) {
                        val result = localKeePassViewModel.movePasswordEntriesToMonicaLocal(keepassEntries)
                        if (result.isSuccess) {
                            val keepassIds = keepassEntries.map { it.id }
                            viewModel.unarchivePasswordsAwait(keepassIds)
                            viewModel.movePasswordsToCategoryAwait(keepassIds, target.categoryId)
                            successCount += keepassIds.size
                        } else {
                            failedCount += keepassEntries.size
                        }
                    }

                    bitwardenEntries.forEach { entry ->
                        val result = viewModel.moveBitwardenPasswordToMonicaLocal(
                            entry = entry,
                            categoryId = target.categoryId
                        )
                        if (result.isSuccess) {
                            recreatedEntries += TimelinePasswordRecreatedEntry(
                                sourceEntryId = entry.id,
                                recreatedEntryId = result.getOrThrow()
                            )
                            successCount++
                        } else {
                            failedCount++
                        }
                    }

                    if (localIds.isNotEmpty()) {
                        viewModel.unarchivePasswordsAwait(localIds)
                        viewModel.movePasswordsToCategoryAwait(localIds, target.categoryId)
                        successCount += localIds.size
                    }
                } catch (_: Exception) {
                    failedCount += selectedEntries.size
                }
            }

            target is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                if (selectedIds.isNotEmpty()) {
                    viewModel.unarchivePasswordsAwait(selectedIds)
                    viewModel.movePasswordsToBitwardenFolderAwait(selectedIds, target.vaultId, "")
                    successCount += selectedEntries.size
                }
            }

            target is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                if (selectedIds.isNotEmpty()) {
                    viewModel.unarchivePasswordsAwait(selectedIds)
                    viewModel.movePasswordsToBitwardenFolderAwait(selectedIds, target.vaultId, target.folderId)
                    successCount += selectedEntries.size
                }
            }

            target is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                try {
                    val passwordProgressBase = processedCount
                    passwordProgressHandledByCallback = true
                    val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                        databaseId = target.databaseId,
                        groupPath = null,
                        entries = selectedEntries,
                        decryptPassword = { encrypted ->
                            decryptedPasswordSnapshot[encrypted]
                                ?: securityManager.decryptData(encrypted)
                                ?: ""
                        },
                        onItemProcessed = { processed, total ->
                            val passwordTotal = selectedEntries.size.takeIf { it > 0 } ?: total
                            val normalizedProcessed = processed.coerceIn(0, passwordTotal)
                            val absoluteProcessed =
                                (passwordProgressBase + normalizedProcessed).coerceAtMost(totalCount)
                            processedCount = absoluteProcessed
                            onProgress?.invoke(absoluteProcessed, totalCount)
                        }
                    )
                    if (result.isSuccess) {
                        viewModel.unarchivePasswordsAwait(selectedIds)
                        viewModel.movePasswordsToKeePassDatabaseAwait(selectedIds, target.databaseId)
                        successCount += selectedEntries.size
                    } else {
                        failedCount += selectedEntries.size
                    }
                } catch (_: Exception) {
                    failedCount += selectedEntries.size
                }
            }

            target is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                try {
                    val passwordProgressBase = processedCount
                    passwordProgressHandledByCallback = true
                    val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                        databaseId = target.databaseId,
                        groupPath = target.groupPath,
                        entries = selectedEntries,
                        decryptPassword = { encrypted ->
                            decryptedPasswordSnapshot[encrypted]
                                ?: securityManager.decryptData(encrypted)
                                ?: ""
                        },
                        onItemProcessed = { processed, total ->
                            val passwordTotal = selectedEntries.size.takeIf { it > 0 } ?: total
                            val normalizedProcessed = processed.coerceIn(0, passwordTotal)
                            val absoluteProcessed =
                                (passwordProgressBase + normalizedProcessed).coerceAtMost(totalCount)
                            processedCount = absoluteProcessed
                            onProgress?.invoke(absoluteProcessed, totalCount)
                        }
                    )
                    if (result.isSuccess) {
                        viewModel.unarchivePasswordsAwait(selectedIds)
                        viewModel.movePasswordsToKeePassGroupAwait(selectedIds, target.databaseId, target.groupPath)
                        successCount += selectedEntries.size
                    } else {
                        failedCount += selectedEntries.size
                    }
                } catch (_: Exception) {
                    failedCount += selectedEntries.size
                }
            }
        }

        if (!passwordProgressHandledByCallback) {
            reportProgress(selectedEntries.size)
        }

        aggregateSelection.totpItems.forEach { item ->
            val totpViewModel = aggregateUiState.totpViewModel
            if (totpViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                val moved = if (item.isLocalOnlyItem()) {
                    totpViewModel.moveToCategory(listOf(item.id), targetCategoryId)
                    true
                } else {
                    totpViewModel.moveTotpToMonicaLocal(item, targetCategoryId).isSuccess
                }
                if (moved) successCount++ else failedCount++
                reportProgress()
            }
        }

        aggregateSelection.notes.forEach { item ->
            val noteViewModel = aggregateUiState.noteViewModel
            if (noteViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            val moved = if (isMonicaLocalTarget) {
                if (item.isLocalOnlyItem()) {
                    noteViewModel.moveNoteToStorage(
                        item = item,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = null,
                        keepassGroupPath = null,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null
                    )
                } else {
                    noteViewModel.moveNoteToMonicaLocal(item, targetCategoryId).isSuccess
                }
            } else {
                noteViewModel.moveNoteToStorage(
                    item = item,
                    categoryId = targetCategoryId,
                    keepassDatabaseId = targetKeepassDatabaseId,
                    keepassGroupPath = targetKeepassGroupPath,
                    bitwardenVaultId = targetBitwardenVaultId,
                    bitwardenFolderId = targetBitwardenFolderId
                )
            }
            if (moved) successCount++ else failedCount++
            reportProgress()
        }

        aggregateSelection.bankCards.forEach { item ->
            val bankCardViewModel = aggregateUiState.bankCardViewModel
            if (bankCardViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                val moved = if (item.isLocalOnlyItem()) {
                    bankCardViewModel.moveCardToStorage(
                        id = item.id,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = null,
                        keepassGroupPath = null,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null
                    )
                    true
                } else {
                    bankCardViewModel.moveCardToMonicaLocal(item, targetCategoryId).isSuccess
                }
                if (moved) successCount++ else failedCount++
                reportProgress()
            } else {
                bankCardViewModel.moveCardToStorage(
                    id = item.id,
                    categoryId = targetCategoryId,
                    keepassDatabaseId = targetKeepassDatabaseId,
                    keepassGroupPath = targetKeepassGroupPath,
                    bitwardenVaultId = targetBitwardenVaultId,
                    bitwardenFolderId = targetBitwardenFolderId
                )
                successCount++
                reportProgress()
            }
        }

        aggregateSelection.documents.forEach { item ->
            val documentViewModel = aggregateUiState.documentViewModel
            if (documentViewModel == null) {
                failedCount++
                reportProgress()
                return@forEach
            }
            if (isMonicaLocalTarget) {
                val moved = if (item.isLocalOnlyItem()) {
                    documentViewModel.moveDocumentToStorage(
                        id = item.id,
                        categoryId = targetCategoryId,
                        keepassDatabaseId = null,
                        keepassGroupPath = null,
                        bitwardenVaultId = null,
                        bitwardenFolderId = null
                    )
                    true
                } else {
                    documentViewModel.moveDocumentToMonicaLocal(item, targetCategoryId).isSuccess
                }
                if (moved) successCount++ else failedCount++
                reportProgress()
            } else {
                documentViewModel.moveDocumentToStorage(
                    id = item.id,
                    categoryId = targetCategoryId,
                    keepassDatabaseId = targetKeepassDatabaseId,
                    keepassGroupPath = targetKeepassGroupPath,
                    bitwardenVaultId = targetBitwardenVaultId,
                    bitwardenFolderId = targetBitwardenFolderId
                )
                successCount++
                reportProgress()
            }
        }

        if (!isMonicaLocalTarget) {
            val totpIds = aggregateSelection.totpItems.map(SecureItem::id)
            when (target) {
                is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                    if (totpIds.isNotEmpty()) {
                        aggregateUiState.totpViewModel?.moveToBitwardenFolder(totpIds, target.vaultId, "")
                        successCount += totpIds.size
                        reportProgress(totpIds.size)
                    }
                }

                is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                    if (totpIds.isNotEmpty()) {
                        aggregateUiState.totpViewModel?.moveToBitwardenFolder(
                            totpIds,
                            target.vaultId,
                            target.folderId
                        )
                        successCount += totpIds.size
                        reportProgress(totpIds.size)
                    }
                }

                is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                    if (totpIds.isNotEmpty()) {
                        aggregateUiState.totpViewModel?.moveToKeePassDatabase(totpIds, target.databaseId)
                        successCount += totpIds.size
                        reportProgress(totpIds.size)
                    }
                }

                is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                    if (totpIds.isNotEmpty()) {
                        aggregateUiState.totpViewModel?.moveToKeePassGroup(
                            totpIds,
                            target.databaseId,
                            target.groupPath
                        )
                        successCount += totpIds.size
                        reportProgress(totpIds.size)
                    }
                }

                else -> Unit
            }
        }

        if (selectedEntries.isNotEmpty()) {
            val targetLabel = buildMoveTargetLabel(
                context = context,
                target = target,
                categories = categories,
                keepassDatabases = keepassDatabases
            )
            logPasswordBatchMoveTimeline(
                context = context,
                selectedEntries = selectedEntries,
                oldStates = oldStates,
                newStates = newStates,
                recreatedEntries = recreatedEntries,
                targetLabel = targetLabel
            )
        }
    }

    val movablePasskeys = aggregateSelection.passkeys
        .filter { it.boundPasswordId == null && it.syncStatus != "REFERENCE" }
    val blockedPasskeysByType = aggregateSelection.passkeys.size - movablePasskeys.size
    if (blockedPasskeysByType > 0) {
        failedCount += blockedPasskeysByType
        reportProgress(blockedPasskeysByType)
    }
    movablePasskeys.forEach { passkey ->
        val updateResult = applyPasswordPagePasskeyStorageTarget(
            passkey = passkey,
            target = target,
            bitwardenRepository = bitwardenRepository
        )
        when {
            updateResult.isSuccess &&
                aggregateUiState.passkeyViewModel?.updatePasskey(updateResult.getOrThrow())?.isSuccess == true -> {
                successCount++
            }

            updateResult.exceptionOrNull() is PasswordPagePasskeyBitwardenMoveBlockedException -> {
                blockedPasskeyCount++
                failedCount++
            }

            else -> failedCount++
        }
        reportProgress()
    }

    if (copiedPasswordIds.isNotEmpty()) {
        logPasswordBatchCopyTimeline(
            context = context,
            copiedEntryIds = copiedPasswordIds
        )
    }

    return MixedPasswordBatchMoveResult(
        successCount = successCount,
        failedCount = failedCount,
        blockedPasskeyCount = blockedPasskeyCount,
        copiedPasswordIds = copiedPasswordIds
    )
}

private suspend fun PasswordViewModel.addPasswordEntryWithResultAwait(
    entry: PasswordEntry
): Long? {
    val deferred = CompletableDeferred<Long?>()
    addPasswordEntryWithResult(
        entry = entry,
        includeDetailedLog = false
    ) { createdId ->
        deferred.complete(createdId)
    }
    return deferred.await()
}

private class PasswordPagePasskeyBitwardenMoveBlockedException :
    IllegalStateException("Passkey cannot be migrated to Bitwarden")

private suspend fun applyPasswordPagePasskeyStorageTarget(
    passkey: PasskeyEntry,
    target: UnifiedMoveCategoryTarget,
    bitwardenRepository: BitwardenRepository
): Result<PasskeyEntry> {
    val currentVaultId = passkey.bitwardenVaultId
    val currentCipherId = passkey.bitwardenCipherId
    val targetVaultId = when (target) {
        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> target.vaultId
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> target.vaultId
        else -> null
    }

    if (targetVaultId != null) {
        val canMoveToBitwarden = withContext(Dispatchers.IO) {
            isPasswordPagePasskeyMigratableToBitwarden(passkey)
        }
        if (!canMoveToBitwarden) {
            return Result.failure(PasswordPagePasskeyBitwardenMoveBlockedException())
        }
    }

    val isLeavingCurrentCipher =
        currentVaultId != null &&
            !currentCipherId.isNullOrBlank() &&
            currentVaultId != targetVaultId

    if (isLeavingCurrentCipher) {
        val queueResult = bitwardenRepository.queueCipherDelete(
            vaultId = currentVaultId,
            cipherId = currentCipherId!!,
            itemType = BitwardenPendingOperation.ITEM_TYPE_PASSKEY
        )
        if (queueResult.isFailure) {
            return Result.failure(
                queueResult.exceptionOrNull()
                    ?: IllegalStateException("Queue Bitwarden delete failed")
            )
        }
    }

    val moved = when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> passkey.copy(
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenFolderId = null,
            bitwardenVaultId = null
        )

        is UnifiedMoveCategoryTarget.MonicaCategory -> passkey.copy(
            categoryId = target.categoryId,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenFolderId = null,
            bitwardenVaultId = null
        )

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> passkey.copy(
            bitwardenVaultId = target.vaultId,
            bitwardenFolderId = null,
            keepassGroupPath = null,
            keepassDatabaseId = null
        )

        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> passkey.copy(
            bitwardenVaultId = target.vaultId,
            bitwardenFolderId = target.folderId,
            keepassGroupPath = null,
            keepassDatabaseId = null
        )

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> passkey.copy(
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = null,
            bitwardenFolderId = null,
            bitwardenVaultId = null
        )

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> passkey.copy(
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = target.groupPath,
            bitwardenFolderId = null,
            bitwardenVaultId = null
        )
    }

    val keepExistingCipher =
        !currentCipherId.isNullOrBlank() &&
            currentVaultId != null &&
            currentVaultId == targetVaultId

    val resolvedSyncStatus = when {
        moved.syncStatus == "REFERENCE" -> "REFERENCE"
        targetVaultId == null -> "NONE"
        keepExistingCipher -> if (passkey.syncStatus == "SYNCED") "SYNCED" else "PENDING"
        else -> "PENDING"
    }
    val resolvedMode = when (target) {
        is UnifiedMoveCategoryTarget.BitwardenVaultTarget,
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> PasskeyEntry.MODE_BW_COMPAT
        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget,
        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> PasskeyEntry.MODE_KEEPASS_COMPAT
        else -> if (passkey.isKeePassCompatible()) {
            PasskeyEntry.MODE_KEEPASS_COMPAT
        } else {
            passkey.passkeyMode
        }
    }

    return Result.success(
        moved.copy(
            passkeyMode = resolvedMode,
            bitwardenCipherId = if (keepExistingCipher) currentCipherId else null,
            syncStatus = resolvedSyncStatus
        )
    )
}

private fun isPasswordPagePasskeyMigratableToBitwarden(passkey: PasskeyEntry): Boolean {
    if (passkey.passkeyMode != PasskeyEntry.MODE_BW_COMPAT) return false
    if (passkey.syncStatus == "REFERENCE") return false
    return PasskeyPrivateKeySupport.hasBitwardenCompatiblePrivateKey(passkey.privateKeyAlias)
}
