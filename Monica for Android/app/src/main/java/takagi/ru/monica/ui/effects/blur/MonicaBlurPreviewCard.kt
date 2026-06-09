package takagi.ru.monica.ui.effects.blur

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.MonicaBlurIntensity
import takagi.ru.monica.data.MonicaBlurMode

@Composable
fun MonicaBlurPreviewCard(
    isPlusActivated: Boolean,
    plusBlurEnabled: Boolean,
    mode: MonicaBlurMode,
    intensity: MonicaBlurIntensity,
    reduceOnBatterySaver: Boolean,
    modifier: Modifier = Modifier
) {
    val enabled = MonicaBlurGate.canUsePlusBlur(
        isPlusActivated = isPlusActivated,
        plusBlurEnabled = plusBlurEnabled,
        isSensitiveScreen = false,
        reduceOnBatterySaver = reduceOnBatterySaver,
        isBatterySaver = false
    )
    val effectiveMode = MonicaBlurGate.effectiveMode(mode)
    val supportsRealtime = MonicaBlurDefaults.supportsRealtimeBlur()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        MonicaBlurBackground(
            enabled = enabled,
            mode = effectiveMode,
            intensity = intensity,
            modifier = Modifier
                .fillMaxWidth()
                .height(172.dp)
        ) {
            MonicaGlassSurface(
                enabled = enabled,
                mode = effectiveMode,
                intensity = intensity,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.plus_blur_preview_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = stringResource(R.string.plus_blur_preview_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (enabled) {
                                        stringResource(R.string.plus_blur_preview_enabled)
                                    } else {
                                        stringResource(R.string.plus_blur_preview_disabled)
                                    }
                                )
                            }
                        )
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    if (supportsRealtime) {
                                        stringResource(R.string.plus_blur_preview_realtime_available)
                                    } else {
                                        stringResource(
                                            R.string.plus_blur_preview_compat,
                                            Build.VERSION.SDK_INT
                                        )
                                    }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
