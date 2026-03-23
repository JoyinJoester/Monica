package takagi.ru.monica.util

import android.content.Context
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.os.UserManager
import android.util.Log
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

    data class PreparedImageImport(
        val bitmap: Bitmap,
        val originalSizeBytes: Long?
    )
    
    companion object {
        private const val TAG = "ImageManager"
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

    private fun createTempPhotoFile(): File {
        val fileName = "temp_photo_${System.currentTimeMillis()}.jpg"
        return File(tempPhotoDirectory, fileName)
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
        return createTempPhotoCaptureRequest().second
    }

    /**
     * 创建拍照请求需要的临时文件和 URI
     */
    fun createTempPhotoCaptureRequest(): Pair<File, Uri> {
        val tempFile = createTempPhotoFile()
        val tempUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            tempFile
        )
        Log.d(TAG, "Created temp photo capture request path=${tempFile.absolutePath} uri=$tempUri")
        return tempFile to tempUri
    }

    /**
     * 准备导入图片：解码预览图并读取原始大小
     */
    suspend fun prepareImageImport(uri: Uri): PreparedImageImport? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "prepareImageImport start uri=$uri")
            val bitmap = decodeSampledBitmapFromUri(uri, MAX_STORED_IMAGE_DIMENSION) ?: return@withContext null
            val originalSizeBytes = queryUriSize(uri)
            Log.d(
                TAG,
                "prepareImageImport success uri=$uri width=${bitmap.width} height=${bitmap.height} originalSizeBytes=$originalSizeBytes"
            )
            PreparedImageImport(
                bitmap = bitmap,
                originalSizeBytes = originalSizeBytes
            )
        } catch (e: Exception) {
            Log.e(TAG, "prepareImageImport failed uri=$uri", e)
            null
        }
    }
    
    /**
     * 保存图片（从Uri）
     * @param uri 图片Uri
     * @return 保存后的文件名，失败返回null
     */
    suspend fun saveImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "saveImageFromUri start uri=$uri")
            val preparedImport = prepareImageImport(uri) ?: return@withContext null
            val bitmap = preparedImport.bitmap
            try {
                saveImage(bitmap).also { fileName ->
                    Log.d(TAG, "saveImageFromUri success uri=$uri fileName=$fileName")
                }
            } finally {
                if (!bitmap.isRecycled) {
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "saveImageFromUri failed uri=$uri", e)
            e.printStackTrace()
            null
        }
    }
    
    /**
     * 保存图片（从Bitmap）
     * @param bitmap 位图
     * @return 保存后的文件名，失败返回null
     */
    suspend fun saveImage(
        bitmap: Bitmap,
        compressionFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
        compressionQuality: Int = 100
    ): String? = withContext(Dispatchers.IO) {
        try {
            // 生成唯一文件名
            val fileName = "${UUID.randomUUID()}.enc"
            val file = File(imageDirectory, fileName)
            val safeQuality = compressionQuality.coerceIn(0, 100)

            val normalizedBitmap = normalizeBitmapForStorage(bitmap, MAX_STORED_IMAGE_DIMENSION)
            try {
                // 将Bitmap转换为字节数组
                val byteArray = bitmapToByteArray(
                    bitmap = normalizedBitmap,
                    compressionFormat = compressionFormat,
                    compressionQuality = safeQuality
                )

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

            Log.d(
                TAG,
                "saveImage success fileName=$fileName path=${file.absolutePath} format=$compressionFormat quality=$safeQuality"
            )
            fileName
        } catch (e: Exception) {
            Log.e(TAG, "saveImage failed", e)
            e.printStackTrace()
            null
        }
    }

    /**
     * 估算按指定压缩参数保存后的图片大小
     */
    suspend fun estimateSavedImageSize(
        bitmap: Bitmap,
        compressionFormat: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        compressionQuality: Int = 80
    ): Long? = withContext(Dispatchers.Default) {
        try {
            val safeQuality = compressionQuality.coerceIn(0, 100)
            val normalizedBitmap = normalizeBitmapForStorage(bitmap, MAX_STORED_IMAGE_DIMENSION)
            try {
                bitmapToByteArray(
                    bitmap = normalizedBitmap,
                    compressionFormat = compressionFormat,
                    compressionQuality = safeQuality
                ).size.toLong()
            } finally {
                if (normalizedBitmap !== bitmap && !normalizedBitmap.isRecycled) {
                    normalizedBitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "estimateSavedImageSize failed format=$compressionFormat quality=$compressionQuality", e)
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
    private fun bitmapToByteArray(
        bitmap: Bitmap,
        compressionFormat: Bitmap.CompressFormat,
        compressionQuality: Int
    ): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(compressionFormat, compressionQuality, stream)
        return stream.toByteArray()
    }

    private fun queryUriSize(uri: Uri): Long? {
        if (uri.scheme == "file") {
            return uri.path
                ?.let(::File)
                ?.takeIf { it.exists() }
                ?.length()
                ?.takeIf { it > 0L }
        }

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it > 0L }?.let { return it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryUriSize via file descriptor failed uri=$uri", e)
        }

        return try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@use null
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex == -1 || cursor.isNull(sizeIndex)) {
                    null
                } else {
                    cursor.getLong(sizeIndex).takeIf { it > 0L }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryUriSize via cursor failed uri=$uri", e)
            null
        }
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
        Log.d(TAG, "decodeSampledBitmapFromUri start uri=$uri maxDimension=$maxDimension")
        if (uri.scheme == "file") {
            return decodeSampledBitmapFromFileUri(uri, maxDimension)
        }
        decodeSampledBitmapFromContentUri(uri, maxDimension)?.let { decoded ->
            return decoded
        }

        Log.w(TAG, "decodeSampledBitmapFromContentUri returned null, falling back to stream decode uri=$uri")
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val firstStream = context.contentResolver.openInputStream(uri)
        if (firstStream == null) {
            Log.w(TAG, "openInputStream returned null for uri=$uri")
            return null
        }
        firstStream.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "decodeSampledBitmapFromUri invalid bounds uri=$uri width=${bounds.outWidth} height=${bounds.outHeight}")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val secondStream = context.contentResolver.openInputStream(uri)
        if (secondStream == null) {
            Log.w(TAG, "second openInputStream returned null for uri=$uri")
            return null
        }
        val decoded = secondStream.use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        }
        Log.d(TAG, "decodeSampledBitmapFromUri result uri=$uri decoded=${decoded != null} sampleSize=${decodeOptions.inSampleSize}")
        return decoded
    }

    private fun decodeSampledBitmapFromContentUri(uri: Uri, maxDimension: Int): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, bounds)
            } ?: run {
                Log.w(TAG, "openFileDescriptor returned null for uri=$uri")
                return null
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "decodeSampledBitmapFromContentUri invalid bounds uri=$uri width=${bounds.outWidth} height=${bounds.outHeight}")
                return null
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                BitmapFactory.decodeFileDescriptor(descriptor.fileDescriptor, null, decodeOptions)
            }
            Log.d(TAG, "decodeSampledBitmapFromContentUri result uri=$uri decoded=${decoded != null} sampleSize=${decodeOptions.inSampleSize}")
            decoded
        } catch (e: Exception) {
            Log.e(TAG, "decodeSampledBitmapFromContentUri failed uri=$uri", e)
            null
        }
    }

    private fun decodeSampledBitmapFromFileUri(uri: Uri, maxDimension: Int): Bitmap? {
        val filePath = uri.path ?: return null
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(filePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            Log.w(TAG, "decodeSampledBitmapFromFileUri invalid bounds uri=$uri width=${bounds.outWidth} height=${bounds.outHeight}")
            return null
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(filePath, decodeOptions)
        Log.d(TAG, "decodeSampledBitmapFromFileUri result uri=$uri decoded=${decoded != null} sampleSize=${decodeOptions.inSampleSize}")
        return decoded
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
