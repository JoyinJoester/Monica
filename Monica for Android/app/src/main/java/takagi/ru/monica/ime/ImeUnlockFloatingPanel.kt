package takagi.ru.monica.ime

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
internal fun ImeUnlockFloatingPanel(
    modifier: Modifier = Modifier,
    unlockPassword: String,
    errorMessage: String?,
    biometricEnabled: Boolean,
    onUnlockPasswordChanged: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometricUnlock: () -> Unit
) {
    var passwordVisible by remember { mutableStateOf(false) }
    val hasError = !errorMessage.isNullOrBlank()
    val isEmpty = unlockPassword.isEmpty()

    // 光标闪烁
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursorAlpha"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 标题行
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 2.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = stringResource(R.string.ime_unlock_message),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // 输入行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 密码显示框
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (hasError) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (hasError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // 占位文字（仅空状态显示，不与光标并排）
                if (isEmpty) {
                    Text(
                        text = stringResource(R.string.ime_unlock_password_label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                    )
                }

                // 内容 + 光标行
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    if (!isEmpty) {
                        if (passwordVisible) {
                            Text(
                                text = unlockPassword,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                repeat(unlockPassword.length.coerceAtMost(18)) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(
                                                MaterialTheme.colorScheme.onSurface,
                                                CircleShape
                                            )
                                    )
                                }
                                if (unlockPassword.length > 18) {
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "+${unlockPassword.length - 18}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                    }

                    // 闪烁光标
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(20.dp)
                            .graphicsLayer { alpha = cursorAlpha }
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(1.dp)
                            )
                    )
                }

                // 明暗切换按钮（右侧）
                if (!isEmpty) {
                    Box(
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { passwordVisible = !passwordVisible },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 解锁按钮
            FilledTonalButton(
                onClick = onUnlock,
                enabled = unlockPassword.isNotBlank(),
                modifier = Modifier.height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.unlock),
                    style = MaterialTheme.typography.labelLarge
                )
            }

            // 生物识别
            if (biometricEnabled) {
                FilledIconButton(
                    onClick = onBiometricUnlock,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = stringResource(R.string.biometric_unlock),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 错误信息
        if (hasError) {
            Text(
                text = errorMessage!!,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
