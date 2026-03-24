package takagi.ru.monica.notes.domain

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import takagi.ru.monica.data.SecureItem
import takagi.ru.monica.data.model.NoteData

data class DecodedNoteContent(
    val content: String,
    val tags: List<String> = emptyList(),
    val isMarkdown: Boolean = false
)

data class NoteInlineToken(
    val text: String? = null,
    val imageId: String? = null
)

object NoteContentCodec {
    private const val INLINE_IMAGE_SCHEME = "monica-image://"
    private val INLINE_IMAGE_REGEX = Regex("!\\[[^\\]]*\\]\\(monica-image://([^\\)\\s]+)\\)")
    private val INLINE_IMAGE_MARKDOWN_REGEX = Regex("!\\[([^\\]]*)\\]\\(monica-image://([^\\)\\s]+)\\)")

    fun decode(itemData: String, fallbackNotes: String): DecodedNoteContent {
        return decodeLegacySafe(
            itemData = itemData,
            fallbackNotes = fallbackNotes
        ) ?: DecodedNoteContent(content = fallbackNotes.ifBlank { itemData })
    }

    fun decodeFromItem(item: SecureItem): DecodedNoteContent = decode(
        itemData = item.itemData,
        fallbackNotes = item.notes
    )

    fun encode(
        content: String,
        tags: List<String> = emptyList(),
        isMarkdown: Boolean = false
    ): Pair<String, String> {
        val sanitizedTags = tags
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        val noteData = NoteData(
            content = content,
            tags = sanitizedTags,
            isMarkdown = isMarkdown
        )
        return Json.encodeToString(noteData) to content
    }

    fun resolveTitle(title: String?, content: String): String {
        if (!title.isNullOrBlank()) return title.trim()
        return if (content.length > 20) {
            content.take(20) + "..."
        } else {
            content.ifEmpty { "New Note" }
        }
    }

    fun decodeImagePaths(imagePaths: String): List<String> {
        if (imagePaths.isBlank()) return emptyList()
        return runCatching { Json.decodeFromString<List<String>>(imagePaths) }
            .getOrElse {
                if (imagePaths.startsWith("[")) emptyList() else listOf(imagePaths)
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    fun encodeImagePaths(paths: List<String>): String {
        val normalized = paths
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        return Json.encodeToString(normalized)
    }

    fun hasAnyImagePath(imagePaths: String): Boolean = decodeImagePaths(imagePaths).isNotEmpty()

    fun buildInlineImageMarkdown(imageId: String): String {
        val normalized = imageId.trim()
        if (normalized.isEmpty()) return ""
        return "![]($INLINE_IMAGE_SCHEME$normalized)"
    }

    fun extractInlineImageIds(content: String): List<String> {
        if (content.isBlank()) return emptyList()
        return INLINE_IMAGE_REGEX.findAll(content)
            .mapNotNull { it.groupValues.getOrNull(1) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .toList()
    }

    fun appendInlineImageRefs(content: String, imageIds: List<String>): String {
        val normalized = imageIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
        if (normalized.isEmpty()) return content
        val existing = extractInlineImageIds(content).toSet()
        val missing = normalized.filterNot { existing.contains(it) }
        if (missing.isEmpty()) return content

        val builder = StringBuilder(content.trimEnd())
        if (builder.isNotEmpty()) {
            builder.append("\n\n")
        }
        missing.forEachIndexed { index, imageId ->
            if (index > 0) builder.append('\n')
            builder.append(buildInlineImageMarkdown(imageId))
        }
        return builder.toString()
    }

    fun removeInlineImageRef(content: String, imageId: String): String {
        val normalized = imageId.trim()
        if (normalized.isEmpty()) return content
        val escapedId = Regex.escape(normalized)
        val refRegex = Regex("!\\[[^\\]]*\\]\\(monica-image://$escapedId\\)\\s*")
        return content
            .replace(refRegex, "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trimEnd()
    }

    fun toExternalReadableContent(content: String): String {
        if (content.isBlank()) return content
        return content.replace(INLINE_IMAGE_MARKDOWN_REGEX) { match ->
            val alt = match.groupValues.getOrNull(1).orEmpty().trim().ifBlank { "Image" }
            val id = match.groupValues.getOrNull(2).orEmpty().trim()
            if (id.isBlank()) {
                "[$alt]"
            } else {
                "[$alt:$id]"
            }
        }
    }

    fun markdownToPlainText(content: String): String {
        return content
            .replace(Regex("```[\\s\\S]*?```"), " ")
            .replace(Regex("`([^`]*)`"), "$1")
            .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
            .replace(Regex("^(#{1,6})\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^[\\-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
            .replace(Regex(">[ \\t]?", RegexOption.MULTILINE), "")
            .replace(Regex("[*_~]"), "")
            .replace(Regex("\\n{2,}"), "\n")
            .trim()
    }

    fun tokenizeInlineContent(content: String): List<NoteInlineToken> {
        if (content.isBlank()) return listOf(NoteInlineToken(text = ""))

        val tokens = mutableListOf<NoteInlineToken>()
        var cursor = 0
        INLINE_IMAGE_REGEX.findAll(content).forEach { match ->
            val start = match.range.first
            val endExclusive = match.range.last + 1
            if (start > cursor) {
                val textSegment = content.substring(cursor, start)
                if (textSegment.isNotEmpty()) {
                    tokens += NoteInlineToken(text = textSegment)
                }
            }
            val imageId = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (imageId.isNotEmpty()) {
                tokens += NoteInlineToken(imageId = imageId)
            }
            cursor = endExclusive
        }

        if (cursor < content.length) {
            val tail = content.substring(cursor)
            if (tail.isNotEmpty()) {
                tokens += NoteInlineToken(text = tail)
            }
        }

        return if (tokens.isEmpty()) listOf(NoteInlineToken(text = content)) else tokens
    }

    fun toPlainPreview(content: String, isMarkdown: Boolean): String {
        if (!isMarkdown) return content
        return markdownToPlainText(content)
    }

    private fun decodeLegacySafe(itemData: String, fallbackNotes: String): DecodedNoteContent? {
        val raw = itemData.trim()
        if (raw.isEmpty()) {
            return DecodedNoteContent(content = fallbackNotes)
        }

        if (raw.startsWith("{")) {
            runCatching { Json.decodeFromString<NoteData>(raw) }
                .getOrNull()
                ?.let { noteData ->
                    return DecodedNoteContent(
                        content = noteData.content.ifBlank { fallbackNotes },
                        tags = noteData.tags
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .distinct(),
                        isMarkdown = noteData.isMarkdown
                    )
                }
        }

        if (!raw.startsWith("{") && !raw.startsWith("\"")) {
            return DecodedNoteContent(content = raw)
        }

        return runCatching {
            when (val parsed = JSONTokener(raw).nextValue()) {
                is JSONObject -> parsed.toDecodedNoteContent(fallbackNotes)
                is String -> DecodedNoteContent(content = parsed.ifBlank { fallbackNotes })
                else -> null
            }
        }.getOrNull()
    }

    private fun JSONObject.toDecodedNoteContent(fallbackNotes: String): DecodedNoteContent {
        val content = optString("content")
            .takeIf { it.isNotBlank() }
            ?: fallbackNotes

        return DecodedNoteContent(
            content = content,
            tags = optJSONArray("tags").toStringList(),
            isMarkdown = optBoolean("isMarkdown", false)
        )
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty() && value !in this) {
                    add(value)
                }
            }
        }
    }
}
