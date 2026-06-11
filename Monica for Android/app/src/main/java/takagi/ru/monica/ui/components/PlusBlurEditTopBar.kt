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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
fun PlusBlurEditTopBar(
    title: String,
    settings: AppSettings,
    hazeState: HazeState,
    hazeStyle: HazeStyle,
    isFavorite: Boolean,
    onNavigateBack: () -> Unit,
    onFavoriteChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
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
                PlusBlurTopBarIconButton(
                    icon = MonicaIcons.Navigation.back,
                    contentDescription = stringResource(R.string.back),
                    onClick = onNavigateBack
                )
            }

            MonicaPlusBlurIslandSurface(
                settings = settings,
                enabledForThisSurface = true,
                modifier = Modifier
                    .height(islandSize)
                    .widthIn(max = 176.dp),
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
                PlusBlurTopBarIconButton(
                    icon = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.favorite),
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                    onClick = { onFavoriteChange(!isFavorite) }
                )
            }
        }
    }
}

@Composable
private fun PlusBlurTopBarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LocalContentColor.current
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(CircleShape)
            .clickable(onClick = onClick),
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
