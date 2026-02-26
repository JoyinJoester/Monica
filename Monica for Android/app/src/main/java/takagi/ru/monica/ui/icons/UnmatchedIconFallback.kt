package takagi.ru.monica.ui.icons

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.data.UnmatchedIconHandlingStrategy

fun shouldShowFallbackSlot(strategy: UnmatchedIconHandlingStrategy): Boolean {
    return strategy != UnmatchedIconHandlingStrategy.HIDE
}

@Composable
fun UnmatchedIconFallback(
    strategy: UnmatchedIconHandlingStrategy,
    primaryText: String?,
    secondaryText: String?,
    defaultIcon: ImageVector,
    iconSize: Dp
) {
    when (strategy) {
        UnmatchedIconHandlingStrategy.DEFAULT_ICON -> {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(iconSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = defaultIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(iconSize * 0.6f)
                    )
                }
            }
        }

        UnmatchedIconHandlingStrategy.WEBSITE_OR_TITLE_INITIAL -> {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(iconSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = resolveInitial(primaryText, secondaryText),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = (iconSize.value * 0.42f).sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        UnmatchedIconHandlingStrategy.HIDE -> Unit
    }
}

private fun resolveInitial(primaryText: String?, secondaryText: String?): String {
    val raw = listOf(primaryText, secondaryText)
        .firstNotNullOfOrNull { source ->
            source
                ?.trim()
                ?.firstOrNull { !it.isWhitespace() }
                ?.uppercaseChar()
                ?.toString()
        }
    return raw ?: "#"
}
