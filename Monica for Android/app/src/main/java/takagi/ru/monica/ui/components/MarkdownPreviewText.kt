package takagi.ru.monica.ui.components

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import takagi.ru.monica.data.NoteCodeBlockCollapseMode

@Composable
fun MarkdownPreviewText(
    markdown: String,
    imageBitmaps: Map<String, Bitmap>,
    onInlineImageClick: ((String) -> Unit)? = null,
    onTaskItemToggle: ((lineIndex: Int, checked: Boolean) -> Unit)? = null,
    onOpenExternalLink: ((String) -> Unit)? = null,
    codeBlockCollapseMode: NoteCodeBlockCollapseMode = NoteCodeBlockCollapseMode.COMPACT,
    enableCodeHighlight: Boolean = true,
    renderImages: Boolean = true,
    maxElements: Int? = null,
    modifier: Modifier = Modifier
) {
    if (enableCodeHighlight) {
        // Reserved for future syntax-highlight mode in Compose renderer.
    }
    val linkCallback = rememberUpdatedState(onOpenExternalLink)
    val imageCallback = rememberUpdatedState(onInlineImageClick)
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val elements = remember(markdown) { parseMarkdownLines(markdown) }
    val visibleElements = remember(elements, maxElements) {
        maxElements?.let { elements.take(it) } ?: elements
    }
    val expandedCodeBlocks = remember(markdown, maxElements) { mutableStateMapOf<Int, Boolean>() }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visibleElements.forEachIndexed { index, element ->
            when (element) {
                is MarkdownElement.Heading -> {
                    Text(
                        text = element.text,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = (28 - (2 * element.level)).coerceAtLeast(16).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                is MarkdownElement.CheckboxItem -> {
                    val toggleTask: (() -> Unit)? = onTaskItemToggle?.let { callback ->
                        { callback(element.lineIndex, !element.checked) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = element.checked,
                            onCheckedChange = { checked ->
                                onTaskItemToggle?.invoke(element.lineIndex, checked)
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .let { base ->
                                    if (toggleTask != null) {
                                        base.clickable { toggleTask() }
                                    } else {
                                        base
                                    }
                                }
                        ) {
                            MarkdownInlineText(
                                text = element.text,
                                onOpenExternalLink = { raw ->
                                    openLink(raw, linkCallback.value, context)
                                }
                            )
                        }
                    }
                }

                is MarkdownElement.ListItem -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(text = "• ")
                        MarkdownInlineText(
                            text = element.text,
                            modifier = Modifier.weight(1f),
                            onOpenExternalLink = { raw ->
                                openLink(raw, linkCallback.value, context)
                            }
                        )
                    }
                }

                is MarkdownElement.Quote -> {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .width(4.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        MarkdownInlineText(
                            text = element.text,
                            modifier = Modifier.weight(1f),
                            onOpenExternalLink = { raw ->
                                openLink(raw, linkCallback.value, context)
                            }
                        )
                    }
                }

                is MarkdownElement.CodeBlock -> {
                    val lineCount = remember(element.code) { element.code.lineSequence().count() }
                    val shouldCollapse = lineCount > collapseLineThreshold(codeBlockCollapseMode) ||
                        element.code.length > collapseCharThreshold(codeBlockCollapseMode)
                    val expanded = expandedCodeBlocks[index] == true
                    val previewLines = previewLines(codeBlockCollapseMode)
                    val visibleCode = if (shouldCollapse && !expanded) {
                        element.code.lineSequence().take(previewLines).joinToString("\n")
                    } else {
                        element.code
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "代码块 ${lineCount}行",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(
                                        text = "复制",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable {
                                            clipboardManager.setText(AnnotatedString(element.code))
                                        }
                                    )
                                    if (shouldCollapse) {
                                        Text(
                                            text = if (expanded) "收起" else "展开",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.clickable {
                                                expandedCodeBlocks[index] = !expanded
                                            }
                                        )
                                    }
                                }
                            }

                            Text(
                                text = visibleCode,
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )

                            if (shouldCollapse && !expanded) {
                                Text(
                                    text = "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                is MarkdownElement.Image -> {
                    if (!renderImages) {
                        return@forEachIndexed
                    }
                    val resolvedId = normalizeInlineImageId(element.source)
                    val bitmap = if (resolvedId.isNotBlank()) imageBitmaps[resolvedId] else null
                    val clickModifier = if (resolvedId.isNotBlank() && imageCallback.value != null) {
                        Modifier.clickable { imageCallback.value?.invoke(resolvedId) }
                    } else {
                        Modifier
                    }

                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(2.dp)
                                .then(clickModifier)
                        )
                    } else {
                        Text(
                            text = element.source,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                                .then(clickModifier)
                        )
                    }
                }

                is MarkdownElement.HorizontalRule -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                is MarkdownElement.NormalText -> {
                    MarkdownInlineText(
                        text = element.text,
                        onOpenExternalLink = { raw ->
                            openLink(raw, linkCallback.value, context)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownInlineText(
    text: String,
    modifier: Modifier = Modifier,
    onOpenExternalLink: (String) -> Unit
) {
    val annotated = remember(text) { buildInlineAnnotatedText(text) }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        modifier = modifier
    ) { offset ->
        annotated.getStringAnnotations(URL_TAG, offset, offset)
            .firstOrNull()
            ?.let { annotation -> onOpenExternalLink(annotation.item) }
    }
}

private fun buildInlineAnnotatedText(input: String): AnnotatedString {
    val baseStyle = SpanStyle(color = Color.Unspecified)
    val urlRegex = Regex("(https?://[\\w-]+(\\.[\\w-]+)+[\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])")

    val builder = buildAnnotatedString {
        appendStyledTextWithoutMarkers(input, baseStyle)
    }

    val mutable = AnnotatedString.Builder(builder)
    urlRegex.findAll(builder.text).forEach { match ->
        mutable.addStyle(
            style = SpanStyle(
                color = Color(0xFF7DB8FF),
                textDecoration = TextDecoration.Underline
            ),
            start = match.range.first,
            end = match.range.last + 1
        )
        mutable.addStringAnnotation(
            tag = URL_TAG,
            annotation = match.value,
            start = match.range.first,
            end = match.range.last + 1
        )
    }
    return mutable.toAnnotatedString()
}

private fun AnnotatedString.Builder.appendStyledTextWithoutMarkers(
    text: String,
    baseStyle: SpanStyle
) {
    var index = 0
    var bold = false
    var italic = false
    var strike = false
    var underline = false
    var highlight = false
    var inlineCode = false

    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                bold = !bold
                index += 2
            }
            text.startsWith("~~", index) -> {
                strike = !strike
                index += 2
            }
            text.startsWith("==", index) -> {
                highlight = !highlight
                index += 2
            }
            text.startsWith("__", index) -> {
                underline = !underline
                index += 2
            }
            text.startsWith("_", index) -> {
                underline = !underline
                index += 1
            }
            text.startsWith("`", index) -> {
                inlineCode = !inlineCode
                index += 1
            }
            text.startsWith("*", index) -> {
                italic = !italic
                index += 1
            }
            else -> {
                val current = text[index].toString()
                val style = baseStyle.merge(
                    SpanStyle(
                        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
                        textDecoration = buildTextDecoration(strike, underline),
                        background = if (highlight) Color(0x33E6E45B) else Color.Unspecified,
                        fontFamily = if (inlineCode) FontFamily.Monospace else null
                    )
                )
                withStyle(style) {
                    append(current)
                }
                index += 1
            }
        }
    }
}

private fun buildTextDecoration(strike: Boolean, underline: Boolean): TextDecoration? {
    return when {
        strike && underline -> TextDecoration.combine(
            listOf(TextDecoration.LineThrough, TextDecoration.Underline)
        )
        strike -> TextDecoration.LineThrough
        underline -> TextDecoration.Underline
        else -> null
    }
}

private fun collapseLineThreshold(mode: NoteCodeBlockCollapseMode): Int = when (mode) {
    NoteCodeBlockCollapseMode.COMPACT -> 6
    NoteCodeBlockCollapseMode.BALANCED -> 10
    NoteCodeBlockCollapseMode.EXPANDED -> 15
}

private fun collapseCharThreshold(mode: NoteCodeBlockCollapseMode): Int = when (mode) {
    NoteCodeBlockCollapseMode.COMPACT -> 260
    NoteCodeBlockCollapseMode.BALANCED -> 420
    NoteCodeBlockCollapseMode.EXPANDED -> 700
}

private fun previewLines(mode: NoteCodeBlockCollapseMode): Int = when (mode) {
    NoteCodeBlockCollapseMode.COMPACT -> 4
    NoteCodeBlockCollapseMode.BALANCED -> 6
    NoteCodeBlockCollapseMode.EXPANDED -> 8
}

private fun parseMarkdownLines(markdown: String): List<MarkdownElement> {
    if (markdown.isBlank()) return emptyList()
    val lines = markdown.lines()
    val output = mutableListOf<MarkdownElement>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.startsWith("```")) {
            val blockBuilder = StringBuilder()
            var cursor = index + 1
            while (cursor < lines.size && !lines[cursor].trim().startsWith("```")) {
                if (blockBuilder.isNotEmpty()) blockBuilder.append('\n')
                blockBuilder.append(lines[cursor])
                cursor++
            }
            if (cursor >= lines.size) {
                // Keep tolerant behavior for incomplete fences.
                output += MarkdownElement.NormalText(line)
                index++
                continue
            }
            output += MarkdownElement.CodeBlock(blockBuilder.toString())
            index = cursor + 1
            continue
        }

        if (trimmed.matches(Regex("^\\[[ xX]]( .*)?")) || trimmed.matches(Regex("^[\\-*+]\\s+\\[[ xX]]\\s+.+"))) {
            val checked = trimmed.startsWith("[x]", true)
            val text = trimmed
                .replace(Regex("^[\\-*+]\\s+"), "")
                .replace(Regex("^\\[[ xX]] ?"), "")
            output += MarkdownElement.CheckboxItem(
                text = text,
                checked = checked,
                lineIndex = index
            )
            index++
            continue
        }

        if (trimmed.startsWith("#")) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
            val textValue = trimmed.drop(level).trim()
            output += MarkdownElement.Heading(level, textValue)
            index++
            continue
        }

        if (trimmed == "---") {
            output += MarkdownElement.HorizontalRule
            index++
            continue
        }

        if (trimmed.startsWith(">")) {
            val quoteText = trimmed.dropWhile { it == '>' }.trim()
            output += MarkdownElement.Quote(quoteText)
            index++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("+ ") || trimmed.startsWith("* ")) {
            output += MarkdownElement.ListItem(trimmed.drop(2).trim())
            index++
            continue
        }

        val extractedImageSource = extractMarkdownImageSource(trimmed)
        if (extractedImageSource != null) {
            output += MarkdownElement.Image(extractedImageSource)
            index++
            continue
        }

        output += MarkdownElement.NormalText(line)
        index++
    }

    return output
}

private fun extractMarkdownImageSource(line: String): String? {
    val standard = Regex("^!\\[[^\\]]*]\\(([^)]+)\\)$")
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    if (!standard.isNullOrBlank()) return standard

    val easyStyle = Regex("^!\\(([^)]+)\\)$")
        .find(line)
        ?.groupValues
        ?.getOrNull(1)
        ?.trim()
    return easyStyle?.takeIf { it.isNotBlank() }
}

private fun normalizeInlineImageId(rawUrl: String): String {
    val id = rawUrl.removePrefix("monica-image://")
    return Uri.decode(id).trim()
}

private fun openLink(
    raw: String,
    callback: ((String) -> Unit)?,
    context: android.content.Context
) {
    if (raw.isBlank()) return
    if (callback != null) {
        callback(raw)
        return
    }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

private sealed interface MarkdownElement {
    data class Heading(val level: Int, val text: String) : MarkdownElement
    data class CheckboxItem(val text: String, val checked: Boolean, val lineIndex: Int) : MarkdownElement
    data class ListItem(val text: String) : MarkdownElement
    data class Quote(val text: String) : MarkdownElement
    data class CodeBlock(val code: String) : MarkdownElement
    data class Image(val source: String) : MarkdownElement
    data object HorizontalRule : MarkdownElement
    data class NormalText(val text: String) : MarkdownElement
}

private const val URL_TAG = "URL"
