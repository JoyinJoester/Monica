package takagi.ru.monica.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class AndroidSyncNetworkGate(context: Context) : SyncNetworkGate {
    private val appContext = context.applicationContext

    override fun evaluate(policy: SyncNetworkPolicy): SyncError? {
        if (policy == SyncNetworkPolicy.ALLOWED || policy == SyncNetworkPolicy.FORBIDDEN) {
            return null
        }

        val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
            ?: return networkUnavailable()
        val activeNetwork = connectivityManager.activeNetwork ?: return networkUnavailable()
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            ?: return networkUnavailable()
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            return networkUnavailable()
        }
        if (policy == SyncNetworkPolicy.WIFI_ONLY &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        ) {
            return SyncError(
                kind = SyncErrorKind.WIFI_REQUIRED,
                redactedMessage = "wifi_required",
                retryable = true
            )
        }
        return null
    }

    private fun networkUnavailable(): SyncError {
        return SyncError(
            kind = SyncErrorKind.NETWORK_UNAVAILABLE,
            redactedMessage = "network_unavailable",
            retryable = true
        )
    }
}
