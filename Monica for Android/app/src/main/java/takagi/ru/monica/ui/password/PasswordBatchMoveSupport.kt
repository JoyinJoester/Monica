package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Date
import takagi.ru.monica.R
import takagi.ru.monica.bitwarden.repository.BitwardenRepository
import takagi.ru.monica.data.Category
import takagi.ru.monica.data.LocalKeePassDatabase
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_COPY_PAYLOAD
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_MOVE_PAYLOAD
import takagi.ru.monica.data.model.TimelineBatchCopyPayload
import takagi.ru.monica.data.model.TimelineBatchMovePayload
import takagi.ru.monica.data.model.TimelinePasswordLocationState
import takagi.ru.monica.ui.password.PasswordAggregateListItemUi
import takagi.ru.monica.ui.components.UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
import takagi.ru.monica.ui.components.UnifiedMoveToCategoryBottomSheet
import takagi.ru.monica.ui.components.UnifiedMoveAction
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.utils.decodeKeePassPathForDisplay
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.OperationLogger
import takagi.ru.monica.viewmodel.PasswordViewModel

internal data class PasswordBatchMoveActionResolution(
    val effectiveAction: UnifiedMoveAction,
    val showKeepassCopyOnlyHint: Boolean
)

internal data class PasswordBatchMoveTargetRouting(
    val isArchiveTarget: Boolean,
    val monicaCategoryId: Long?,
    val isMonicaCopyTarget: Boolean
)

internal fun resolvePasswordBatchMoveAction(
    requestedAction: UnifiedMoveAction,
    selectedEntries: List<PasswordEntry>
): PasswordBatchMoveActionResolution {
    val hasKeePassEntries = selectedEntries.any { it.isKeePassEntry() }
    val forceCopy = requestedAction == UnifiedMoveAction.MOVE && hasKeePassEntries
    return PasswordBatchMoveActionResolution(
        effectiveAction = if (forceCopy) UnifiedMoveAction.COPY else requestedAction,
        showKeepassCopyOnlyHint = forceCopy
    )
}

internal fun resolvePasswordBatchMoveTargetRouting(
    target: UnifiedMoveCategoryTarget
): PasswordBatchMoveTargetRouting {
    val isArchiveTarget = target is UnifiedMoveCategoryTarget.MonicaCategory &&
        target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
    val monicaCategoryId = when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> null
        is UnifiedMoveCategoryTarget.MonicaCategory ->
            target.categoryId.takeUnless { it == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID }
        else -> null
    }
    val isMonicaCopyTarget = target == UnifiedMoveCategoryTarget.Uncategorized ||
        (target is UnifiedMoveCategoryTarget.MonicaCategory && !isArchiveTarget)
    return PasswordBatchMoveTargetRouting(
        isArchiveTarget = isArchiveTarget,
        monicaCategoryId = monicaCategoryId,
        isMonicaCopyTarget = isMonicaCopyTarget
    )
}

internal fun toLocationState(entry: PasswordEntry): TimelinePasswordLocationState {
    return TimelinePasswordLocationState(
        id = entry.id,
        categoryId = entry.categoryId,
        keepassDatabaseId = entry.keepassDatabaseId,
        keepassGroupPath = entry.keepassGroupPath,
        bitwardenVaultId = entry.bitwardenVaultId,
        bitwardenFolderId = entry.bitwardenFolderId,
        bitwardenLocalModified = entry.bitwardenLocalModified,
        isArchived = entry.isArchived,
        archivedAtMillis = entry.archivedAt?.time
    )
}

internal fun toMovedLocationState(
    entry: PasswordEntry,
    target: UnifiedMoveCategoryTarget
): TimelinePasswordLocationState {
    val archivedAt = if (target is UnifiedMoveCategoryTarget.MonicaCategory &&
        target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
    ) {
        entry.archivedAt?.time ?: System.currentTimeMillis()
    } else {
        null
    }

    return when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.MonicaCategory -> {
            if (target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID) {
                TimelinePasswordLocationState(
                    id = entry.id,
                    categoryId = null,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    bitwardenLocalModified = false,
                    isArchived = true,
                    archivedAtMillis = archivedAt
                )
            } else {
                TimelinePasswordLocationState(
                    id = entry.id,
                    categoryId = target.categoryId,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    bitwardenVaultId = null,
                    bitwardenFolderId = null,
                    bitwardenLocalModified = false,
                    isArchived = false,
                    archivedAtMillis = null
                )
            }
        }

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = target.vaultId,
            bitwardenFolderId = "",
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            bitwardenVaultId = target.vaultId,
            bitwardenFolderId = target.folderId,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = null,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> TimelinePasswordLocationState(
            id = entry.id,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = target.groupPath,
            bitwardenVaultId = null,
            bitwardenFolderId = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAtMillis = null
        )
    }
}

internal fun buildCopiedEntryForTarget(
    entry: PasswordEntry,
    target: UnifiedMoveCategoryTarget
): PasswordEntry {
    val now = Date()
    return when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.MonicaCategory -> {
            if (target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID) {
                entry.copy(
                    id = 0,
                    createdAt = now,
                    updatedAt = now,
                    categoryId = null,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    keepassEntryUuid = null,
                    keepassGroupUuid = null,
                    bitwardenVaultId = null,
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
                    bitwardenLocalModified = false,
                    isArchived = true,
                    archivedAt = now,
                    isDeleted = false,
                    deletedAt = null
                )
            } else {
                entry.copy(
                    id = 0,
                    createdAt = now,
                    updatedAt = now,
                    categoryId = target.categoryId,
                    keepassDatabaseId = null,
                    keepassGroupPath = null,
                    keepassEntryUuid = null,
                    keepassGroupUuid = null,
                    bitwardenVaultId = null,
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
                    bitwardenLocalModified = false,
                    isArchived = false,
                    archivedAt = null,
                    isDeleted = false,
                    deletedAt = null
                )
            }
        }

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = target.vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = "",
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = null,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = target.vaultId,
            bitwardenCipherId = null,
            bitwardenFolderId = target.folderId,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = null,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> entry.copy(
            id = 0,
            createdAt = now,
            updatedAt = now,
            categoryId = null,
            keepassDatabaseId = target.databaseId,
            keepassGroupPath = target.groupPath,
            keepassEntryUuid = null,
            keepassGroupUuid = null,
            bitwardenVaultId = null,
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
            bitwardenLocalModified = false,
            isArchived = false,
            archivedAt = null,
            isDeleted = false,
            deletedAt = null
        )
    }
}

internal fun buildMoveTargetLabel(
    context: Context,
    target: UnifiedMoveCategoryTarget,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>
): String {
    return when (target) {
        UnifiedMoveCategoryTarget.Uncategorized -> context.getString(R.string.category_none)
        is UnifiedMoveCategoryTarget.MonicaCategory -> {
            if (target.categoryId == UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID) {
                context.getString(R.string.archive_page_title)
            } else {
                categories.find { it.id == target.categoryId }?.name
                    ?: context.getString(R.string.filter_monica)
            }
        }

        is UnifiedMoveCategoryTarget.BitwardenVaultTarget,
        is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> context.getString(R.string.filter_bitwarden)

        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
            keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"
        }

        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> decodeKeePassPathForDisplay(target.groupPath)
    }
}

internal fun executePasswordBatchCopy(
    context: Context,
    coroutineScope: CoroutineScope,
    selectedEntries: List<PasswordEntry>,
    target: UnifiedMoveCategoryTarget,
    targetRouting: PasswordBatchMoveTargetRouting,
    copyPasswordToMonicaLocal: suspend (PasswordEntry, Long?) -> Long?,
    addCopiedEntry: (PasswordEntry, (Long?) -> Unit) -> Unit,
    buildCopiedEntryForTarget: (PasswordEntry, UnifiedMoveCategoryTarget) -> PasswordEntry
) {
    if (targetRouting.isMonicaCopyTarget) {
        coroutineScope.launch {
            val copiedIds = mutableListOf<Long>()
            selectedEntries.forEach { entry ->
                val createdId = copyPasswordToMonicaLocal(entry, targetRouting.monicaCategoryId)
                if (createdId != null && createdId > 0) {
                    copiedIds += createdId
                }
            }
            logPasswordBatchCopyTimeline(
                context = context,
                copiedEntryIds = copiedIds.toList()
            )
            Toast.makeText(
                context,
                context.getString(R.string.selected_items, copiedIds.size),
                Toast.LENGTH_SHORT
            ).show()
        }
        return
    }

    val copiedIds = mutableListOf<Long>()
    var remaining = selectedEntries.size
    selectedEntries.forEach { entry ->
        val copiedEntry = buildCopiedEntryForTarget(entry, target)
        addCopiedEntry(copiedEntry) { createdId ->
            if (createdId != null && createdId > 0) {
                copiedIds.add(createdId)
            }
            remaining -= 1
            if (remaining == 0) {
                logPasswordBatchCopyTimeline(
                    context = context,
                    copiedEntryIds = copiedIds.toList()
                )
            }
        }
    }
    Toast.makeText(
        context,
        context.getString(R.string.selected_items, selectedEntries.size),
        Toast.LENGTH_SHORT
    ).show()
}

private val timelineBatchJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

internal fun logPasswordBatchCopyTimeline(
    context: Context,
    copiedEntryIds: List<Long>
) {
    if (copiedEntryIds.isEmpty()) return
    val payload = TimelineBatchCopyPayload(copiedEntryIds = copiedEntryIds)
    OperationLogger.logUpdate(
        itemType = OperationLogItemType.PASSWORD,
        itemId = System.currentTimeMillis(),
        itemTitle = context.getString(
            R.string.timeline_batch_copy_title,
            copiedEntryIds.size
        ),
        changes = listOf(
            FieldChange(
                fieldName = context.getString(R.string.timeline_field_batch_copy),
                oldValue = "0",
                newValue = copiedEntryIds.size.toString()
            ),
            FieldChange(
                fieldName = TIMELINE_FIELD_BATCH_COPY_PAYLOAD,
                oldValue = "{}",
                newValue = timelineBatchJson.encodeToString(payload)
            )
        )
    )
}

internal fun logPasswordBatchMoveTimeline(
    context: Context,
    selectedEntries: List<PasswordEntry>,
    oldStates: List<TimelinePasswordLocationState>,
    newStates: List<TimelinePasswordLocationState>,
    targetLabel: String
) {
    if (selectedEntries.isEmpty()) return
    val payload = TimelineBatchMovePayload(oldStates = oldStates, newStates = newStates)
    val payloadJson = timelineBatchJson.encodeToString(payload)
    OperationLogger.logUpdate(
        itemType = OperationLogItemType.PASSWORD,
        itemId = System.currentTimeMillis(),
        itemTitle = context.getString(
            R.string.timeline_batch_move_title,
            selectedEntries.size
        ),
        changes = listOf(
            FieldChange(
                fieldName = context.getString(R.string.timeline_field_batch_move),
                oldValue = context.getString(R.string.timeline_batch_source_multiple),
                newValue = targetLabel
            ),
            FieldChange(
                fieldName = TIMELINE_FIELD_BATCH_MOVE_PAYLOAD,
                oldValue = payloadJson,
                newValue = payloadJson
            )
        )
    )
}

@Composable
internal fun PasswordBatchMoveSheet(
    visible: Boolean,
    categories: List<Category>,
    keepassDatabases: List<LocalKeePassDatabase>,
    bitwardenVaults: List<takagi.ru.monica.data.bitwarden.BitwardenVault>,
    database: takagi.ru.monica.data.PasswordDatabase,
    localKeePassViewModel: takagi.ru.monica.viewmodel.LocalKeePassViewModel,
    securityManager: SecurityManager,
    selectedPasswords: Set<Long>,
    selectedSupplementaryItems: List<PasswordAggregateListItemUi>,
    passwordEntries: List<PasswordEntry>,
    aggregateUiState: PasswordListAggregateUiState,
    viewModel: PasswordViewModel,
    bitwardenRepository: BitwardenRepository,
    context: Context,
    coroutineScope: CoroutineScope,
    onRenameCategory: (Category) -> Unit,
    onDeleteCategory: (Category) -> Unit,
    onDismiss: () -> Unit,
    onSelectionCleared: () -> Unit
) {
    val selectedEntries = remember(selectedPasswords, passwordEntries) {
        passwordEntries.filter { it.id in selectedPasswords }
    }
    val aggregateSelection = remember(
        selectedSupplementaryItems,
        aggregateUiState.bankCards,
        aggregateUiState.documents,
        aggregateUiState.notes,
        aggregateUiState.totpItems,
        aggregateUiState.passkeys
    ) {
        aggregateUiState.resolveBatchAggregateSelection(selectedSupplementaryItems)
    }
    val hasMixedSelection = aggregateSelection.hasItems

    UnifiedMoveToCategoryBottomSheet(
        visible = visible,
        onDismiss = onDismiss,
        categories = categories,
        keepassDatabases = keepassDatabases,
        bitwardenVaults = bitwardenVaults,
        getBitwardenFolders = { vaultId -> database.bitwardenFolderDao().getFoldersByVaultFlow(vaultId) },
        getKeePassGroups = localKeePassViewModel::getGroups,
        showBitwardenFolderTargets = false,
        allowCopy = true,
        allowMove = selectedEntries.none { it.isKeePassEntry() } && !aggregateSelection.hasKeePassOwned,
        allowArchiveTarget = !hasMixedSelection,
        onTargetSelected = { target, action ->
            val selectedIds = selectedPasswords.toList()
            if (hasMixedSelection) {
                coroutineScope.launch {
                    val result = executeMixedPasswordBatchMove(
                        context = context,
                        action = action,
                        target = target,
                        selectedEntries = selectedEntries,
                        aggregateSelection = aggregateSelection,
                        categories = categories,
                        keepassDatabases = keepassDatabases,
                        localKeePassViewModel = localKeePassViewModel,
                        securityManager = securityManager,
                        viewModel = viewModel,
                        aggregateUiState = aggregateUiState,
                        bitwardenRepository = bitwardenRepository
                    )
                    if (result.blockedPasskeyCount > 0) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.passkey_bitwarden_move_blocked),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    val toastMessage = if (result.failedCount > 0) {
                        "${context.getString(R.string.selected_items, result.successCount)}，失败${result.failedCount}"
                    } else {
                        context.getString(R.string.selected_items, result.successCount)
                    }
                    Toast.makeText(context, toastMessage, Toast.LENGTH_SHORT).show()
                    onDismiss()
                    onSelectionCleared()
                }
                return@UnifiedMoveToCategoryBottomSheet
            }

            val actionResolution = resolvePasswordBatchMoveAction(
                requestedAction = action,
                selectedEntries = selectedEntries
            )
            if (actionResolution.showKeepassCopyOnlyHint) {
                Toast.makeText(
                    context,
                    context.getString(R.string.keepass_copy_only_hint),
                    Toast.LENGTH_SHORT
                ).show()
            }
            val effectiveAction = actionResolution.effectiveAction
            val targetRouting = resolvePasswordBatchMoveTargetRouting(target)
            if (effectiveAction == UnifiedMoveAction.COPY) {
                executePasswordBatchCopy(
                    context = context,
                    coroutineScope = coroutineScope,
                    selectedEntries = selectedEntries,
                    target = target,
                    targetRouting = targetRouting,
                    copyPasswordToMonicaLocal = { entry, categoryId ->
                        viewModel.copyPasswordToMonicaLocal(
                            entry = entry,
                            categoryId = categoryId
                        )
                    },
                    addCopiedEntry = { entry, onResult ->
                        viewModel.addPasswordEntryWithResult(
                            entry = entry,
                            includeDetailedLog = false,
                            onResult = onResult
                        )
                    },
                    buildCopiedEntryForTarget = ::buildCopiedEntryForTarget
                )
                onDismiss()
                onSelectionCleared()
            } else {
                val oldStates = selectedEntries.map(::toLocationState)
                val newStates = selectedEntries.map { toMovedLocationState(it, target) }
                coroutineScope.launch {
                    try {
                        when {
                            targetRouting.isArchiveTarget -> {
                                viewModel.archivePasswords(selectedIds)
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.archive_page_title),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            target == UnifiedMoveCategoryTarget.Uncategorized -> {
                                val keepassEntries = selectedEntries.filter { it.isKeePassEntry() }
                                val bitwardenEntries = selectedEntries.filter { it.isBitwardenEntry() }
                                val localIds = selectedEntries
                                    .filter { it.isLocalOnlyEntry() }
                                    .map { it.id }

                                if (keepassEntries.isNotEmpty()) {
                                    val result = localKeePassViewModel.movePasswordEntriesToMonicaLocal(keepassEntries)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.webdav_operation_failed,
                                                result.exceptionOrNull()?.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val keepassIds = keepassEntries.map { it.id }
                                    viewModel.unarchivePasswordsAwait(keepassIds)
                                    viewModel.movePasswordsToCategoryAwait(keepassIds, null)
                                }

                                bitwardenEntries.forEach { entry ->
                                    val result = viewModel.moveBitwardenPasswordToMonicaLocal(entry, null)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.webdav_operation_failed,
                                                result.exceptionOrNull()?.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                }

                                if (localIds.isNotEmpty()) {
                                    viewModel.unarchivePasswordsAwait(localIds)
                                    viewModel.movePasswordsToCategoryAwait(localIds, null)
                                }

                                Toast.makeText(
                                    context,
                                    context.getString(R.string.category_none),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            target is UnifiedMoveCategoryTarget.MonicaCategory -> {
                                val keepassEntries = selectedEntries.filter { it.isKeePassEntry() }
                                val bitwardenEntries = selectedEntries.filter { it.isBitwardenEntry() }
                                val localIds = selectedEntries
                                    .filter { it.isLocalOnlyEntry() }
                                    .map { it.id }

                                if (keepassEntries.isNotEmpty()) {
                                    val result = localKeePassViewModel.movePasswordEntriesToMonicaLocal(keepassEntries)
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.webdav_operation_failed,
                                                result.exceptionOrNull()?.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                    val keepassIds = keepassEntries.map { it.id }
                                    viewModel.unarchivePasswordsAwait(keepassIds)
                                    viewModel.movePasswordsToCategoryAwait(keepassIds, target.categoryId)
                                }

                                bitwardenEntries.forEach { entry ->
                                    val result = viewModel.moveBitwardenPasswordToMonicaLocal(
                                        entry = entry,
                                        categoryId = target.categoryId
                                    )
                                    if (result.isFailure) {
                                        Toast.makeText(
                                            context,
                                            context.getString(
                                                R.string.webdav_operation_failed,
                                                result.exceptionOrNull()?.message ?: ""
                                            ),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@launch
                                    }
                                }

                                if (localIds.isNotEmpty()) {
                                    viewModel.unarchivePasswordsAwait(localIds)
                                    viewModel.movePasswordsToCategoryAwait(localIds, target.categoryId)
                                }

                                val name = categories.find { it.id == target.categoryId }?.name ?: ""
                                Toast.makeText(
                                    context,
                                    "${context.getString(R.string.move_to_category)} $name",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            target is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                                viewModel.unarchivePasswordsAwait(selectedIds)
                                viewModel.movePasswordsToBitwardenFolderAwait(selectedIds, target.vaultId, "")
                                Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                            }

                            target is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                                viewModel.unarchivePasswordsAwait(selectedIds)
                                viewModel.movePasswordsToBitwardenFolderAwait(selectedIds, target.vaultId, target.folderId)
                                Toast.makeText(context, context.getString(R.string.filter_bitwarden), Toast.LENGTH_SHORT).show()
                            }

                            target is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                                val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                                    databaseId = target.databaseId,
                                    groupPath = null,
                                    entries = selectedEntries,
                                    decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                )
                                if (result.isSuccess) {
                                    viewModel.unarchivePasswordsAwait(selectedIds)
                                    viewModel.movePasswordsToKeePassDatabaseAwait(selectedIds, target.databaseId)
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.move_to_category)} ${keepassDatabases.find { it.id == target.databaseId }?.name ?: "KeePass"}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.webdav_operation_failed,
                                            result.exceptionOrNull()?.message ?: ""
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                            }

                            target is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                                val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                                    databaseId = target.databaseId,
                                    groupPath = target.groupPath,
                                    entries = selectedEntries,
                                    decryptPassword = { encrypted -> securityManager.decryptData(encrypted) ?: "" }
                                )
                                if (result.isSuccess) {
                                    viewModel.unarchivePasswordsAwait(selectedIds)
                                    viewModel.movePasswordsToKeePassGroupAwait(selectedIds, target.databaseId, target.groupPath)
                                    Toast.makeText(
                                        context,
                                        "${context.getString(R.string.move_to_category)} ${decodeKeePassPathForDisplay(target.groupPath)}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(
                                            R.string.webdav_operation_failed,
                                            result.exceptionOrNull()?.message ?: ""
                                        ),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    return@launch
                                }
                            }
                        }

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
                            targetLabel = targetLabel
                        )
                        onDismiss()
                        onSelectionCleared()
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
                return@UnifiedMoveToCategoryBottomSheet
            }
        }
    )
}
