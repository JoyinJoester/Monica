package takagi.ru.monica.util

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.os.UserManager
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * 图片管理器
 * 负责图片的加密存储、解密读取和删除
 */
class ImageManager(private val context: Context) {
    
    companion object {
        private const val IMAGE_DIR = "secure_images"
        private const val TEMP_IMAGE_DIR = "temp_share"
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGORITHM = "AES"
        private const val MAX_STORED_IMAGE_DIMENSION = 2048
        private const val DEFAULT_LOAD_MAX_DIMENSION = 1600
        
        // 简单的加密密钥（实际应用中应该使用更安全的密钥管理方案）
        private val ENCRYPTION_KEY = "MonicaSecureKey1".toByteArray()
        private val IV = "MonicaSecureIV16".toByteArray()
    }
    
    private val imageDirectory: File by lazy {
        ensureDirectoriesExist(context.filesDir, IMAGE_DIR)
    }
    
    private val tempPhotoDirectory: File by lazy {
        ensureDirectoriesExist(context.cacheDir, TEMP_IMAGE_DIR)
    }
    
    /**
     * 确保目录存在，在访问前检查用户状态
     */
    private fun ensureDirectoriesExist(parentDir: File, childDirName: String): File {
        // 检查用户是否已解锁
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        if (!userManager.isUserUnlocked) {
            android.util.Log.w("ImageManager", "User is not unlocked, deferring directory creation")
        }
        
        return File(parentDir, childDirName).apply {
            if (!exists()) {
                try {
                    if (!mkdirs()) {
                        android.util.Log.e("ImageManager", "Failed to create directory: ${this.path}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ImageManager", "Exception creating directory: ${this.path}", e)
                }
            }
        }
    }
    
    /**
     * 创建临时照片 URI（用于拍照）
     * @return 临时照片的 URI
     */
    fun createTempPhotoUri(): Uri {
        // 生成临时文件
        val fileName = "temp_photo_${System.currentTimeMillis()}.jpg"
        val tempFile = File(tempPhotoDirectory, fileName)
        
        // 使用 FileProvider 创建 URI
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
    }
    
    /**
     * 保存图片（从Uri）
     * @param uri 图片Uri
     * @return 保存后的文件名，失败返回null
     */
    suspend fun saveImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeSampledBitmapFromUri(uri, MAX_STORED_IMAGE_DIMENSION) ?: return@withContext null
            try {
                saveImage(bitmap)
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 保存图片（从Bitmap）
     * @param bitmap 位图
     * @return 保存后的文件名，失败返回null
     */
    suspend fun saveImage(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        try {
            // 生成唯一文件名
            val fileName = "${UUID.randomUUID()}.enc"
            val file = File(imageDirectory, fileName)

            val normalizedBitmap = normalizeBitmapForStorage(bitmap, MAX_STORED_IMAGE_DIMENSION)
            try {
                // 将Bitmap转换为字节数组
                val byteArray = bitmapToByteArray(normalizedBitmap)

                // 加密并保存
                val encryptedData = encrypt(byteArray)
                FileOutputStream(file).use { fos ->
                    fos.write(encryptedData)
                }
            } finally {
                if (normalizedBitmap !== bitmap && !normalizedBitmap.isRecycled) {
                    normalizedBitmap.recycle()
                }
            }

            fileName
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 读取图片
     * @param fileName 文件名
     * @return 解密后的Bitmap，失败返回null
     */
    suspend fun loadImage(
        fileName: String,
        maxDimension: Int = DEFAULT_LOAD_MAX_DIMENSION
    ): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(imageDirectory, fileName)
            if (!file.exists()) {
                return@withContext null
            }
            
            // 读取加密数据
            val encryptedData = file.readBytes()
            
            // 解密
            val decryptedData = decrypt(encryptedData)

            decodeSampledBitmapFromBytes(
                data = decryptedData,
                maxDimension = maxDimension
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 删除图片
     * @param fileName 文件名
     * @return 是否成功删除
     */
    suspend fun deleteImage(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(imageDirectory, fileName)
            file.delete()
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    /**
     * 删除多个图片
     * @param fileNames 文件名列表
     */
    suspend fun deleteImages(fileNames: List<String>) = withContext(Dispatchers.IO) {
        fileNames.forEach { fileName ->
            deleteImage(fileName)
        }
    }
    
    /**
     * 检查图片是否存在
     */
    fun imageExists(fileName: String): Boolean {
        return File(imageDirectory, fileName).exists()
    }
    
    /**
     * 将Bitmap转换为字节数组
     */
    private fun bitmapToByteArray(bitmap: Bitmap): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun normalizeBitmapForStorage(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= maxDimension && height <= maxDimension) {
            return bitmap
        }

        val scale = minOf(
            maxDimension.toFloat() / width.toFloat(),
            maxDimension.toFloat() / height.toFloat()
        )
        val targetWidth = (width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun decodeSampledBitmapFromUri(uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        }
    }

    private fun decodeSampledBitmapFromBytes(data: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeStream(ByteArrayInputStream(data), null, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeStream(ByteArrayInputStream(data), null, decodeOptions)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimension || currentHeight > maxDimension) {
            sampleSize *= 2
            currentWidth /= 2
            currentHeight /= 2
        }
        return sampleSize.coerceAtLeast(1)
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
    
    /**
     * 保存图片到公共相册
     * @param fileName 加密图片的文件名
     * @param displayName 保存到相册的文件名（不包含扩展名）
     * @return 是否成功保存
     */
    suspend fun saveImageToGallery(fileName: String, displayName: String = "Monica_Document"): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先解密加载图片
            val bitmap = loadImage(fileName) ?: return@withContext false
            
            // 生成带时间戳的文件名
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageName = "${displayName}_$timestamp.jpg"
            
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 及以上使用 MediaStore
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, imageName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Monica")
                }
                
                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
                
                uri?.let {
                    context.contentResolver.openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }
                    true
                } ?: false
            } else {
                // Android 9 及以下使用传统方式
                @Suppress("DEPRECATION")
                val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                val monicaDir = File(picturesDir, "Monica")
                
                if (!monicaDir.exists()) {
                    monicaDir.mkdirs()
                }
                
                val imageFile = File(monicaDir, imageName)
                FileOutputStream(imageFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                
                // 通知系统扫描新文件
                @Suppress("DEPRECATION")
                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                mediaScanIntent.data = Uri.fromFile(imageFile)
                context.sendBroadcast(mediaScanIntent)
                
                true
            }
            
            saved
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
