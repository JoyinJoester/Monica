package takagi.ru.monica.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import takagi.ru.monica.R
import takagi.ru.monica.data.AppSettings
import takagi.ru.monica.ui.effects.blur.MonicaPlusBlurIslandSurface
import takagi.ru.monica.ui.icons.MonicaIcons

@Composable
fun PlusBlurNoteEditTopBar(
    title: String,
    settings: AppSettings,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    isEditing: Boolean,
    isFavorite: Boolean,
    onNavigateBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onEnterFullScreen: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val islandSize = 40.dp
    val topBarHeight = statusBarTop + 52.dp

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = statusBarTop)
                .height(52.dp)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier.size(islandSize),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                fillContent = true,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            ) {
                PlusBlurNoteIconButton(
                    icon = MonicaIcons.Navigation.back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onNavigateBack,
                    enabled = enabled
                )
            }

            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier
                    .height(islandSize)
                    .widthIn(max = 132.dp),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                contentAlignment = Alignment.CenterStart,
                fillContent = false,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier.size(islandSize),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                fillContent = true,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            ) {
                PlusBlurNoteIconButton(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    onClick = onToggleFavorite,
                    enabled = enabled
                )
            }

            if (isEditing) {
                MonicaPlusBlurIslandSurface(
                    settings = settings,
                    enabledForThisSurface = true,
                    modifier = Modifier.size(islandSize),
                    shape = CircleShape,
                    contentPadding = PaddingValues(0.dp),
                    fillContent = true,
                    hazeState = hazeState,
                    hazeStyle = hazeStyle
                ) {
                    PlusBlurNoteIconButton(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        onClick = onDelete,
                        enabled = enabled
                    )
                }
            }

            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier.size(islandSize),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp),
                fillContent = true,
                hazeState = hazeState,
                hazeStyle = hazeStyle
            ) {
                PlusBlurNoteIconButton(
                    icon = Icons.Default.Fullscreen,
                    contentDescription = stringResource(R.string.note_detail_preview_fullscreen),
                    onClick = onEnterFullScreen,
                    enabled = enabled
                )
            }
        }
    }
}

@Composable
private fun PlusBlurNoteIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    enabled: Boolean,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}
