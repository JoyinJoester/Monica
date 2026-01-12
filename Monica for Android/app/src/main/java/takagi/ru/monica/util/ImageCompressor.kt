package takagi.ru.monica.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * 图片压缩工具类
 * 用于压缩备份中的图片以减少备份文件大小
 * 支持检测已压缩图片避免重复压缩导致失真
 */
class ImageCompressor(private val context: Context) {
    
    companion object {
        private const val IMAGE_DIR = "secure_images"
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGORITHM = "AES"
        
        // 与 ImageManager 使用相同的密钥（保持兼容性）
        private val ENCRYPTION_KEY = "MonicaSecureKey1".toByteArray()
        private val IV = "MonicaSecureIV16".toByteArray()
        
        // 压缩标记文件后缀（用于记录已压缩的图片）
        private const val COMPRESSED_MARKER = ".compressed"
        
        // 压缩阈值：大于此大小的图片才需要压缩（500KB）
        private const val COMPRESSION_THRESHOLD_BYTES = 500 * 1024
        
        // 目标最大尺寸（像素）
        private const val MAX_DIMENSION = 1920
        
        // 压缩质量（0-100）
        private const val COMPRESSION_QUALITY = 85
        
        // 最小压缩质量（避免过度压缩导致失真）
        private const val MIN_COMPRESSION_QUALITY = 60
        
        // 目标文件大小（200KB）
        private const val TARGET_FILE_SIZE = 200 * 1024
    }
    
    private val imageDirectory: File by lazy {
        File(context.filesDir, IMAGE_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }
    
    private val compressedMarkersFile: File by lazy {
        File(context.filesDir, "compressed_images.txt")
    }
    
    // 用于记录已压缩的图片
    private val compressedImages: MutableSet<String> by lazy {
        loadCompressedMarkers()
    }
    
    /**
     * 压缩结果数据类
     */
    data class CompressionResult(
        val totalImages: Int,
        val compressedImages: Int,
        val skippedImages: Int,
        val failedImages: Int,
        val savedBytes: Long,
        val errors: List<String>
    ) {
        val success: Boolean get() = failedImages == 0
        
        fun getSummary(): String {
            return buildString {
                append("压缩完成: ")
                append("共 $totalImages 张图片, ")
                append("压缩 $compressedImages 张, ")
                append("跳过 $skippedImages 张")
                if (failedImages > 0) {
                    append(", 失败 $failedImages 张")
                }
                if (savedBytes > 0) {
                    append("\n节省空间: ${formatBytes(savedBytes)}")
                }
            }
        }
        
        private fun formatBytes(bytes: Long): String {
            return when {
                bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
                bytes >= 1024 -> String.format("%.2f KB", bytes / 1024.0)
                else -> "$bytes B"
            }
        }
    }
    
    /**
     * 压缩进度回调
     */
    interface CompressionProgressCallback {
        fun onProgress(current: Int, total: Int, currentFileName: String)
        fun onComplete(result: CompressionResult)
    }
    
    /**
     * 加载已压缩图片的标记
     */
    private fun loadCompressedMarkers(): MutableSet<String> {
        return try {
            if (compressedMarkersFile.exists()) {
                compressedMarkersFile.readLines().toMutableSet()
            } else {
                mutableSetOf()
            }
        } catch (e: Exception) {
            mutableSetOf()
        }
    }
    
    /**
     * 保存已压缩图片的标记
     */
    private fun saveCompressedMarkers() {
        try {
            compressedMarkersFile.writeText(compressedImages.joinToString("\n"))
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Failed to save compressed markers", e)
        }
    }
    
    /**
     * 检查图片是否已被压缩
     */
    fun isImageCompressed(fileName: String): Boolean {
        return compressedImages.contains(fileName)
    }
    
    /**
     * 标记图片为已压缩
     */
    private fun markAsCompressed(fileName: String) {
        compressedImages.add(fileName)
        saveCompressedMarkers()
    }
    
    /**
     * 检查图片是否需要压缩
     * @param fileName 加密图片的文件名
     * @return true 如果需要压缩
     */
    suspend fun needsCompression(fileName: String): Boolean = withContext(Dispatchers.IO) {
        // 如果已经压缩过，不需要再压缩
        if (isImageCompressed(fileName)) {
            return@withContext false
        }
        
        val file = File(imageDirectory, fileName)
        if (!file.exists()) {
            return@withContext false
        }
        
        // 文件大于阈值才需要压缩
        file.length() > COMPRESSION_THRESHOLD_BYTES
    }
    
    /**
     * 压缩单个加密图片
     * @param fileName 加密图片的文件名
     * @return 压缩后节省的字节数，如果失败或跳过则返回0
     */
    suspend fun compressImage(fileName: String): Long = withContext(Dispatchers.IO) {
        try {
            // 检查是否已压缩
            if (isImageCompressed(fileName)) {
                android.util.Log.d("ImageCompressor", "Image already compressed: $fileName")
                return@withContext 0L
            }
            
            val file = File(imageDirectory, fileName)
            if (!file.exists()) {
                android.util.Log.w("ImageCompressor", "Image file not found: $fileName")
                return@withContext 0L
            }
            
            val originalSize = file.length()
            
            // 如果文件较小，不需要压缩
            if (originalSize <= COMPRESSION_THRESHOLD_BYTES) {
                markAsCompressed(fileName)
                return@withContext 0L
            }
            
            // 读取并解密图片
            val encryptedData = file.readBytes()
            val decryptedData = decrypt(encryptedData)
            
            // 解码为Bitmap
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size, options)
            
            // 计算采样率
            val sampleSize = calculateInSampleSize(options, MAX_DIMENSION, MAX_DIMENSION)
            
            // 重新解码并压缩
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            
            var bitmap = BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size, decodeOptions)
                ?: return@withContext 0L
            
            // 如果图片仍然太大，进一步缩放
            bitmap = resizeBitmapIfNeeded(bitmap)
            
            // 压缩图片到目标大小
            val compressedData = compressBitmapToTargetSize(bitmap, TARGET_FILE_SIZE)
            bitmap.recycle()
            
            // 加密压缩后的数据
            val newEncryptedData = encrypt(compressedData)
            
            // 只有当压缩后确实更小时才保存
            if (newEncryptedData.size < originalSize) {
                // 写入压缩后的数据
                FileOutputStream(file).use { fos ->
                    fos.write(newEncryptedData)
                }
                
                markAsCompressed(fileName)
                
                val savedBytes = originalSize - newEncryptedData.size
                android.util.Log.d("ImageCompressor", 
                    "Compressed $fileName: ${originalSize / 1024}KB -> ${newEncryptedData.size / 1024}KB, saved ${savedBytes / 1024}KB")
                return@withContext savedBytes
            } else {
                // 压缩后反而更大，标记为已压缩但不修改文件
                markAsCompressed(fileName)
                android.util.Log.d("ImageCompressor", 
                    "Compression would increase size for $fileName, skipping")
                return@withContext 0L
            }
        } catch (e: Exception) {
            android.util.Log.e("ImageCompressor", "Failed to compress image: $fileName", e)
            return@withContext -1L // 返回-1表示失败
        }
    }
    
    /**
     * 压缩所有图片
     * @param callback 进度回调
     * @return 压缩结果
     */
    suspend fun compressAllImages(callback: CompressionProgressCallback? = null): CompressionResult = withContext(Dispatchers.IO) {
        val imageFiles = imageDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".enc")
        } ?: emptyArray()
        
        val totalImages = imageFiles.size
        var compressedCount = 0
        var skippedCount = 0
        var failedCount = 0
        var totalSavedBytes = 0L
        val errors = mutableListOf<String>()
        
        imageFiles.forEachIndexed { index, file ->
            val fileName = file.name
            
            callback?.onProgress(index + 1, totalImages, fileName)
            
            val savedBytes = compressImage(fileName)
            
            when {
                savedBytes > 0 -> {
                    compressedCount++
                    totalSavedBytes += savedBytes
                }
                savedBytes == 0L -> {
                    skippedCount++
                }
                else -> {
                    failedCount++
                    errors.add("压缩失败: $fileName")
                }
            }
        }
        
        val result = CompressionResult(
            totalImages = totalImages,
            compressedImages = compressedCount,
            skippedImages = skippedCount,
            failedImages = failedCount,
            savedBytes = totalSavedBytes,
            errors = errors
        )
        
        callback?.onComplete(result)
        
        result
    }
    
    /**
     * 获取图片统计信息
     */
    suspend fun getImageStats(): ImageStats = withContext(Dispatchers.IO) {
        val imageFiles = imageDirectory.listFiles { file ->
            file.isFile && file.name.endsWith(".enc")
        } ?: emptyArray()
        
        var totalSize = 0L
        var compressedCount = 0
        var uncompressedCount = 0
        var largeImageCount = 0
        
        imageFiles.forEach { file ->
            totalSize += file.length()
            if (isImageCompressed(file.name)) {
                compressedCount++
            } else {
                uncompressedCount++
                if (file.length() > COMPRESSION_THRESHOLD_BYTES) {
                    largeImageCount++
                }
            }
        }
        
        ImageStats(
            totalImages = imageFiles.size,
            totalSize = totalSize,
            compressedCount = compressedCount,
            uncompressedCount = uncompressedCount,
            largeImageCount = largeImageCount
        )
    }
    
    data class ImageStats(
        val totalImages: Int,
        val totalSize: Long,
        val compressedCount: Int,
        val uncompressedCount: Int,
        val largeImageCount: Int
    ) {
        fun formatTotalSize(): String {
            return when {
                totalSize >= 1024 * 1024 -> String.format("%.2f MB", totalSize / (1024.0 * 1024.0))
                totalSize >= 1024 -> String.format("%.2f KB", totalSize / 1024.0)
                else -> "$totalSize B"
            }
        }
    }
    
    /**
     * 计算图片采样率
     */
    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * 如果需要，调整Bitmap大小
     */
    private fun resizeBitmapIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return bitmap
        }
        
        val scale = minOf(
            MAX_DIMENSION.toFloat() / width,
            MAX_DIMENSION.toFloat() / height
        )
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        if (resized != bitmap) {
            bitmap.recycle()
        }
        return resized
    }
    
    /**
     * 压缩Bitmap到目标大小
     */
    private fun compressBitmapToTargetSize(bitmap: Bitmap, targetSize: Int): ByteArray {
        var quality = COMPRESSION_QUALITY
        var compressed: ByteArray
        
        do {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            compressed = stream.toByteArray()
            
            if (compressed.size <= targetSize || quality <= MIN_COMPRESSION_QUALITY) {
                break
            }
            
            quality -= 5
        } while (quality > MIN_COMPRESSION_QUALITY)
        
        return compressed
    }
    
    /**
     * 加密数据
     */
    private fun encrypt(data: ByteArray): ByteArray {
        val secretKey: SecretKey = SecretKeySpec(ENCRYPTION_KEY, KEY_ALGORITHM)
        val cipher = Cipher.getInstance(ALGORITHM)
        val ivSpec = IvParameterSpec(IV)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(data)
    }
    
    /**
     * 解密数据
     */
    private fun decrypt(encryptedData: ByteArray): ByteArray {
        val secretKey: SecretKey = SecretKeySpec(ENCRYPTION_KEY, KEY_ALGORITHM)
        val cipher = Cipher.getInstance(ALGORITHM)
        val ivSpec = IvParameterSpec(IV)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
        return cipher.doFinal(encryptedData)
    }
}
