package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.PasswordCardDisplayMode
import takagi.ru.monica.ui.components.SettingsOptionItem
import takagi.ru.monica.ui.password.StackCardMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PasswordDisplayOptionsSheet(
    stackCardMode: StackCardMode,
    groupMode: String,
    passwordCardDisplayMode: PasswordCardDisplayMode,
    onDismiss: () -> Unit,
    onStackCardModeSelected: (StackCardMode) -> Unit,
    onGroupModeSelected: (String) -> Unit,
    onPasswordCardDisplayModeSelected: (PasswordCardDisplayMode) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun dismissDisplayOptionsSheet(afterDismiss: (() -> Unit)? = null) {
        coroutineScope.launch {
            if (sheetState.isVisible) {
                sheetState.hide()
            }
            onDismiss()
            afterDismiss?.invoke()
        }
    }

    ModalBottomSheet(
        onDismissRequest = { dismissDisplayOptionsSheet() },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.display_options_menu_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )

            Text(
                text = stringResource(R.string.stack_mode_menu_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            listOf(StackCardMode.AUTO, StackCardMode.ALWAYS_EXPANDED).forEach { mode ->
                val selected = mode == stackCardMode
                val (modeTitle, desc, icon) = when (mode) {
                    StackCardMode.AUTO -> Triple(
                        stringResource(R.string.stack_mode_auto),
                        stringResource(R.string.stack_mode_auto_desc),
                        Icons.Default.AutoAwesome,
                    )

                    StackCardMode.ALWAYS_EXPANDED -> Triple(
                        stringResource(R.string.stack_mode_expand),
                        stringResource(R.string.stack_mode_expand_desc),
                        Icons.Default.UnfoldMore,
                    )
                }
                SettingsOptionItem(
                    title = modeTitle,
                    description = desc,
                    icon = icon,
                    selected = selected,
                    onClick = {
                        dismissDisplayOptionsSheet {
                            onStackCardModeSelected(mode)
                        }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Text(
                text = stringResource(R.string.group_mode_menu_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            val groupModes = listOf(
                "smart" to Triple(
                    stringResource(R.string.group_mode_smart),
                    stringResource(R.string.group_mode_smart_desc),
                    Icons.Default.DashboardCustomize,
                ),
                "note" to Triple(
                    stringResource(R.string.group_mode_note),
                    stringResource(R.string.group_mode_note_desc),
                    Icons.Default.Description,
                ),
                "website" to Triple(
                    stringResource(R.string.group_mode_website),
                    stringResource(R.string.group_mode_website_desc),
                    Icons.Default.Language,
                ),
                "app" to Triple(
                    stringResource(R.string.group_mode_app),
                    stringResource(R.string.group_mode_app_desc),
                    Icons.Default.Apps,
                ),
                "title" to Triple(
                    stringResource(R.string.group_mode_title),
                    stringResource(R.string.group_mode_title_desc),
                    Icons.Default.Title,
                )
            )
            groupModes.forEach { (modeKey, meta) ->
                val selected = groupMode == modeKey
                val (modeTitle, desc, icon) = meta
                SettingsOptionItem(
                    title = modeTitle,
                    description = desc,
                    icon = icon,
                    selected = selected,
                    onClick = {
                        dismissDisplayOptionsSheet {
                            onGroupModeSelected(modeKey)
                        }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 24.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            )

            Text(
                text = stringResource(R.string.password_card_display_mode_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            listOf(
                PasswordCardDisplayMode.SHOW_ALL,
                PasswordCardDisplayMode.TITLE_USERNAME,
                PasswordCardDisplayMode.TITLE_ONLY
            ).forEach { mode ->
                val selected = mode == passwordCardDisplayMode
                val (modeTitle, desc, icon) = when (mode) {
                    PasswordCardDisplayMode.SHOW_ALL -> Triple(
                        stringResource(R.string.display_mode_all),
                        stringResource(R.string.display_mode_all_desc),
                        Icons.Default.Visibility,
                    )

                    PasswordCardDisplayMode.TITLE_USERNAME -> Triple(
                        stringResource(R.string.display_mode_title_username),
                        stringResource(R.string.display_mode_title_username_desc),
                        Icons.Default.Person,
                    )

                    PasswordCardDisplayMode.TITLE_ONLY -> Triple(
                        stringResource(R.string.display_mode_title_only),
                        stringResource(R.string.display_mode_title_only_desc),
                        Icons.Default.Title,
                    )
                }
                SettingsOptionItem(
                    title = modeTitle,
                    description = desc,
                    icon = icon,
                    selected = selected,
                    onClick = {
                        dismissDisplayOptionsSheet {
                            onPasswordCardDisplayModeSelected(mode)
                        }
                    }
                )
            }
        }
    }
}
