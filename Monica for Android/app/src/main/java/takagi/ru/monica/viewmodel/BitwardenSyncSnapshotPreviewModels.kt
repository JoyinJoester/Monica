package takagi.ru.monica.viewmodel

enum class BitwardenSyncSnapshotPreviewStatus {
    READY,
    VAULT_LOCKED,
    UNSUPPORTED_TYPE,
    INVALID_PAYLOAD
}

data class BitwardenSyncSnapshotFieldPreview(
    val name: String,
    val value: String,
    val hidden: Boolean = false
)

data class BitwardenSyncSnapshotFieldGroupPreview(
    val title: String,
    val fields: List<BitwardenSyncSnapshotFieldPreview> = emptyList()
)

data class BitwardenSyncSnapshotPreview(
    val status: BitwardenSyncSnapshotPreviewStatus,
    val cipherType: Int? = null,
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val totp: String = "",
    val websites: List<String> = emptyList(),
    val notes: String = "",
    val customFields: List<BitwardenSyncSnapshotFieldPreview> = emptyList(),
    val metadataFields: List<BitwardenSyncSnapshotFieldPreview> = emptyList(),
    val extraSections: List<BitwardenSyncSnapshotFieldGroupPreview> = emptyList()
) {
    val isReady: Boolean
        get() = status == BitwardenSyncSnapshotPreviewStatus.READY
}
