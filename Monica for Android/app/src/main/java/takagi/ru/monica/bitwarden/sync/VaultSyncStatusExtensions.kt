package takagi.ru.monica.bitwarden.sync

fun VaultSyncStatus?.isUserVisibleSyncInProgress(): Boolean {
    val current = this ?: return false
    return current.isRunning || current.queuedReason != null
}
