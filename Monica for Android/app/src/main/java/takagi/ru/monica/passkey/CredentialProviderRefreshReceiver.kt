package takagi.ru.monica.passkey

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Soft-refreshes CredentialProviderService component state right after app update.
 *
 * We avoid DISABLED state to keep provider continuously available, but still
 * force a state transition (DEFAULT <-> ENABLED) so OEM caches can re-discover
 * this provider without requiring manual settings toggles.
 */
class CredentialProviderRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val action = intent?.action ?: return
        if (!shouldHandleAction(context, intent, action)) return

        runCatching {
            softRefreshCredentialProviderComponentState(context)
            Log.d(TAG, "CredentialProviderService state soft-refreshed on action=$action")
        }.onFailure { error ->
            Log.w(TAG, "Failed to soft-refresh CredentialProviderService on action=$action", error)
        }
    }

    private fun shouldHandleAction(context: Context, intent: Intent, action: String): Boolean {
        return when (action) {
            Intent.ACTION_MY_PACKAGE_REPLACED -> true
            Intent.ACTION_PACKAGE_REPLACED -> {
                // PACKAGE_REPLACED carries package:<name> in data.
                val replacedPackage = intent.data?.schemeSpecificPart
                replacedPackage == context.packageName
            }
            else -> false
        }
    }

    private fun softRefreshCredentialProviderComponentState(context: Context) {
        val componentName = ComponentName(context, MonicaCredentialProviderService::class.java)
        val pm = context.packageManager
        val currentState = pm.getComponentEnabledSetting(componentName)

        when (currentState) {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> {
                // Respect explicit disable from user/system.
                return
            }

            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> {
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }

            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> {
                pm.setComponentEnabledSetting(
                    componentName,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }
        }
    }

    companion object {
        private const val TAG = "CredProviderRefreshRx"
    }
}
