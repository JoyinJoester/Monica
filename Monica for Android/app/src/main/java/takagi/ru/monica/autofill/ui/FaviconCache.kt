package takagi.ru.monica.autofill.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Favicon cache for fetching and storing website icons.
 */
object FaviconCache {
    private const val TAG = "FaviconCache"
    private const val CACHE_DIR_NAME = "favicons"
    private const val MAX_MEMORY_CACHE_SIZE = 50 // Keep up to 50 icons in memory

    private val memoryCache = LruCache<String, ImageBitmap>(MAX_MEMORY_CACHE_SIZE)

    /**
     * Get icon for domain.
     * This function should be called from a coroutine.
     */
    suspend fun getIcon(context: Context, url: String): ImageBitmap? {
        val domain = getDomainFromUrl(url) ?: return null
        val cacheKey = hashString(domain)

        // 1. Check memory cache
        memoryCache.get(cacheKey)?.let {
            return it
        }

        // 2. Check disk cache
        val cacheDir = File(context.cacheDir, CACHE_DIR_NAME)
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val cacheFile = File(cacheDir, "$cacheKey.png")
        if (cacheFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    val imageBitmap = bitmap.asImageBitmap()
                    memoryCache.put(cacheKey, imageBitmap)
                    return imageBitmap
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading from disk cache", e)
            }
        }

        // 3. Fetch from network
        try {
            // Using Google S2 service for favicons
            // sz=64 requests 64x64 icon
            val faviconUrl = "https://www.google.com/s2/favicons?domain=$domain&sz=64"
            
            return withContext(Dispatchers.IO) {
                val connection = URL(faviconUrl).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    
                    if (bitmap != null) {
                        // Save to disk
                        try {
                            val out = FileOutputStream(cacheFile)
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            out.flush()
                            out.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error saving to disk cache", e)
                        }

                        // Save to memory
                        val imageBitmap = bitmap.asImageBitmap()
                        memoryCache.put(cacheKey, imageBitmap)
                        imageBitmap
                    } else {
                        null
                    }
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching favicon for $domain", e)
            return null
        }
    }

    private fun getDomainFromUrl(url: String): String? {
        if (url.isBlank()) return null
        return try {
            val uri = java.net.URI(url)
            val domain = uri.host
            if (domain != null) {
                return domain.removePrefix("www.")
            }
            // Fallback for URLs without scheme
            val simpleUrl = if (url.startsWith("http")) url else "http://$url"
            java.net.URI(simpleUrl).host?.removePrefix("www.")
        } catch (e: Exception) {
            null
        }
    }

    private fun hashString(input: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Composable to load website favicon.
 *
 * @param url Website URL
 * @param enabled Whether icon fetching is enabled
 */
@Composable
fun rememberFavicon(url: String, enabled: Boolean): ImageBitmap? {
    val context = LocalContext.current
    var icon by remember(url) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(url, enabled) {
        if (!enabled || url.isBlank()) return@LaunchedEffect

        // 首次加载失败时自动做短重试，避免用户必须手动关开开关触发第二次请求。
        val maxAttempts = 3
        repeat(maxAttempts) { index ->
            val loadedIcon = FaviconCache.getIcon(context, url)
            if (loadedIcon != null) {
                icon = loadedIcon
                return@LaunchedEffect
            }
            if (index < maxAttempts - 1) {
                delay((index + 1) * 600L)
            }
        }
    }

    // If enabled is false, we should still return the cached icon if available?
    // User requested: "if icon switch is off, do not load icons anymore. if cached, keep it?"
    // Requirement: "if closed, do not load icon anymore"
    // Requirement 2: "get once and cache" - handled by Cache logic
    // Requirement 3: "if closed, won't clean cache for next time usage" - handled by persistent disk cache
    
    // If enabled is false, we simply don't trigger the fetch. 
    // However, if we already have it in state (from previous render when enabled was true), 
    // allow showing it? Or hide it?
    // "如果关闭了带图标的卡片开关将不会再加载图标" -> If switch is OFF, just don't load.
    // Use case implies: Switch OFF -> Do not show icons on UI.
    // So if !enabled, return null.
    
    if (!enabled) return null
    return icon
}
