package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
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
import takagi.ru.monica.data.model.TimelinePasswordRecreatedEntry
import takagi.ru.monica.ui.password.PasswordAggregateListItemUi
import takagi.ru.monica.ui.password.PasswordBatchTransferProgressTracker
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

internal data class PasswordBatchTransferProgressUiState(
    val action: UnifiedMoveAction,
    val targetLabel: String,
    val processed: Int,
    val total: Int
) {
    val progressFraction: Float
        get() = if (total <= 0) 0f else processed.toFloat() / total.toFloat()

    val progressText: String
        get() = "$processed / $total"
}

private fun formatBatchResultToast(
    context: Context,
    successCount: Int,
    failedCount: Int
): String {
    return if (failedCount > 0) {
        context.getString(
            R.string.password_batch_transfer_partial_result,
            successCount,
            failedCount
        )
    } else {
        context.getString(R.string.selected_items, successCount)
    }
}

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
        bitwardenCipherId = entry.bitwardenCipherId,
        bitwardenFolderId = entry.bitwardenFolderId,
        bitwardenRevisionDate = entry.bitwardenRevisionDate,
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
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
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
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
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
                    bitwardenCipherId = null,
                    bitwardenFolderId = null,
                    bitwardenRevisionDate = null,
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
            bitwardenCipherId = null,
            bitwardenFolderId = "",
            bitwardenRevisionDate = null,
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
            bitwardenCipherId = null,
            bitwardenFolderId = target.folderId,
            bitwardenRevisionDate = null,
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
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
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
            bitwardenCipherId = null,
            bitwardenFolderId = null,
            bitwardenRevisionDate = null,
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

internal data class PasswordBatchCopyResult(
    val successCount: Int,
    val failedCount: Int,
    val copiedEntryIds: List<Long>
)

internal suspend fun executePasswordBatchCopy(
    context: Context,
    selectedEntries: List<PasswordEntry>,
    target: UnifiedMoveCategoryTarget,
    targetRouting: PasswordBatchMoveTargetRouting,
    copyPasswordToMonicaLocal: suspend (PasswordEntry, Long?) -> Long?,
    addCopiedEntry: suspend (PasswordEntry) -> Long?,
    buildCopiedEntryForTarget: (PasswordEntry, UnifiedMoveCategoryTarget) -> PasswordEntry,
    onProgress: ((Int, Int) -> Unit)? = null
): PasswordBatchCopyResult {
    val copiedIds = mutableListOf<Long>()
    var failedCount = 0
    val total = selectedEntries.size
    var processed = 0
    if (total > 0) {
        onProgress?.invoke(0, total)
    }

    if (targetRouting.isMonicaCopyTarget) {
        selectedEntries.forEach { entry ->
            val createdId = copyPasswordToMonicaLocal(entry, targetRouting.monicaCategoryId)
            if (createdId != null && createdId > 0) {
                copiedIds += createdId
            } else {
                failedCount += 1
            }
            processed += 1
            onProgress?.invoke(processed, total)
        }
    } else {
        selectedEntries.forEach { entry ->
            val copiedEntry = buildCopiedEntryForTarget(entry, target)
            val createdId = addCopiedEntry(copiedEntry)
            if (createdId != null && createdId > 0) {
                copiedIds += createdId
            } else {
                failedCount += 1
            }
            processed += 1
            onProgress?.invoke(processed, total)
        }
    }

    logPasswordBatchCopyTimeline(
        context = context,
        copiedEntryIds = copiedIds.toList()
    )

    return PasswordBatchCopyResult(
        successCount = copiedIds.size,
        failedCount = failedCount,
        copiedEntryIds = copiedIds.toList()
    )
}

private val timelineBatchJson = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

internal fun logPasswordBatchCopyTimeline(
    context: Context,
    copiedEntryIds: List<Long>,
    copiedCountOverride: Int? = null
) {
    val copiedCount = copiedCountOverride ?: copiedEntryIds.size
    if (copiedCount <= 0) return
    val payload = TimelineBatchCopyPayload(copiedEntryIds = copiedEntryIds)
    OperationLogger.logUpdate(
        itemType = OperationLogItemType.PASSWORD,
        itemId = System.currentTimeMillis(),
        itemTitle = context.getString(
            R.string.timeline_batch_copy_title,
            copiedCount
        ),
        changes = listOf(
            FieldChange(
                fieldName = context.getString(R.string.timeline_field_batch_copy),
                oldValue = "0",
                newValue = copiedCount.toString()
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
    recreatedEntries: List<TimelinePasswordRecreatedEntry> = emptyList(),
    targetLabel: String
) {
    if (selectedEntries.isEmpty()) return
    val payload = TimelineBatchMovePayload(
        oldStates = oldStates,
        newStates = newStates,
        recreatedEntries = recreatedEntries
    )
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

private fun buildPasswordDecryptSnapshot(
    entries: List<PasswordEntry>,
    securityManager: SecurityManager
): Map<String, String> {
    return entries.mapNotNull { entry ->
        runCatching { securityManager.decryptData(entry.password) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.let { plain -> entry.password to plain }
    }.toMap()
}

private fun resolvePasswordForBatchMove(
    encrypted: String,
    decryptSnapshot: Map<String, String>,
    securityManager: SecurityManager
): String {
    return decryptSnapshot[encrypted]
        ?: securityManager.decryptData(encrypted)
        ?: ""
}

private fun PasswordBatchAggregateSelection.totalItemCount(
    selectedPasswordCount: Int
): Int {
    return selectedPasswordCount +
        bankCards.size +
        documents.size +
        notes.size +
        totpItems.size +
        passkeys.size
}

@Composable
private fun PasswordBatchTransferProgressDialog(
    state: PasswordBatchTransferProgressUiState,
    onMoveToBackground: () -> Unit
) {
    val title = when (state.action) {
        UnifiedMoveAction.COPY -> R.string.password_batch_transfer_progress_title_copy
        UnifiedMoveAction.MOVE -> R.string.password_batch_transfer_progress_title_move
    }
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(text = state.targetLabel)
        },
        text = {
            Column {
                Text(text = stringResource(id = title))
                Spacer(modifier = Modifier.height(12.dp))
                if (state.total > 0 && state.processed <= 0) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { state.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (state.total > 0 && state.processed <= 0) {
                        stringResource(R.string.password_batch_transfer_progress_preparing)
                    } else {
                        state.progressText
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onMoveToBackground) {
                Text(text = stringResource(R.string.password_batch_transfer_continue_in_background))
            }
        }
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
    var transferProgress by remember {
        mutableStateOf<PasswordBatchTransferProgressUiState?>(null)
    }
    var showProgressDialog by remember {
        mutableStateOf(true)
    }

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
            val selectedIds = selectedEntries.map(PasswordEntry::id)
            val forceCopyForProgress = action == UnifiedMoveAction.MOVE &&
                (selectedEntries.any { it.isKeePassEntry() } || aggregateSelection.hasKeePassOwned)
            val effectiveAction = if (forceCopyForProgress) {
                UnifiedMoveAction.COPY
            } else {
                action
            }
            val totalCount = if (hasMixedSelection) {
                aggregateSelection.totalItemCount(selectedEntries.size)
            } else {
                selectedEntries.size
            }
            if (totalCount <= 0) {
                onDismiss()
                onSelectionCleared()
                return@UnifiedMoveToCategoryBottomSheet
            }

            val targetLabel = buildMoveTargetLabel(
                context = context,
                target = target,
                categories = categories,
                keepassDatabases = keepassDatabases
            )
            val notificationId = PasswordBatchTransferNotificationHelper.createNotificationId()
            var lastKnownProcessed = 0
            var lastKnownTotal = totalCount
            val onProgressUpdate: (Int, Int) -> Unit = { processed, total ->
                val normalizedTotal = total.coerceAtLeast(totalCount)
                val normalizedProcessed = processed.coerceIn(0, normalizedTotal)
                lastKnownProcessed = maxOf(lastKnownProcessed, normalizedProcessed)
                lastKnownTotal = normalizedTotal
                coroutineScope.launch {
                    transferProgress = PasswordBatchTransferProgressUiState(
                        action = effectiveAction,
                        targetLabel = targetLabel,
                        processed = normalizedProcessed,
                        total = normalizedTotal
                    )
                }
                PasswordBatchTransferProgressTracker.update(
                    action = effectiveAction,
                    targetLabel = targetLabel,
                    processed = normalizedProcessed,
                    total = normalizedTotal
                )
                PasswordBatchTransferNotificationHelper.showProgress(
                    context = context,
                    notificationId = notificationId,
                    action = effectiveAction,
                    processed = normalizedProcessed,
                    total = normalizedTotal,
                    targetLabel = targetLabel
                )
            }

            showProgressDialog = true
            onProgressUpdate(if (totalCount > 1) 1 else 0, totalCount)
            onDismiss()

            viewModel.viewModelScope.launch {
                var successCount = 0
                var failedCount = 0
                try {
                    if (hasMixedSelection) {
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
                            bitwardenRepository = bitwardenRepository,
                            onProgress = onProgressUpdate
                        )
                        successCount = result.successCount
                        failedCount = result.failedCount
                        if (result.blockedPasskeyCount > 0) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.passkey_bitwarden_move_blocked),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    } else {
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

                        val resolvedAction = actionResolution.effectiveAction
                        val targetRouting = resolvePasswordBatchMoveTargetRouting(target)
                        if (resolvedAction == UnifiedMoveAction.COPY) {
                            when (target) {
                                is UnifiedMoveCategoryTarget.KeePassDatabaseTarget,
                                is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                                    val decryptSnapshot = buildPasswordDecryptSnapshot(
                                        entries = selectedEntries,
                                        securityManager = securityManager
                                    )
                                    val copiedEntries = selectedEntries.map {
                                        buildCopiedEntryForTarget(it, target)
                                    }
                                    val targetDatabaseId = when (target) {
                                        is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> target.databaseId
                                        is UnifiedMoveCategoryTarget.KeePassGroupTarget -> target.databaseId
                                        else -> error("Unexpected KeePass target")
                                    }
                                    val addResult = localKeePassViewModel.addPasswordEntriesToKdbx(
                                        databaseId = targetDatabaseId,
                                        entries = copiedEntries,
                                        decryptPassword = { encrypted ->
                                            resolvePasswordForBatchMove(
                                                encrypted = encrypted,
                                                decryptSnapshot = decryptSnapshot,
                                                securityManager = securityManager
                                            )
                                        },
                                        onItemProcessed = onProgressUpdate
                                    )
                                    if (addResult.isFailure) {
                                        throw addResult.exceptionOrNull()
                                            ?: IllegalStateException("Copy to KeePass failed")
                                    }
                                    val addedCount = addResult.getOrThrow().coerceIn(0, selectedEntries.size)
                                    successCount = addedCount
                                    failedCount = (selectedEntries.size - addedCount).coerceAtLeast(0)
                                    logPasswordBatchCopyTimeline(
                                        context = context,
                                        copiedEntryIds = emptyList(),
                                        copiedCountOverride = successCount
                                    )
                                }

                                else -> {
                                    val copyResult = executePasswordBatchCopy(
                                        context = context,
                                        selectedEntries = selectedEntries,
                                        target = target,
                                        targetRouting = targetRouting,
                                        copyPasswordToMonicaLocal = { entry, categoryId ->
                                            viewModel.copyPasswordToMonicaLocal(
                                                entry = entry,
                                                categoryId = categoryId
                                            )
                                        },
                                        addCopiedEntry = { entry ->
                                            viewModel.addPasswordEntryWithResultAwait(entry)
                                        },
                                        buildCopiedEntryForTarget = ::buildCopiedEntryForTarget,
                                        onProgress = onProgressUpdate
                                    )
                                    successCount = copyResult.successCount
                                    failedCount = copyResult.failedCount
                                }
                            }
                        } else {
                            val oldStates = selectedEntries.map(::toLocationState)
                            val newStates = selectedEntries.map { toMovedLocationState(it, target) }
                            val recreatedEntries = mutableListOf<TimelinePasswordRecreatedEntry>()
                            val decryptSnapshot = buildPasswordDecryptSnapshot(
                                entries = selectedEntries,
                                securityManager = securityManager
                            )

                            when {
                                targetRouting.isArchiveTarget -> {
                                    viewModel.archivePasswords(selectedIds)
                                    onProgressUpdate(selectedEntries.size, selectedEntries.size)
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
                                            throw result.exceptionOrNull()
                                                ?: IllegalStateException("Keepass move failed")
                                        }
                                        val keepassIds = keepassEntries.map { it.id }
                                        viewModel.unarchivePasswordsAwait(keepassIds)
                                        viewModel.movePasswordsToCategoryAwait(keepassIds, null)
                                    }

                                    bitwardenEntries.forEach { entry ->
                                        val result = viewModel.moveBitwardenPasswordToMonicaLocal(entry, null)
                                        if (result.isFailure) {
                                            throw result.exceptionOrNull()
                                                ?: IllegalStateException("Bitwarden move failed")
                                        }
                                        recreatedEntries += TimelinePasswordRecreatedEntry(
                                            sourceEntryId = entry.id,
                                            recreatedEntryId = result.getOrThrow()
                                        )
                                    }

                                    if (localIds.isNotEmpty()) {
                                        viewModel.unarchivePasswordsAwait(localIds)
                                        viewModel.movePasswordsToCategoryAwait(localIds, null)
                                    }
                                    onProgressUpdate(selectedEntries.size, selectedEntries.size)
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
                                            throw result.exceptionOrNull()
                                                ?: IllegalStateException("Keepass move failed")
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
                                            throw result.exceptionOrNull()
                                                ?: IllegalStateException("Bitwarden move failed")
                                        }
                                        recreatedEntries += TimelinePasswordRecreatedEntry(
                                            sourceEntryId = entry.id,
                                            recreatedEntryId = result.getOrThrow()
                                        )
                                    }

                                    if (localIds.isNotEmpty()) {
                                        viewModel.unarchivePasswordsAwait(localIds)
                                        viewModel.movePasswordsToCategoryAwait(localIds, target.categoryId)
                                    }
                                    onProgressUpdate(selectedEntries.size, selectedEntries.size)
                                }

                                target is UnifiedMoveCategoryTarget.BitwardenVaultTarget -> {
                                    viewModel.unarchivePasswordsAwait(selectedIds)
                                    viewModel.movePasswordsToBitwardenFolderAwait(selectedIds, target.vaultId, "")
                                    onProgressUpdate(selectedEntries.size, selectedEntries.size)
                                }

                                target is UnifiedMoveCategoryTarget.BitwardenFolderTarget -> {
                                    viewModel.unarchivePasswordsAwait(selectedIds)
                                    viewModel.movePasswordsToBitwardenFolderAwait(
                                        selectedIds,
                                        target.vaultId,
                                        target.folderId
                                    )
                                    onProgressUpdate(selectedEntries.size, selectedEntries.size)
                                }

                                target is UnifiedMoveCategoryTarget.KeePassDatabaseTarget -> {
                                    val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                                        databaseId = target.databaseId,
                                        groupPath = null,
                                        entries = selectedEntries,
                                        decryptPassword = { encrypted ->
                                            resolvePasswordForBatchMove(
                                                encrypted = encrypted,
                                                decryptSnapshot = decryptSnapshot,
                                                securityManager = securityManager
                                            )
                                        },
                                        onItemProcessed = onProgressUpdate
                                    )
                                    if (result.isFailure) {
                                        throw result.exceptionOrNull()
                                            ?: IllegalStateException("Move to KeePass database failed")
                                    }
                                    viewModel.unarchivePasswordsAwait(selectedIds)
                                    viewModel.movePasswordsToKeePassDatabaseAwait(selectedIds, target.databaseId)
                                }

                                target is UnifiedMoveCategoryTarget.KeePassGroupTarget -> {
                                    val result = localKeePassViewModel.movePasswordEntriesToKdbx(
                                        databaseId = target.databaseId,
                                        groupPath = target.groupPath,
                                        entries = selectedEntries,
                                        decryptPassword = { encrypted ->
                                            resolvePasswordForBatchMove(
                                                encrypted = encrypted,
                                                decryptSnapshot = decryptSnapshot,
                                                securityManager = securityManager
                                            )
                                        },
                                        onItemProcessed = onProgressUpdate
                                    )
                                    if (result.isFailure) {
                                        throw result.exceptionOrNull()
                                            ?: IllegalStateException("Move to KeePass group failed")
                                    }
                                    viewModel.unarchivePasswordsAwait(selectedIds)
                                    viewModel.movePasswordsToKeePassGroupAwait(
                                        selectedIds,
                                        target.databaseId,
                                        target.groupPath
                                    )
                                }
                            }

                            logPasswordBatchMoveTimeline(
                                context = context,
                                selectedEntries = selectedEntries,
                                oldStates = oldStates,
                                newStates = newStates,
                                recreatedEntries = recreatedEntries,
                                targetLabel = targetLabel
                            )
                            successCount = selectedEntries.size
                            failedCount = 0
                        }
                    }

                    PasswordBatchTransferNotificationHelper.showCompleted(
                        context = context,
                        notificationId = notificationId,
                        action = effectiveAction,
                        successCount = successCount,
                        failedCount = failedCount
                    )
                    Toast.makeText(
                        context,
                        formatBatchResultToast(
                            context = context,
                            successCount = successCount,
                            failedCount = failedCount
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    onSelectionCleared()
                } catch (e: Exception) {
                    val normalizedTotal = lastKnownTotal.coerceAtLeast(totalCount)
                    val inferredSuccessCount = maxOf(
                        successCount,
                        (lastKnownProcessed - failedCount).coerceAtLeast(0)
                    ).coerceIn(0, normalizedTotal)
                    val normalizedFailedCount = if (failedCount > 0) {
                        maxOf(failedCount, normalizedTotal - inferredSuccessCount)
                            .coerceIn(0, normalizedTotal)
                    } else {
                        (normalizedTotal - inferredSuccessCount).coerceAtLeast(0)
                    }
                    PasswordBatchTransferNotificationHelper.showCompleted(
                        context = context,
                        notificationId = notificationId,
                        action = effectiveAction,
                        successCount = inferredSuccessCount,
                        failedCount = normalizedFailedCount
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.webdav_operation_failed, e.message ?: ""),
                        Toast.LENGTH_SHORT
                    ).show()
                } finally {
                    transferProgress = null
                    showProgressDialog = false
                    PasswordBatchTransferProgressTracker.clear()
                }
            }
            return@UnifiedMoveToCategoryBottomSheet
        }
    )

    if (showProgressDialog) {
        transferProgress?.let { state ->
            PasswordBatchTransferProgressDialog(
                state = state,
                onMoveToBackground = { showProgressDialog = false }
            )
        }
    }
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
