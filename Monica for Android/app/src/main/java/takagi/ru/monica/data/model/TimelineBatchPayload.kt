package takagi.ru.monica.data.model

import kotlinx.serialization.Serializable

const val TIMELINE_FIELD_BATCH_MOVE_PAYLOAD = "__BATCH_MOVE_PAYLOAD__"
const val TIMELINE_FIELD_BATCH_COPY_PAYLOAD = "__BATCH_COPY_PAYLOAD__"
const val TIMELINE_FIELD_MAINTENANCE_SNAPSHOT_PAYLOAD = "__MAINTENANCE_SNAPSHOT_PAYLOAD__"

@Serializable
data class TimelinePasswordLocationState(
    val id: Long,
    val categoryId: Long? = null,
    val keepassDatabaseId: Long? = null,
    val keepassGroupPath: String? = null,
    val bitwardenVaultId: Long? = null,
    val bitwardenFolderId: String? = null,
    val bitwardenLocalModified: Boolean = false,
    val isArchived: Boolean = false,
    val archivedAtMillis: Long? = null
)

@Serializable
data class TimelineBatchMovePayload(
    val oldStates: List<TimelinePasswordLocationState>,
    val newStates: List<TimelinePasswordLocationState>
)

@Serializable
data class TimelineBatchCopyPayload(
    val copiedEntryIds: List<Long>
)

@Serializable
data class TimelineMaintenanceSnapshotPayload(
    val schemaVersion: Int = 1,
    val passwordIds: List<Long> = emptyList(),
    val secureItemIds: List<Long> = emptyList(),
    val passwordRows: List<String> = emptyList(),
    val secureItemRows: List<String> = emptyList(),
    val compression: String? = null,
    val passwordRowsCompressedChunks: List<String> = emptyList(),
    val secureItemRowsCompressedChunks: List<String> = emptyList()
)
