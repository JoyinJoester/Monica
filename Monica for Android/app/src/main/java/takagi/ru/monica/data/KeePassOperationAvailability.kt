package takagi.ru.monica.data

enum class KeePassOperationBlockReason {
    MISSING_DATABASE,
    NEEDS_REFRESH,
    SYNCING,
    CONFLICT,
    FAILED
}

data class KeePassOperationAvailability(
    val canOperate: Boolean,
    val reason: KeePassOperationBlockReason? = null
)

fun LocalKeePassDatabase.writeOperationAvailability(): KeePassOperationAvailability {
    if (!isRemoteSource()) {
        return KeePassOperationAvailability(canOperate = true)
    }

    val hasLocalCopy = !workingCopyPath.isNullOrBlank() || !cacheCopyPath.isNullOrBlank()
    if (!isOfflineAvailable && !hasLocalCopy) {
        return KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.NEEDS_REFRESH
        )
    }

    return when (lastSyncStatus) {
        KeePassSyncStatus.IN_SYNC,
        KeePassSyncStatus.PENDING_UPLOAD -> KeePassOperationAvailability(canOperate = true)
        KeePassSyncStatus.SYNCING -> KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.SYNCING
        )
        KeePassSyncStatus.CONFLICT -> KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.CONFLICT
        )
        KeePassSyncStatus.FAILED -> KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.FAILED
        )
        KeePassSyncStatus.LOCAL_ONLY,
        KeePassSyncStatus.REMOTE_CHANGED -> KeePassOperationAvailability(
            canOperate = false,
            reason = KeePassOperationBlockReason.NEEDS_REFRESH
        )
    }
}

