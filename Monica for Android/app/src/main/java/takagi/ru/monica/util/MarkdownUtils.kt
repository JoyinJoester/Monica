package takagi.ru.monica.util

import android.text.Spanned
import androidx.core.text.HtmlCompat
import org.commonmark.parser.Parser
import org.commonmark.renderer.html.HtmlRenderer

/**
 * Utility helpers for converting markdown text into renderable HTML/Spanned content.
 */
object MarkdownUtils {
    private val parser: Parser by lazy { Parser.builder().build() }
    private val renderer: HtmlRenderer by lazy {
        HtmlRenderer.builder()
            .escapeHtml(true)
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
    fun markdownToSpanned(markdown: String): Spanned {
        val html = markdownToHtml(markdown)
        return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }
}
