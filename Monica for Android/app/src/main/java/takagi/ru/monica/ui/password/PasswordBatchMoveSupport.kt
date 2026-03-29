package takagi.ru.monica.ui

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.R
import takagi.ru.monica.data.OperationLogItemType
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_COPY_PAYLOAD
import takagi.ru.monica.data.model.TIMELINE_FIELD_BATCH_MOVE_PAYLOAD
import takagi.ru.monica.data.model.TimelineBatchCopyPayload
import takagi.ru.monica.data.model.TimelineBatchMovePayload
import takagi.ru.monica.data.model.TimelinePasswordLocationState
import takagi.ru.monica.ui.components.UNIFIED_MOVE_ARCHIVE_SENTINEL_CATEGORY_ID
import takagi.ru.monica.ui.components.UnifiedMoveAction
import takagi.ru.monica.ui.components.UnifiedMoveCategoryTarget
import takagi.ru.monica.utils.FieldChange
import takagi.ru.monica.utils.OperationLogger

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
