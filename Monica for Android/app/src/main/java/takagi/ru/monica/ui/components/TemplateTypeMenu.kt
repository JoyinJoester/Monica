package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal data class TemplateTypeMenuOption(
    val label: String,
    val icon: ImageVector,
    val selected: Boolean,
    val onClick: () -> Unit,
    val tag: String = "",
)

/** Compact, content-sized template choices shared by credential and wallet editors. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TemplateTypeMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    options: List<TemplateTypeMenuOption>,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    DropdownMenuPopup(expanded, onDismissRequest = onDismiss) {
        val popupDensity = LocalDensity.current
        val maxHeight = with(density) {
            with(popupDensity) {
                (LocalConfiguration.current.screenHeightDp.dp - 96.dp).coerceAtLeast(48.dp).toPx()
            }.toDp()
        }
        CompositionLocalProvider(LocalDensity provides density, LocalContext provides context,
            LocalConfiguration provides configuration) {
            Surface(shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 0.dp, shadowElevation = 6.dp) {
                Column(Modifier.width(IntrinsicSize.Max).widthIn(min = 112.dp, max = 280.dp)
                    .heightIn(max = maxHeight).verticalScroll(rememberScrollState())
                    .padding(vertical = 8.dp).testTag("template_type_menu")) {
                    options.forEach { option ->
                        val color = if (option.selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                        DropdownMenuItem(
                            text = { Text(option.label, style = MaterialTheme.typography.bodyLarge) },
                            leadingIcon = { Icon(option.icon, null) },
                            trailingIcon = if (option.selected) {
                                { Icon(Icons.Default.Check, null) }
                            } else null,
                            colors = MenuDefaults.itemColors(textColor = color,
                                leadingIconColor = color, trailingIconColor = color),
                            onClick = { onDismiss(); if (!option.selected) option.onClick() },
                            modifier = if (option.tag.isBlank()) Modifier else Modifier.testTag(option.tag),
                        )
                    }
                }
            }
        }
    }
}
