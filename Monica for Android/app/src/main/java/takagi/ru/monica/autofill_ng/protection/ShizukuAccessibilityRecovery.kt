package takagi.ru.monica.autofill_ng.protection

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import takagi.ru.monica.service.MonicaAccessibilityService

import takagi.ru.monica.autofill_ng.privilege.PrivilegeAccess

object ShizukuAccessibilityRecovery {
    const val PERMISSION_REQUEST = 9531
    private val recoveryLock = Mutex()

    fun access(): ShizukuAccess = runCatching {
        when {
            !Shizuku.pingBinder() -> ShizukuAccess.UNAVAILABLE
            Shizuku.isPreV11() || Shizuku.getVersion() < 13 -> ShizukuAccess.UNSUPPORTED
            Shizuku.getUid() == 0 -> ShizukuAccess.ROOT_MODE
            Shizuku.getUid() != 2000 -> ShizukuAccess.UNSUPPORTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuAccess.NEEDS_PERMISSION
            else -> ShizukuAccess.ADB_AUTHORIZED
        }
    }.getOrDefault(ShizukuAccess.UNAVAILABLE)

    fun requestPermission(): Boolean = runCatching {
        if (access() != ShizukuAccess.NEEDS_PERMISSION || Shizuku.shouldShowRequestPermissionRationale()) {
            return@runCatching false
        }
        Shizuku.requestPermission(PERMISSION_REQUEST)
        true
    }.getOrDefault(false)

    fun observeAccess(): Flow<ShizukuAccess> = callbackFlow {
        val received = Shizuku.OnBinderReceivedListener { trySend(access()) }
        val dead = Shizuku.OnBinderDeadListener { trySend(access()) }
        val permission = Shizuku.OnRequestPermissionResultListener { _, _ -> trySend(access()) }
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permission)
        trySend(access())
        awaitClose {
            Shizuku.removeBinderReceivedListener(received)
            Shizuku.removeBinderDeadListener(dead)
            Shizuku.removeRequestPermissionResultListener(permission)
        }
    }.distinctUntilChanged()

    suspend fun recover(context: Context): AccessibilityRecoveryResult = recoveryLock.withLock {
        val appContext = context.applicationContext
        val state = AutofillProtection.snapshot(appContext)
        if (state.accessibility == AccessibilityConnection.CONNECTED) return@withLock AccessibilityRecoveryResult.CONNECTED
        if (state.accessibility == AccessibilityConnection.DISABLED) return@withLock AccessibilityRecoveryResult.NOT_ENABLED
        if (!state.canRecoverAccessibility()) return@withLock AccessibilityRecoveryResult.NOT_AUTHORIZED
        val now = System.currentTimeMillis()
        val lastAttempt = AutofillProtection.lastRecoveryAttempt(appContext)
        if (lastAttempt != 0L && now - lastAttempt < AutofillProtectionState.RECOVERY_COOLDOWN_MS) {
            return@withLock AccessibilityRecoveryResult.COOLDOWN
        }
        try {
            withContext(Dispatchers.IO) { AutofillProtection.recordRecoveryAttempt(appContext, now) }
        } catch (_: Exception) {
            return@withLock AccessibilityRecoveryResult.FAILED
        }

        // A coroutine cancellation must not kill the helper between disconnect and restoration.
        val result = withContext(NonCancellable) {
            if (state.useRoot) restartThroughRoot(appContext) else restartThroughShizuku(appContext)
        }
        AutofillProtection.refresh()
        if (result != AccessibilityRecoveryResult.RESTART_REQUESTED) return@withLock result
        val connected = withTimeoutOrNull(6_000) {
            MonicaAccessibilityService.connectionState.first { it }
        } ?: false
        if (connected) AccessibilityRecoveryResult.CONNECTED else AccessibilityRecoveryResult.FAILED
    }

    private suspend fun restartThroughRoot(context: Context): AccessibilityRecoveryResult = withContext(Dispatchers.IO) {
        PrivilegeAccess.transaction.withLock {
            if (!AutofillProtection.snapshot(context).let { it.useRoot && it.canRecoverAccessibility() }) {
                return@withLock AccessibilityRecoveryResult.NOT_AUTHORIZED
            }
            PrivilegeAccess.restartAccessibility(context)
        }
    }

    private suspend fun restartThroughShizuku(context: Context): AccessibilityRecoveryResult {
        val args = Shizuku.UserServiceArgs(ComponentName(context, ShizukuAccessibilityUserService::class.java))
            .tag("monica-accessibility-recovery-${android.os.Process.myUid()}")
            .version(1)
            .processNameSuffix("accessibility_recovery")
            .debuggable(false)
            .daemon(true)
        val ready = CompletableDeferred<IMonicaAccessibilityRecovery>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder?.pingBinder() == true) {
                    ready.complete(IMonicaAccessibilityRecovery.Stub.asInterface(binder))
                } else {
                    ready.completeExceptionally(IllegalStateException("Recovery helper unavailable"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                ready.completeExceptionally(IllegalStateException("Recovery helper disconnected"))
            }
        }
        return try {
            withContext(Dispatchers.Main.immediate) { Shizuku.bindUserService(args, connection) }
            val helper = withTimeout(8_000) { ready.await() }
            // Re-check consent after binding, including revocation or a switch turned off meanwhile.
            if (!AutofillProtection.snapshot(context).let { !it.useRoot && it.canRecoverAccessibility() }) {
                AccessibilityRecoveryResult.NOT_AUTHORIZED
            } else {
                withContext(Dispatchers.IO) {
                    AccessibilityRecoveryResult.fromCode(helper.restartMonicaAccessibility())
                }
            }
        } catch (_: Exception) {
            AccessibilityRecoveryResult.FAILED
        } finally {
            withContext(Dispatchers.Main.immediate) {
                runCatching { Shizuku.unbindUserService(args, connection, true) }
            }
        }
    }
}
