package takagi.ru.monica.ui.password

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Locale
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.security.maskCardNumbersInPasswordPreview
import takagi.ru.monica.security.maskPasswordNotePreview
import takagi.ru.monica.ui.rememberTotpTickerMillis
import takagi.ru.monica.util.TotpDataResolver
import takagi.ru.monica.util.TotpGenerator

data class PasswordCardDisplayLine(
    val field: PasswordCardDisplayField,
    val icon: ImageVector,
    val text: String
)

data class PasswordAuthenticatorDisplayState(
    val code: String,
    val remainingSeconds: Int?,
    val progress: Float?,
    val periodSeconds: Int? = null,
    val sampledAtMillis: Long? = null
)

fun resolvePasswordCardDisplayLines(
    entry: PasswordEntry,
    fields: List<PasswordCardDisplayField>
): List<PasswordCardDisplayLine> {
    var formatter: SimpleDateFormat? = null
    return fields.mapNotNull { field ->
        when (field) {
            PasswordCardDisplayField.USERNAME -> entry.username
                .takeIf { it.isNotBlank() }
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Person, maskCardNumbersInPasswordPreview(it)) }

            PasswordCardDisplayField.WEBSITE -> entry.website
                .takeIf { it.isNotBlank() }
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Language, it) }

            PasswordCardDisplayField.APP_NAME -> null

            PasswordCardDisplayField.NOTE_PREVIEW -> maskPasswordNotePreview(entry.notes)
                .takeIf { it.isNotBlank() }
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Description, it) }

            PasswordCardDisplayField.UPDATED_AT -> {
                val dateFormatter = formatter ?: SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).also {
                    formatter = it
                }
                PasswordCardDisplayLine(
                    field = field,
                    icon = Icons.Default.Update,
                    text = dateFormatter.format(entry.updatedAt)
                )
            }
        }
    }
}

@Composable
fun rememberPasswordAuthenticatorDisplayState(
    authenticatorKey: String,
    fallbackIssuer: String = "",
    fallbackAccountName: String = "",
    timeOffsetSeconds: Int,
    smoothProgress: Boolean,
    decryptAuthenticatorKey: ((String) -> String)? = null
): PasswordAuthenticatorDisplayState? {
    val totpData = remember(authenticatorKey, fallbackIssuer, fallbackAccountName, decryptAuthenticatorKey) {
        val resolvedAuthenticatorKey = decryptAuthenticatorKey?.let { decrypt ->
            runCatching { decrypt(authenticatorKey) }.getOrDefault(authenticatorKey)
        } ?: authenticatorKey
        parsePasswordAuthenticatorTotpData(
            authenticatorKey = resolvedAuthenticatorKey,
            fallbackIssuer = fallbackIssuer,
            fallbackAccountName = fallbackAccountName
        )
    } ?: return null

    // The progress bar performs its own draw-layer animation. Sampling once per
    // second keeps the whole password card out of the 50 ms recomposition loop.
    val currentTimeMillis = rememberTotpTickerMillis(smooth = false)
    val currentSeconds = currentTimeMillis / 1000

    val rawCode = remember(totpData, currentSeconds, timeOffsetSeconds) {
        TotpGenerator.generateOtp(
            totpData = totpData,
            timeOffset = timeOffsetSeconds,
            currentSeconds = currentSeconds
        )
    }
    val formattedCode = remember(rawCode, totpData.otpType) {
        formatAuthenticatorCode(rawCode, totpData.otpType)
    }

    return if (totpData.otpType == OtpType.HOTP) {
        PasswordAuthenticatorDisplayState(
            code = formattedCode,
            remainingSeconds = null,
            progress = null,
            periodSeconds = null
        )
    } else {
        val remaining = remember(totpData, currentSeconds, timeOffsetSeconds) {
            TotpGenerator.getRemainingSeconds(
                period = totpData.period,
                timeOffset = timeOffsetSeconds,
                currentSeconds = currentSeconds
            )
        }
        val progress = remember(
            totpData,
            currentTimeMillis,
            currentSeconds,
            timeOffsetSeconds,
            smoothProgress
        ) {
            if (smoothProgress) {
                val periodMillis = (totpData.period * 1000L).coerceAtLeast(1000L)
                val correctedMillis = currentTimeMillis + (timeOffsetSeconds * 1000L)
                val elapsedInPeriod = ((correctedMillis % periodMillis) + periodMillis) % periodMillis
                (elapsedInPeriod.toFloat() / periodMillis.toFloat()).coerceIn(0f, 1f)
            } else {
                TotpGenerator.getProgress(
                    period = totpData.period,
                    timeOffset = timeOffsetSeconds,
                    currentSeconds = currentSeconds
                ).coerceIn(0f, 1f)
            }
        }
        PasswordAuthenticatorDisplayState(
            code = formattedCode,
            remainingSeconds = remaining,
            progress = progress,
            periodSeconds = totpData.period.coerceAtLeast(1),
            sampledAtMillis = currentTimeMillis
        )
    }
}

@Composable
internal fun PasswordAuthenticatorProgressIndicator(
    state: PasswordAuthenticatorDisplayState,
    smoothProgress: Boolean,
    modifier: Modifier = Modifier
) {
    val sampledProgress = state.progress ?: return
    val clampedProgress = if (sampledProgress.isFinite()) {
        sampledProgress.coerceIn(0f, 1f)
    } else {
        0f
    }
    val periodSeconds = state.periodSeconds?.coerceAtLeast(1) ?: 30
    val animatedProgress = remember(periodSeconds) { Animatable(clampedProgress) }

    LaunchedEffect(clampedProgress, smoothProgress, periodSeconds, state.sampledAtMillis) {
        // Samples are clock corrections, not animation targets. This also handles
        // foregrounding, clock changes and rollover without replaying stale time.
        animatedProgress.snapTo(clampedProgress)
        if (smoothProgress) {
            val durationMillis = state.sampledAtMillis?.let {
                1000L - Math.floorMod(it, 1000L)
            }?.toInt() ?: 1000
            animatedProgress.animateTo(
                targetValue = (clampedProgress + durationMillis / (periodSeconds * 1000f)).coerceAtMost(1f),
                animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing)
            )
        }
    }

    LinearProgressIndicator(
        progress = { animatedProgress.value },
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
}

private fun parsePasswordAuthenticatorTotpData(
    authenticatorKey: String,
    fallbackIssuer: String,
    fallbackAccountName: String
): TotpData? {
    return TotpDataResolver.fromAuthenticatorKey(
        rawKey = authenticatorKey,
        fallbackIssuer = fallbackIssuer,
        fallbackAccountName = fallbackAccountName
    )
}

private fun formatAuthenticatorCode(code: String, otpType: OtpType): String {
    val compact = code.replace(" ", "")
    if (compact.length <= 4) return compact

    if (otpType == OtpType.STEAM && compact.length == 5) {
        return "${compact.substring(0, 2)} ${compact.substring(2)}"
    }

    if (compact.length % 2 == 0) {
        return compact.chunked(compact.length / 2).joinToString(" ")
    }
    return compact.chunked(3).joinToString(" ")
}
