package takagi.ru.monica.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.HdrStrong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import takagi.ru.monica.R
import takagi.ru.monica.data.NoteCodeBlockCollapseMode
import takagi.ru.monica.ui.components.MarkdownPreviewText

private val NoteEditorFieldColors
    @Composable
    get() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Color.Transparent,
        unfocusedBorderColor = Color.Transparent,
        disabledBorderColor = Color.Transparent,
        errorBorderColor = Color.Transparent,
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NoteEditorSection(
    modifier: Modifier = Modifier,
    title: String,
    onTitleChange: (String) -> Unit,
    content: TextFieldValue,
    onContentChange: (TextFieldValue) -> Unit,
    isMarkdownPreview: Boolean,
    onMarkdownPreviewModeChange: (Boolean) -> Unit,
    showModeSwitcher: Boolean = true,
    codeBlockCollapseMode: NoteCodeBlockCollapseMode,
    inlineImageBitmaps: Map<String, Bitmap>,
    onPreviewInlineImage: (String) -> Unit,
    onTaskItemToggle: ((lineIndex: Int, checked: Boolean) -> Unit)? = null,
    tagsText: String,
    onTagsTextChange: (String) -> Unit,
    borderless: Boolean = false,
    showTags: Boolean = true,
    editorTakesRemainingSpace: Boolean = false
) {
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val titleBringIntoViewRequester = remember { BringIntoViewRequester() }
    val contentBringIntoViewRequester = remember { BringIntoViewRequester() }
    val tagsBringIntoViewRequester = remember { BringIntoViewRequester() }
    var titleFocused by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    var tagsFocused by remember { mutableStateOf(false) }
    val compactEditorViewportHeight = (configuration.screenHeightDp.dp * 0.38f).coerceIn(220.dp, 420.dp)

    LaunchedEffect(titleFocused) {
        if (titleFocused) {
            delay(120)
            titleBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(contentFocused, content.selection, content.text, isMarkdownPreview) {
        if (contentFocused && !isMarkdownPreview) {
            delay(120)
            contentBringIntoViewRequester.bringIntoView()
        }
    }

    LaunchedEffect(tagsFocused) {
        if (tagsFocused) {
            delay(120)
            tagsBringIntoViewRequester.bringIntoView()
        }
    }

    val containerModifier = if (borderless) {
        if (editorTakesRemainingSpace) Modifier.fillMaxSize() else Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .padding(14.dp)
    }

    @Composable
    fun EditorContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier.then(containerModifier),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (showModeSwitcher) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isMarkdownPreview,
                        onClick = {
                            onMarkdownPreviewModeChange(false)
                        },
                        label = { Text(stringResource(R.string.note_mode_edit)) }
                    )
                    FilterChip(
                        selected = isMarkdownPreview,
                        onClick = {
                            onMarkdownPreviewModeChange(true)
                        },
                        label = { Text(stringResource(R.string.note_mode_preview)) }
                    )
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .bringIntoViewRequester(titleBringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        titleFocused = focusState.isFocused
                    },
                placeholder = {
                    Text(
                        stringResource(R.string.title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                colors = NoteEditorFieldColors
            )

            Box(
                modifier = if (editorTakesRemainingSpace) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 220.dp, max = compactEditorViewportHeight)
                }
            ) {
                if (!isMarkdownPreview) {
                    OutlinedTextField(
                        value = content,
                        onValueChange = onContentChange,
                        modifier = if (editorTakesRemainingSpace) {
                            Modifier
                                .fillMaxSize()
                                .bringIntoViewRequester(contentBringIntoViewRequester)
                                .onFocusChanged { focusState ->
                                    contentFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(120)
                                            contentBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                }
                        } else {
                            Modifier
                                .fillMaxWidth()
                                .bringIntoViewRequester(contentBringIntoViewRequester)
                                .onFocusChanged { focusState ->
                                    contentFocused = focusState.isFocused
                                    if (focusState.isFocused) {
                                        scope.launch {
                                            delay(120)
                                            contentBringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                }
                        },
                        placeholder = { Text(stringResource(R.string.note_placeholder)) },
                        minLines = if (editorTakesRemainingSpace) 1 else 8,
                        maxLines = Int.MAX_VALUE,
                        shape = RoundedCornerShape(if (borderless) 0.dp else 12.dp),
                        colors = NoteEditorFieldColors
                    )
                } else {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize(),
                        shape = if (borderless) RoundedCornerShape(0.dp) else RoundedCornerShape(12.dp),
                        color = if (borderless) Color.Transparent else MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(if (borderless) 0.dp else 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (content.text.isBlank()) {
                                Text(
                                    text = stringResource(R.string.note_markdown_preview_empty),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                MarkdownPreviewText(
                                    markdown = content.text,
                                    imageBitmaps = inlineImageBitmaps,
                                    codeBlockCollapseMode = codeBlockCollapseMode,
                                    onInlineImageClick = onPreviewInlineImage,
                                    onTaskItemToggle = onTaskItemToggle,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            if (showTags) {
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = onTagsTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(tagsBringIntoViewRequester)
                        .onFocusChanged { focusState ->
                            tagsFocused = focusState.isFocused
                        },
                    placeholder = { Text(stringResource(R.string.note_tags_placeholder)) },
                    singleLine = true,
                    colors = NoteEditorFieldColors
                )
            }
        }
    }

    if (borderless) {
        EditorContent(modifier = modifier)
    } else {
        Surface(
            modifier = modifier,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            tonalElevation = 1.dp
        ) {
            EditorContent()
        }
    }
}

internal enum class MarkdownEditorAction {
    List,
    Checkbox,
    Heading,
    Bold,
    Italic,
    Underline,
    Strike,
    Quote,
    CodeBlock,
    Image
}

@Composable
internal fun MarkdownQuickToolbar(
    onAction: (MarkdownEditorAction) -> Unit
) {
    val actions = listOf(
        MarkdownEditorAction.List to Icons.Default.FormatListBulleted,
        MarkdownEditorAction.Checkbox to Icons.Default.CheckBox,
        MarkdownEditorAction.Heading to Icons.Default.HdrStrong,
        MarkdownEditorAction.Bold to Icons.Default.FormatBold,
        MarkdownEditorAction.Italic to Icons.Default.FormatItalic,
        MarkdownEditorAction.Underline to Icons.Default.FormatUnderlined,
        MarkdownEditorAction.Strike to Icons.Default.Remove,
        MarkdownEditorAction.Quote to Icons.Default.FormatQuote,
        MarkdownEditorAction.CodeBlock to Icons.Default.Code,
        MarkdownEditorAction.Image to Icons.Default.Image
    )

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(actions) { _, (action, icon) ->
                IconButton(onClick = { onAction(action) }) {
                    Icon(
                        imageVector = icon,
                        contentDescription = action.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

internal fun applyMarkdownAction(
    field: TextFieldValue,
    action: MarkdownEditorAction
): TextFieldValue {
    val start = field.selection.start.coerceAtLeast(0)
    val end = field.selection.end.coerceAtLeast(0)
    val safeStart = minOf(start, field.text.length)
    val safeEnd = minOf(end, field.text.length)
    val selected = field.text.substring(safeStart, safeEnd)

    return when (action) {
        MarkdownEditorAction.List -> insertPrefix(field, "- ")
        MarkdownEditorAction.Checkbox -> insertPrefix(field, "[ ] ")
        MarkdownEditorAction.Heading -> insertPrefix(field, "# ")
        MarkdownEditorAction.Quote -> insertPrefix(field, "> ")
        MarkdownEditorAction.Bold -> wrapSelection(field, selected, "**", "**")
        MarkdownEditorAction.Italic -> wrapSelection(field, selected, "*", "*")
        MarkdownEditorAction.Underline -> wrapSelection(field, selected, "_", "_")
        MarkdownEditorAction.Strike -> wrapSelection(field, selected, "~~", "~~")
        MarkdownEditorAction.CodeBlock -> wrapSelection(field, selected, "```\n", "\n```")
        MarkdownEditorAction.Image -> field
    }
}

private fun insertPrefix(field: TextFieldValue, prefix: String): TextFieldValue {
    val start = field.selection.start.coerceIn(0, field.text.length)
    val end = field.selection.end.coerceIn(0, field.text.length)
    val before = field.text.substring(0, start)
    val selected = field.text.substring(start, end)
    val after = field.text.substring(end)
    val replacement = prefix + selected
    val newText = before + replacement + after
    val cursor = (before.length + replacement.length).coerceAtMost(newText.length)
    return field.copy(text = newText, selection = TextRange(cursor))
}

private fun wrapSelection(
    field: TextFieldValue,
    selected: String,
    prefix: String,
    suffix: String
): TextFieldValue {
    val start = field.selection.start.coerceIn(0, field.text.length)
    val end = field.selection.end.coerceIn(0, field.text.length)
    val before = field.text.substring(0, start)
    val after = field.text.substring(end)
    val replacementCore = selected.ifBlank { "text" }
    val replacement = prefix + replacementCore + suffix
    val newText = before + replacement + after
    val coreStart = before.length + prefix.length
    val coreEnd = coreStart + replacementCore.length
    return field.copy(text = newText, selection = TextRange(coreStart, coreEnd))
}

@Composable
internal fun NoteImagesSection(
    noteImagePaths: List<String>,
    noteImageBitmaps: Map<String, Bitmap>,
    imageActionsEnabled: Boolean,
    disabledReason: String?,
    onAddImageClick: () -> Unit,
    onPreviewImage: (String) -> Unit,
    onRemoveImage: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.section_photos),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = disabledReason ?: stringResource(R.string.note_images_embedded_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onAddImageClick,
                    enabled = imageActionsEnabled
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.note_add_image))
                }
            }

            if (noteImagePaths.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(noteImagePaths) { fileName ->
                        val bitmap = noteImageBitmaps[fileName]
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clickable { onPreviewImage(fileName) },
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }

                            IconButton(
                                onClick = { onRemoveImage(fileName) },
                                enabled = imageActionsEnabled,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .background(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f),
                                        RoundedCornerShape(bottomStart = 8.dp)
                                    )
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.note_remove_image),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = imageActionsEnabled) { onAddImageClick() },
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.note_add_image),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
