package takagi.ru.monica.autofill.ui

import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap

/**
 * 应用图标LRU缓存
 * 
 * 使用LRU策略缓存应用图标,避免重复加载
 * 缓存大小限制为50个图标
 */
object AppIconCache {
    private const val MAX_CACHE_SIZE = 50
    
    private val cache = LruCache<String, ImageBitmap>(MAX_CACHE_SIZE)
    private val missingIcons = mutableSetOf<String>()
    
    /**
     * 获取应用图标
     * 
     * @param packageName 应用包名
     * @param packageManager PackageManager实例
     * @return ImageBitmap或null
     */
    fun getIcon(packageName: String, packageManager: PackageManager): ImageBitmap? {
        android.util.Log.d("AppIconCache", "getIcon: packageName = $packageName")
        
        // 先从内存缓存获取
        val cached = cache.get(packageName)
        if (cached != null) {
            android.util.Log.d("AppIconCache", "getIcon: returning cached icon for $packageName")
            return cached
        }
        
        // 检查是否在"缺失"缓存中
        if (packageName in missingIcons) {
            android.util.Log.d("AppIconCache", "getIcon: returning cached null (missing) for $packageName")
            return null
        }
        
        // 缓存未命中,加载图标
        val icon = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            android.util.Log.d("AppIconCache", "getIcon: successfully loaded icon for $packageName")
            // 使用固定尺寸转换，避免某些 Drawable (如 AdaptiveIconDrawable) 崩溃
            val size = 96 // 合适的图标尺寸
            drawable.toBitmap(size, size).asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) {
            android.util.Log.w("AppIconCache", "getIcon: app not found: $packageName", e)
            null
        } catch (e: OutOfMemoryError) {
            android.util.Log.e("AppIconCache", "getIcon: OOM loading icon for $packageName", e)
            null
        } catch (e: Exception) {
            android.util.Log.e("AppIconCache", "getIcon: error loading icon for $packageName", e)
            null
        }
        
        // 存入对应的缓存
        if (icon != null) {
            cache.put(packageName, icon)
            android.util.Log.d("AppIconCache", "getIcon: cached icon for $packageName")
        } else {
            missingIcons.add(packageName)
            android.util.Log.d("AppIconCache", "getIcon: marked $packageName as missing")
        }
        return icon
    }
    
    /**
     * 清除缓存
     */
    fun clear() {
        cache.evictAll()
        missingIcons.clear()
    }
    
    /**
     * 获取缓存的键集合
     */
    private fun getCachedKeys(): Set<String> {
        val keys = mutableSetOf<String>()
        val snapshot = cache.snapshot()
        keys.addAll(snapshot.keys)
        return keys
    }
    
    /**
     * 获取缓存统计信息
     */
    fun getCacheStats(): CacheStats {
        return CacheStats(
            size = cache.size(),
            maxSize = cache.maxSize(),
            hitCount = cache.hitCount(),
            missCount = cache.missCount(),
            evictionCount = cache.evictionCount()
        )
    }
    
    /**
     * 缓存统计信息
     */
    data class CacheStats(
        val size: Int,
        val maxSize: Int,
        val hitCount: Int,
        val missCount: Int,
        val evictionCount: Int
    ) {
        val hitRate: Float
            get() = if (hitCount + missCount > 0) {
                hitCount.toFloat() / (hitCount + missCount)
            } else {
                0f
            }
    }
}

/**
 * Composable函数:使用缓存获取应用图标
 * 
 * @param packageName 应用包名
 * @return ImageBitmap或null
 */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val packageManager = context.packageManager
    
    return remember(packageName) {
        AppIconCache.getIcon(packageName, packageManager)
    }
}
