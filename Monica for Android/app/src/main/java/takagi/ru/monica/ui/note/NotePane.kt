package takagi.ru.monica.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import takagi.ru.monica.security.SecurityManager
import takagi.ru.monica.ui.common.layout.DetailPane
import takagi.ru.monica.ui.common.layout.ListPane
import takagi.ru.monica.ui.screens.AddEditNoteScreen
import takagi.ru.monica.ui.screens.NoteListScreen
import takagi.ru.monica.viewmodel.NoteViewModel
import takagi.ru.monica.viewmodel.SettingsViewModel

@Composable
internal fun NotePane(
    isCompactWidth: Boolean,
    wideListPaneWidth: Dp,
    noteViewModel: NoteViewModel,
    settingsViewModel: SettingsViewModel,
    securityManager: SecurityManager,
    onNavigateToAddNote: (Long?) -> Unit,
    onSelectionModeChange: (Boolean) -> Unit,
    isAddingNoteInline: Boolean,
    inlineNoteEditorId: Long?,
    onInlineNoteEditorBack: () -> Unit
) {
    if (isCompactWidth) {
        NoteListScreen(
            viewModel = noteViewModel,
            settingsViewModel = settingsViewModel,
            onNavigateToAddNote = onNavigateToAddNote,
            securityManager = securityManager,
            onSelectionModeChange = onSelectionModeChange
        )
    } else {
        Row(modifier = Modifier.fillMaxSize()) {
            ListPane(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(wideListPaneWidth)
            ) {
                NoteListScreen(
                    viewModel = noteViewModel,
                    settingsViewModel = settingsViewModel,
                    onNavigateToAddNote = onNavigateToAddNote,
                    securityManager = securityManager,
                    onSelectionModeChange = onSelectionModeChange
                )
            }

            DetailPane(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                if (isAddingNoteInline || inlineNoteEditorId != null) {
                    AddEditNoteScreen(
                        noteId = inlineNoteEditorId ?: -1L,
                        onNavigateBack = onInlineNoteEditorBack,
                        viewModel = noteViewModel
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Select a note to view or edit",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
