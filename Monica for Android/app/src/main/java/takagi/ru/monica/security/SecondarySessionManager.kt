package takagi.ru.monica.security

import android.app.KeyguardManager
import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory unlock window for secondary secure entry points such as Autofill and IME.
 *
 * This state is intentionally isolated from the main app session so that secondary
 * verification can unlock the current secure request without implicitly unlocking
 * the main application.
 */
object SecondarySessionManager {

    private const val TAG = "SecondarySessionManager"

    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    private var unlockTimestamp: Long = 0L
    private var autoLockMinutes: Int = 5

    fun markUnlocked() {
        _isUnlocked.value = true
        unlockTimestamp = SystemClock.elapsedRealtime()
        android.util.Log.d(TAG, "Secondary session unlocked at $unlockTimestamp")
    }

    fun markLocked(clearRuntimeUnlockCache: Boolean = true) {
        _isUnlocked.value = false
        unlockTimestamp = 0L
        if (clearRuntimeUnlockCache && !SessionManager.isUnlocked.value) {
            SecurityManager.clearRuntimeUnlockCache()
        }
        android.util.Log.d(TAG, "Secondary session locked")
    }

    fun updateAutoLockTimeout(minutes: Int) {
        autoLockMinutes = minutes
        android.util.Log.d(TAG, "Secondary auto-lock timeout updated to $minutes minutes")
    }

    fun canSkipVerification(context: Context): Boolean {
        if (!_isUnlocked.value) {
            android.util.Log.d(TAG, "canSkipVerification: false (not unlocked)")
            return false
        }

        val elapsedMinutes = (SystemClock.elapsedRealtime() - unlockTimestamp) / 60000
        if (autoLockMinutes != -1 && elapsedMinutes >= autoLockMinutes) {
            android.util.Log.d(TAG, "canSkipVerification: false (session expired, elapsed=$elapsedMinutes min)")
            markLocked()
            return false
        }

        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        if (keyguardManager?.isKeyguardLocked == true) {
            android.util.Log.d(TAG, "canSkipVerification: false (device locked)")
            return false
        }

        android.util.Log.d(TAG, "canSkipVerification: true (unlocked, within timeout, screen unlocked)")
        return true
    }
}
