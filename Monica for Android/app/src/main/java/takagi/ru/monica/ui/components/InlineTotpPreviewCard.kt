package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.LoadingIndicator as MaterialExpressiveLoadingIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.data.model.OtpType
import takagi.ru.monica.data.model.TotpData
import takagi.ru.monica.util.TotpGenerator

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InlineTotpPreviewCard(
    totpData: TotpData,
    currentSeconds: Long,
    progressTimeMillis: Long,
    timeOffset: Int,
    smoothProgress: Boolean,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
    showProgress: Boolean = true
) {
    val currentCode = remember(currentSeconds, totpData, timeOffset) {
        when (totpData.otpType) {
            OtpType.HOTP -> TotpGenerator.generateOtp(totpData)
            else -> TotpGenerator.generateOtp(
                totpData = totpData,
                timeOffset = timeOffset,
                currentSeconds = currentSeconds
            )
        }
    }
    val remainingSeconds = remember(currentSeconds, totpData, timeOffset) {
        if (totpData.otpType == OtpType.HOTP) {
            0
        } else {
            TotpGenerator.getRemainingSeconds(
                period = totpData.period,
                timeOffset = timeOffset,
                currentSeconds = currentSeconds
            )
        }
    }
    val progress = remember(
        currentSeconds,
        progressTimeMillis,
        totpData,
        timeOffset,
        smoothProgress
    ) {
        if (totpData.otpType == OtpType.HOTP) {
            0f
        } else if (smoothProgress) {
            val periodMillis = (totpData.period * 1000L).coerceAtLeast(1000L)
            val correctedMillis = progressTimeMillis + (timeOffset * 1000L)
            val elapsedInPeriod = ((correctedMillis % periodMillis) + periodMillis) % periodMillis
            (elapsedInPeriod.toFloat() / periodMillis.toFloat()).coerceIn(0f, 1f)
        } else {
            TotpGenerator.getProgress(
                period = totpData.period,
                timeOffset = timeOffset,
                currentSeconds = currentSeconds
            ).coerceIn(0f, 1f)
        }
    }
    val badgeValue = if (totpData.otpType == OtpType.HOTP) {
        totpData.counter.toString()
    } else {
        remainingSeconds.toString()
    }
    val badgeContainerColor = if (totpData.otpType == OtpType.HOTP) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = if (totpData.otpType == OtpType.HOTP) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 1.dp,
            shadowElevation = 0.dp
        ) {
            if (showHeader || (showProgress && totpData.otpType != OtpType.HOTP)) {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showHeader) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = if (totpData.otpType == OtpType.STEAM) Icons.Default.Games else Icons.Default.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = when (totpData.otpType) {
                                    OtpType.HOTP -> "HOTP"
                                    OtpType.STEAM -> "Steam"
                                    else -> "TOTP"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f)
                            )
                        }
                    }

                    Text(
                        text = formatInlinePreviewOtpCode(currentCode, totpData.otpType),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    if (showProgress && totpData.otpType != OtpType.HOTP) {
                        LinearProgressIndicator(
                            progress = { progress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.14f),
                            strokeCap = StrokeCap.Round
                        )
                    }
                }
            } else {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = formatInlinePreviewOtpCode(currentCode, totpData.otpType),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        InlineTotpNumericBadge(
            value = badgeValue,
            containerColor = badgeContainerColor,
            contentColor = badgeContentColor,
            isHotp = totpData.otpType == OtpType.HOTP
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InlineTotpNumericBadge(
    value: String,
    containerColor: Color,
    contentColor: Color,
    isHotp: Boolean
) {
    Box(
        modifier = Modifier.size(72.dp),
        contentAlignment = Alignment.Center
    ) {
        MaterialExpressiveLoadingIndicator(
            modifier = Modifier.size(60.dp),
            color = if (isHotp) containerColor else contentColor
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.surface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

private fun formatInlinePreviewOtpCode(code: String, otpType: OtpType): String {
    return when (otpType) {
        OtpType.STEAM -> {
            if (code.length == 5) {
                "${code.substring(0, 2)} ${code.substring(2)}"
            } else {
                code
            }
        }

        else -> {
            when (code.length) {
                6 -> "${code.substring(0, 3)} ${code.substring(3)}"
                8 -> "${code.substring(0, 4)} ${code.substring(4)}"
                else -> code
            }
        }
    }
}
