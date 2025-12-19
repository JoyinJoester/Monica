package takagi.ru.monica.utils

import android.util.Base64
import android.util.Log
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 加密帮助类
 * 使用 AES-256-GCM 算法加密 WebDAV 备份数据
 */
class EncryptionHelper {
    
    companion object {
        private const val TAG = "EncryptionHelper"

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
                    return true
                }
                
                // 通过文件头判断
                if (file.length() < FILE_MAGIC.length) {
                    return false
                }
                
                FileInputStream(file).use { fis ->
                    val header = ByteArray(FILE_MAGIC.length)
                    val bytesRead = fis.read(header)
                    if (bytesRead < FILE_MAGIC.length) {
                        return false
                    }
                    return String(header, Charsets.UTF_8) == FILE_MAGIC
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking if file is encrypted", e)
                return false
            }
        }
        
        /**
         * 从密码派生加密密钥
         */
        private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
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
         * 生成随机盐值
         */
        private fun generateSalt(): ByteArray {
            val salt = ByteArray(SALT_LENGTH)
            SecureRandom().nextBytes(salt)
            return salt
        }
        
        /**
         * 生成随机 IV
         */
        private fun generateIV(): ByteArray {
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            return iv
        }
        
        /**
         * 加密文件
         */
        fun encryptFile(inputFile: File, outputFile: File, password: String): Result<File> {
            return try {
                Log.d(TAG, "Starting encryption: ${inputFile.name} -> ${outputFile.name}")

                // 1. 生成盐值和 IV
                val salt = generateSalt()
                val iv = generateIV()
                
                // 2. 派生密钥
                val key = deriveKey(password, salt)
                
                // 3. 初始化加密器
                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)
                
                // 4. 读取输入文件
                val inputBytes = inputFile.readBytes()
                
                // 5. 加密数据
                val encryptedBytes = cipher.doFinal(inputBytes)
                
                // 6. 写入输出文件
                // 文件格式: [MAGIC][SALT][IV][ENCRYPTED_DATA]
                FileOutputStream(outputFile).use { fos ->
                    fos.write(FILE_MAGIC.toByteArray(Charsets.UTF_8))
                    fos.write(salt)
                    fos.write(iv)
                    fos.write(encryptedBytes)
                }
                
                Log.d(TAG, "File encrypted successfully: ${outputFile.name}")
                Result.success(outputFile)
                
            } catch (e: Exception) {
                Log.e(TAG, "Encryption failed", e)
                Result.failure(Exception("加密失败: ${e.message}", e))
            }
        }
        
        /**
         * 解密文件
         */
        fun decryptFile(inputFile: File, outputFile: File, password: String): Result<File> {
            return try {
                Log.d(TAG, "Starting decryption: ${inputFile.name} -> ${outputFile.name}")

                // 1. 读取文件
                val fileBytes = inputFile.readBytes()
                
                // 2. 验证文件大小
                if (fileBytes.size < FILE_MAGIC.length + SALT_LENGTH + GCM_IV_LENGTH) {
                    return Result.failure(Exception("加密文件太小，可能已损坏"))
                }

                // 3. 验证文件头
                val magicBytes = fileBytes.copyOfRange(0, FILE_MAGIC.length)
                val magic = String(magicBytes, Charsets.UTF_8)
                if (magic != FILE_MAGIC) {
                    return Result.failure(Exception("无效的加密文件格式"))
                }
                
                var offset = FILE_MAGIC.length
                
                // 4. 提取盐值
                val salt = fileBytes.copyOfRange(offset, offset + SALT_LENGTH)
                offset += SALT_LENGTH
                
                // 5. 提取 IV
                val iv = fileBytes.copyOfRange(offset, offset + GCM_IV_LENGTH)
                offset += GCM_IV_LENGTH
                
                // 6. 提取加密数据
                val encryptedData = fileBytes.copyOfRange(offset, fileBytes.size)
                
                // 7. 派生密钥
                val key = deriveKey(password, salt)
                
                // 8. 初始化解密器
                val cipher = Cipher.getInstance(ALGORITHM)
                val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
                cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
                
                // 9. 解密数据
                val decryptedBytes = cipher.doFinal(encryptedData)
                
                // 10. 写入输出文件
                outputFile.writeBytes(decryptedBytes)
                
                Log.d(TAG, "File decrypted successfully: ${outputFile.name}")
                Result.success(outputFile)
                
            } catch (e: javax.crypto.AEADBadTagException) {
                Log.e(TAG, "Decryption failed - wrong password", e)
                Result.failure(Exception("解密失败: 密码错误或文件已损坏", e))
            } catch (e: Exception) {
                Log.e(TAG, "Decryption failed", e)
                Result.failure(Exception("解密失败: ${e.message}", e))
            }
        }
        
        /**
         * 测试密码是否正确
         */
        fun testPassword(encryptedFile: File, password: String): Boolean {
            val tempFile = File.createTempFile("monica_pwd_test", ".tmp")
            try {
                val result = decryptFile(encryptedFile, tempFile, password)
                return result.isSuccess
            } catch (e: Exception) {
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
         */
        fun decryptIfNeeded(file: File, password: String?): Result<File> {
            return try {
                if (!isEncryptedFile(file)) {
                    return Result.success(file)
                }
                
                if (password.isNullOrBlank()) {
                    return Result.failure(Exception("文件已加密，但未提供解密密码"))
                }
                
                // 创建临时文件用于存储解密结果
                val decryptedFile = File.createTempFile(
                    "decrypted_${file.nameWithoutExtension}",
                    ".zip",
                    file.parentFile
                )
                
                val result = decryptFile(file, decryptedFile, password)
                
                if (result.isSuccess) {
                    result
                } else {
                    decryptedFile.delete()
                    result
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
