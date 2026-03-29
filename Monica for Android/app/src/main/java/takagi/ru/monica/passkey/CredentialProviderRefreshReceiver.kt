package takagi.ru.monica.passkey

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * Records CredentialProviderService state right after app update.
 *
 * Note:
 * Avoid force-toggling component state on upgrade/startup. Some OEM builds may
 * invalidate provider selection when component state is flipped programmatically,
 * causing users to re-enter system passkey settings manually.
 */
class CredentialProviderRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        val action = intent?.action ?: return
        if (!shouldHandleAction(context, intent, action)) return

        runCatching {
            logCredentialProviderComponentState(context, action)
        }.onFailure { error ->
            Log.w(TAG, "Failed to inspect CredentialProviderService on action=$action", error)
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

    private fun logCredentialProviderComponentState(context: Context, action: String) {
        val componentName = ComponentName(context, MonicaCredentialProviderService::class.java)
        val pm = context.packageManager
        val currentState = pm.getComponentEnabledSetting(componentName)
        val stateLabel = when (currentState) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> "DEFAULT"
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> "ENABLED"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> "DISABLED"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER -> "DISABLED_USER"
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED -> "DISABLED_UNTIL_USED"
            else -> currentState.toString()
        }
        Log.d(TAG, "CredentialProviderService state on action=$action is $stateLabel")
    }

    companion object {
        private const val TAG = "CredProviderRefreshRx"
    }
}
