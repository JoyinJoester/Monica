package takagi.ru.monica.autofill_ng.protection

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.UserManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import takagi.ru.monica.service.MonicaAccessibilityService

import takagi.ru.monica.autofill_ng.privilege.PrivilegeAccess
import takagi.ru.monica.autofill_ng.privilege.PrivilegeBackend

object AutofillProtection {
    private const val PREFS = "autofill_protection"
    private const val KEY_ENABLED = "background_enabled"
    private const val KEY_ENHANCED = "shizuku_recovery_enabled"
    private const val KEY_LAST_RECOVERY = "last_recovery_attempt"
    internal val runtime = MutableStateFlow(ProtectionRuntime.STOPPED)
    private val refreshVersion = MutableStateFlow(0L)

    private fun preferences(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean = preferences(context).getBoolean(KEY_ENABLED, false)

    fun isEnhancedRecoveryEnabled(context: Context): Boolean =
        preferences(context).getBoolean(KEY_ENHANCED, false)

    fun lastRecoveryAttempt(context: Context): Long = preferences(context).getLong(KEY_LAST_RECOVERY, 0L)

    internal fun recordRecoveryAttempt(context: Context, now: Long) {
        // Persist before the privileged operation so a process restart cannot cause a repair loop.
        check(preferences(context).edit().putLong(KEY_LAST_RECOVERY, now).commit())
    }

    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        preferences(context).edit().apply {
            putBoolean(KEY_ENABLED, enabled)
            if (!enabled) putBoolean(KEY_ENHANCED, false)
        }.apply()
        if (enabled) return restoreIfEnabled(context)
        context.stopService(Intent(context, AutofillProtectionService::class.java))
        runtime.value = ProtectionRuntime.STOPPED
        refresh()
        return true
    }

    fun setEnhancedRecoveryEnabled(context: Context, enabled: Boolean) {
        val allowed = enabled && isEnabled(context) &&
            (if (PrivilegeAccess.backend(context) == PrivilegeBackend.ROOT) PrivilegeAccess.rootAllowed(context)
             else ShizukuAccessibilityRecovery.access() == ShizukuAccess.ADB_AUTHORIZED)
        preferences(context).edit().putBoolean(KEY_ENHANCED, allowed).apply()
        refresh()
    }

    /** Call only from a visible activity, a system-bound accessibility service, or boot/update. */
    fun restoreIfEnabled(context: Context): Boolean {
        if (!isEnabled(context)) return false
        if (context.getSystemService(UserManager::class.java)?.isUserUnlocked != true) return false
        return try {
            if (runtime.value != ProtectionRuntime.RUNNING) runtime.value = ProtectionRuntime.STARTING
            ContextCompat.startForegroundService(
                context.applicationContext,
                Intent(context.applicationContext, AutofillProtectionService::class.java),
            )
            true
        } catch (error: RuntimeException) {
            runtime.value = ProtectionRuntime.STOPPED
            Log.w("AutofillProtection", "Background protection start was rejected", error)
            false
        }
    }

    fun refresh() {
        refreshVersion.update { it + 1 }
    }

    fun snapshot(context: Context, shizuku: ShizukuAccess = ShizukuAccessibilityRecovery.access()): AutofillProtectionState {
        val appContext = context.applicationContext
        return AutofillProtectionState(
            backgroundEnabled = isEnabled(appContext),
            enhancedRecoveryEnabled = isEnhancedRecoveryEnabled(appContext),
            runtime = runtime.value,
            accessibility = AutofillProtectionState.accessibilityConnection(
                enabled = hasAccessibilityConsent(appContext),
                connected = MonicaAccessibilityService.connectionState.value,
            ),
            batteryExempt = appContext.getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(appContext.packageName) == true,
            backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                appContext.getSystemService(ActivityManager::class.java)?.isBackgroundRestricted == true,
            notificationsAllowed = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
            shizuku = shizuku,
            useRoot = PrivilegeAccess.backend(context) == PrivilegeBackend.ROOT,
            rootAuthorized = PrivilegeAccess.rootAllowed(context),
        )
    }

    private fun hasAccessibilityConsent(context: Context): Boolean = runCatching {
        // AccessibilityManager's enabled-services query only returns bound services. Read the
        // user's setting separately so a disconnected service is not mistaken for revoked consent.
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            .orEmpty().split(':').any(::isMonicaAccessibilityComponent)
    }.getOrDefault(false)

    fun observe(context: Context): Flow<AutofillProtectionState> {
        val appContext = context.applicationContext
        return combine(
            systemChanges(appContext),
            runtime,
            MonicaAccessibilityService.connectionState,
            ShizukuAccessibilityRecovery.observeAccess(),
            PrivilegeAccess.status,
        ) { _, _, _, _, _ -> snapshot(appContext) }.distinctUntilChanged()
    }

    private fun systemChanges(context: Context): Flow<Unit> = callbackFlow {
        val prefs = preferences(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), false, observer,
        )
        val refreshJob = launch { refreshVersion.collect { trySend(Unit) } }
        trySend(Unit)
        awaitClose {
            refreshJob.cancel()
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }
}
