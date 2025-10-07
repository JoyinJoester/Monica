package takagi.ru.monica.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import takagi.ru.monica.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MarkdownToolbar(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val actions = listOf(
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_heading1),
            transform = { insertHeading(it, level = 1) }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_heading2),
            transform = { insertHeading(it, level = 2) }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_heading3),
            transform = { insertHeading(it, level = 3) }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_bold),
            transform = { wrapSelection(it, "**") }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_italic),
            transform = { wrapSelection(it, "*") }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_quote),
            transform = { insertBlockPrefix(it, "> ") }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_list_unordered),
            transform = { insertBlockPrefix(it, "- ") }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_list_ordered),
            transform = { insertBlockPrefix(it, "1. ") }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_list_task),
            transform = { insertBlockPrefix(it, "- [ ] ") }
        ),
        MarkdownToolbarAction(
            label = stringResource(R.string.markdown_action_code),
            transform = { wrapSelection(it, "`") }
        )
    )

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEach { action ->
            AssistChip(
                onClick = { onValueChange(action.transform(value)) },
                label = { Text(action.label) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}

private data class MarkdownToolbarAction(
    val label: String,
    val transform: (TextFieldValue) -> TextFieldValue
)

private fun insertHeading(value: TextFieldValue, level: Int): TextFieldValue {
    val linePrefix = "#".repeat(level).takeIf { it.isNotEmpty() }?.plus(" ") ?: ""
    if (linePrefix.isEmpty()) return value

    val text = value.text
    val selectionStart = value.selection.start
    val selectionEnd = value.selection.end
    val lineStart = text.lastIndexOf('\n', selectionStart - 1).let { if (it == -1) 0 else it + 1 }

    val alreadyPrefixed = text.startsWith(linePrefix, lineStart)
    if (alreadyPrefixed) {
        return value
    }

    val newText = text.substring(0, lineStart) + linePrefix + text.substring(lineStart)
    val shift = linePrefix.length
    return value.copy(
        text = newText,
        selection = TextRange(selectionStart + shift, selectionEnd + shift)
    )
}

private fun insertBlockPrefix(value: TextFieldValue, prefix: String): TextFieldValue {
    val text = value.text
    val selectionStart = value.selection.start
    val selectionEnd = value.selection.end

    if (selectionStart == selectionEnd) {
        val needsNewLine = selectionStart > 0 && text[selectionStart - 1] != '\n'
        val insertion = buildString {
            if (needsNewLine) append('\n')
            append(prefix)
        }

        val newText = text.replaceRange(selectionStart, selectionEnd, insertion)
        val cursor = selectionStart + insertion.length
        return value.copy(
            text = newText,
            selection = TextRange(cursor, cursor)
        )
    }

    val rangeStart = text.lastIndexOf('\n', selectionStart - 1).let { if (it == -1) 0 else it + 1 }
    val rangeEnd = text.indexOf('\n', selectionEnd).let { if (it == -1) text.length else it }
    val originalSegment = text.substring(rangeStart, rangeEnd)
    val updatedSegment = originalSegment
        .split('\n')
        .joinToString("\n") { line ->
            if (line.startsWith(prefix)) line else prefix + line
        }

    val newText = text.replaceRange(rangeStart, rangeEnd, updatedSegment)
    val newSelectionStart = rangeStart
    val newSelectionEnd = rangeStart + updatedSegment.length
    return value.copy(
        text = newText,
        selection = TextRange(newSelectionStart, newSelectionEnd)
    )
}

private fun wrapSelection(value: TextFieldValue, wrapperStart: String, wrapperEnd: String = wrapperStart): TextFieldValue {
    val start = value.selection.start
    val end = value.selection.end
    val text = value.text

    return if (start != end) {
        val selectedText = text.substring(start, end)
        val newSegment = wrapperStart + selectedText + wrapperEnd
        val newText = text.replaceRange(start, end, newSegment)
        value.copy(
            text = newText,
            selection = TextRange(start + wrapperStart.length, start + wrapperStart.length + selectedText.length)
        )
    } else {
        val placeholder = wrapperStart + wrapperEnd
        val newText = text.replaceRange(start, end, placeholder)
        val cursor = start + wrapperStart.length
        value.copy(
            text = newText,
            selection = TextRange(cursor, cursor)
        )
    }
}