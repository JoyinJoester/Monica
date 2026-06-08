package takagi.ru.monica.data.dedup

import takagi.ru.monica.data.CustomField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.SecureItem

enum class DedupMergeSourceKind {
    MONICA_LOCAL,
    MDBX,
    KEEPASS,
    BITWARDEN
}

data class DedupMergeSourceOption(
    val key: String,
    val kind: DedupMergeSourceKind,
    val label: String,
    val passwordCount: Int,
    val secureItemCount: Int = 0,
    val passkeyCount: Int = 0
)

sealed class DedupMergeTarget {
    data object MonicaLocal : DedupMergeTarget()
    data class MdbxDatabase(
        val databaseId: Long,
        val label: String
    ) : DedupMergeTarget()
}

data class DedupMergeTargetOption(
    val target: DedupMergeTarget,
    val label: String,
    val passwordCount: Int,
    val secureItemCount: Int = 0,
    val passkeyCount: Int = 0
)

data class DedupResolvedPassword(
    val mergeKey: String,
    val entry: PasswordEntry,
    val customFields: List<CustomField>,
    val sourceEntryIds: List<Long>,
    val sourceLabels: List<String>,
    val conflictFields: Set<String>,
    val existsInTarget: Boolean = false
)

data class DedupResolvedSecureItem(
    val mergeKey: String,
    val item: SecureItem,
    val sourceItemIds: List<Long>,
    val sourceLabels: List<String>,
    val conflictFields: Set<String>,
    val existsInTarget: Boolean = false
)

data class DedupMergePlan(
    val selectedSources: List<DedupMergeSourceOption> = emptyList(),
    val target: DedupMergeTarget? = null,
    val totalSourcePasswords: Int = 0,
    val totalSourceSecureItems: Int = 0,
    val unsupportedSourcePasskeys: Int = 0,
    val uniquePasswords: Int = 0,
    val uniqueSecureItems: Int = 0,
    val duplicateGroups: Int = 0,
    val duplicateSecureItemGroups: Int = 0,
    val targetExistingDuplicates: Int = 0,
    val targetExistingSecureItems: Int = 0,
    val previewPasswords: List<DedupResolvedPassword> = emptyList(),
    val previewSecureItems: List<DedupResolvedSecureItem> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    val writablePasswords: Int
        get() = (uniquePasswords - targetExistingDuplicates).coerceAtLeast(0)

    val writableSecureItems: Int
        get() = (uniqueSecureItems - targetExistingSecureItems).coerceAtLeast(0)

    val writableItems: Int
        get() = writablePasswords + writableSecureItems

    val totalSourceItems: Int
        get() = totalSourcePasswords + totalSourceSecureItems + unsupportedSourcePasskeys
}

data class DedupMergeExecutionResult(
    val insertedPasswords: Int,
    val insertedSecureItems: Int = 0,
    val skippedExistingPasswords: Int,
    val skippedExistingSecureItems: Int = 0,
    val skippedUnsupportedPasskeys: Int = 0,
    val failedPasswords: Int,
    val failedSecureItems: Int = 0,
    val targetLabel: String
) {
    val insertedItems: Int
        get() = insertedPasswords + insertedSecureItems

    val skippedExistingItems: Int
        get() = skippedExistingPasswords + skippedExistingSecureItems
}
