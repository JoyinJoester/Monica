package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R
import takagi.ru.monica.data.Category

internal data class PasswordCategoryDialogsParams(
    val categoryActionTarget: Category?,
    val renameCategoryTarget: Category?,
    val renameCategoryInput: String,
    val onDismissCategoryAction: () -> Unit,
    val onStartRename: ((Category) -> Unit)?,
    val onDeleteCategory: ((Category) -> Unit)?,
    val onRenameInputChange: (String) -> Unit,
    val onConfirmRename: (Category, String) -> Unit,
    val onDismissRename: () -> Unit
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
