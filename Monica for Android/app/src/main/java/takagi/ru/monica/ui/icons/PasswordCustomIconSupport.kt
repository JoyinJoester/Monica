package takagi.ru.monica.ui.icons

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.net.Uri
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.random.Random

const val PASSWORD_ICON_TYPE_NONE = "NONE"
const val PASSWORD_ICON_TYPE_SIMPLE = "SIMPLE_ICON"
const val PASSWORD_ICON_TYPE_UPLOADED = "UPLOADED"

data class SimpleIconOption(
    val slug: String,
    val label: String
)

object SimpleIconCatalog {
    val options: List<SimpleIconOption> = listOf(
        SimpleIconOption("google", "Google"),
        SimpleIconOption("apple", "Apple"),
        SimpleIconOption("microsoft", "Microsoft"),
        SimpleIconOption("amazon", "Amazon"),
        SimpleIconOption("adobe", "Adobe"),
        SimpleIconOption("facebook", "Facebook"),
        SimpleIconOption("instagram", "Instagram"),
        SimpleIconOption("x", "X"),
        SimpleIconOption("reddit", "Reddit"),
        SimpleIconOption("tiktok", "TikTok"),
        SimpleIconOption("youtube", "YouTube"),
        SimpleIconOption("twitch", "Twitch"),
        SimpleIconOption("discord", "Discord"),
        SimpleIconOption("telegram", "Telegram"),
        SimpleIconOption("wechat", "WeChat"),
        SimpleIconOption("whatsapp", "WhatsApp"),
        SimpleIconOption("line", "LINE"),
        SimpleIconOption("github", "GitHub"),
        SimpleIconOption("gitlab", "GitLab"),
        SimpleIconOption("bitbucket", "Bitbucket"),
        SimpleIconOption("stackoverflow", "Stack Overflow"),
        SimpleIconOption("android", "Android"),
        SimpleIconOption("androidstudio", "Android Studio"),
        SimpleIconOption("jetpackcompose", "Jetpack Compose"),
        SimpleIconOption("kotlin", "Kotlin"),
        SimpleIconOption("java", "Java"),
        SimpleIconOption("python", "Python"),
        SimpleIconOption("javascript", "JavaScript"),
        SimpleIconOption("typescript", "TypeScript"),
        SimpleIconOption("react", "React"),
        SimpleIconOption("vuejs", "Vue"),
        SimpleIconOption("nextdotjs", "Next.js"),
        SimpleIconOption("flutter", "Flutter"),
        SimpleIconOption("docker", "Docker"),
        SimpleIconOption("kubernetes", "Kubernetes"),
        SimpleIconOption("nginx", "Nginx"),
        SimpleIconOption("cloudflare", "Cloudflare"),
        SimpleIconOption("digitalocean", "DigitalOcean"),
        SimpleIconOption("vercel", "Vercel"),
        SimpleIconOption("netlify", "Netlify"),
        SimpleIconOption("aws", "AWS"),
        SimpleIconOption("googlecloud", "Google Cloud"),
        SimpleIconOption("firebase", "Firebase"),
        SimpleIconOption("mysql", "MySQL"),
        SimpleIconOption("postgresql", "PostgreSQL"),
        SimpleIconOption("redis", "Redis"),
        SimpleIconOption("mongodb", "MongoDB"),
        SimpleIconOption("sqlite", "SQLite"),
        SimpleIconOption("notion", "Notion"),
        SimpleIconOption("slack", "Slack"),
        SimpleIconOption("zoom", "Zoom"),
        SimpleIconOption("dropbox", "Dropbox"),
        SimpleIconOption("googledrive", "Google Drive"),
        SimpleIconOption("onedrive", "OneDrive"),
        SimpleIconOption("spotify", "Spotify"),
        SimpleIconOption("steam", "Steam"),
        SimpleIconOption("epicgames", "Epic Games"),
        SimpleIconOption("playstation", "PlayStation"),
        SimpleIconOption("nintendo", "Nintendo"),
        SimpleIconOption("bitwarden", "Bitwarden"),
        SimpleIconOption("keepassxc", "KeePassXC"),
        SimpleIconOption("1password", "1Password"),
        SimpleIconOption("paypal", "PayPal"),
        SimpleIconOption("visa", "Visa"),
        SimpleIconOption("mastercard", "Mastercard"),
        SimpleIconOption("alipay", "Alipay"),
        SimpleIconOption("wechatpay", "WeChat Pay")
    ).sortedBy { it.label.lowercase(Locale.ROOT) }

    fun search(query: String): List<SimpleIconOption> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return options
        return options.filter { option ->
            option.label.lowercase(Locale.ROOT).contains(q) ||
                option.slug.lowercase(Locale.ROOT).contains(q)
        }
    }
}

object PasswordCustomIconStore {
    private const val TAG = "PasswordCustomIconStore"
    private const val ICON_DIR = "password_icons"
    private const val MAX_DIMENSION = 384

    fun getIconDir(context: Context): File {
        val dir = File(context.filesDir, ICON_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun resolveIconFile(context: Context, value: String?): File? {
        if (value.isNullOrBlank()) return null
        val safeName = File(value).name
        val file = File(getIconDir(context), safeName)
        return if (file.exists()) file else null
    }

    fun deleteIconFile(context: Context, value: String?): Boolean {
        val file = resolveIconFile(context, value) ?: return false
        return runCatching { file.delete() }.getOrDefault(false)
    }

    suspend fun importAndCompress(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val decoded = decodeBitmapCompat(context, uri)
                ?: throw IllegalStateException("Unsupported image format")

            val finalBitmap = resizeIfNeeded(decoded, MAX_DIMENSION)
            if (finalBitmap !== decoded) decoded.recycle()

            val fileName = "icon_${System.currentTimeMillis()}_${Random.nextInt(1000, 9999)}.png"
            val target = File(getIconDir(context), fileName)
            FileOutputStream(target).use { out ->
                if (!finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    throw IllegalStateException("Failed to compress image")
                }
                out.flush()
            }
            finalBitmap.recycle()
            fileName
        }
    }

    private fun decodeBitmapCompat(context: Context, uri: Uri): Bitmap? {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            runCatching {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val sample = calculateSampleSize(info.size.width, info.size.height, MAX_DIMENSION)
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            }.onFailure {
                Log.w(TAG, "ImageDecoder failed, fallback to BitmapFactory", it)
            }
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimension || currentHeight > maxDimension) {
            sample *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun resizeIfNeeded(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= maxDimension && h <= maxDimension) return bitmap
        val scale = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
        val targetW = (w * scale).toInt().coerceAtLeast(1)
        val targetH = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }
}

private object SimpleIconCache {
    private const val TAG = "SimpleIconCache"
    private const val DISK_DIR = "simple_icons"
    private const val RENDER_SIZE_PX = 256
    private const val SAFE_INSET_PX = 12
    private const val CACHE_VERSION = "v4"
    private val memory = LruCache<String, ImageBitmap>(80)

    suspend fun getIcon(context: Context, slug: String, hexColor: String): ImageBitmap? {
        val normalizedSlug = normalizeSimpleIconSlug(slug)
        if (normalizedSlug.isEmpty()) return null
        val key = "${normalizedSlug}_${hexColor}_$CACHE_VERSION"

        memory.get(key)?.let { return it }

        val diskDir = File(context.cacheDir, DISK_DIR).also { if (!it.exists()) it.mkdirs() }
        val diskFile = File(diskDir, "$key.png")
        if (diskFile.exists()) {
            BitmapFactory.decodeFile(diskFile.absolutePath)?.let { bitmap ->
                val image = bitmap.asImageBitmap()
                memory.put(key, image)
                return image
            }
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val bitmap = fetchSimpleIconBitmap(normalizedSlug, hexColor) ?: return@runCatching null
                FileOutputStream(diskFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.flush()
                }
                val image = bitmap.asImageBitmap()
                memory.put(key, image)
                image
            }.onFailure { error ->
                Log.w(TAG, "Failed to load simple icon: slug=$slug", error)
            }.getOrNull()
        }
    }

    private fun fetchSimpleIconBitmap(normalizedSlug: String, hexColor: String): Bitmap? {
        val endpoints = listOf(
            "https://api.iconify.design/simple-icons:$normalizedSlug.svg?color=$hexColor",
            "https://api.iconify.design/simple-icons/$normalizedSlug.svg?color=$hexColor",
            "https://cdn.simpleicons.org/$normalizedSlug/$hexColor"
        )

        var lastError: Throwable? = null
        for (endpoint in endpoints) {
            val bitmap = runCatching {
                val connection = URL(endpoint).openConnection() as HttpURLConnection
                connection.connectTimeout = 6000
                connection.readTimeout = 6000
                connection.instanceFollowRedirects = true
                try {
                    connection.inputStream.use { stream ->
                        val svg = SVG.getFromInputStream(stream)
                        val innerSize = (RENDER_SIZE_PX - SAFE_INSET_PX * 2).toFloat().coerceAtLeast(1f)
                        svg.setDocumentWidth(innerSize)
                        svg.setDocumentHeight(innerSize)
                        Bitmap.createBitmap(RENDER_SIZE_PX, RENDER_SIZE_PX, Bitmap.Config.ARGB_8888).apply {
                            val canvas = Canvas(this)
                            canvas.translate(SAFE_INSET_PX.toFloat(), SAFE_INSET_PX.toFloat())
                            svg.renderToCanvas(canvas)
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }.onFailure {
                lastError = it
            }.getOrNull()

            if (bitmap != null) {
                return tintBitmap(bitmap, hexColor)
            }
        }

        lastError?.let { throw it }
        return null
    }

    private fun tintBitmap(source: Bitmap, hexColor: String): Bitmap {
        val targetColor = runCatching {
            AndroidColor.parseColor("#$hexColor")
        }.getOrElse {
            return source
        }

        val tinted = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = PorterDuffColorFilter(targetColor, PorterDuff.Mode.SRC_IN)
        }
        val canvas = Canvas(tinted)
        canvas.drawBitmap(source, 0f, 0f, paint)
        if (source != tinted) {
            source.recycle()
        }
        return tinted
    }
}

fun normalizeSimpleIconSlug(input: String): String {
    return input.trim().lowercase(Locale.ROOT).replace(" ", "")
}

@Composable
fun rememberUploadedPasswordIcon(value: String?): ImageBitmap? {
    val context = LocalContext.current
    var icon by remember(value) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(value) {
        if (value.isNullOrBlank()) {
            icon = null
            return@LaunchedEffect
        }
        icon = withContext(Dispatchers.IO) {
            val file = PasswordCustomIconStore.resolveIconFile(context, value) ?: return@withContext null
            BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
        }
    }
    return icon
}

@Composable
fun rememberSimpleIconBitmap(slug: String?, tintColor: Color, enabled: Boolean = true): ImageBitmap? {
    val context = LocalContext.current
    val colorHex = remember(tintColor) {
        String.format("%06X", (0xFFFFFF and tintColor.toArgb()))
    }
    var icon by remember(slug, colorHex, enabled) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(slug, colorHex, enabled) {
        if (!enabled || slug.isNullOrBlank()) {
            icon = null
            return@LaunchedEffect
        }
        icon = SimpleIconCache.getIcon(context, slug, colorHex)
    }
    return icon
}
