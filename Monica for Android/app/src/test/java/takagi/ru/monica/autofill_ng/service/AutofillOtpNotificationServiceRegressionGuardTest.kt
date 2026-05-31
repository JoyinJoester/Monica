package takagi.ru.monica.autofill_ng.service

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillOtpNotificationServiceRegressionGuardTest {

    @Test
    fun notificationTicksUseSessionCalculatorAndStaleJobsCannotStopFreshSessions() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/autofill_ng/service/AutofillOtpNotificationService.kt"
        ).readText()

        assertTrue(
            "The notification service should delegate OTP/time math to a testable session calculator instead of caching a countdown value.",
            source.contains("AutofillOtpNotificationSession(") &&
                source.contains("session.snapshot(")
        )
        assertTrue(
            "Each started notification run needs an id so an older update coroutine cannot cancel a newer OTP notification.",
            source.contains("sessionCounter.incrementAndGet()") &&
                source.contains("activeSessionId = sessionId") &&
                source.contains("activeSessionId != sessionId") &&
                source.contains("stopSelfCompletely(sessionId)")
        )
        assertTrue(
            "Stale stop requests must be ignored; otherwise a previous countdown can leave the visible notification frozen.",
            source.contains("if (sessionId != null && sessionId != activeSessionId)") &&
                source.contains("return")
        )
        assertFalse(
            "The service must not hold mutable TotpData/deadline fields shared by old and new coroutines.",
            source.contains("private var totpData") ||
                source.contains("private var deadlineElapsedMs")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}
