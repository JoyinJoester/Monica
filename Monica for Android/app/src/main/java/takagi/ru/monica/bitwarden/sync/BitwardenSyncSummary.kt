package takagi.ru.monica.bitwarden.sync

import android.content.Context
import takagi.ru.monica.R

data class BitwardenSyncSummary(
    val vaultId: Long,
    val vaultLabel: String,
    val appliedChangeCount: Int,
    val offlineReadyCount: Int,
    val conflictCount: Int,
    val uploadFailedCount: Int,
    val skippedDueToLocalDirtyCount: Int
) {
    val hasWarnings: Boolean
        get() = conflictCount > 0 || uploadFailedCount > 0 || skippedDueToLocalDirtyCount > 0
}

fun BitwardenSyncSummary.buildHeadline(context: Context): String {
    val titleRes = if (hasWarnings) {
        R.string.bitwarden_sync_summary_warning_title
    } else {
        R.string.bitwarden_sync_summary_success_title
    }
    return context.getString(titleRes, appliedChangeCount)
}

fun BitwardenSyncSummary.buildDetailLine(context: Context): String {
    val parts = mutableListOf<String>()
    if (vaultLabel.isNotBlank()) {
        parts += vaultLabel
    }
    parts += context.getString(R.string.bitwarden_sync_summary_offline_detail, offlineReadyCount)
    if (conflictCount > 0) {
        parts += context.getString(R.string.bitwarden_sync_summary_conflict_detail, conflictCount)
    }
    if (uploadFailedCount > 0) {
        parts += context.getString(R.string.bitwarden_sync_summary_upload_failed_detail, uploadFailedCount)
    }
    if (skippedDueToLocalDirtyCount > 0) {
        parts += context.getString(
            R.string.bitwarden_sync_summary_local_dirty_detail,
            skippedDueToLocalDirtyCount
        )
    }
    return parts.joinToString(" · ")
}

fun BitwardenSyncSummary.buildMiniHintTitle(context: Context): String {
    val titleRes = if (hasWarnings) {
        R.string.bitwarden_sync_mini_hint_warning_title
    } else {
        R.string.bitwarden_sync_mini_hint_success_title
    }
    return context.getString(titleRes, appliedChangeCount)
}

fun BitwardenSyncSummary.buildMiniHintDetail(context: Context): String? {
    val parts = buildList {
        if (conflictCount > 0) {
            add(context.getString(R.string.bitwarden_sync_summary_conflict_detail, conflictCount))
        }
        if (uploadFailedCount > 0) {
            add(context.getString(R.string.bitwarden_sync_summary_upload_failed_detail, uploadFailedCount))
        }
        if (skippedDueToLocalDirtyCount > 0) {
            add(
                context.getString(
                    R.string.bitwarden_sync_summary_local_dirty_detail,
                    skippedDueToLocalDirtyCount
                )
            )
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}
