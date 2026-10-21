package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp

internal data class EditorMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val supporting: String? = null,
    val destructive: Boolean = false,
    val tag: String = "",
)

/** Shared M3 Expressive anchored menu for create/edit item actions. */
@Composable
internal fun EditorActionMenu(expanded: Boolean, onDismiss: () -> Unit, actions: List<EditorMenuAction>) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val menuWidth = (screenWidthDp * 0.76f).dp.coerceIn(224.dp, 320.dp)
    DropdownMenu(expanded, onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 0.dp, shadowElevation = 6.dp,
        modifier = Modifier.width(menuWidth).testTag("editor_action_menu")) {
        actions.forEach { action ->
            val color = if (action.destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            DropdownMenuItem(
                text = { Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(action.label, style = MaterialTheme.typography.bodyLarge)
                    action.supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } },
                leadingIcon = { Icon(action.icon, null, Modifier.size(22.dp)) },
                colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color),
                onClick = { onDismiss(); action.onClick() },
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                modifier = Modifier
                    .then(if (action.tag.isNotBlank()) Modifier.testTag(action.tag) else Modifier))
        }
    }
}

/** Single-action advanced entrances use the same anchored menu as every other editor. */
@Composable
internal fun EditorAdvancedMenuButton(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val label = androidx.compose.ui.res.stringResource(takagi.ru.monica.R.string.advanced_options)
    Box {
        IconButton(onClick = { expanded = true }, modifier = modifier) {
            Icon(androidx.compose.material.icons.Icons.Default.MoreVert, label)
        }
        EditorActionMenu(expanded, { expanded = false }, listOf(
            EditorMenuAction(label, androidx.compose.material.icons.Icons.Default.Tune, onOpen, tag = "editor_advanced_open")))
    }
}
