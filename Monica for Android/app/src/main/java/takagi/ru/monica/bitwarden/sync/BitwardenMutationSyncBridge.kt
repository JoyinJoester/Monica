package takagi.ru.monica.bitwarden.sync

import android.content.Context
import android.util.Log

/**
 * Bridges repository-level Bitwarden mutations to the visible sync controller.
 *
 * When the Bitwarden screen is alive, the ViewModel handles the request so UI
 * status stays accurate. If no ViewModel is registered, WorkManager performs a
 * vault-level background sync as a fallback.
 */
object BitwardenMutationSyncBridge {
    private const val TAG = "BitwardenMutationSyncBridge"

    private data class HandlerRegistration(
        val owner: Any,
        val handler: (Long) -> Boolean
    )

    @Volatile
    private var registration: HandlerRegistration? = null

    fun register(owner: Any, handler: (Long) -> Boolean) {
        registration = HandlerRegistration(owner, handler)
    }

    fun unregister(owner: Any) {
        if (registration?.owner === owner) {
            registration = null
        }
    }

    fun requestLocalMutationSync(
        context: Context,
        vaultId: Long,
        requiresWifi: Boolean = false,
        autoSyncEnabled: Boolean = true
    ) {
        val handledInProcess = runCatching {
            registration?.handler?.invoke(vaultId) == true
        }.onFailure { error ->
            Log.w(TAG, "In-process mutation sync handler failed", error)
        }.getOrDefault(false)

        if (handledInProcess) return
        if (!autoSyncEnabled) return

        BitwardenSyncWorker.triggerImmediateSync(
            context = context.applicationContext,
            syncType = BitwardenSyncWorker.SYNC_TYPE_FULL,
            vaultId = vaultId,
            requiresWifi = requiresWifi
        )
    }
}
