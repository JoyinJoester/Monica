package takagi.ru.monica.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import takagi.ru.monica.data.AppLauncherIcon
import takagi.ru.monica.utils.AppLauncherIconManager
import takagi.ru.monica.utils.SettingsManager

class LauncherEntryRepairReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) {
            return
        }

        runCatching {
            val selectedIcon = runBlocking {
                SettingsManager(context).settingsFlow.first().appLauncherIcon
            }
            AppLauncherIconManager.repairLaunchEntryPointsAfterUpgrade(
                context,
                selectedIcon
            )
        }.onFailure { error ->
            Log.w(TAG, "Failed to repair launcher entry points after package replace", error)
            runCatching {
                AppLauncherIconManager.repairLaunchEntryPointsAfterUpgrade(
                    context,
                    AppLauncherIcon.LOCK_CLASSIC
                )
            }
        }
    }

    companion object {
        private const val TAG = "LauncherEntryRepair"
    }
}
