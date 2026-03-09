package takagi.ru.monica.util

import android.text.Html
import android.text.Spanned
import androidx.core.text.HtmlCompat
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.TaskListItemsExtension
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Utility helpers for converting markdown text into renderable HTML/Spanned content.
 */
object MarkdownUtils {
    private val extensions by lazy {
        listOf(
            TablesExtension.create(),
            StrikethroughExtension.create(),
            TaskListItemsExtension.create()
        )
    }

    private val parser: Parser by lazy {
        Parser.builder()
            .extensions(extensions)
            .build()
    }
    private val renderer: HtmlRenderer by lazy {
        HtmlRenderer.builder()
            .extensions(extensions)
            .escapeHtml(true)
            .softbreak("<br />")
            .build()
    }

    /**
     * Convert markdown text to raw HTML string.
     */
    fun markdownToHtml(markdown: String): String {
        if (markdown.isBlank()) return ""
        val document = parser.parse(markdown)
        return renderer.render(document)
    }

    /**
     * Convert markdown text to a Spanned instance for display inside TextView/AndroidView.
     */
    fun markdownToSpanned(
        markdown: String,
        imageGetter: Html.ImageGetter? = null
    ): Spanned {
        val html = markdownToHtml(markdown)
        return HtmlCompat.fromHtml(
            html,
            HtmlCompat.FROM_HTML_MODE_LEGACY,
            imageGetter,
            null
        )
    }
}
