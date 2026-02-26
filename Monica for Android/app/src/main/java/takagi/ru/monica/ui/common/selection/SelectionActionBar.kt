package takagi.ru.monica.ui.common.selection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@Composable
fun SelectionActionBar(
    modifier: Modifier = Modifier,
    selectedCount: Int,
    onExit: () -> Unit,
    onSelectAll: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    onMoveToCategory: (() -> Unit)? = null,
    onStack: (() -> Unit)? = null,
    onDelete: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = containerColor,
        contentColor = contentColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Text(
                    text = selectedCount.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            ActionIcon(
                icon = Icons.Outlined.CheckCircle,
                contentDescription = stringResource(id = R.string.select_all),
                onClick = onSelectAll
            )

            onFavorite?.let {
                ActionIcon(
                    icon = Icons.Outlined.FavoriteBorder,
                    contentDescription = stringResource(id = R.string.favorite),
                    onClick = it
                )
            }

            onMoveToCategory?.let {
                ActionIcon(
                    icon = Icons.Default.Folder,
                    contentDescription = stringResource(id = R.string.move_to_category),
                    onClick = it
                )
            }

            onStack?.let {
                ActionIcon(
                    icon = Icons.Default.Layers,
                    contentDescription = stringResource(id = R.string.batch_stack),
                    onClick = it
                )
            }

            ActionIcon(
                icon = Icons.Outlined.Delete,
                contentDescription = stringResource(id = R.string.delete),
                onClick = onDelete
            )

            Spacer(modifier = Modifier.width(4.dp))

            ActionIcon(
                icon = Icons.Default.Close,
                contentDescription = stringResource(id = R.string.close),
                onClick = onExit
            )
        }
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
