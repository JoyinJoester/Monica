package takagi.ru.monica.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardUtilsTest {

    @Test
    fun shouldClearDelayedClipboardWhenCopiedCredentialIsStillCurrent() {
        val snapshot = ClipboardSnapshot(
            text = "secret-password",
            label = "Password",
            canVerify = true
        )

        assertTrue(
            ClipboardUtils.shouldClearDelayedClipboard(
                snapshot = snapshot,
                expectedLabel = "Password",
                expectedText = "secret-password"
            )
        )
    }

    @Test
    fun shouldNotClearDelayedClipboardWhenUserCopiedSomethingElse() {
        val snapshot = ClipboardSnapshot(
            text = "new clipboard text",
            label = "Browser",
            canVerify = true
        )

        assertFalse(
            ClipboardUtils.shouldClearDelayedClipboard(
                snapshot = snapshot,
                expectedLabel = "Password",
                expectedText = "secret-password"
            )
        )
    }

    @Test
    fun shouldClearDelayedClipboardWhenAndroidCannotVerifyBackgroundClipboard() {
        val snapshot = ClipboardSnapshot(
            text = null,
            label = null,
            canVerify = false
        )

        assertTrue(
            ClipboardUtils.shouldClearDelayedClipboard(
                snapshot = snapshot,
                expectedLabel = "Password",
                expectedText = "secret-password"
            )
        )
    }
}
