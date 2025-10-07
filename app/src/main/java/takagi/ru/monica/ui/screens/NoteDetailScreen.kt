package takagi.ru.monica.ui.screens

import android.text.method.LinkMovementMethod
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import takagi.ru.monica.R
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.util.MarkdownUtils
import takagi.ru.monica.viewmodel.NoteViewModel
import java.text.DateFormat

private enum class NoteContentTab {
    Preview,
    Raw
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: Long,
    viewModel: NoteViewModel,
    onNavigateBack: () -> Unit,
    onEditNote: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (noteId <= 0) {
        InvalidNoteDialog(onDismiss = onNavigateBack)
        return
    }

    val noteFlow = remember(noteId) { viewModel.observeNoteById(noteId) }
    val note by noteFlow.collectAsState(initial = null)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scrollState = rememberScrollState()

    var selectedTab by rememberSaveable { mutableStateOf(NoteContentTab.Preview) }
    var isPreviewFullscreen by rememberSaveable { mutableStateOf(false) }

    val noteData = remember(note?.itemData) {
        note?.itemData?.let { viewModel.parseNoteData(it) }
    }

    val formattedUpdatedAt = remember(note?.updatedAt) {
        note?.updatedAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(note?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.note_detail_title))
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (note != null) {
                        val shareContent = buildShareContent(
                            note = note,
                            noteData = noteData,
                            fallbackTitle = context.getString(R.string.note_detail_title),
                            tagsLabel = context.getString(R.string.note_detail_tags_label)
                        )
                        IconButton(onClick = { onEditNote(noteId) }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                        }
                        if (
                            noteData?.isMarkdown == true &&
                            noteData.content.isNotBlank() &&
                            selectedTab == NoteContentTab.Preview
                        ) {
                            IconButton(onClick = { isPreviewFullscreen = true }) {
                                Icon(Icons.Default.Fullscreen, contentDescription = stringResource(R.string.note_detail_preview_fullscreen))
                            }
                        }
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(shareContent))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.note_detail_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            enabled = shareContent.isNotBlank()
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.copy))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        when {
            note == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.StickyNote2,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.note_detail_not_found_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.note_detail_not_found_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            else -> {
                Column(
                    modifier = modifier
                        .padding(paddingValues)
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .fillMaxSize()
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    note?.let { currentNote ->
                        NoteHeader(note = currentNote, formattedUpdatedAt = formattedUpdatedAt, noteData = noteData)

                        if (noteData?.isMarkdown == true) {
                            val tabs = listOf(NoteContentTab.Preview, NoteContentTab.Raw)
                            TabRow(selectedTabIndex = tabs.indexOf(selectedTab)) {
                                tabs.forEach { tab ->
                                    Tab(
                                        selected = selectedTab == tab,
                                        onClick = { selectedTab = tab },
                                        text = {
                                            Text(
                                                text = when (tab) {
                                                    NoteContentTab.Preview -> stringResource(R.string.note_detail_preview_tab)
                                                    NoteContentTab.Raw -> stringResource(R.string.note_detail_raw_tab)
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }

                        val content = noteData?.content ?: ""
                        if (noteData?.isMarkdown == true && selectedTab == NoteContentTab.Preview) {
                            MarkdownPreview(markdown = content)
                        } else {
                            SelectionContainer {
                                Text(
                                    text = content.ifBlank { stringResource(R.string.note_detail_empty_content) },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        if (!currentNote.notes.isNullOrBlank()) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = stringResource(R.string.note_detail_additional_notes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                SelectionContainer {
                                    Text(
                                        text = currentNote.notes,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isPreviewFullscreen && noteData?.isMarkdown == true) {
        MarkdownPreviewFullScreenDialog(
            markdown = noteData.content,
            onClose = { isPreviewFullscreen = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteHeader(
    note: SecureItem,
    formattedUpdatedAt: String?,
    noteData: takagi.ru.monica.data.model.NoteData?
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = note.title.takeIf { it.isNotBlank() } ?: stringResource(R.string.note_detail_title),
            style = MaterialTheme.typography.headlineSmall
        )

        if (!noteData?.tags.isNullOrEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                noteData!!.tags.forEach { tag ->
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text(tag) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    )
                }
            }
        }

        if (formattedUpdatedAt != null) {
            Text(
                text = stringResource(R.string.note_detail_updated_at, formattedUpdatedAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val spanned = remember(markdown) { MarkdownUtils.markdownToSpanned(markdown) }
    val textColor = MaterialTheme.colorScheme.onSurface
    val linkColor = MaterialTheme.colorScheme.primary

    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { context ->
            TextView(context).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextColor(textColor.toArgb())
                setLinkTextColor(linkColor.toArgb())
                text = spanned
            }
        },
        update = { view ->
            view.setTextColor(textColor.toArgb())
            view.setLinkTextColor(linkColor.toArgb())
            view.text = spanned
        }
    )
}

@Composable
private fun MarkdownPreviewFullScreenDialog(
    markdown: String,
    onClose: () -> Unit
) {
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.note_detail_preview_fullscreen_title),
                        style = MaterialTheme.typography.titleLarge
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                    }
                }
                HorizontalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                        .padding(20.dp)
                ) {
                    MarkdownPreview(
                        markdown = markdown,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun InvalidNoteDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.note_detail_not_found_title))
        },
        text = {
            Text(text = stringResource(R.string.note_detail_not_found_message))
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

private fun buildShareContent(
    note: SecureItem?,
    noteData: takagi.ru.monica.data.model.NoteData?,
    fallbackTitle: String,
    tagsLabel: String
): String {
    if (note == null || noteData == null) return ""
    val builder = StringBuilder()
    builder.append(note.title.ifBlank { fallbackTitle })
    builder.append('\n')
    builder.append(noteData.content)
    if (!note.notes.isNullOrBlank()) {
        builder.append("\n\n")
        builder.append(note.notes)
    }
    if (!noteData.tags.isNullOrEmpty()) {
        builder.append("\n\n")
        builder.append(tagsLabel)
        builder.append(':')
        builder.append(' ')
        builder.append(noteData.tags.joinToString(", "))
    }
    return builder.toString()
}
