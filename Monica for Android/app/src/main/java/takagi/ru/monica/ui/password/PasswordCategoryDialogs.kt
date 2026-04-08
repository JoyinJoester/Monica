package takagi.ru.monica.ui

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import takagi.ru.monica.ui.components.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.Category
import takagi.ru.monica.utils.getLocalCategoryLeafName
import takagi.ru.monica.utils.getLocalCategoryParentPath
import takagi.ru.monica.utils.isLocalCategoryDescendantPath
import takagi.ru.monica.utils.normalizeLocalCategoryPath

internal data class PasswordCategoryDialogsParams(
    val categoryActionTarget: Category?,
    val renameCategoryTarget: Category?,
    val renameCategoryInput: String,
    val onDismissCategoryAction: () -> Unit,
    val onStartRename: ((Category) -> Unit)?,
    val moveCategoryTarget: Category?,
    val onStartMove: ((Category) -> Unit)?,
    val onDismissMove: () -> Unit,
    val availableMoveTargets: List<Category>,
    val onConfirmMove: (Category, Long?) -> Unit,
    val onDeleteCategory: ((Category) -> Unit)?,
    val onRenameInputChange: (String) -> Unit,
    val onConfirmRename: (Category, String) -> Unit,
    val onDismissRename: () -> Unit
)

private data class PasswordMoveTargetNode(
    val category: Category,
    val displayName: String,
    val depth: Int,
    val parentPathLabel: String?
)

@Composable
internal fun PasswordCategoryDialogs(params: PasswordCategoryDialogsParams) {
    if (params.categoryActionTarget != null) {
        val target = params.categoryActionTarget
        AlertDialog(
            onDismissRequest = params.onDismissCategoryAction,
            title = { Text(stringResource(R.string.edit_category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (params.onStartRename != null) {
                        FilledTonalButton(
                            onClick = { params.onStartRename.invoke(target) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.rename_category))
                        }
                    }
                    if (params.onStartMove != null) {
                        FilledTonalButton(
                            onClick = { params.onStartMove.invoke(target) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.move))
                        }
                    }
                    if (params.onDeleteCategory != null) {
                        FilledTonalButton(
                            onClick = { params.onDeleteCategory.invoke(target) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.delete))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = params.onDismissCategoryAction) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (params.moveCategoryTarget != null) {
        val target = params.moveCategoryTarget
        val scrollState = rememberScrollState()
        val moveTargets = params.availableMoveTargets
            .map { category ->
                val normalizedPath = normalizeLocalCategoryPath(category.name)
                PasswordMoveTargetNode(
                    category = category,
                    displayName = getLocalCategoryLeafName(normalizedPath).ifBlank { normalizedPath },
                    depth = normalizedPath.split('/').size.minus(1).coerceAtLeast(0),
                    parentPathLabel = getLocalCategoryParentPath(normalizedPath)?.replace("/", " / ")
                )
            }
            .sortedBy { normalizeLocalCategoryPath(it.category.name).lowercase() }
        AlertDialog(
            onDismissRequest = params.onDismissMove,
            title = { Text(stringResource(R.string.move_folder_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = target.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.move_folder_destination_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { params.onConfirmMove(target, null) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.FolderOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.move_folder_root_target))
                    }
                    moveTargets.forEach { destination ->
                        FilledTonalButton(
                            onClick = { params.onConfirmMove(target, destination.category.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = (destination.depth * 14).coerceAtMost(70).dp)
                        ) {
                            Icon(Icons.Default.Folder, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = destination.displayName,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!destination.parentPathLabel.isNullOrBlank()) {
                                    Text(
                                        text = destination.parentPathLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = params.onDismissMove) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (params.renameCategoryTarget != null) {
        val target = params.renameCategoryTarget
        AlertDialog(
            onDismissRequest = params.onDismissRename,
            title = { Text(stringResource(R.string.rename_category)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = params.renameCategoryInput,
                        onValueChange = params.onRenameInputChange,
                        label = { Text(stringResource(R.string.category_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { params.onConfirmRename(target, params.renameCategoryInput) }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = params.onDismissRename) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
