package takagi.ru.monica.security

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build
import android.util.Log

/**
 * Forces Monica back to a locked state when the installed app version changes.
 *
 * Some OEMs may keep parts of the previous process alive across in-place updates,
 * which can incorrectly preserve in-memory unlock/session state. We explicitly
 * invalidate that state on every detected upgrade/downgrade.
 */
object AppUpdateSecurityGuard {

    private const val TAG = "AppUpdateSecurityGuard"
    private const val PREFS_NAME = "monica_app_update_guard"
    private const val KEY_LAST_VERSION_CODE = "last_version_code"
    private const val KEY_LAST_VERSION_NAME = "last_version_name"

    fun enforceLockIfAppUpdated(context: Context, reason: String) {
        val appContext = context.applicationContext
        val packageInfo = runCatching {
            getPackageInfoCompat(appContext)
        }.getOrElse { error ->
            Log.w(TAG, "Failed to inspect package info for reason=$reason", error)
            return
        }

        val currentVersionCode = packageInfo.longVersionCodeCompat()
        val currentVersionName = packageInfo.versionName.orEmpty()
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getLong(KEY_LAST_VERSION_CODE, Long.MIN_VALUE)
        val lastVersionName = prefs.getString(KEY_LAST_VERSION_NAME, null)

        val isFirstObservation = lastVersionCode == Long.MIN_VALUE && lastVersionName == null
        val versionChanged = !isFirstObservation &&
            (lastVersionCode != currentVersionCode || lastVersionName != currentVersionName)

        if (versionChanged) {
            Log.w(
                TAG,
                "App version changed ($lastVersionCode/$lastVersionName -> $currentVersionCode/$currentVersionName), forcing lock. reason=$reason"
            )
            SessionManager.markLocked()
        }

        if (isFirstObservation || versionChanged) {
            prefs.edit()
                .putLong(KEY_LAST_VERSION_CODE, currentVersionCode)
                .putString(KEY_LAST_VERSION_NAME, currentVersionName)
                .apply()
        }
    }

    @Suppress("DEPRECATION")
    private fun getPackageInfoCompat(context: Context): PackageInfo {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            packageManager.getPackageInfo(context.packageName, 0)
        }
    }

    private fun PackageInfo.longVersionCodeCompat(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            longVersionCode
        } else {
            @Suppress("DEPRECATION")
            versionCode.toLong()
        }
    }
}
