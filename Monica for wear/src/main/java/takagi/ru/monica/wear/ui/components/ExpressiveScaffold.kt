package takagi.ru.monica.wear.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText

@Composable
fun MonicaTimeText() {
    TimeText()
}

@Composable
fun ExpressiveBackground(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primary.copy(alpha = 0.24f),
                        colors.secondary.copy(alpha = 0.14f),
                        colors.background
                    )
                )
            )
    )
}

@Composable
fun roundContentWidthFraction(
    round: Float = 0.82f,
    square: Float = 0.92f
): Float = if (LocalConfiguration.current.isScreenRound) round else square

@Composable
fun RoundHeaderChip(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    WearPanel(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.26f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun RoundSectionChip(
    text: String,
    modifier: Modifier = Modifier
) {
    WearPanel(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
        borderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RoundStagePanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit
) {
    WearPanel(
        modifier = modifier,
        shape = RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
        contentAlignment = contentAlignment
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentAlignment = contentAlignment
        ) {
            content()
        }
    }
}

@Composable
fun RoundActionDock(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    WearPanel(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.96f),
        borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.22f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

fun Color.withScrim(alpha: Float): Color = copy(alpha = alpha)
