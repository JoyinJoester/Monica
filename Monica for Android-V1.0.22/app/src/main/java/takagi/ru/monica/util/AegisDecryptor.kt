package takagi.ru.monica.util

import com.lambdaworks.crypto.SCrypt
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Aegis加密文件解密器
 * 支持解密Aegis备份文件中的加密数据
 */
class AegisDecryptor {
    
    companion object {
        private const val SCRYPT_N = 32768 // CPU/内存成本参数
        private const val SCRYPT_R = 8     // 块大小
        private const val SCRYPT_P = 1     // 并行化参数
        private const val SCRYPT_KEY_LENGTH = 32 // 密钥长度（字节）
        
        private const val GCM_TAG_LENGTH = 128 // GCM标签长度（位）
    }
    
    /**
     * 解密Aegis加密文件中的主密钥
     * @param password 用户密码
     * @param salt 盐值（十六进制字符串）
     * @param keyParams 密钥参数（包含nonce和tag）
     * @param encryptedKey 加密的主密钥（十六进制字符串）
     * @return 解密后的主密钥（字节数组）
     */
    @Throws(Exception::class)
    fun decryptMasterKey(
        password: String,
        salt: String,
        keyParams: KeyParams,
        encryptedKey: String
    ): ByteArray {
        // 1. 使用scrypt算法派生密钥
        val saltBytes = hexStringToByteArray(salt)
        val derivedKey = SCrypt.scrypt(
            password.toByteArray(Charsets.UTF_8),
            saltBytes,
            SCRYPT_N,
            SCRYPT_R,
            SCRYPT_P,
            SCRYPT_KEY_LENGTH
        )
        
        // 2. 使用派生的密钥解密主密钥
        // 注意：在Aegis格式中，key和tag是分开存储的，需要合并
        val encryptedKeyBytes = hexStringToByteArray(encryptedKey)
        val tagBytes = hexStringToByteArray(keyParams.tag)
        val nonceBytes = hexStringToByteArray(keyParams.nonce)
        
        // GCM模式需要将tag附加到密文末尾
        val cipherTextWithTag = encryptedKeyBytes + tagBytes
        
        // 使用AES-GCM解密
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonceBytes)
        val secretKey = SecretKeySpec(derivedKey, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(cipherTextWithTag)
    }
    
    /**
     * 使用指定密钥解密数据（十六进制格式）
     * @param key 密钥（字节数组）
     * @param keyParams 密钥参数（包含nonce和tag）
     * @param encryptedData 加密的数据（十六进制字符串）
     * @return 解密后的数据（字节数组）
     */
    @Throws(Exception::class)
    fun decryptWithKey(
        key: ByteArray,
        keyParams: KeyParams,
        encryptedData: String
    ): ByteArray {
        // 使用指定的密钥解密加密的数据
        val encryptedBytes = hexStringToByteArray(encryptedData)
        val tagBytes = hexStringToByteArray(keyParams.tag)
        val nonceBytes = hexStringToByteArray(keyParams.nonce)
        
        // GCM模式需要将tag附加到密文末尾
        val cipherTextWithTag = encryptedBytes + tagBytes
        
        // 使用AES-GCM解密
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonceBytes)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(cipherTextWithTag)
    }
    
    /**
     * 使用指定密钥解密数据（Base64格式）
     * @param key 密钥（字节数组）
     * @param keyParams 密钥参数（包含nonce和tag）
     * @param encryptedData 加密的数据（Base64字符串）
     * @return 解密后的数据（字节数组）
     */
    @Throws(Exception::class)
    fun decryptWithKeyBase64(
        key: ByteArray,
        keyParams: KeyParams,
        encryptedData: String
    ): ByteArray {
        // 使用指定的密钥解密加密的数据
        val encryptedBytes = Base64.getDecoder().decode(encryptedData)
        val tagBytes = hexStringToByteArray(keyParams.tag)
        val nonceBytes = hexStringToByteArray(keyParams.nonce)
        
        // GCM模式需要将tag附加到密文末尾
        val cipherTextWithTag = encryptedBytes + tagBytes
        
        // 使用AES-GCM解密
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, nonceBytes)
        val secretKey = SecretKeySpec(key, "AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        
        return cipher.doFinal(cipherTextWithTag)
    }
    
    /**
     * 检查文件是否为加密的Aegis文件
     * @param jsonContent JSON文件内容
     * @return 如果是加密文件返回true，否则返回false
     */
    fun isEncryptedAegisFile(jsonContent: String): Boolean {
        return try {
            val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            val root = json.parseToJsonElement(jsonContent).jsonObject
            
            // 检查是否存在header和db字段，且db是字符串（加密数据）
            val header = root["header"]
            val db = root["db"]?.jsonPrimitive?.content
            
            header != null && db != null
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 将十六进制字符串转换为字节数组
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val hex = hexString.uppercase()
        val bytes = ByteArray(hex.length / 2)
        for (i in 0 until hex.length step 2) {
            bytes[i / 2] = hex.substring(i, i + 2).toInt(16).toByte()
        }
        return bytes
    }
    
    /**
     * 密钥参数数据类
     */
    data class KeyParams(
        val nonce: String, // 十六进制字符串
        val tag: String     // 十六进制字符串
    )
}