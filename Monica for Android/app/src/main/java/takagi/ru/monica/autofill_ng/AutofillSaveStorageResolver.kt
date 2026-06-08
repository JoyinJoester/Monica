package takagi.ru.monica.autofill_ng

import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.PasswordEntry
import java.util.Locale

private const val SAVED_FILTER_MDBX_DATABASE = "mdbx_database"
private const val SAVED_FILTER_MDBX_FOLDER = "mdbx_folder"

internal data class AutofillSaveInitialTarget(
    val mdbxDatabaseId: Long? = null,
    val mdbxFolderId: String? = null,
    val mdbxDatabasesFallback: List<LocalMdbxDatabase> = emptyList()
) {
    val isMdbx: Boolean get() = mdbxDatabaseId != null

    fun diagnosticLabel(): String =
        if (mdbxDatabaseId != null) {
            "mdbx:$mdbxDatabaseId:${mdbxFolderId.orEmpty()}"
        } else {
            "local"
        }
}

internal fun resolveAutofillSaveInitialTarget(
    settings: AppSettings,
    mdbxDatabases: List<LocalMdbxDatabase>
): AutofillSaveInitialTarget {
    val type = settings.lastPasswordCategoryFilterType.trim().lowercase(Locale.ROOT)
    val databaseId = when (type) {
        SAVED_FILTER_MDBX_DATABASE -> settings.lastPasswordCategoryFilterPrimaryId
        SAVED_FILTER_MDBX_FOLDER -> {
            settings.lastPasswordCategoryFilterPrimaryId
                ?: settings.lastPasswordCategoryFilterSecondaryId
        }
        else -> null
    }

    if (databaseId == null || mdbxDatabases.none { it.id == databaseId }) {
        return AutofillSaveInitialTarget(mdbxDatabasesFallback = mdbxDatabases)
    }

    val folderId = if (type == SAVED_FILTER_MDBX_FOLDER) {
        settings.lastPasswordCategoryFilterText?.takeIf { it.isNotBlank() }
    } else {
        null
    }

    return AutofillSaveInitialTarget(
        mdbxDatabaseId = databaseId,
        mdbxFolderId = folderId,
        mdbxDatabasesFallback = mdbxDatabases
    )
}

internal fun PasswordEntry.withAutofillSaveInitialTarget(
    target: AutofillSaveInitialTarget
): PasswordEntry {
    val databaseId = target.mdbxDatabaseId ?: return this
    return copy(
        categoryId = null,
        keepassDatabaseId = null,
        keepassGroupPath = null,
        keepassEntryUuid = null,
        keepassGroupUuid = null,
        mdbxDatabaseId = databaseId,
        mdbxFolderId = target.mdbxFolderId,
        bitwardenVaultId = null,
        bitwardenCipherId = null,
        bitwardenFolderId = null,
        bitwardenRevisionDate = null,
        bitwardenLocalModified = false
    )
}
