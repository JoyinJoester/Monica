package takagi.ru.monica.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.widget.TextViewCompat
import takagi.ru.monica.util.MarkdownUtils
import kotlin.math.roundToInt

@Composable
fun MarkdownSpannedText(
    markdown: String,
    imageBitmaps: Map<String, Bitmap>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.bodyLarge
    val textColor = colors.onSurface.toArgb()
    val linkColor = colors.primary.toArgb()
    val contentKey = remember(markdown, imageBitmaps.entries.toList()) {
        buildString {
            append(markdown.hashCode())
            append('|')
            imageBitmaps.entries
                .sortedBy { it.key }
                .forEach { (id, bitmap) ->
                    append(id)
                    append(':')
                    append(bitmap.width)
                    append('x')
                    append(bitmap.height)
                    append(';')
                }
        }
    }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextView(context).apply {
                linksClickable = true
                isClickable = true
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(0f, 1.25f)
            }
        },
        update = { textView ->
            TextViewCompat.setTextAppearance(
                textView,
                android.R.style.TextAppearance_Material_Body1
            )
            textView.textSize = textStyle.fontSize.value
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)

            val oldKey = textView.tag as? String
            if (oldKey == contentKey) return@AndroidView
            textView.tag = contentKey

            val imageGetter = Html.ImageGetter { source ->
                val bitmap = resolveInlineImageBitmap(source, imageBitmaps) ?: return@ImageGetter null
                val drawable = BitmapDrawable(textView.resources, bitmap)
                val viewWidth = textView.width
                    .takeIf { it > 0 }
                    ?: textView.resources.displayMetrics.widthPixels
                val availableWidth = (viewWidth - textView.paddingLeft - textView.paddingRight)
                    .coerceAtLeast(1)
                val width = bitmap.width.coerceAtMost(availableWidth)
                val ratio = if (bitmap.width > 0) {
                    width.toFloat() / bitmap.width.toFloat()
                } else {
                    1f
                }
                val height = (bitmap.height * ratio).roundToInt().coerceAtLeast(1)
                drawable.setBounds(0, 0, width, height)
                drawable
            }

            textView.text = MarkdownUtils.markdownToSpanned(
                markdown = markdown,
                imageGetter = imageGetter
            )
        }
    )
}

private fun resolveInlineImageBitmap(
    source: String?,
    imageBitmaps: Map<String, Bitmap>
): Bitmap? {
    val rawSource = source?.trim().orEmpty()
    if (rawSource.isEmpty()) return null

    val normalizedId = rawSource
        .removePrefix("monica-image://")
        .let { Uri.decode(it).trim() }

    if (normalizedId.isEmpty()) return null

    return imageBitmaps[normalizedId]
        ?: imageBitmaps[normalizedId.substringAfterLast('/')]
}
