package takagi.ru.monica.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
        private const val ALGORITHM = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGORITHM = "AES"
        
        // 简单的加密密钥（实际应用中应该使用更安全的密钥管理方案）
        private val ENCRYPTION_KEY = "MonicaSecureKey1".toByteArray()
        private val IV = "MonicaSecureIV16".toByteArray()
    }
    
    private val imageDirectory: File by lazy {
        File(context.filesDir, IMAGE_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    /**
     * 保存图片（从Uri）
     * @param uri 图片Uri
     * @return 保存后的文件名，失败返回null
     */
    suspend fun saveImageFromUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return@withContext null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap != null) {
                saveImage(bitmap)
            } else {
                null
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
            
            // 将Bitmap转换为字节数组
            val byteArray = bitmapToByteArray(bitmap)
            
            // 加密并保存
            val encryptedData = encrypt(byteArray)
            FileOutputStream(file).use { fos ->
                fos.write(encryptedData)
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
    suspend fun loadImage(fileName: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val file = File(imageDirectory, fileName)
            if (!file.exists()) {
                return@withContext null
            }
            
            // 读取加密数据
            val encryptedData = file.readBytes()
            
            // 解密
            val decryptedData = decrypt(encryptedData)
            
            // 转换为Bitmap
            BitmapFactory.decodeByteArray(decryptedData, 0, decryptedData.size)
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
