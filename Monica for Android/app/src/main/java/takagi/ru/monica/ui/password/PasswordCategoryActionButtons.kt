package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

internal data class PasswordCategoryActionButtonsParams(
    val canCreateCategory: Boolean,
    val canManageExistingCategories: Boolean,
    val categoryEditMode: Boolean,
    val onCreateCategory: () -> Unit,
    val onToggleEditMode: () -> Unit
)

@Composable
internal fun PasswordCategoryActionButtons(
    params: PasswordCategoryActionButtonsParams,
    modifier: Modifier = Modifier
) {
    if (!params.canCreateCategory && !params.canManageExistingCategories) {
        return
    }

    HorizontalDivider(modifier = modifier.padding(vertical = 4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val hasBothActions = params.canCreateCategory && params.canManageExistingCategories
        val buttonContentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        if (params.canCreateCategory) {
            OutlinedButton(
                onClick = params.onCreateCategory,
                modifier = Modifier.weight(if (hasBothActions) 1.16f else 1f),
                contentPadding = buttonContentPadding
            ) {
                Icon(
                    imageVector = Icons.Default.CreateNewFolder,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.add_category),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (params.canManageExistingCategories) {
            OutlinedButton(
                onClick = params.onToggleEditMode,
                modifier = Modifier.weight(if (hasBothActions) 0.84f else 1f),
                contentPadding = buttonContentPadding
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (params.categoryEditMode) {
                        stringResource(R.string.cancel)
                    } else {
                        stringResource(R.string.edit_category)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
