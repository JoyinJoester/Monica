package takagi.ru.monica.passkey

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Refreshes CredentialProviderService registration right after app update.
 *
 * Some OEM builds keep stale provider cache until the provider component
 * changes state. This receiver forces a disable/enable cycle after
 * ACTION_MY_PACKAGE_REPLACED.
 */
class CredentialProviderRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        runCatching {
            val componentName = ComponentName(context, MonicaCredentialProviderService::class.java)
            val pm = context.packageManager
            val currentState = pm.getComponentEnabledSetting(componentName)
            val enabled = currentState == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT ||
                currentState == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            if (!enabled) return@runCatching

            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.d(TAG, "CredentialProviderService refreshed on package replaced")
        }.onFailure { error ->
            Log.w(TAG, "Failed to refresh CredentialProviderService on package replaced", error)
        }
    }

    companion object {
        private const val TAG = "CredProviderRefreshRx"
    }
}

