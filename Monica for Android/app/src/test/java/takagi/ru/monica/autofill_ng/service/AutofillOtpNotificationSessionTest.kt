package takagi.ru.monica.autofill_ng.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import takagi.ru.monica.data.model.TotpData

class AutofillOtpNotificationSessionTest {

    @Test
    fun snapshotRecomputesCodeAndOtpRemainingFromWallClockEveryTick() {
        val seenWallSeconds = mutableListOf<Long>()
        val session = AutofillOtpNotificationSession(
            data = TotpData(secret = "JBSWY3DPEHPK3PXP", period = 30),
            startedElapsedMs = 1_000L,
            durationSeconds = 30,
            generateCode = { _, currentSeconds ->
                seenWallSeconds += currentSeconds
                "code-$currentSeconds"
            },
            remainingSecondsForPeriod = { period, currentSeconds ->
                period - (currentSeconds % period).toInt()
            }
        )

        val first = session.snapshot(nowElapsedMs = 1_000L, nowWallSeconds = 10L)
        val second = session.snapshot(nowElapsedMs = 2_000L, nowWallSeconds = 11L)

        assertEquals("code-10", first.code)
        assertEquals(20, first.remainingSeconds)
        assertEquals("code-11", second.code)
        assertEquals(19, second.remainingSeconds)
        assertEquals(listOf(10L, 11L), seenWallSeconds)
    }

    @Test
    fun snapshotCapsRemainingByNotificationDurationAndExpiresAtDeadline() {
        val session = AutofillOtpNotificationSession(
            data = TotpData(secret = "JBSWY3DPEHPK3PXP", period = 30),
            startedElapsedMs = 10_000L,
            durationSeconds = 3,
            generateCode = { _, _ -> "123456" },
            remainingSecondsForPeriod = { _, _ -> 30 }
        )

        val active = session.snapshot(nowElapsedMs = 11_000L, nowWallSeconds = 100L)
        val expired = session.snapshot(nowElapsedMs = 13_000L, nowWallSeconds = 102L)

        assertEquals(2, active.remainingSeconds)
        assertFalse(active.expired)
        assertEquals(0, expired.remainingSeconds)
        assertTrue(expired.expired)
    }

    @Test
    fun invalidPeriodFallsBackToOneSecondPeriod() {
        val session = AutofillOtpNotificationSession(
            data = TotpData(secret = "JBSWY3DPEHPK3PXP", period = 0),
            startedElapsedMs = 0L,
            durationSeconds = 5,
            generateCode = { _, _ -> "123456" },
            remainingSecondsForPeriod = { period, _ -> period }
        )

        val snapshot = session.snapshot(nowElapsedMs = 0L, nowWallSeconds = 0L)

        assertEquals(1, snapshot.remainingSeconds)
        assertFalse(snapshot.expired)
    }
}
