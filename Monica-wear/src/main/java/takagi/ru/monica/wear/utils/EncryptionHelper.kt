package takagi.ru.monica.wear.utils

import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Wear 版加密帮助类
 * 只实现解密功能，用于解密从 WebDAV 下载的备份文件
 * Wear 版本不上传备份，因此不需要加密功能
 */
class EncryptionHelper {
    
    companion object {
        private const val TAG = "WearEncryptionHelper"
        
        // AES-GCM 参数
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_SIZE = 256 // AES-256
        private const val GCM_TAG_LENGTH = 128 // 16 bytes
        private const val GCM_IV_LENGTH = 12 // 12 bytes (recommended for GCM)
        
        // PBKDF2 参数
        private const val PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256"
        private const val PBKDF2_ITERATIONS = 100000 // 高强度迭代次数
        private const val SALT_LENGTH = 32 // 32 bytes salt
        
        // 文件头标识 (用于验证文件是否是 Monica 加密文件)
        private const val FILE_MAGIC = "MONICA_ENC_V1"
        
        /**
         * 检测文件是否加密
         */
        fun isEncryptedFile(file: File): Boolean {
            try {
                // 通过扩展名判断
                if (file.name.endsWith(".enc.zip")) {
                    Log.d(TAG, "File ${file.name} is encrypted (by extension)")
                    return true
                }
                
                // 通过文件头判断
                if (file.length() < FILE_MAGIC.length) {
                    Log.d(TAG, "File ${file.name} too small to be encrypted")
                    return false
                }
                
                FileInputStream(file).use { fis ->
                    val header = ByteArray(FILE_MAGIC.length)
                    val bytesRead = fis.read(header)
                    if (bytesRead < FILE_MAGIC.length) {
                        return false
                    }
                    val isEncrypted = String(header, Charsets.UTF_8) == FILE_MAGIC
                    Log.d(TAG, "File ${file.name} encrypted check (by header): $isEncrypted")
                    return isEncrypted
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if file is encrypted", e)
                return false
            }
        }
        
        /**
         * 从密码派生加密密钥
         * @param password 用户密码
         * @param salt 盐值
         * @return 派生的密钥
         */
        private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
            Log.d(TAG, "Deriving key from password...")
            val spec = PBEKeySpec(
                password.toCharArray(),
                salt,
                PBKDF2_ITERATIONS,
                KEY_SIZE
            )
            val factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM)
            val keyBytes = factory.generateSecret(spec).encoded
            return SecretKeySpec(keyBytes, "AES")
        }
        
        /**
         * 解密文件
         * @param inputFile 输入文件 (密文)
         * @param outputFile 输出文件 (明文)
         * @param password 解密密码
         * @return 解密结果
         */
        fun decryptFile(inputFile: File, outputFile: File, password: String): Result<File> {
            return try {
                Log.d(TAG, "Starting decryption: ${inputFile.name} -> ${outputFile.name}")
                
                // 1. 读取文件
                val fileBytes = inputFile.readBytes()
                Log.d(TAG, "Read ${fileBytes.size} bytes from encrypted file")
                
                // 2. 验证文件头
                if (fileBytes.size < FILE_MAGIC.length + SALT_LENGTH + GCM_IV_LENGTH) {
                    Log.e(TAG, "File too small: ${fileBytes.size} bytes")
                    return Result.failure(Exception("加密文件太小，可能已损坏"))
                }
                
                val magicBytes = fileBytes.copyOfRange(0, FILE_MAGIC.length)
                val magic = String(magicBytes, Charsets.UTF_8)
                if (magic != FILE_MAGIC) {
                    Log.e(TAG, "Invalid file magic: $magic (expected: $FILE_MAGIC)")
                    return Result.failure(Exception("无效的加密文件格式"))
                }
                Log.d(TAG, "File header verified")
                
                var offset = FILE_MAGIC.length
                
                // 3. 提取盐值
                val salt = fileBytes.copyOfRange(offset, offset + SALT_LENGTH)
                offset += SALT_LENGTH
                Log.d(TAG, "Extracted salt (${salt.size} bytes)")
                
                // 4. 提取 IV
                val iv = fileBytes.copyOfRange(offset, offset + GCM_IV_LENGTH)
                offset += GCM_IV_LENGTH
                Log.d(TAG, "Extracted IV (${iv.size} bytes)")
                
                // 5. 提取加密数据
                val encryptedData = fileBytes.copyOfRange(offset, fileBytes.size)
                Log.d(TAG, "Extracted encrypted data (${encryptedData.size} bytes)")
                
                // 6. 派生密钥
                val key = deriveKey(password, salt)
                
                // 7. 初始化解密器
                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
                Log.d(TAG, "Cipher initialized")
                
                // 8. 解密数据
                val decryptedBytes = cipher.doFinal(encryptedData)
                Log.d(TAG, "Decrypted ${decryptedBytes.size} bytes")
                
                // 9. 写入输出文件
                outputFile.writeBytes(decryptedBytes)
                Log.d(TAG, "File decrypted successfully: ${outputFile.name} (${outputFile.length()} bytes)")
                
                Result.success(outputFile)
                
            } catch (e: javax.crypto.AEADBadTagException) {
                Log.e(TAG, "Decryption failed - wrong password or corrupted file", e)
                Result.failure(Exception("解密失败: 密码错误或文件已损坏", e))
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed", e)
                Result.failure(Exception("解密失败: ${e.message}", e))
            }
        }
        
        /**
         * 测试密码是否正确
         * @param encryptedFile 加密文件
         * @param password 密码
         * @return 密码是否正确
         */
        fun testPassword(encryptedFile: File, password: String): Boolean {
            Log.d(TAG, "Testing password for file: ${encryptedFile.name}")
            val tempFile = File.createTempFile("monica_pwd_test", ".tmp")
            try {
                val result = decryptFile(encryptedFile, tempFile, password)
                val isCorrect = result.isSuccess
                Log.d(TAG, "Password test result: $isCorrect")
                return isCorrect
            } catch (e: Exception) {
                Log.e(TAG, "Password test failed", e)
                return false
            } finally {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
            }
        }
        
        /**
         * 尝试解密文件（如果是加密文件）
         * 如果不是加密文件，直接返回原文件
         * @param file 输入文件
         * @param password 解密密码（如果文件已加密）
         * @return 解密后的文件或原文件
         */
        fun decryptIfNeeded(file: File, password: String?): Result<File> {
            return try {
                if (!isEncryptedFile(file)) {
                    Log.d(TAG, "File is not encrypted, returning original file")
                    return Result.success(file)
                }
                
                if (password.isNullOrBlank()) {
                    Log.e(TAG, "File is encrypted but no password provided")
                    return Result.failure(Exception("文件已加密，但未提供解密密码"))
                }
                
                // 创建临时文件用于存储解密结果
                val decryptedFile = File.createTempFile(
                    "decrypted_${file.nameWithoutExtension}",
                    ".zip",
                    file.parentFile
                )
                
                Log.d(TAG, "Decrypting file...")
                val result = decryptFile(file, decryptedFile, password)
                
                if (result.isSuccess) {
                    Log.d(TAG, "Decryption successful, returning decrypted file")
                    result
                } else {
                    decryptedFile.delete()
                    Log.e(TAG, "Decryption failed: ${result.exceptionOrNull()?.message}")
                    result
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in decryptIfNeeded", e)
                Result.failure(e)
            }
        }
    }
}
