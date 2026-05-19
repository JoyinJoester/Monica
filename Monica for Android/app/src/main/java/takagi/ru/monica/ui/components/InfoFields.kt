package takagi.ru.monica.ui.components

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.R
import takagi.ru.monica.ui.icons.MonicaIcons

// ============================================
// 🔧 信息字段组件
// ============================================
@Composable
fun InfoField(
    label: String,
    value: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun InfoFieldWithCopy(
    label: String,
    value: String,
    copyValue: String = value,
    context: Context,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val actionMenuState = rememberPasswordFieldActionMenuState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        copyPasswordDetailFieldValue(context, label, copyValue)
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = MonicaIcons.Action.copy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .clickable { actionMenuState.open() }
                .padding(vertical = 6.dp)
        ) {
            PasswordFieldActionMenuHost(
                state = actionMenuState,
                label = label,
                value = copyValue,
                displayValue = value,
                context = context,
                onCreateSend = onCreateSend
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun PasswordField(
    label: String,
    value: String,
    visible: Boolean, // External control state
    onToggleVisibility: () -> Unit,
    context: Context,
    onCreateSend: ((title: String, text: String) -> Unit)? = null
) {
    val actionMenuState = rememberPasswordFieldActionMenuState()

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { actionMenuState.open() }
                    .padding(vertical = 8.dp)
            ) {
                PasswordFieldActionMenuHost(
                    state = actionMenuState,
                    label = label,
                    value = value,
                    displayValue = if (visible) value else "•".repeat(value.length),
                    context = context,
                    includeVisibilityToggle = true,
                    isVisible = visible,
                    onToggleVisibility = onToggleVisibility,
                    onCreateSend = onCreateSend
                )
                Text(
                    text = if (visible) value else "•".repeat(value.length),
                    style = if (visible) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 3.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row {
                // 显示/隐藏按钮
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        imageVector = if (visible) 
                            MonicaIcons.Security.visibilityOff 
                        else 
                            MonicaIcons.Security.visibility,
                        contentDescription = if (visible) 
                            stringResource(R.string.hide) 
                        else 
                            stringResource(R.string.show),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 复制按钮
                IconButton(
                    onClick = {
                        copyPasswordDetailFieldValue(context, label, value)
                    }
                ) {
                    Icon(
                        imageVector = MonicaIcons.Action.copy,
                        contentDescription = stringResource(R.string.copy),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
