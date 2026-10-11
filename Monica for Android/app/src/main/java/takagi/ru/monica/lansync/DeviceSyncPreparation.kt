package takagi.ru.monica.lansync

import kotlinx.coroutines.CancellationException

/** Report a fixed operation label without retaining exception messages or credential content. */
internal inline fun <T> preparingDeviceSync(operation: String, block: () -> T): T = try {
    block()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    if (failure is LanSyncException && failure.operation.isNotEmpty()) throw failure
    throw LanSyncException((failure as? LanSyncException)?.code ?: "device_sync_failed", operation,
        (failure as? LanSyncException)?.recordName.orEmpty(),
        (failure as? LanSyncException)?.failureKind?.ifEmpty { null } ?: failure.javaClass.simpleName)
}
