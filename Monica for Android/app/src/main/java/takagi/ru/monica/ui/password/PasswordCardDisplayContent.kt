package takagi.ru.monica.ui.password

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.SimpleDateFormat
import java.util.Locale
import kotlinx.coroutines.delay
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.data.PasswordCardDisplayField
import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.util.TotpGenerator
import takagi.ru.monica.util.TotpUriParser

data class PasswordCardDisplayLine(
    val field: PasswordCardDisplayField,
    val icon: ImageVector,
    val text: String
)

data class PasswordAuthenticatorDisplayState(
    val code: String,
    val remainingSeconds: Int?,
    val progress: Float?
)

fun resolvePasswordCardDisplayLines(
    entry: PasswordEntry,
    fields: List<PasswordCardDisplayField>
): List<PasswordCardDisplayLine> {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return fields.mapNotNull { field ->
        when (field) {
            PasswordCardDisplayField.USERNAME -> entry.username
                .takeIf { it.isNotBlank() }
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Person, it) }

            PasswordCardDisplayField.WEBSITE -> entry.website
                .takeIf { it.isNotBlank() }
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Language, it) }

            PasswordCardDisplayField.APP_NAME -> entry.appName
                .takeIf { it.isNotBlank() }
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Apps, it) }

            PasswordCardDisplayField.NOTE_PREVIEW -> entry.notes
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.let { PasswordCardDisplayLine(field, Icons.Default.Description, it) }

            PasswordCardDisplayField.UPDATED_AT -> PasswordCardDisplayLine(
                field = field,
                icon = Icons.Default.Update,
                text = formatter.format(entry.updatedAt)
            )
        }
    }
}

@Composable
fun rememberPasswordAuthenticatorDisplayState(
    authenticatorKey: String,
    timeOffsetSeconds: Int,
    smoothProgress: Boolean
): PasswordAuthenticatorDisplayState? {
    val totpData = remember(authenticatorKey) {
        parsePasswordAuthenticatorTotpData(authenticatorKey)
    } ?: return null

    val currentTimeMillis by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = totpData,
        key2 = timeOffsetSeconds,
        key3 = smoothProgress
    ) {
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            val waitMillis = if (smoothProgress) {
                50L
            } else {
                (1000L - (now % 1000L)).coerceAtLeast(16L)
            }
            delay(waitMillis)
        }
    }
    val currentSeconds = currentTimeMillis / 1000

    val rawCode = remember(totpData, currentSeconds, timeOffsetSeconds) {
        TotpGenerator.generateOtp(
            totpData = totpData,
            timeOffset = timeOffsetSeconds,
            currentSeconds = currentSeconds
        )
    }
    val formattedCode = remember(rawCode) { formatAuthenticatorCode(rawCode) }

    return if (totpData.otpType == OtpType.HOTP) {
        PasswordAuthenticatorDisplayState(
            code = formattedCode,
            remainingSeconds = null,
            progress = null
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
            progress = progress
        )
    }
}

private fun parsePasswordAuthenticatorTotpData(
    authenticatorKey: String
): TotpData? {
    val normalized = authenticatorKey.trim()
    if (normalized.isBlank()) return null

    return if (normalized.contains("://")) {
        TotpUriParser.parseUri(normalized)?.totpData
    } else {
        TotpData(secret = normalized)
    }
}

private fun formatAuthenticatorCode(code: String): String {
    val compact = code.replace(" ", "")
    if (compact.length <= 4) return compact
    if (compact.length % 2 == 0) {
        return compact.chunked(compact.length / 2).joinToString(" ")
    }
    return compact.chunked(3).joinToString(" ")
}
