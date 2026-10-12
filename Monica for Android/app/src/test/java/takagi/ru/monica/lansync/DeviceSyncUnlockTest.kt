package takagi.ru.monica.lansync

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import takagi.ru.monica.security.SessionManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DeviceSyncUnlockTest {
    @Test fun unlock_observers_receive_a_session_with_valid_timestamps() = runBlocking {
        SessionManager.markLocked()
        SessionManager.updateAutoLockTimeout(5)
        try {
            // An undispatched collector reproduces the sync worker waking on the unlock emission.
            val expiredWhenNotified = async(Dispatchers.Unconfined) {
                SessionManager.isUnlocked.first { it }
                SessionManager.isSessionExpired()
            }
            SessionManager.markUnlocked()
            assertFalse("Unlock must not wake sync with zero/expired timestamps", expiredWhenNotified.await())
        } finally { SessionManager.markLocked() }
    }
}
