package takagi.ru.monica.ui.components

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import takagi.ru.monica.util.MarkdownUtils
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

@Composable
fun MarkdownPreviewText(
    markdown: String,
    imageBitmaps: Map<String, Bitmap>,
    onInlineImageClick: ((String) -> Unit)? = null,
    onOpenExternalLink: ((String) -> Unit)? = null,
    enableCodeHighlight: Boolean = true,
    modifier: Modifier = Modifier
) {
    val callbackState = rememberUpdatedState(onInlineImageClick)
    val linkCallbackState = rememberUpdatedState(onOpenExternalLink)
    val inlineImageIdsInContent = remember(markdown) {
        extractInlineImageIdsFromMarkdown(markdown)
    }
    val imageSignature = imageBitmaps.entries
        .sortedBy { it.key }
        .joinToString(separator = "|") { (key, bitmap) ->
            "$key:${bitmap.width}x${bitmap.height}"
        }

    val colorScheme = MaterialTheme.colorScheme
    val colors = MarkdownCssColors(
        background = colorScheme.surface.toArgb(),
        text = colorScheme.onSurface.toArgb(),
        link = colorScheme.primary.toArgb(),
        codeBackground = colorScheme.surfaceVariant.toArgb(),
        blockQuoteBorder = colorScheme.outlineVariant.toArgb(),
        tableBorder = colorScheme.outlineVariant.toArgb()
    )

    BoxWithConstraints(modifier = modifier) {
        var encodedInlineImages by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
        LaunchedEffect(imageSignature, inlineImageIdsInContent) {
            encodedInlineImages = withContext(Dispatchers.Default) {
                imageBitmaps
                    .filterKeys { inlineImageIdsInContent.contains(it) }
                    .mapValues { (_, bitmap) -> bitmapToDataUrlForPreview(bitmap) }
            }
        }
        val htmlDocument = remember(markdown, encodedInlineImages, colors, enableCodeHighlight) {
            buildStyledMarkdownHtml(
                markdown = markdown,
                encodedInlineImages = encodedInlineImages,
                colors = colors,
                enableCodeHighlight = enableCodeHighlight
            )
        }

        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    overScrollMode = WebView.OVER_SCROLL_NEVER
                    settings.apply {
                        // JS is only needed for optional code highlighting.
                        javaScriptEnabled = enableCodeHighlight
                        domStorageEnabled = false
                        allowFileAccess = false
                        allowContentAccess = false
                        builtInZoomControls = false
                        displayZoomControls = false
                        setSupportZoom(false)
                    }
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val raw = request?.url?.toString().orEmpty()
                            if (raw.startsWith("monica-image://")) {
                                val imageId = normalizeInlineImageId(raw)
                                if (imageId.isNotEmpty()) {
                                    callbackState.value?.invoke(imageId)
                                }
                                return true
                            }
                            if (raw.startsWith("about:blank")) return false
                            if (raw.isBlank()) return true

                            val handledByCallback = runCatching {
                                linkCallbackState.value?.invoke(raw)
                            }.isSuccess && linkCallbackState.value != null

                            if (!handledByCallback) {
                                runCatching {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(raw)).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            }
                            return true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.let { updateWebViewContentHeight(it, retries = 3) }
                        }
                    }
                }
            },
            update = { webView ->
                val previous = webView.tag?.toString()
                if (previous != htmlDocument) {
                    webView.tag = htmlDocument
                    webView.loadDataWithBaseURL(
                        null,
                        htmlDocument,
                        "text/html",
                        "utf-8",
                        null
                    )
                } else {
                    updateWebViewContentHeight(webView, retries = 1)
                }
            }
        )
    }
}

private data class MarkdownCssColors(
    val background: Int,
    val text: Int,
    val link: Int,
    val codeBackground: Int,
    val blockQuoteBorder: Int,
    val tableBorder: Int
)

private fun buildStyledMarkdownHtml(
    markdown: String,
    encodedInlineImages: Map<String, String>,
    colors: MarkdownCssColors,
    enableCodeHighlight: Boolean
): String {
    val coreHtml = MarkdownUtils.markdownToHtml(markdown)
    val transformed = coreHtml.replace(
        Regex("""<img\s+([^>]*?)src="([^"]+)"([^>]*)>""", RegexOption.IGNORE_CASE)
    ) { match ->
        val before = match.groupValues[1]
        val src = match.groupValues[2]
        val after = match.groupValues[3]
        if (src.startsWith("monica-image://")) {
            val imageId = normalizeInlineImageId(src)
            val resolvedSrc = encodedInlineImages[imageId] ?: src
            """<a href="$src" class="monica-inline-image"><img $before src="$resolvedSrc" $after></a>"""
        } else {
            match.value
        }
    }
    val codeHighlightScript = if (enableCodeHighlight) {
        """
            <script>
              (function() {
                function escapeHtml(text) {
                  return text
                    .replace(/&/g, '&amp;')
                    .replace(/</g, '&lt;')
                    .replace(/>/g, '&gt;');
                }

                function getLanguage(codeElement) {
                  var className = codeElement.className || '';
                  var match = className.match(/language-([a-zA-Z0-9_+-]+)/);
                  return match ? match[1].toLowerCase() : '';
                }

                function keywordSet(lang) {
                  var map = {
                    kotlin: ['fun','val','var','class','object','interface','when','if','else','for','while','return','null','true','false','is','in','as','try','catch','finally','throw','import','package','private','public','internal','data','sealed','enum','companion','override','open','abstract','suspend'],
                    java: ['class','interface','enum','public','private','protected','static','final','void','int','long','float','double','boolean','char','if','else','switch','case','default','for','while','do','break','continue','return','new','null','true','false','try','catch','finally','throw','throws','extends','implements','import','package'],
                    javascript: ['function','const','let','var','class','if','else','switch','case','default','for','while','do','break','continue','return','new','null','true','false','try','catch','finally','throw','import','export','from','await','async','this'],
                    typescript: ['function','const','let','var','class','interface','type','enum','if','else','switch','case','default','for','while','do','break','continue','return','new','null','true','false','try','catch','finally','throw','import','export','from','await','async','this','extends','implements','public','private','protected','readonly'],
                    python: ['def','class','if','elif','else','for','while','break','continue','return','import','from','as','try','except','finally','raise','with','yield','lambda','None','True','False','pass','global','nonlocal','async','await'],
                    go: ['func','var','const','type','struct','interface','if','else','switch','case','default','for','range','break','continue','return','go','defer','chan','map','package','import','nil','true','false'],
                    rust: ['fn','let','mut','struct','enum','impl','trait','if','else','match','for','while','loop','break','continue','return','pub','use','mod','crate','self','super','None','Some','true','false','async','await'],
                    sql: ['select','from','where','group','by','order','insert','into','values','update','set','delete','create','table','alter','drop','join','left','right','inner','outer','on','and','or','not','null','as','limit','offset'],
                    bash: ['if','then','else','fi','for','in','do','done','while','case','esac','function','return','break','continue','export','local','readonly']
                  };
                  return map[lang] || [];
                }

                function highlightCode(text, lang) {
                  var source = escapeHtml(text || '');
                  var placeholders = [];
                  function stash(value) {
                    var token = '__TOK_' + placeholders.length + '__';
                    placeholders.push(value);
                    return token;
                  }

                  source = source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|#[^\n]*/g, function(m) {
                    return stash('<span class="tok-comment">' + m + '</span>');
                  });

                  source = source.replace(/"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|`(?:\\.|[^`\\])*`/g, function(m) {
                    return stash('<span class="tok-string">' + m + '</span>');
                  });

                  source = source.replace(/\b\d+(?:\.\d+)?\b/g, function(m) {
                    return '<span class="tok-number">' + m + '</span>';
                  });

                  source = source.replace(/\b(true|false)\b/gi, function(m) {
                    return '<span class="tok-boolean">' + m + '</span>';
                  });

                  source = source.replace(/\bnull\b/gi, function(m) {
                    return '<span class="tok-null">' + m + '</span>';
                  });

                  var keywords = keywordSet(lang);
                  if (keywords.length > 0) {
                    var kwRegex = new RegExp('\\b(' + keywords.join('|') + ')\\b', 'gi');
                    source = source.replace(kwRegex, function(m) {
                      return '<span class="tok-keyword">' + m + '</span>';
                    });
                  }

                  source = source.replace(/__TOK_(\d+)__/g, function(_, i) {
                    return placeholders[Number(i)] || '';
                  });

                  return source
                    .split('\n')
                    .map(function(line) { return '<span class="line">' + (line || '&nbsp;') + '</span>'; })
                    .join('');
                }

                function applyHighlight() {
                  var blocks = document.querySelectorAll('pre code');
                  blocks.forEach(function(block) {
                    var lang = getLanguage(block);
                    var text = block.textContent || '';
                    block.innerHTML = highlightCode(text, lang);
                  });
                }

                if (document.readyState === 'loading') {
                  document.addEventListener('DOMContentLoaded', applyHighlight);
                } else {
                  applyHighlight();
                }
              })();
            </script>
        """.trimIndent()
    } else {
        ""
    }

    return """
        <html>
          <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no" />
            <style>
              html, body { margin: 0; padding: 0; background: ${colors.background.toCssHex()}; color: ${colors.text.toCssHex()}; }
              body {
                font-size: 16px;
                line-height: 1.6;
                word-break: break-word;
                overflow-wrap: anywhere;
                -webkit-text-size-adjust: 100%;
              }
              p { margin: 0 0 12px 0; }
              h1, h2, h3, h4, h5, h6 { margin: 14px 0 8px 0; line-height: 1.35; }
              ul, ol { margin: 8px 0 12px 20px; padding: 0; }
              li { margin: 4px 0; }
              ul.contains-task-list {
                list-style: none;
                margin-left: 0;
                padding-left: 0;
              }
              li.task-list-item {
                list-style: none;
                display: flex;
                align-items: flex-start;
                gap: 8px;
              }
              li.task-list-item > input[type="checkbox"] {
                margin-top: 4px;
                width: 16px;
                height: 16px;
                accent-color: ${colors.link.toCssHex()};
                pointer-events: none;
                flex: 0 0 auto;
              }
              li.task-list-item p {
                margin: 0;
              }
              blockquote {
                margin: 10px 0;
                padding: 8px 12px;
                border-left: 3px solid ${colors.blockQuoteBorder.toCssHex()};
                background: ${colors.codeBackground.toCssHex()};
                border-radius: 8px;
              }
              code {
                font-family: monospace;
                background: ${colors.codeBackground.toCssHex()};
                padding: 2px 6px;
                border-radius: 6px;
              }
              pre {
                margin: 10px 0 14px 0;
                padding: 12px;
                border-radius: 10px;
                background: ${colors.codeBackground.toCssHex()};
                overflow-x: auto;
                white-space: pre;
              }
              pre code {
                background: transparent;
                padding: 0;
                border-radius: 0;
                color: inherit;
              }
              .tok-keyword { color: #c678dd; font-weight: 600; }
              .tok-string { color: #98c379; }
              .tok-number { color: #d19a66; }
              .tok-comment { color: #7f848e; font-style: italic; }
              .tok-operator { color: #56b6c2; }
              .tok-boolean { color: #e06c75; font-weight: 600; }
              .tok-null { color: #e06c75; font-weight: 600; }
              .tok-punctuation { color: #abb2bf; }
              .tok-type { color: #61afef; }
              .tok-function { color: #e5c07b; }
              pre code .line {
                display: block;
              }
              a { color: ${colors.link.toCssHex()}; text-decoration: none; }
              hr { border: 0; border-top: 1px solid ${colors.tableBorder.toCssHex()}; margin: 16px 0; }
              .table-wrap {
                width: 100%;
                overflow-x: auto;
                -webkit-overflow-scrolling: touch;
                margin: 10px 0 14px 0;
              }
              table {
                border-collapse: collapse;
                min-width: max-content;
              }
              th, td {
                border: 1px solid ${colors.tableBorder.toCssHex()};
                padding: 6px 10px;
                text-align: left;
                vertical-align: top;
              }
              img {
                display: block;
                max-width: 100%;
                height: auto;
                border-radius: 10px;
                margin: 8px 0;
              }
            </style>
            $codeHighlightScript
          </head>
          <body>${wrapTables(transformed)}</body>
        </html>
    """.trimIndent()
}

private fun wrapTables(html: String): String {
    return html.replace(
        Regex("""<table[\s\S]*?</table>""", RegexOption.IGNORE_CASE)
    ) { match ->
        """<div class="table-wrap">${match.value}</div>"""
    }
}

private fun updateWebViewContentHeight(webView: WebView) {
    updateWebViewContentHeight(webView = webView, retries = 2)
}

private fun updateWebViewContentHeight(
    webView: WebView,
    retries: Int
) {
    webView.post {
        webView.evaluateJavascript(
            "(function(){return Math.max(document.body.scrollHeight, document.documentElement.scrollHeight);})();"
        ) { value ->
            val cssHeight = value?.trim('"')?.toFloatOrNull() ?: return@evaluateJavascript
            val px = (cssHeight * webView.resources.displayMetrics.density).toInt()
            val minPx = (120 * webView.resources.displayMetrics.density).toInt()
            val targetHeight = px.coerceAtLeast(minPx)
            val params = webView.layoutParams
            if (params != null && kotlin.math.abs(params.height - targetHeight) > 2) {
                params.height = max(targetHeight, minPx)
                webView.layoutParams = params
            }

            if (retries > 0) {
                webView.postDelayed(
                    { updateWebViewContentHeight(webView, retries - 1) },
                    48L
                )
            }
        }
    }
}

private fun bitmapToDataUrlForPreview(bitmap: Bitmap): String {
    val preparedBitmap = prepareBitmapForPreview(bitmap)
    val hasAlpha = preparedBitmap.hasAlpha()
    val format = if (hasAlpha) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
    val quality = if (hasAlpha) 95 else 84
    val bytes = ByteArrayOutputStream().use { output ->
        preparedBitmap.compress(format, quality, output)
        output.toByteArray()
    }
    val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
    return if (hasAlpha) {
        "data:image/png;base64,$base64"
    } else {
        "data:image/jpeg;base64,$base64"
    }
}

private fun prepareBitmapForPreview(bitmap: Bitmap): Bitmap {
    val maxSide = max(bitmap.width, bitmap.height)
    if (maxSide <= MAX_INLINE_IMAGE_SIDE_PX) {
        return bitmap
    }
    val scale = MAX_INLINE_IMAGE_SIDE_PX.toFloat() / maxSide.toFloat()
    val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
    val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
}

private fun normalizeInlineImageId(rawUrl: String): String {
    val id = rawUrl.removePrefix("monica-image://")
    return Uri.decode(id).trim()
}

private fun extractInlineImageIdsFromMarkdown(markdown: String): Set<String> {
    if (markdown.isBlank()) return emptySet()
    val regex = Regex("""!\[[^\]]*]\(monica-image://([^)]+)\)""")
    return regex.findAll(markdown)
        .map { it.groupValues.getOrNull(1).orEmpty() }
        .map { Uri.decode(it).trim() }
        .filter { it.isNotEmpty() }
        .toSet()
}

private fun Int.toCssHex(): String = String.format("#%06X", 0xFFFFFF and this)

private const val MAX_INLINE_IMAGE_SIDE_PX = 1280
