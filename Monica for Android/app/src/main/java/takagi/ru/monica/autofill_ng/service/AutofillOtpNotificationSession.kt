package takagi.ru.monica.autofill_ng.service

import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpGenerator

/**
 * Pure state calculator for the autofill OTP notification.
 *
 * The service owns Android lifecycle and notification publishing; this class owns the time math.
 * Keeping it separate makes the countdown behavior testable and prevents drift from cached state.
 */
internal class AutofillOtpNotificationSession(
    private val data: TotpData,
    private val startedElapsedMs: Long,
    durationSeconds: Int,
    private val generateCode: (TotpData, Long) -> String = { totpData, currentSeconds ->
        TotpGenerator.generateOtp(totpData, currentSeconds = currentSeconds)
    },
    private val remainingSecondsForPeriod: (Int, Long) -> Int = { period, currentSeconds ->
        TotpGenerator.getRemainingSeconds(period = period, currentSeconds = currentSeconds)
    }
) {
    private val periodSeconds = data.period.coerceAtLeast(1)
    private val deadlineElapsedMs = startedElapsedMs + durationSeconds.coerceAtLeast(1) * 1000L

    fun snapshot(nowElapsedMs: Long, nowWallSeconds: Long): Snapshot {
        val dismissRemaining = ((deadlineElapsedMs - nowElapsedMs) / 1000L)
            .toInt()
            .coerceAtLeast(0)
        val expired = dismissRemaining <= 0
        val otpRemaining = runCatching {
            remainingSecondsForPeriod(periodSeconds, nowWallSeconds)
        }.getOrDefault(periodSeconds)
        val code = runCatching {
            generateCode(data, nowWallSeconds)
        }.getOrDefault("")

        return Snapshot(
            code = code,
            remainingSeconds = minOf(otpRemaining, dismissRemaining),
            expired = expired
        )
    }

    data class Snapshot(
        val code: String,
        val remainingSeconds: Int,
        val expired: Boolean
    )
}
