package takagi.ru.monica.security

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 34], application = Application::class)
class SessionManagerLockGenerationTest {
    @Test fun repeatedLocksRevokeIndependentAuthorizationsEvenWhenBooleanStateDoesNotChange() {
        SessionManager.markLocked()
        val before = SessionManager.lockGeneration.value
        assertFalse(SessionManager.isUnlocked.value)
        SessionManager.markLocked()
        assertEquals(before + 1, SessionManager.lockGeneration.value)
        assertFalse(SessionManager.isUnlocked.value)
        SessionManager.markUnlocked()
        assertEquals(before + 1, SessionManager.lockGeneration.value)
        SessionManager.markLocked()
        assertEquals(before + 2, SessionManager.lockGeneration.value)
    }
}
